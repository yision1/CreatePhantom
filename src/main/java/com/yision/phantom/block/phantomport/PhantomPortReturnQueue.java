package com.yision.phantom.block.phantomport;

import com.yision.phantom.item.miniphantom.MiniPhantomItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

final class PhantomPortReturnQueue {

	static final int RETURN_RETRY_TICKS = 100;
	static final int RETURN_LAUNCH_DELAY_TICKS = 40;

	private final PhantomPortBlockEntity port;
	private final PhantomPortInventory inventory;
	private final PhantomPortBeltAccess beltAccess;
	private final Deque<PendingReturnCarrier> pendingReturnCarriers = new ArrayDeque<>();

	PhantomPortReturnQueue(PhantomPortBlockEntity port,
						   PhantomPortInventory inventory,
						   PhantomPortBeltAccess beltAccess) {
		this.port = port;
		this.inventory = inventory;
		this.beltAccess = beltAccess;
	}

	void tick() {
		if (pendingReturnCarriers.isEmpty()) {
			return;
		}
		if (!inventory.hasStoredCarrier()) {
			pendingReturnCarriers.clear();
			port.setChanged();
			return;
		}
		PendingReturnCarrier task = pendingReturnCarriers.peekFirst();
		if (task == null) {
			return;
		}
		if (task.delayTicks() > 0) {
			pendingReturnCarriers.removeFirst();
			pendingReturnCarriers.addFirst(task.withDelay(task.delayTicks() - 1));
			port.setChanged();
			return;
		}
		if (task.isPlayerReturn()) {
			if (tryQueueStoredReturnCarrierToPlayer(task.playerId())) {
				pendingReturnCarriers.removeFirst();
				port.markPortContentsChanged();
				return;
			}
		} else if (task.isPhantomPortReturn()) {
			if (tryQueueStoredReturnCarrier(task.dimension(), task.pos())) {
				pendingReturnCarriers.removeFirst();
				port.markPortContentsChanged();
				return;
			}
		}
		int remaining = task.retryTicks() - 1;
		if (remaining <= 0) {
			pendingReturnCarriers.removeFirst();
			if (task.isPlayerReturn()) {
				inventory.dropOneCarrier();
			}
			port.markPortContentsChanged();
			return;
		}
		pendingReturnCarriers.removeFirst();
		pendingReturnCarriers.addFirst(task.withRetry(remaining));
		port.setChanged();
	}

	boolean tryQueueReturnCarrier(@Nullable ResourceKey<Level> returnDimension,
								  @Nullable BlockPos returnPos) {
		if (!(port.getLevel() instanceof ServerLevel) || returnDimension == null || returnPos == null) {
			return false;
		}
		return beltAccess.tryInsertToLaunchBelt(MiniPhantomItem.returningTo(returnDimension, returnPos));
	}

	boolean receivePackageAndScheduleCarrierReturnToPlayer(ItemStack box, UUID playerId, int delayTicks) {
		if (!inventory.canReceivePackage(box) || !inventory.canReceiveCarrier()) {
			return false;
		}
		ItemStack carrier = com.yision.phantom.registry.AllItems.MINI_PHANTOM.asStack();
		if (!inventory.carrierInventory.insertItem(0, carrier.copy(), false).isEmpty()) {
			return false;
		}
		if (!inventory.addPackage(box.copy(), false)) {
			inventory.carrierInventory.extractItem(0, 1, false);
			port.markPortContentsChanged();
			return false;
		}
		schedulePendingReturnToPlayer(playerId, delayTicks);
		return true;
	}

	PhantomPortBlockEntity.CourierReceiveResult receivePackageAndHandleCarrier(
		ItemStack box,
		@Nullable ResourceKey<Level> returnDimension,
		@Nullable BlockPos returnPos
	) {
		if (!inventory.canReceivePackage(box)) {
			return PhantomPortBlockEntity.CourierReceiveResult.REJECTED;
		}

		if (returnDimension != null && returnPos != null) {
			if (!inventory.canReceiveCarrier()) {
				return PhantomPortBlockEntity.CourierReceiveResult.REJECTED;
			}
			if (!inventory.receivePackage(box)) {
				return PhantomPortBlockEntity.CourierReceiveResult.REJECTED;
			}
			if (!inventory.receiveCarrier()) {
				return PhantomPortBlockEntity.CourierReceiveResult.REJECTED;
			}
			schedulePendingReturnCarrier(returnDimension, returnPos, RETURN_LAUNCH_DELAY_TICKS);
			return PhantomPortBlockEntity.CourierReceiveResult.CARRIER_STORED;
		}

		return inventory.receiveCourier(box)
			? PhantomPortBlockEntity.CourierReceiveResult.CARRIER_STORED
			: PhantomPortBlockEntity.CourierReceiveResult.REJECTED;
	}

	private void schedulePendingReturnCarrier(ResourceKey<Level> returnDimension, BlockPos returnPos, int delayTicks) {
		pendingReturnCarriers.addLast(PendingReturnCarrier.toPhantomPort(
			returnDimension, returnPos, Math.max(0, delayTicks), RETURN_RETRY_TICKS));
		port.markPortContentsChanged();
	}

	private void schedulePendingReturnToPlayer(UUID playerId, int delayTicks) {
		pendingReturnCarriers.addLast(PendingReturnCarrier.toPlayer(
			playerId, Math.max(0, delayTicks), RETURN_RETRY_TICKS));
		port.markPortContentsChanged();
	}

