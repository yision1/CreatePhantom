package com.yision.phantom.logistics.courier;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.content.logistics.box.PackageEntity;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.yision.phantom.block.phantomport.PhantomPortBlockEntity;
import com.yision.phantom.block.phantomport.PhantomPortReference;
import com.yision.phantom.entity.courier.AirCourierEntity;
import com.yision.phantom.logistics.courier.hud.AirCourierHudSync;
import com.yision.phantom.registry.AllItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class AirCourierDeliveryService {
	private AirCourierDeliveryService() {
	}

	static boolean finishDelivery(
		MinecraftServer server,
		ItemStack box,
		AirCourierEntity.Mission mission,
		@Nullable ResourceKey<Level> sourceDimension,
		@Nullable BlockPos sourcePhantomPortPos,
		@Nullable PhantomPortReference sourcePort,
		@Nullable UUID sourcePlayerId,
		@Nullable PhantomPortBlockEntity targetPhantomPort,
		@Nullable UUID targetPlayerId,
		@Nullable UUID hudPlayerId,
		@Nullable UUID hudEntryId) {
		ServerPlayer targetPlayer = targetPhantomPort == null ? resolvePlayer(server, targetPlayerId) : null;

		ServerPlayer hudPlayer = resolvePlayer(server, hudPlayerId);

		switch (mission) {
			case PACKAGE_TO_PLAYER -> {
				if (targetPlayer == null || !PackageItem.isPackage(box) || !givePackage(targetPlayer, box)) {
					return false;
				}
				AirCourierHudSync.onCourierDelivered(targetPlayer, box, hudEntryId);
				if (hudPlayer != null && !hudPlayer.getUUID().equals(targetPlayer.getUUID())) {
					AirCourierHudSync.onCourierDelivered(hudPlayer, box, hudEntryId);
				}
				return true;
			}
			case PACKAGE_TO_AIRPORT -> {
				if (targetPhantomPort == null) {
					return false;
				}
				boolean received;
				if (sourcePlayerId != null && (sourcePhantomPortPos == null || sourceDimension == null)) {
					received = targetPhantomPort.receivePackageAndScheduleCarrierReturnToPlayer(box, sourcePlayerId);
				} else {
					received = targetPhantomPort.receivePackageAndHandleCarrier(box,
						sourceDimension, sourcePhantomPortPos, sourcePort);
				}
				if (!received) {
					return false;
				}
				if (hudPlayer != null) {
					AirCourierHudSync.onCourierDelivered(hudPlayer, box, hudEntryId);
				}
				return true;
			}
			case CARRIER_RETURN -> {
				return targetPhantomPort != null && targetPhantomPort.receiveCarrier();
			}
			case CARRIER_RETURN_TO_PLAYER -> {
				if (targetPlayer == null || !giveCarrier(targetPlayer))
					return false;
				AirCourierHudSync.onCourierDelivered(targetPlayer, box, hudEntryId);
				return true;
			}
		}
		return false;
	}

	private static boolean givePackage(ServerPlayer player, ItemStack box) {
		ItemStack remaining = box.copy();
		tryInsert(player, remaining);
		return remaining.isEmpty() || dropPackage(player.serverLevel(), player.position(), remaining);
	}

	private static boolean giveCarrier(ServerPlayer player) {
		ItemStack carrier = AllItems.MINI_PHANTOM.asStack();
		tryInsert(player, carrier);
		return carrier.isEmpty() || dropCarrier(player.serverLevel(), player.position());
	}

	private static void tryInsert(ServerPlayer player, ItemStack stack) {
		var inventory = player.getInventory();
		if (inventory.getSlotWithRemainingSpace(stack) >= 0 || inventory.getFreeSlot() >= 0)
			inventory.add(stack);
	}

	public static void notifyFailure(
		MinecraftServer server,
		ItemStack box,
		@Nullable UUID targetPlayerId,
		@Nullable UUID hudPlayerId,
		@Nullable UUID hudEntryId) {
		ServerPlayer targetPlayer = resolvePlayer(server, targetPlayerId);
		ServerPlayer hudPlayer = resolvePlayer(server, hudPlayerId);

		if (targetPlayer != null) {
			AirCourierHudSync.onCourierFailed(targetPlayer, box, hudEntryId);
		}
		if (hudPlayer != null && (targetPlayer == null || !hudPlayer.getUUID().equals(targetPlayer.getUUID()))) {
			AirCourierHudSync.onCourierFailed(hudPlayer, box, hudEntryId);
		}
	}

	public static boolean dropPackage(ServerLevel level, Vec3 position, ItemStack box) {
		return !PackageItem.isPackage(box)
			|| level.addFreshEntity(PackageEntity.fromItemStack(level, position, box.copy()));
	}

	public static boolean dropCarrier(ServerLevel level, Vec3 position) {
		if (!level.addFreshEntity(new ItemEntity(level, position.x, position.y, position.z,
			AllItems.MINI_PHANTOM.asStack())))
			return false;
		level.playSound(null, BlockPos.containing(position),
			SoundEvents.ITEM_FRAME_BREAK, SoundSource.NEUTRAL, 0.7f, 0.9f);
		return true;
	}

	public static void spawnDeliveryParticles(@Nullable ServerLevel level, Vec3 pos) {
		if (level != null) {
			level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y, pos.z,
				10, 0.15, 0.15, 0.15, 0.01);
		}
	}

	public static @Nullable PhantomPortBlockEntity resolveTargetPhantomPort(
		@Nullable ServerLevel level, @Nullable BlockPos pos) {
		if (level == null || pos == null || !isPortChunkLoaded(level, pos))
			return null;
		return level.getBlockEntity(pos) instanceof PhantomPortBlockEntity port && !port.isRemoved() ? port : null;
	}

	public static boolean isPortChunkLoaded(ServerLevel level, BlockPos pos) {
		return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
	}

	public static @Nullable ServerPlayer resolvePlayer(
		MinecraftServer server, @Nullable UUID playerId) {
		if (playerId == null)
			return null;
		ServerPlayer player = server.getPlayerList().getPlayer(playerId);
		return player != null && player.isAlive() ? player : null;
	}
}
