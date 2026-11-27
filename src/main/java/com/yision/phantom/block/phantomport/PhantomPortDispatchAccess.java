package com.yision.phantom.block.phantomport;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.yision.phantom.item.miniphantom.MiniPhantomDispatch;
import com.yision.phantom.item.miniphantom.MiniPhantomItem;
import com.yision.phantom.logistics.address.PhantomAddressRules;
import com.yision.phantom.logistics.courier.AirCourierDeliveryService;
import com.yision.phantom.logistics.courier.AirCourierDispatchService;
import com.yision.phantom.logistics.courier.AirCourierHelper;
import com.yision.phantom.logistics.courier.AirCourierTarget;
import com.yision.phantom.logistics.courier.hud.AirCourierHudSync;
import com.yision.phantom.registry.AllDataComponents;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.items.IItemHandler;

final class PhantomPortDispatchAccess {
	private final PhantomPortBlockEntity port;
	private final PhantomPortInventory inventory;
	private final PhantomPortBeltAccess beltAccess;

	private final EnumMap<Direction, IItemHandler> launchFunnelHandlers = new EnumMap<>(Direction.class);
	private final IItemHandler packageFunnelHandler = createPackageFunnelHandler();
	private final Map<Integer, PendingDispatch> pendingDispatches = new HashMap<>();

	PhantomPortDispatchAccess(PhantomPortBlockEntity port,
		PhantomPortInventory inventory,
		PhantomPortBeltAccess beltAccess) {
		this.port = port;
		this.inventory = inventory;
		this.beltAccess = beltAccess;

		for (Direction direction : Direction.Plane.HORIZONTAL)
			launchFunnelHandlers.put(direction, createLaunchFunnelHandler(direction));
	}

	@Nullable
	IItemHandler getItemHandler(@Nullable Direction side) {
		if (side != null && side.getAxis().isHorizontal()) {
			if (side == beltAccess.specialSide()) {
				return beltAccess.hasManualDispatchFunnel(side) ? launchFunnelHandlers.get(side) : null;
			}
			if (beltAccess.hasManualDispatchFunnel(side)) {
				return packageFunnelHandler;
			}
		}
		return inventory.combinedHandler();
	}

	void clearPendingDispatches() {
		pendingDispatches.clear();
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

				ItemStack extracted = candidate.phantomStack();

				if (!simulate) {
					ItemStack storedCarrier = inventory.extractOneCarrier();
					if (storedCarrier.isEmpty()) {
						return ItemStack.EMPTY;
					}
					ItemStack extractedPackage = port.inventory.extractItem(candidate.packageSlot(), 1, false);
					if (extractedPackage.isEmpty()) {
						inventory.returnCarrier(storedCarrier);
						return ItemStack.EMPTY;
					}
					if (!inventory.hasCarrier())
						port.cancelPendingReturns();
					if (candidate.target() instanceof AirCourierTarget.PlayerTarget playerTarget) {
						ServerPlayer player = port.getLevel() instanceof ServerLevel sl
							? sl.getServer().getPlayerList().getPlayer(playerTarget.playerId())
							: null;
						UUID hudEntryId = MiniPhantomItem.getHudEntryId(extracted);
						if (player != null && hudEntryId != null) {
							AirCourierHudSync.onCourierPreparing(player, MiniPhantomItem.copyCargoPackage(extracted),
								hudEntryId);
						}
					}
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

	private IItemHandler createPackageFunnelHandler() {
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
				return port.inventory.extractItem(packageSlot, amount, simulate);
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
		return candidate.phantomStack();
	}

	private @Nullable DispatchCandidate findDispatchCandidate(Direction side) {
		if (!(port.getLevel() instanceof ServerLevel serverLevel)) {
			return null;
		}
		if (!beltAccess.canDispatchThrough(side)) {
			return null;
		}
		if (!inventory.hasCarrier()) {
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

			UUID id = getOrCreateDispatchId(slot, singlePackage, target);
			if (!lockDispatch(serverLevel, phantomStack, target, id))
				continue;
			if (target instanceof AirCourierTarget.PlayerTarget)
				MiniPhantomItem.setHudEntryId(phantomStack, id);
			return new DispatchCandidate(slot, phantomStack, target);
		}
		return null;
	}

	private boolean lockDispatch(ServerLevel level, ItemStack stack, AirCourierTarget target, UUID id) {
		PhantomPortReference destination = null;
		UUID playerId = null;
		switch (target) {
			case AirCourierTarget.PhantomPortTarget portTarget -> {
				ServerLevel targetLevel = level.getServer().getLevel(portTarget.dimension());
				PhantomPortBlockEntity targetPort =
					AirCourierDeliveryService.resolveTargetPhantomPort(targetLevel, portTarget.pos());
				if (targetPort == null || targetLevel == null)
					return false;
				destination = targetPort.courierReference(targetLevel);
			}
			case AirCourierTarget.PlayerTarget playerTarget -> playerId = playerTarget.playerId();
		}
		stack.set(AllDataComponents.MINI_PHANTOM_DISPATCH.get(),
			new MiniPhantomDispatch(id, port.courierReference(level), target.dimension(),
				Optional.ofNullable(destination), Optional.ofNullable(playerId)));
		return true;
	}

	private UUID getOrCreateDispatchId(int slot, ItemStack packageStack, AirCourierTarget target) {
		PendingDispatch existing = pendingDispatches.get(slot);
		if (existing != null && ItemStack.matches(existing.packageSnapshot(), packageStack)
			&& existing.target().equals(target)) {
			return existing.id();
		}
		UUID id = UUID.randomUUID();
		pendingDispatches.put(slot, new PendingDispatch(id, packageStack.copy(), target));
		return id;
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

	private record PendingDispatch(UUID id, ItemStack packageSnapshot, AirCourierTarget target) {
	}

	private record DispatchCandidate(int packageSlot, ItemStack phantomStack, AirCourierTarget target) {
	}
}
