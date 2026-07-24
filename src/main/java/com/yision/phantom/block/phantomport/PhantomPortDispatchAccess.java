package com.yision.phantom.block.phantomport;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.yision.phantom.item.miniphantom.MiniPhantomItem;
import com.yision.phantom.logistics.address.PhantomAddressRules;
import com.yision.phantom.logistics.courier.AirCourierDispatchService;
import com.yision.phantom.logistics.courier.AirCourierHelper;
import com.yision.phantom.logistics.courier.AirCourierTarget;
import com.yision.phantom.logistics.courier.hud.AirCourierHudSync;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

final class PhantomPortDispatchAccess {

	private final PhantomPortBlockEntity port;
	private final PhantomPortInventory inventory;
	private final PhantomPortBeltAccess beltAccess;

	private final EnumMap<Direction, IItemHandler> launchFunnelHandlers = new EnumMap<>(Direction.class);
	private final EnumMap<Direction, IItemHandler> packageFunnelHandlers = new EnumMap<>(Direction.class);
	private final Map<Integer, PendingHudEntry> pendingHudEntries = new HashMap<>();

	PhantomPortDispatchAccess(PhantomPortBlockEntity port,
							  PhantomPortInventory inventory,
							  PhantomPortBeltAccess beltAccess) {
		this.port = port;
		this.inventory = inventory;
		this.beltAccess = beltAccess;

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			launchFunnelHandlers.put(direction, createLaunchFunnelHandler(direction));
			packageFunnelHandlers.put(direction, createPackageFunnelHandler(direction));
		}
	}

	@Nullable IItemHandler getItemHandler(@Nullable Direction side) {
		if (side != null && side.getAxis().isHorizontal()) {
			if (side == beltAccess.specialSide()) {
				return beltAccess.hasManualDispatchFunnel(side) ? launchFunnelHandlers.get(side) : null;
			}
			if (beltAccess.hasManualDispatchFunnel(side)) {
				return packageFunnelHandlers.get(side);
			}
		}
		return inventory.combinedHandler();
	}

	void clearPendingHudEntries() {
		pendingHudEntries.clear();
	}

	private IItemHandler createLaunchFunnelHandler(Direction side) {
		return new IItemHandler() {
			@Override
			public int getSlots() {
				return 1;
			}

			@Override
			public @NotNull ItemStack getStackInSlot(int slot) {
				if (slot != 0) {
					return ItemStack.EMPTY;
				}
				return getDispatchStack(side);
			}

			@Override
			public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
				return stack;
			}

			@Override
			public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
				if (slot != 0 || amount <= 0) {
					return ItemStack.EMPTY;
				}
				DispatchCandidate candidate = findDispatchCandidate(side);
				if (candidate == null) {
					return ItemStack.EMPTY;
				}

				ItemStack extracted = candidate.phantomStack().copy();
				extracted.setCount(1);

				if (!simulate) {
					if (!inventory.hasUsableCarrier()) {
						return ItemStack.EMPTY;
					}
					ItemStack storedCarrier = inventory.extractOneCarrier(false);
					if (storedCarrier.isEmpty()) {
						return ItemStack.EMPTY;
					}
					ItemStack extractedPackage = port.inventory.extractItem(candidate.packageSlot(), 1, false);
					if (extractedPackage.isEmpty()) {
						inventory.returnCarrier(storedCarrier);
						return ItemStack.EMPTY;
					}
					if (candidate.target() instanceof AirCourierTarget.PlayerTarget playerTarget) {
						ServerPlayer player = port.getLevel() instanceof ServerLevel sl
							? sl.getServer().getPlayerList().getPlayer(playerTarget.playerId()) : null;
						if (player != null) {
							UUID hudEntryId = candidate.hudEntryId();
							if (hudEntryId != null) {
								MiniPhantomItem.setHudEntryId(extracted, hudEntryId);
								AirCourierHudSync.onCourierPreparing(player, MiniPhantomItem.copyCargoPackage(extracted), hudEntryId);
							}
						}
					}
					pendingHudEntries.remove(candidate.packageSlot());
				}

				return extracted;
			}

			@Override
			public int getSlotLimit(int slot) {
				return 1;
			}

			@Override
			public boolean isItemValid(int slot, @NotNull ItemStack stack) {
				return false;
			}
		};
	}

	private IItemHandler createPackageFunnelHandler(Direction side) {
		return new IItemHandler() {
			@Override
			public int getSlots() {
				return 1;
			}

			@Override
			public @NotNull ItemStack getStackInSlot(int slot) {
				if (slot != 0) {
					return ItemStack.EMPTY;
				}
				return getLocalPackageStack();
			}

			@Override
			public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
				return stack;
			}

			@Override
			public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
				if (slot != 0 || amount <= 0) {
					return ItemStack.EMPTY;
				}
				int packageSlot = findLocalPackageSlot();
				if (packageSlot < 0) {
					return ItemStack.EMPTY;
				}
				ItemStack extracted = port.inventory.extractItem(packageSlot, amount, simulate);
				return extracted;
			}

			@Override
			public int getSlotLimit(int slot) {
				return 64;
			}

			@Override
			public boolean isItemValid(int slot, @NotNull ItemStack stack) {
				return false;
			}
		};
	}

	private @NotNull ItemStack getDispatchStack(Direction side) {
		DispatchCandidate candidate = findDispatchCandidate(side);
		if (candidate == null) {
			return ItemStack.EMPTY;
		}
		return candidate.phantomStack().copy();
	}

	private @Nullable DispatchCandidate findDispatchCandidate(Direction side) {
		if (!(port.getLevel() instanceof ServerLevel serverLevel)) {
			return null;
		}
		if (!beltAccess.canDispatchThrough(side)) {
			return null;
		}
		if (!inventory.hasUsableCarrier()) {
			return null;
		}

		Direction heading = beltAccess.resolveBeltHeading(side);
		int headingAngle = AirCourierHelper.getHeadingAngle(heading);
		String filterString = port.getFilterString();
		for (int slot = 0; slot < port.inventory.getSlots(); slot++) {
			ItemStack packageInSlot = port.inventory.getStackInSlot(slot);
			if (packageInSlot.isEmpty() || !PackageItem.isPackage(packageInSlot)) {
				continue;
			}
			if (filterString != null && !PhantomAddressRules.isBlank(filterString)
				&& PhantomAddressRules.matchesPackage(packageInSlot, filterString)) {
				continue;
			}
			AirCourierTarget target = AirCourierDispatchService.resolvePackageTarget(serverLevel, packageInSlot,
				Vec3.atCenterOf(port.getBlockPos()), serverLevel.dimension(), port.getBlockPos());
			if (target == null) {
				continue;
			}

			ItemStack singlePackage = packageInSlot.copy();
			singlePackage.setCount(1);
			ItemStack phantomStack = MiniPhantomItem.createLoadedWithHeading(singlePackage, headingAngle);
			MiniPhantomItem.setReturnTarget(phantomStack, serverLevel.dimension(), port.getBlockPos());

			UUID hudEntryId = null;
			if (target instanceof AirCourierTarget.PlayerTarget) {
				hudEntryId = getOrCreatePendingHudEntryId(slot, singlePackage);
				MiniPhantomItem.setHudEntryId(phantomStack, hudEntryId);
			}
			return new DispatchCandidate(slot, phantomStack, target, hudEntryId);
		}
		return null;
	}

	private UUID getOrCreatePendingHudEntryId(int slot, ItemStack packageStack) {
		PendingHudEntry existing = pendingHudEntries.get(slot);
		if (existing != null && ItemStack.matches(existing.packageSnapshot(), packageStack)) {
			return existing.hudEntryId();
		}
		UUID newId = UUID.randomUUID();
		pendingHudEntries.put(slot, new PendingHudEntry(newId, packageStack.copy()));
		return newId;
	}

	private @NotNull ItemStack getLocalPackageStack() {
		int packageSlot = findLocalPackageSlot();
		if (packageSlot < 0) {
			return ItemStack.EMPTY;
		}
		return port.inventory.getStackInSlot(packageSlot).copy();
	}

	private int findLocalPackageSlot() {
		String filterString = port.getFilterString();
		if (filterString == null || PhantomAddressRules.isBlank(filterString)) {
			return -1;
		}
		for (int slot = 0; slot < port.inventory.getSlots(); slot++) {
			ItemStack packageInSlot = port.inventory.getStackInSlot(slot);
			if (packageInSlot.isEmpty() || !PackageItem.isPackage(packageInSlot)) {
				continue;
			}
			if (PhantomAddressRules.matchesPackage(packageInSlot, filterString)) {
				return slot;
			}
		}
		return -1;
	}

	private record PendingHudEntry(UUID hudEntryId, ItemStack packageSnapshot) {}

	private record DispatchCandidate(int packageSlot, ItemStack phantomStack,
									 @Nullable AirCourierTarget target, @Nullable UUID hudEntryId) {}
}
