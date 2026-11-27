package com.yision.phantom.block.phantomport;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.yision.phantom.item.miniphantom.MiniPhantomItem;
import com.yision.phantom.logistics.courier.AirCourierDeliveryService;
import com.yision.phantom.registry.AllDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.items.IItemHandler;

final class PhantomPortReturnQueue {
	private static final int RETURN_LAUNCH_DELAY_TICKS = 40;
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

	boolean clear() {
		if (pendingReturnCarriers.isEmpty())
			return false;
		pendingReturnCarriers.clear();
		return true;
	}

	void tick() {
		if (pendingReturnCarriers.isEmpty() || !(port.getLevel() instanceof ServerLevel serverLevel))
			return;
		if (!inventory.hasCarrier() || beltAccess.hasMissingReturnOutlet()) {
			clear();
			port.setChanged();
			return;
		}
		long gameTime = serverLevel.getGameTime();
		for (int remaining = pendingReturnCarriers.size(); remaining > 0; remaining--) {
			PendingReturnCarrier task = pendingReturnCarriers.removeFirst();
			if (gameTime < task.nextAttemptGameTime()
				|| task.isPlayerReturn()
					&& AirCourierDeliveryService.resolvePlayer(serverLevel.getServer(), task.playerId()) == null) {
				pendingReturnCarriers.addLast(task);
				continue;
			}
			if (tryQueueStoredReturnCarrier(task)) {
				if (!inventory.hasCarrier())
					clear();
				port.notifyUpdate();
			} else {
				pendingReturnCarriers.addLast(task.withNextAttempt(gameTime + RETURN_RETRY_INTERVAL_TICKS));
				port.setChanged();
			}
			return;
		}
	}

	boolean receivePackageAndScheduleCarrierReturnToPlayer(ItemStack box, UUID playerId) {
		return receiveForReturn(box, new PendingReturnCarrier(null, null, null, playerId,
			currentGameTime() + RETURN_LAUNCH_DELAY_TICKS));
	}

	boolean receivePackageAndHandleCarrier(ItemStack box, @Nullable ResourceKey<Level> returnDimension,
		@Nullable BlockPos returnPos, @Nullable PhantomPortReference returnPort) {
		if (returnDimension == null || returnPos == null)
			return inventory.receiveCourier(box);
		return receiveForReturn(box, new PendingReturnCarrier(returnDimension, returnPos, returnPort, null,
			currentGameTime() + RETURN_LAUNCH_DELAY_TICKS));
	}

	private boolean receiveForReturn(ItemStack box, PendingReturnCarrier task) {
		if (!inventory.receiveCourier(box))
			return false;
		if (!beltAccess.hasMissingReturnOutlet()) {
			pendingReturnCarriers.addLast(task);
			port.setChanged();
		}
		return true;
	}

	private long currentGameTime() {
		return port.getLevel() == null ? 0 : port.getLevel().getGameTime();
	}

	private boolean tryQueueStoredReturnCarrier(PendingReturnCarrier task) {
		Direction side = beltAccess.specialSide();
		if (!beltAccess.canLaunchThrough(side))
			return false;
		IItemHandler beltHandler = beltAccess.launchBeltHandler(side);
		if (beltHandler == null)
			return false;
		ItemStack returningCarrier = task.isPlayerReturn()
			? MiniPhantomItem.returningToPlayer(task.playerId())
			: MiniPhantomItem.returningTo(task.dimension(), task.pos());
		if (task.port() != null)
			returningCarrier.set(AllDataComponents.MINI_PHANTOM_RETURN_PORT.get(), task.port());
		if (!beltHandler.insertItem(0, returningCarrier.copy(), true).isEmpty())
			return false;
		ItemStack carrier = inventory.extractOneCarrier();
		if (carrier.isEmpty())
			return false;
		if (beltHandler.insertItem(0, returningCarrier, false).isEmpty())
			return true;
		inventory.returnCarrier(carrier);
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
				entry.putLong("NextAttemptGameTime", task.nextAttemptGameTime());
				if (task.port() != null)
					PhantomPortReference.CODEC.encodeStart(NbtOps.INSTANCE, task.port())
						.result().ifPresent(value -> entry.put("Port", value));
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
				long nextAttempt = entry.getLong("NextAttemptGameTime");

				if ("player".equals(type) && entry.hasUUID("PlayerId")) {
					pendingReturnCarriers.addLast(new PendingReturnCarrier(
						null, null, null, entry.getUUID("PlayerId"), nextAttempt));
				} else if ("phantom_port".equals(type)) {
					ResourceKey<Level> dim = entry.contains("Dimension")
						? ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(entry.getString("Dimension")))
						: null;
					BlockPos pos = entry.contains("Pos")
						? NbtUtils.readBlockPos(entry, "Pos").orElse(null)
						: null;
					if (dim != null && pos != null) {
						PhantomPortReference reference = entry.contains("Port")
							? PhantomPortReference.CODEC.parse(NbtOps.INSTANCE, entry.get("Port")).result().orElse(null)
							: null;
						pendingReturnCarriers.addLast(new PendingReturnCarrier(
							dim, pos.immutable(), reference, null, nextAttempt));
					}
				}
			}
		}
	}

	private record PendingReturnCarrier(
		@Nullable ResourceKey<Level> dimension,
		@Nullable BlockPos pos,
		@Nullable PhantomPortReference port,
		@Nullable UUID playerId,
		long nextAttemptGameTime) {
		PendingReturnCarrier withNextAttempt(long nextAttempt) {
			return new PendingReturnCarrier(dimension, pos, port, playerId, nextAttempt);
		}

		boolean isPlayerReturn() {
			return playerId != null;
		}
	}
}