	private boolean tryQueueStoredReturnCarrier(@Nullable ResourceKey<Level> returnDimension, @Nullable BlockPos returnPos) {
		if (!(port.getLevel() instanceof ServerLevel) || returnDimension == null || returnPos == null) {
			return false;
		}
		if (!inventory.hasStoredCarrier()) {
			return false;
		}
		Direction side = beltAccess.specialSide();
		if (!beltAccess.hasManualDispatchFunnel(side) || !beltAccess.isBeltOutputCompatible(side)) {
			return false;
		}
		IItemHandler beltHandler = beltAccess.launchBeltHandler(side);
		if (beltHandler == null) {
			return false;
		}
		ItemStack returningCarrier = MiniPhantomItem.returningTo(returnDimension, returnPos);
		if (!beltHandler.insertItem(0, returningCarrier.copy(), true).isEmpty()) {
			return false;
		}

		ItemStack storedCarrier = inventory.extractOneCarrier(false);
		if (storedCarrier.isEmpty()) {
			return false;
		}
		ItemStack remainder = beltHandler.insertItem(0, returningCarrier.copy(), false);
		if (remainder.isEmpty()) {
			port.markPortContentsChanged();
			return true;
		}
		inventory.returnCarrier(storedCarrier);
		return false;
	}

	private boolean tryQueueStoredReturnCarrierToPlayer(UUID playerId) {
		if (!(port.getLevel() instanceof ServerLevel serverLevel)) {
			return false;
		}
		ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(playerId);
		if (player == null || !player.isAlive()) {
			return false;
		}
		if (!inventory.hasStoredCarrier()) {
			return false;
		}
		Direction side = beltAccess.specialSide();
		if (!beltAccess.hasManualDispatchFunnel(side) || !beltAccess.isBeltOutputCompatible(side)) {
			return false;
		}
		IItemHandler beltHandler = beltAccess.launchBeltHandler(side);
		if (beltHandler == null) {
			return false;
		}
		ItemStack returningCarrier = MiniPhantomItem.returningToPlayer(playerId);
		if (!beltHandler.insertItem(0, returningCarrier.copy(), true).isEmpty()) {
			return false;
		}

		ItemStack storedCarrier = inventory.extractOneCarrier(false);
		if (storedCarrier.isEmpty()) {
			return false;
		}
		ItemStack remainder = beltHandler.insertItem(0, returningCarrier.copy(), false);
		if (remainder.isEmpty()) {
			port.markPortContentsChanged();
			return true;
		}
		inventory.returnCarrier(storedCarrier);
		return false;
	}

	void write(CompoundTag tag) {
		if (!pendingReturnCarriers.isEmpty()) {
			ListTag list = new ListTag();
			for (PendingReturnCarrier task : pendingReturnCarriers) {
				CompoundTag entry = new CompoundTag();
				if (task.isPlayerReturn()) {
					entry.putString("Type", "player");
					entry.putUUID("PlayerId", task.playerId());
				} else {
					entry.putString("Type", "phantom_port");
					if (task.dimension() != null) {
						entry.putString("Dimension", task.dimension().location().toString());
					}
					if (task.pos() != null) {
						entry.put("Pos", NbtUtils.writeBlockPos(task.pos()));
					}
				}
				entry.putInt("DelayTicks", task.delayTicks());
				entry.putInt("RetryTicks", task.retryTicks());
				list.add(entry);
			}
			tag.put("PendingReturnCarriers", list);
		}
	}

	void read(CompoundTag tag) {
		pendingReturnCarriers.clear();
		if (tag.contains("PendingReturnCarriers", Tag.TAG_LIST)) {
			ListTag list = tag.getList("PendingReturnCarriers", Tag.TAG_COMPOUND);
			for (int i = 0; i < list.size(); i++) {
				CompoundTag entry = list.getCompound(i);
				String type = entry.getString("Type");
				int delay = entry.contains("DelayTicks") ? entry.getInt("DelayTicks") : 0;
				int retry = entry.contains("RetryTicks") ? entry.getInt("RetryTicks") : RETURN_RETRY_TICKS;
				if ("player".equals(type) && entry.hasUUID("PlayerId")) {
					pendingReturnCarriers.addLast(PendingReturnCarrier.toPlayer(entry.getUUID("PlayerId"), delay, retry));
				} else if ("phantom_port".equals(type)) {
					ResourceKey<Level> dim = entry.contains("Dimension")
						? ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(entry.getString("Dimension")))
						: null;
					BlockPos pos = entry.contains("Pos")
						? NbtUtils.readBlockPos(entry, "Pos").orElse(null)
						: null;
					if (dim != null && pos != null) {
						pendingReturnCarriers.addLast(PendingReturnCarrier.toPhantomPort(dim, pos, delay, retry));
					}
				}
			}
		}
	}

	private record PendingReturnCarrier(
		@Nullable ResourceKey<Level> dimension,
		@Nullable BlockPos pos,
		@Nullable UUID playerId,
		int delayTicks,
		int retryTicks
	) {
		static PendingReturnCarrier toPhantomPort(ResourceKey<Level> dim, BlockPos p, int delay, int retry) {
			return new PendingReturnCarrier(dim, p.immutable(), null, delay, retry);
		}

		static PendingReturnCarrier toPlayer(UUID pid, int delay, int retry) {
			return new PendingReturnCarrier(null, null, pid, delay, retry);
		}

		PendingReturnCarrier withDelay(int newDelay) {
			return new PendingReturnCarrier(dimension, pos, playerId, newDelay, retryTicks);
		}

		PendingReturnCarrier withRetry(int newRetry) {
			return new PendingReturnCarrier(dimension, pos, playerId, delayTicks, newRetry);
		}

		boolean isPlayerReturn() {
			return playerId != null;
		}

		boolean isPhantomPortReturn() {
			return dimension != null && pos != null;
		}
	}
}
