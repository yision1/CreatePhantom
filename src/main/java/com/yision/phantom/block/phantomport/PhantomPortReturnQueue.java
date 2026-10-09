package com.yision.phantom.block.phantomport;

import com.yision.phantom.item.miniphantom.MiniPhantomItem;
import com.yision.phantom.entity.courier.AirCourierEntity;
import com.yision.phantom.logistics.courier.AirCourierDeliveryService;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

final class PhantomPortReturnQueue {

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
		if (pendingReturnCarriers.isEmpty() || !(port.getLevel() instanceof ServerLevel serverLevel)) return;
		if (!inventory.hasUsableCarrier()) {
			pendingReturnCarriers.clear();
			port.setChanged();
			return;
		}
		long gameTime = serverLevel.getGameTime();
		for (int remaining = pendingReturnCarriers.size(); remaining > 0; remaining--) {
			PendingReturnCarrier task = pendingReturnCarriers.removeFirst();
			if (task.relativeTime()) {
				task = task.resolveRelative(gameTime);
				port.setChanged();
			}
			if (gameTime < task.nextAttemptGameTime()) {
				pendingReturnCarriers.addLast(task);
				continue;
			}
			AirCourierDeliveryService.TargetState state = task.dropPending()
				? AirCourierDeliveryService.TargetState.UNAVAILABLE
				: AirCourierDeliveryService.targetState(serverLevel.getServer(),
					task.isPlayerReturn() ? AirCourierEntity.Mission.CARRIER_RETURN_TO_PLAYER : AirCourierEntity.Mission.CARRIER_RETURN,
					task.dimension(), task.pos(), task.playerId(), ItemStack.EMPTY);
			if (state == AirCourierDeliveryService.TargetState.PLAYER_OFFLINE) {
				pendingReturnCarriers.addLast(task);
				continue;
			}
			if (state == AirCourierDeliveryService.TargetState.UNAVAILABLE) {
				if (!inventory.dropOneCarrier()) pendingReturnCarriers.addLast(task.asDropPending());
			} else if (!tryQueueStoredReturnCarrier(task)) {
				pendingReturnCarriers.addLast(task.withNextAttempt(gameTime + RETURN_RETRY_INTERVAL_TICKS));
			}
			port.setChanged();
			return;
		}
	}

	boolean receivePackageAndScheduleCarrierReturnToPlayer(ItemStack box, UUID playerId, int delayTicks) {
		if (!inventory.canReceiveCourier(box)) {
			return false;
		}
		ItemStack carrier = com.yision.phantom.registry.AllItems.MINI_PHANTOM.asStack();
		if (!inventory.carrierInventory.insertItem(0, carrier.copy(), false).isEmpty()) {
			return false;
		}
		if (!inventory.addPackage(box.copy(), false)) {
			inventory.carrierInventory.extractItem(0, 1, false);
			return false;
		}
		schedulePendingReturnToPlayer(playerId, delayTicks);
		return true;
	}

	boolean receivePackageAndHandleCarrier(
		ItemStack box,
		@Nullable ResourceKey<Level> returnDimension,
		@Nullable BlockPos returnPos
	) {
		if (!inventory.receiveCourier(box)) return false;
		if (returnDimension != null && returnPos != null) {
			schedulePendingReturnCarrier(returnDimension, returnPos, RETURN_LAUNCH_DELAY_TICKS);
		}
		return true;
	}

	private void schedulePendingReturnCarrier(ResourceKey<Level> returnDimension, BlockPos returnPos, int delayTicks) {
		pendingReturnCarriers.addLast(PendingReturnCarrier.toPhantomPort(
			returnDimension, returnPos, currentGameTime(), Math.max(0, delayTicks)));
		port.setChanged();
	}

	private void schedulePendingReturnToPlayer(UUID playerId, int delayTicks) {
		pendingReturnCarriers.addLast(PendingReturnCarrier.toPlayer(
			playerId, currentGameTime(), Math.max(0, delayTicks)));
		port.setChanged();
	}

	private long currentGameTime() {
		return port.getLevel() == null ? 0 : port.getLevel().getGameTime();
	}

	private boolean tryQueueStoredReturnCarrier(PendingReturnCarrier task) {
		Direction side = beltAccess.specialSide();
		if (!beltAccess.hasManualDispatchFunnel(side)) return false;
		IItemHandler beltHandler = beltAccess.launchBeltHandler(side);
		if (beltHandler == null) return false;
		ItemStack returningCarrier = task.isPlayerReturn()
			? MiniPhantomItem.returningToPlayer(task.playerId()) : MiniPhantomItem.returningTo(task.dimension(), task.pos());
		if (!beltHandler.insertItem(0, returningCarrier.copy(), true).isEmpty()) return false;
		ItemStack storedCarrier = inventory.extractOneCarrier(false);
		if (storedCarrier.isEmpty()) return false;
		if (beltHandler.insertItem(0, returningCarrier.copy(), false).isEmpty()) return true;
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
				} else {
					entry.putLong("NextAttemptGameTime", task.nextAttemptGameTime());
				}
				entry.putBoolean("DropPending", task.dropPending());
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
				boolean absoluteTime = entry.contains("NextAttemptGameTime", Tag.TAG_LONG);
				long nextAttempt = absoluteTime ? entry.getLong("NextAttemptGameTime")
					: Math.max(0, entry.getInt("DelayTicks"));
				boolean dropPending = entry.getBoolean("DropPending");
				if ("player".equals(type) && entry.hasUUID("PlayerId")) {
					pendingReturnCarriers.addLast(new PendingReturnCarrier(
						null, null, entry.getUUID("PlayerId"), nextAttempt, !absoluteTime, dropPending));
				} else if ("phantom_port".equals(type)) {
					ResourceKey<Level> dim = entry.contains("Dimension")
						? ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(entry.getString("Dimension")))
						: null;
					BlockPos pos = entry.contains("Pos")
						? NbtUtils.readBlockPos(entry, "Pos").orElse(null)
						: null;
					if (dim != null && pos != null) {
						pendingReturnCarriers.addLast(new PendingReturnCarrier(
							dim, pos.immutable(), null, nextAttempt, !absoluteTime, dropPending));
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
		boolean relativeTime,
		boolean dropPending
	) {
		static PendingReturnCarrier toPhantomPort(ResourceKey<Level> dim, BlockPos pos, long now, int delay) {
			return new PendingReturnCarrier(dim, pos.immutable(), null, now + delay, false, false);
		}

		static PendingReturnCarrier toPlayer(UUID playerId, long now, int delay) {
			return new PendingReturnCarrier(null, null, playerId, now + delay, false, false);
		}

		PendingReturnCarrier withNextAttempt(long nextAttempt) {
			return new PendingReturnCarrier(dimension, pos, playerId, nextAttempt, false, dropPending);
		}

		PendingReturnCarrier resolveRelative(long now) {
			return withNextAttempt(now + nextAttemptGameTime);
		}

		PendingReturnCarrier asDropPending() {
			return new PendingReturnCarrier(dimension, pos, playerId, nextAttemptGameTime, false, true);
		}

		boolean isPlayerReturn() {
			return playerId != null;
		}
	}

}
