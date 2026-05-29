package com.yision.phantom.logistics.courier;

import com.yision.phantom.block.phantomport.PhantomPortBlockEntity;
import com.yision.phantom.block.phantomport.PhantomPortBlockEntity.CourierReceiveResult;
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
		@Nullable UUID hudEntryId,
		@Nullable ServerLevel currentLevel,
		Vec3 currentPosition,
		Vec3 landingTarget
	) {
		ServerLevel targetLevel = resolveTargetLevel(server, targetDimension, targetPhantomPortPos, targetPlayerId);
		PhantomPortBlockEntity targetPhantomPort = resolveTargetPhantomPort(targetLevel, targetPhantomPortPos);
		ServerPlayer targetPlayer = targetPhantomPort == null ? resolveTargetPlayer(server, targetPlayerId, targetPhantomPort) : null;

		ServerPlayer hudPlayer = resolvePlayer(server, hudPlayerId);

		switch (mission) {
			case PACKAGE_TO_PLAYER -> {
				if (targetPlayer == null) {
					failAndDrop(server, box, mission, sourceDimension, sourcePhantomPortPos, currentLevel, landingTarget,
						targetPlayerId, hudPlayerId, hudEntryId);
					return true;
				}
				if (!AirCourierHelper.deliverPackageOnly(targetPlayer, box)) {
					failAndDrop(server, box, mission, sourceDimension, sourcePhantomPortPos, currentLevel, landingTarget,
						targetPlayerId, hudPlayerId, hudEntryId);
					return true;
				}
				AirCourierHudSync.onCourierDelivered(targetPlayer, box, hudEntryId);
				if (hudPlayer != null && !hudPlayer.getUUID().equals(targetPlayer.getUUID())) {
					AirCourierHudSync.onCourierDelivered(hudPlayer, box, hudEntryId);
				}
				return true;
			}
			case PACKAGE_TO_AIRPORT -> {
				if (targetPhantomPort == null) {
					failAndDrop(server, box, mission, sourceDimension, sourcePhantomPortPos, currentLevel, landingTarget,
						targetPlayerId, hudPlayerId, hudEntryId);
					return true;
				}
				if (sourcePhantomPortPos != null && sourceDimension != null) {
					CourierReceiveResult result = targetPhantomPort.receivePackageAndHandleCarrier(box,
						sourceDimension, sourcePhantomPortPos);
					if (result == CourierReceiveResult.REJECTED) {
						failAndDrop(server, box, mission, sourceDimension, sourcePhantomPortPos, currentLevel, landingTarget,
							targetPlayerId, hudPlayerId, hudEntryId);
						return true;
					}
				} else if (sourcePlayerId != null) {
					if (!targetPhantomPort.receivePackageAndScheduleCarrierReturnToPlayer(box, sourcePlayerId)) {
						failAndDrop(server, box, mission, sourceDimension, sourcePhantomPortPos, currentLevel, landingTarget,
							targetPlayerId, hudPlayerId, hudEntryId);
						return true;
					}
				} else {
					CourierReceiveResult result = targetPhantomPort.receivePackageAndHandleCarrier(box,
						sourceDimension, sourcePhantomPortPos);
					if (result == CourierReceiveResult.REJECTED) {
						failAndDrop(server, box, mission, sourceDimension, sourcePhantomPortPos, currentLevel, landingTarget,
							targetPlayerId, hudPlayerId, hudEntryId);
						return true;
					}
				}
				if (hudPlayer != null) {
					AirCourierHudSync.onCourierDelivered(hudPlayer, box, hudEntryId);
				}
				return true;
			}
			case CARRIER_RETURN -> {
				if (targetPhantomPort != null) {
					if (!targetPhantomPort.receiveCarrier()) {
						dropCarrierOnly(currentLevel, landingTarget);
					}
				} else {
					dropCarrierOnly(currentLevel, landingTarget);
				}
				return true;
			}
			case CARRIER_RETURN_TO_PLAYER -> {
				if (targetPlayer != null && AirCourierHelper.canReceiveCarrier(targetPlayer)) {
					AirCourierHelper.deliverCarrier(targetPlayer);
					AirCourierHudSync.onCourierDelivered(targetPlayer, box, hudEntryId);
				} else {
					if (targetPlayer != null) {
						AirCourierHudSync.onCourierFailed(targetPlayer, box, hudEntryId);
					}
					dropCarrierOnly(currentLevel, targetPlayer != null ? landingTarget : currentPosition);
				}
				return true;
			}
		}
		return false;
	}

	public static void failAndDrop(
		MinecraftServer server,
		ItemStack box,
		AirCourierEntity.Mission mission,
		@Nullable ResourceKey<Level> sourceDimension,
		@Nullable BlockPos sourcePhantomPortPos,
		@Nullable ServerLevel currentLevel,
		Vec3 dropPos,
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

		if (currentLevel != null) {
			if (mission == AirCourierEntity.Mission.CARRIER_RETURN
				|| mission == AirCourierEntity.Mission.CARRIER_RETURN_TO_PLAYER) {
				dropCarrierOnly(currentLevel, dropPos);
			} else {
				AirCourierHelper.dropPackage(currentLevel, dropPos, box);
			}
			currentLevel.playSound(null, BlockPos.containing(dropPos),
				SoundEvents.ITEM_FRAME_BREAK, SoundSource.NEUTRAL, 0.7f, 0.9f);
		}
	}

	public static void spawnDeliveryParticles(@Nullable ServerLevel level, Vec3 pos) {
		if (level != null) {
			level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y, pos.z,
				10, 0.15, 0.15, 0.15, 0.01);
		}
	}

	public static void dropCarrierOnly(@Nullable ServerLevel level, Vec3 pos) {
		if (level == null) return;
		level.addFreshEntity(new ItemEntity(level, pos.x, pos.y, pos.z,
			AllItems.MINI_PHANTOM.asStack()));
		level.playSound(null, BlockPos.containing(pos),
			SoundEvents.ITEM_FRAME_BREAK, SoundSource.NEUTRAL, 0.7f, 0.9f);
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
		if (level == null || pos == null) return null;
		return level.getBlockEntity(pos) instanceof PhantomPortBlockEntity be ? be : null;
	}

	public static @Nullable ServerPlayer resolveTargetPlayer(
		net.minecraft.server.MinecraftServer server, @Nullable UUID targetPlayerId,
		@Nullable PhantomPortBlockEntity phantomPort
	) {
		if (phantomPort != null) return null;
		return resolvePlayer(server, targetPlayerId);
	}

	public static @Nullable ServerPlayer resolvePlayer(
		net.minecraft.server.MinecraftServer server, @Nullable UUID playerId
	) {
		if (playerId == null) return null;
		ServerPlayer player = server.getPlayerList().getPlayer(playerId);
		return player != null && player.isAlive() ? player : null;
	}
}
