package com.yision.phantom.logistics.courier;

import com.simibubi.create.content.logistics.box.PackageEntity;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.yision.phantom.block.phantomport.PhantomPortBlockEntity;
import com.yision.phantom.entity.courier.AirCourierEntity;
import com.yision.phantom.logistics.courier.hud.AirCourierHudSync;
import com.yision.phantom.registry.AllItems;
import java.util.UUID;
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
import org.jetbrains.annotations.Nullable;

public final class AirCourierDeliveryService {

	private AirCourierDeliveryService() {}

	public enum TargetState { AVAILABLE, PLAYER_OFFLINE, UNAVAILABLE }

	public static TargetState targetState(MinecraftServer server, AirCourierEntity.Mission mission,
		@Nullable ResourceKey<Level> dimension, @Nullable BlockPos portPos,
		@Nullable UUID playerId, ItemStack box) {
		if (portPos != null) {
			PhantomPortBlockEntity port = resolveTargetPhantomPort(
				dimension != null ? server.getLevel(dimension) : null, portPos);
			return port != null && (mission == AirCourierEntity.Mission.CARRIER_RETURN
				? port.canReceiveCarrier() : port.canReceiveCourier(box))
				? TargetState.AVAILABLE : TargetState.UNAVAILABLE;
		}
		if (playerId == null) return TargetState.UNAVAILABLE;
		ServerPlayer player = server.getPlayerList().getPlayer(playerId);
		if (player == null) return TargetState.PLAYER_OFFLINE;
		return player.isAlive() ? TargetState.AVAILABLE : TargetState.UNAVAILABLE;
	}

	public static boolean finishDelivery(
		MinecraftServer server,
		ItemStack box,
		AirCourierEntity.Mission mission,
		@Nullable ResourceKey<Level> sourceDimension,
		@Nullable BlockPos sourcePhantomPortPos,
		@Nullable UUID sourcePlayerId,
		@Nullable ResourceKey<Level> targetDimension,
		@Nullable BlockPos targetPhantomPortPos,
		@Nullable UUID targetPlayerId,
		@Nullable UUID hudPlayerId,
		@Nullable UUID hudEntryId
	) {
		ServerLevel targetLevel = resolveTargetLevel(server, targetDimension, targetPhantomPortPos, targetPlayerId);
		PhantomPortBlockEntity targetPhantomPort = resolveTargetPhantomPort(targetLevel, targetPhantomPortPos);
		ServerPlayer targetPlayer = targetPhantomPort == null ? resolvePlayer(server, targetPlayerId) : null;

		ServerPlayer hudPlayer = resolvePlayer(server, hudPlayerId);

		switch (mission) {
			case PACKAGE_TO_PLAYER -> {
				if (targetPlayer == null || !AirCourierHelper.deliverPackageOnly(targetPlayer, box)) {
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
						sourceDimension, sourcePhantomPortPos);
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
				if (targetPlayer == null) {
					return false;
				}
				AirCourierHelper.deliverCarrier(targetPlayer);
				AirCourierHudSync.onCourierDelivered(targetPlayer, box, hudEntryId);
				return true;
			}
		}
		return false;
	}

	public static void notifyFailure(
		MinecraftServer server,
		ItemStack box,
		@Nullable UUID targetPlayerId,
		@Nullable UUID hudPlayerId,
		@Nullable UUID hudEntryId
	) {
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
			AllItems.MINI_PHANTOM.asStack()))) return false;
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

	public static @Nullable ServerLevel resolveTargetLevel(
		net.minecraft.server.MinecraftServer server,
		@Nullable ResourceKey<Level> targetDimension,
		@Nullable BlockPos targetPhantomPortPos,
		@Nullable UUID targetPlayerId
	) {
		if (targetPhantomPortPos != null && targetDimension != null) {
			return server.getLevel(targetDimension);
		}
		ServerPlayer player = resolvePlayer(server, targetPlayerId);
		if (player != null) {
			return player.serverLevel();
		}
		return targetDimension != null ? server.getLevel(targetDimension) : null;
	}

	public static @Nullable PhantomPortBlockEntity resolveTargetPhantomPort(
		@Nullable ServerLevel level, @Nullable BlockPos pos
	) {
		if (level == null || pos == null || !level.isPositionEntityTicking(pos)) return null;
		return level.getBlockEntity(pos) instanceof PhantomPortBlockEntity be ? be : null;
	}

	public static @Nullable ServerPlayer resolvePlayer(
		net.minecraft.server.MinecraftServer server, @Nullable UUID playerId
	) {
		if (playerId == null) return null;
		ServerPlayer player = server.getPlayerList().getPlayer(playerId);
		return player != null && player.isAlive() ? player : null;
	}
}
