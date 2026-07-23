package com.yision.phantom.block.phantomport;

import com.yision.phantom.item.miniphantom.MiniPhantomItem;
import com.yision.phantom.logistics.courier.AirCourierDispatchService;
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
	private static final int RETURN_RETRY_INTERVAL_TICKS = 20;

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
		long gameTime = ((ServerLevel) port.getLevel()).getGameTime();
		if (task.relativeTime()) {
			pendingReturnCarriers.removeFirst();
			task = task.resolveRelative(gameTime);
			pendingReturnCarriers.addFirst(task);
			port.setChanged();
		}
		if (gameTime < task.nextAttemptGameTime()) {
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
		if (gameTime >= task.expiresAtGameTime()) {
			pendingReturnCarriers.removeFirst();
			if (task.isPlayerReturn()) {
				inventory.dropOneCarrier();
			}
			port.markPortContentsChanged();
			return;
		}
		pendingReturnCarriers.removeFirst();
		pendingReturnCarriers.addFirst(task.withNextAttempt(
			Math.min(gameTime + RETURN_RETRY_INTERVAL_TICKS, task.expiresAtGameTime())));
		port.setChanged();
	}

	boolean tryQueueReturnCarrier(@Nullable ResourceKey<Level> returnDimension,
								  @Nullable BlockPos returnPos) {
		if (!(port.getLevel() instanceof ServerLevel serverLevel) || returnDimension == null || returnPos == null
			|| !AirCourierDispatchService.canReceiveCarrierTarget(serverLevel, returnDimension, returnPos)) {
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
			returnDimension, returnPos, currentGameTime(), Math.max(0, delayTicks), RETURN_RETRY_TICKS));
		port.markPortContentsChanged();
	}

	private void schedulePendingReturnToPlayer(UUID playerId, int delayTicks) {
		pendingReturnCarriers.addLast(PendingReturnCarrier.toPlayer(
			playerId, currentGameTime(), Math.max(0, delayTicks), RETURN_RETRY_TICKS));
		port.markPortContentsChanged();
	}

	private long currentGameTime() {
		return port.getLevel() == null ? 0 : port.getLevel().getGameTime();
	}

	private boolean tryQueueStoredReturnCarrier(@Nullable ResourceKey<Level> returnDimension, @Nullable BlockPos returnPos) {
		if (!(port.getLevel() instanceof ServerLevel serverLevel) || returnDimension == null || returnPos == null
			|| !AirCourierDispatchService.canReceiveCarrierTarget(serverLevel, returnDimension, returnPos)) {
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
				if (task.relativeTime()) {
					entry.putInt("DelayTicks", (int) task.nextAttemptGameTime());
					entry.putInt("RetryTicks",
						(int) Math.max(0, task.expiresAtGameTime() - task.nextAttemptGameTime()));
				} else {
					entry.putLong("NextAttemptGameTime", task.nextAttemptGameTime());
					entry.putLong("ExpiresAtGameTime", task.expiresAtGameTime());
				}
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
				boolean absoluteTime = entry.contains("NextAttemptGameTime", Tag.TAG_LONG)
					&& entry.contains("ExpiresAtGameTime", Tag.TAG_LONG);
				long nextAttempt = absoluteTime ? entry.getLong("NextAttemptGameTime")
					: Math.max(0, entry.getInt("DelayTicks"));
				long expiresAt = absoluteTime ? entry.getLong("ExpiresAtGameTime")
					: nextAttempt + (entry.contains("RetryTicks")
						? Math.max(0, entry.getInt("RetryTicks")) : RETURN_RETRY_TICKS);
				if ("player".equals(type) && entry.hasUUID("PlayerId")) {
					pendingReturnCarriers.addLast(new PendingReturnCarrier(
						null, null, entry.getUUID("PlayerId"), nextAttempt, expiresAt, !absoluteTime));
				} else if ("phantom_port".equals(type)) {
					ResourceKey<Level> dim = entry.contains("Dimension")
						? ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(entry.getString("Dimension")))
						: null;
					BlockPos pos = entry.contains("Pos")
						? NbtUtils.readBlockPos(entry, "Pos").orElse(null)
						: null;
					if (dim != null && pos != null) {
						pendingReturnCarriers.addLast(new PendingReturnCarrier(
							dim, pos.immutable(), null, nextAttempt, expiresAt, !absoluteTime));
					}
				}
			}
		}
	}

	private record PendingReturnCarrier(
		@Nullable ResourceKey<Level> dimension,
		@Nullable BlockPos pos,
		@Nullable UUID playerId,
		long nextAttemptGameTime,
		long expiresAtGameTime,
		boolean relativeTime
	) {
		static PendingReturnCarrier toPhantomPort(ResourceKey<Level> dim, BlockPos p, long now, int delay, int retry) {
			long readyAt = now + delay;
			return new PendingReturnCarrier(dim, p.immutable(), null, readyAt, readyAt + retry, false);
		}

		static PendingReturnCarrier toPlayer(UUID pid, long now, int delay, int retry) {
			long readyAt = now + delay;
			return new PendingReturnCarrier(null, null, pid, readyAt, readyAt + retry, false);
		}

		PendingReturnCarrier withNextAttempt(long nextAttempt) {
			return new PendingReturnCarrier(
				dimension, pos, playerId, nextAttempt, expiresAtGameTime, false);
		}

		PendingReturnCarrier resolveRelative(long now) {
			return new PendingReturnCarrier(dimension, pos, playerId,
				now + nextAttemptGameTime, now + expiresAtGameTime, false);
		}

		boolean isPlayerReturn() {
			return playerId != null;
		}

		boolean isPhantomPortReturn() {
			return dimension != null && pos != null;
		}
	}
}
