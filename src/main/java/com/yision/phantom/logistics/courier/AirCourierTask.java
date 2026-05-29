package com.yision.phantom.logistics.courier;

import com.yision.phantom.block.phantomport.PhantomPortBlockEntity;
import com.yision.phantom.entity.courier.AirCourierEntity;
import com.yision.phantom.logistics.courier.flight.AirCourierFlightEstimate;
import com.yision.phantom.logistics.courier.flight.AirCourierFlightMath;
import com.yision.phantom.logistics.courier.flight.AirCourierFlightPlanner;
import com.yision.phantom.logistics.courier.flight.AirCourierFlightProfile;
import com.yision.phantom.logistics.courier.flight.AirCourierFlightTargets;
import com.yision.phantom.logistics.courier.hud.AirCourierHudStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class AirCourierTask {

	public static final int TELEPORT_AFTER_TICKS = 300;
	public static final int FORCE_ARRIVAL_TICKS = 600;

	private static final AirCourierFlightProfile FLIGHT = AirCourierFlightProfile.DEFAULT;

	private final UUID id;
	private ItemStack box;
	private ResourceKey<Level> currentDimension;
	private ResourceKey<Level> targetDimension;
	private @Nullable BlockPos sourcePhantomPortPos;
	private @Nullable BlockPos targetPhantomPortPos;
	private @Nullable UUID targetPlayerId;
	private @Nullable UUID hudPlayerId;
	private @Nullable UUID hudEntryId;
	private @Nullable UUID sourcePlayerId;
	private @Nullable ResourceKey<Level> sourceDimension;
	private AirCourierEntity.Mission mission;
	private AirCourierEntity.Phase phase;
	private Vec3 position;
	private Vec3 motion;
	private Vec3 launchDirection;
	private int phaseTicks;
	private int deliveryElapsedTicks;
	private boolean removed;

	private boolean teleportedNearTarget;

	private @Nullable Vec3 takeoffTarget;
	private @Nullable Vec3 takeoffMotion;
	private @Nullable Vec3 takeoffStart;
	private @Nullable Vec3 takeoffInitialMotion;
	private @Nullable Vec3 cachedApproachGate;
	private @Nullable Vec3 smoothedLandingTarget;
	private int approachGateTicksSinceUpdate;

	private AirCourierTask(
		UUID id, ItemStack box,
		ResourceKey<Level> currentDimension, ResourceKey<Level> targetDimension,
		@Nullable BlockPos sourcePhantomPortPos, @Nullable BlockPos targetPhantomPortPos,
		@Nullable UUID targetPlayerId, @Nullable UUID hudPlayerId, @Nullable UUID hudEntryId,
		@Nullable UUID sourcePlayerId, @Nullable ResourceKey<Level> sourceDimension,
		AirCourierEntity.Mission mission, Vec3 position, Vec3 motion, Vec3 launchDirection
	) {
		this.id = id;
		this.box = box.copy();
		this.currentDimension = currentDimension;
		this.targetDimension = targetDimension;
		this.sourcePhantomPortPos = sourcePhantomPortPos != null ? sourcePhantomPortPos.immutable() : null;
		this.targetPhantomPortPos = targetPhantomPortPos != null ? targetPhantomPortPos.immutable() : null;
		this.targetPlayerId = targetPlayerId;
		this.hudPlayerId = hudPlayerId;
		this.hudEntryId = hudEntryId;
		this.sourcePlayerId = sourcePlayerId;
		this.sourceDimension = sourceDimension;
		this.mission = mission;
		this.phase = AirCourierEntity.Phase.TAKEOFF;
		this.position = position;
		this.motion = motion;
		this.launchDirection = launchDirection;
		this.takeoffStart = position;
		this.takeoffInitialMotion = motion;
		this.removed = false;
	}

	public static AirCourierTask forPackageToAirport(
		UUID id, ItemStack box,
		ServerLevel spawnLevel, ResourceKey<Level> targetDimension, BlockPos targetPhantomPortPos,
		Vec3 spawnPos, Vec3 launchDirection, Vec3 launchMotion,
		@Nullable ResourceKey<Level> sourceDimension, @Nullable BlockPos sourcePhantomPortPos,
		@Nullable UUID hudPlayerId, @Nullable UUID hudEntryId, @Nullable UUID sourcePlayerId
	) {
		return new AirCourierTask(id, box, spawnLevel.dimension(), targetDimension,
			sourcePhantomPortPos, targetPhantomPortPos, null, hudPlayerId, hudEntryId,
			sourcePlayerId, sourceDimension, AirCourierEntity.Mission.PACKAGE_TO_AIRPORT,
			spawnPos, launchMotion, launchDirection);
	}

	public static AirCourierTask forPackageToPlayer(
		UUID id, ItemStack box,
		ServerLevel spawnLevel, UUID targetPlayerId, ResourceKey<Level> targetDimension,
		Vec3 spawnPos, Vec3 launchDirection, Vec3 launchMotion,
		@Nullable ResourceKey<Level> sourceDimension, @Nullable BlockPos sourcePhantomPortPos,
		@Nullable UUID hudPlayerId, @Nullable UUID hudEntryId, @Nullable UUID sourcePlayerId
	) {
		return new AirCourierTask(id, box, spawnLevel.dimension(), targetDimension,
			sourcePhantomPortPos, null, targetPlayerId, hudPlayerId, hudEntryId,
			sourcePlayerId, sourceDimension, AirCourierEntity.Mission.PACKAGE_TO_PLAYER,
			spawnPos, launchMotion, launchDirection);
	}

	public static AirCourierTask forCarrierReturn(
		UUID id, ServerLevel spawnLevel,
		ResourceKey<Level> targetDimension, BlockPos targetPhantomPortPos,
		Vec3 spawnPos, Vec3 launchDirection, Vec3 launchMotion
	) {
		return new AirCourierTask(id, ItemStack.EMPTY, spawnLevel.dimension(), targetDimension,
			null, targetPhantomPortPos, null, null, null,
			null, null, AirCourierEntity.Mission.CARRIER_RETURN,
			spawnPos, launchMotion, launchDirection);
	}

	public static AirCourierTask forCarrierReturnToPlayer(
		UUID id, ServerLevel spawnLevel, UUID targetPlayerId,
		ResourceKey<Level> targetDimension,
		Vec3 spawnPos, Vec3 launchDirection, Vec3 launchMotion
	) {
		return new AirCourierTask(id, ItemStack.EMPTY, spawnLevel.dimension(), targetDimension,
			null, null, targetPlayerId, null, null,
			null, null, AirCourierEntity.Mission.CARRIER_RETURN_TO_PLAYER,
			spawnPos, launchMotion, launchDirection);
	}

	public void tick(MinecraftServer server) {
		if (removed) return;

		ServerLevel currentLevel = server.getLevel(currentDimension);
		if (currentLevel == null) {
			markRemoved();
			return;
		}

		deliveryElapsedTicks++;

		if (deliveryElapsedTicks > FORCE_ARRIVAL_TICKS) {
			forceArrive(server, currentLevel);
			return;
		}

		if (!teleportedNearTarget && deliveryElapsedTicks >= TELEPORT_AFTER_TICKS) {
			teleportNearTarget(server, currentLevel);
		}

		switch (phase) {
			case TAKEOFF -> tickTakeoff(server, currentLevel);
			case EXITING_DIMENSION -> tickExitDimension(server, currentLevel);
			case CRUISE -> tickCruise(server, currentLevel);
			case LANDING -> tickLanding(server, currentLevel);
			case WAITING -> markRemoved();
		}
	}

	private void tickTakeoff(MinecraftServer server, ServerLevel currentLevel) {
		initializeTakeoffTarget();
		phaseTicks++;

		ResolvedTarget rt = resolveTarget(server);
		if (rt == null) { doFail(server, currentLevel); return; }

	Vec3 landingTarget = AirCourierFlightTargets.landingTarget(FLIGHT, rt.phantomPort, rt.player);
		Vec3 exitTarget = getInitialApproachGate(landingTarget, rt.player != null);
		AirCourierFlightPlanner.FlightStep step = AirCourierFlightPlanner.takeoff(FLIGHT,
			position, motion, launchDirection, phaseTicks, takeoffTarget,
			takeoffStart, takeoffInitialMotion, exitTarget);

		if (takeoffTarget != null && step.motion().lengthSqr() > 1.0E-6) {
			takeoffMotion = step.motion();
		}
		if (takeoffMotion != null) {
			motion = takeoffMotion;
		}

		if (step.complete()) {
			if (!rt.level.dimension().equals(currentDimension) && !teleportedNearTarget) {
				phase = AirCourierEntity.Phase.EXITING_DIMENSION;
				phaseTicks = 0;
				clearCaches();
			} else {
				beginCruise();
			}
		}

		position = position.add(motion);
	}

	private void tickExitDimension(MinecraftServer server, ServerLevel currentLevel) {
		Vec3 direction = AirCourierFlightMath.sanitizeNonNegativeDirection(new Vec3(motion.x, 0, motion.z));
		if (direction.lengthSqr() < 1.0E-6) {
			direction = AirCourierFlightMath.sanitizeNonNegativeDirection(new Vec3(launchDirection.x, 0, launchDirection.z));
		}
		motion = direction.scale(FLIGHT.cruiseSpeed());
		phaseTicks++;
		position = position.add(motion);
	}

	private void tickCruise(MinecraftServer server, ServerLevel currentLevel) {
		phaseTicks++;
		ResolvedTarget rt = resolveTarget(server);
		if (rt == null) { doFail(server, currentLevel); return; }

		Vec3 landingTarget = AirCourierFlightTargets.landingTarget(FLIGHT, rt.phantomPort, rt.player);
		Vec3 approachGate = getApproachGate(landingTarget, rt.player != null);
		AirCourierFlightPlanner.FlightStep step = AirCourierFlightPlanner.cruise(FLIGHT,
			position, motion, approachGate, landingTarget, phaseTicks, rt.player != null);
		motion = step.motion();

		if (step.complete()) {
			phase = AirCourierEntity.Phase.LANDING;
			phaseTicks = 0;
			smoothedLandingTarget = landingTarget;
			setLandingOpen(rt.level, rt.phantomPort, true);
		}

		position = position.add(motion);
	}

	private void tickLanding(MinecraftServer server, ServerLevel currentLevel) {
		phaseTicks++;
		ResolvedTarget rt = resolveTarget(server);
		if (rt == null) { doFail(server, currentLevel); return; }

		setLandingOpen(rt.level, rt.phantomPort, true);

		if (rt.player != null && hasReachedPlayer(rt.player)) {
			doFinishDelivery(server, currentLevel);
			return;
		}

		Vec3 landingTarget = getSmoothedLandingTarget(
			AirCourierFlightTargets.landingTarget(FLIGHT, rt.phantomPort, rt.player), rt.player != null);
		double completionDistance = AirCourierFlightTargets.completionDistance(FLIGHT, rt.phantomPort, rt.player);

		AirCourierFlightPlanner.FlightStep step = AirCourierFlightPlanner.landing(FLIGHT,
			position, motion, landingTarget, completionDistance, rt.player != null);
		motion = step.motion();

		if (step.complete() || (rt.player != null && hasReachedPlayer(rt.player))) {
			doFinishDelivery(server, currentLevel);
			return;
		}

		position = position.add(motion);
	}

	private void teleportNearTarget(MinecraftServer server, ServerLevel currentLevel) {
		ResolvedTarget rt = resolveTarget(server);
		if (rt == null) return;

		if (targetPhantomPortPos != null) {
			rt.level.getChunkAt(targetPhantomPortPos);
		}

		Vec3 preferredSpawn = computeNearTargetSpawn(rt.phantomPort, rt.player);
		Vec3 cruiseTarget = AirCourierFlightTargets.cruiseTarget(FLIGHT, rt.phantomPort, rt.player);
		Vec3 spawnPos = findTickingPosTowardTarget(rt.level, preferredSpawn, cruiseTarget);

		currentDimension = rt.level.dimension();
		if (rt.player != null) {
			targetDimension = rt.player.serverLevel().dimension();
		}
		position = spawnPos;

		Vec3 desired = cruiseTarget.subtract(position);
		if (desired.lengthSqr() > 1.0E-6) {
			motion = desired.normalize().scale(FLIGHT.cruiseSpeed());
		} else {
			Vec3 away = new Vec3(position.x - cruiseTarget.x, 0, position.z - cruiseTarget.z);
			if (away.lengthSqr() < 1.0E-6) away = new Vec3(-launchDirection.x, 0, -launchDirection.z);
			if (away.lengthSqr() < 1.0E-6) away = new Vec3(0, 0, 1);
			motion = away.normalize().scale(-FLIGHT.cruiseSpeed());
		}

		phase = AirCourierEntity.Phase.CRUISE;
		phaseTicks = 0;
		teleportedNearTarget = true;
		clearCaches();
	}

	private Vec3 computeNearTargetSpawn(@Nullable PhantomPortBlockEntity phantomPort, @Nullable ServerPlayer targetPlayer) {
		Vec3 landingTarget = AirCourierFlightTargets.landingTarget(FLIGHT, phantomPort, targetPlayer);
		Vec3 cruiseTarget = AirCourierFlightTargets.cruiseTarget(FLIGHT, phantomPort, targetPlayer);

		Vec3 away = new Vec3(position.x - landingTarget.x, 0, position.z - landingTarget.z);
		if (away.lengthSqr() < 1.0E-6) {
			away = new Vec3(-launchDirection.x, 0, -launchDirection.z);
		}
		if (away.lengthSqr() < 1.0E-6) {
			away = new Vec3(0, 0, 1);
		}
		away = away.normalize();

		double distance = targetPlayer != null ? 48.0 : 96.0;
		double yOffset = targetPlayer != null ? 4.0 : 18.0;
		return new Vec3(
			cruiseTarget.x + away.x * distance,
			cruiseTarget.y + yOffset,
			cruiseTarget.z + away.z * distance
		);
	}

	private Vec3 findTickingPosTowardTarget(ServerLevel level, Vec3 preferredSpawn, Vec3 cruiseTarget) {
		Vec3 path = cruiseTarget.subtract(preferredSpawn);
		if (path.lengthSqr() < 1.0E-6) {
			return preferredSpawn;
		}

		Vec3 step = path.normalize().scale(8.0);
		Vec3 candidate = preferredSpawn;
		int iterations = Math.max(1, Mth.ceil(path.length() / 8.0));
		for (int i = 0; i <= iterations; i++) {
			if (level.isPositionEntityTicking(BlockPos.containing(candidate))) {
				return candidate;
			}
			candidate = candidate.add(step);
		}

		return preferredSpawn;
	}

	private void forceArrive(MinecraftServer server, ServerLevel fallbackLevel) {
		ResolvedTarget rt = resolveTarget(server);
		if (rt == null) {
			doFail(server, fallbackLevel);
			return;
		}

		if (targetPhantomPortPos != null) {
			rt.level.getChunkAt(targetPhantomPortPos);
		}

		position = AirCourierFlightTargets.landingTarget(FLIGHT, rt.phantomPort, rt.player);
		currentDimension = rt.level.dimension();
		doFinishDeliveryAt(server, rt.level);
	}

	private void beginCruise() {
		phase = AirCourierEntity.Phase.CRUISE;
		phaseTicks = 0;
		clearCaches();
	}

	private void doFinishDelivery(MinecraftServer server, ServerLevel currentLevel) {
		doFinishDeliveryAt(server, currentLevel);
	}

	private void doFinishDeliveryAt(MinecraftServer server, @Nullable ServerLevel level) {
		if (level == null) { markRemoved(); return; }

		ResolvedTarget rt = resolveTarget(server);
		Vec3 landingTarget = rt != null
			? AirCourierFlightTargets.landingTarget(FLIGHT, rt.phantomPort, rt.player)
			: position;

		setLandingOpen(rt != null ? rt.level : null, rt != null ? rt.phantomPort : null, false);

		boolean handled = AirCourierDeliveryService.finishDelivery(
			server, box, mission, sourceDimension, sourcePhantomPortPos, sourcePlayerId,
			targetDimension, targetPhantomPortPos, targetPlayerId, hudPlayerId, hudEntryId,
			level, position, landingTarget);

		if (handled) {
			AirCourierDeliveryService.spawnDeliveryParticles(level, position);
			if (mission == AirCourierEntity.Mission.PACKAGE_TO_PLAYER && !box.isEmpty()) {
				startCarrierReturn(server);
				return;
			}
		}
		markRemoved();
	}

	private void doFail(MinecraftServer server, @Nullable ServerLevel currentLevel) {
		if (currentLevel == null) { markRemoved(); return; }

		ResolvedTarget rt = resolveTarget(server);
		Vec3 dropTarget = rt != null ? AirCourierFlightTargets.landingTarget(FLIGHT, rt.phantomPort, null) : position;
		Vec3 dropPos = rt != null && rt.phantomPort != null ? dropTarget : position;

		setLandingOpen(rt != null ? rt.level : null, rt != null ? rt.phantomPort : null, false);
		AirCourierDeliveryService.failAndDrop(server, box, mission, sourceDimension,
			sourcePhantomPortPos, currentLevel, dropPos, targetPlayerId, hudPlayerId, hudEntryId);
		markRemoved();
	}

	private void startCarrierReturn(MinecraftServer server) {
		if (sourcePhantomPortPos != null && sourceDimension != null) {
			targetPhantomPortPos = sourcePhantomPortPos;
			targetDimension = sourceDimension;
			targetPlayerId = null;
			resetForReturn(AirCourierEntity.Mission.CARRIER_RETURN);
		} else if (sourcePlayerId != null) {
			ServerPlayer sourcePlayer = server.getPlayerList().getPlayer(sourcePlayerId);
			if (sourcePlayer != null && sourcePlayer.isAlive()) {
				targetPhantomPortPos = null;
				targetPlayerId = sourcePlayerId;
				targetDimension = sourcePlayer.serverLevel().dimension();
				resetForReturn(AirCourierEntity.Mission.CARRIER_RETURN_TO_PLAYER);
			} else {
				AirCourierDeliveryService.dropCarrierOnly(server.getLevel(currentDimension), position);
				markRemoved();
			}
		} else {
			AirCourierDeliveryService.dropCarrierOnly(server.getLevel(currentDimension), position);
			markRemoved();
		}
	}

	private void resetForReturn(AirCourierEntity.Mission nextMission) {
		box = ItemStack.EMPTY;
		hudPlayerId = null;
		hudEntryId = null;
		mission = nextMission;
		phase = AirCourierEntity.Phase.TAKEOFF;
		phaseTicks = 0;
		deliveryElapsedTicks = 0;
		teleportedNearTarget = false;
		clearCaches();
		Vec3 direction = AirCourierFlightMath.sanitizeNonNegativeDirection(new Vec3(launchDirection.x, 0, launchDirection.z));
		motion = direction.scale(FLIGHT.takeoffSpeed()).add(0, 0.15, 0);
		takeoffStart = position;
		takeoffInitialMotion = motion;
	}

	private Vec3 previewTeleportPosition(@Nullable PhantomPortBlockEntity phantomPort, @Nullable ServerPlayer targetPlayer) {
		return computeNearTargetSpawn(phantomPort, targetPlayer);
	}

	private @Nullable ServerLevel resolveTargetLevel(MinecraftServer server) {
		if (targetPhantomPortPos != null && targetDimension != null) {
			return server.getLevel(targetDimension);
		}
		ServerPlayer player = resolveTargetPlayer(server);
		if (player != null) return player.serverLevel();
		return targetDimension != null ? server.getLevel(targetDimension) : null;
	}

	private @Nullable PhantomPortBlockEntity resolveTargetPhantomPort(@Nullable ServerLevel level) {
		return AirCourierDeliveryService.resolveTargetPhantomPort(level, targetPhantomPortPos);
	}

	private @Nullable ServerPlayer resolveTargetPlayer(MinecraftServer server) {
		return AirCourierDeliveryService.resolvePlayer(server, targetPlayerId);
	}

	private void initializeTakeoffTarget() {
		if (takeoffTarget != null) return;
		Vec3 hDir = AirCourierFlightMath.sanitizeNonNegativeDirection(new Vec3(motion.x, 0, motion.z));
		if (hDir.lengthSqr() < 1.0E-4) {
			hDir = AirCourierFlightMath.sanitizeNonNegativeDirection(launchDirection);
		}
		if (hDir.lengthSqr() < 1.0E-4) return;
		Vec3 origin = takeoffStart != null ? takeoffStart : position;
		takeoffTarget = origin.add(hDir.scale(FLIGHT.takeoffForwardDistance()))
			.add(0, FLIGHT.takeoffAltitudeGain(), 0);
		Vec3 desired = takeoffTarget.subtract(position);
		if (desired.lengthSqr() > 1.0E-6) {
			takeoffMotion = desired.normalize().scale(FLIGHT.takeoffSpeed());
		}
	}

	private Vec3 getInitialApproachGate(Vec3 landingTarget, boolean playerTarget) {
		Vec3 gatePos = takeoffTarget != null ? takeoffTarget : position;
		Vec3 gateMotion = takeoffMotion != null ? takeoffMotion : motion;
		return AirCourierFlightTargets.approachGate(FLIGHT, gatePos, gateMotion, landingTarget, playerTarget);
	}

	private Vec3 getApproachGate(Vec3 landingTarget, boolean playerTarget) {
		Vec3 nextGate = AirCourierFlightTargets.approachGate(FLIGHT, position, motion, landingTarget, playerTarget);
		if (cachedApproachGate == null || !playerTarget) {
			cachedApproachGate = cachedApproachGate == null ? nextGate : cachedApproachGate;
			return cachedApproachGate;
		}
		approachGateTicksSinceUpdate++;
		if (approachGateTicksSinceUpdate >= FLIGHT.playerApproachGateUpdateTicks()) {
			cachedApproachGate = cachedApproachGate.lerp(nextGate, FLIGHT.playerApproachGateLerp());
			approachGateTicksSinceUpdate = 0;
		}
		return cachedApproachGate;
	}

	private Vec3 getSmoothedLandingTarget(Vec3 landingTarget, boolean playerTarget) {
		if (!playerTarget) { smoothedLandingTarget = landingTarget; return landingTarget; }
		if (smoothedLandingTarget == null) {
			smoothedLandingTarget = landingTarget;
		} else {
			smoothedLandingTarget = smoothedLandingTarget.lerp(landingTarget, FLIGHT.playerLandingTargetLerp());
		}
		return smoothedLandingTarget;
	}

	private boolean hasReachedPlayer(ServerPlayer targetPlayer) {
		return targetPlayer.getBoundingBox().inflate(0.45, 0.6, 0.45).contains(position)
			|| position.distanceTo(AirCourierFlightTargets.playerDeliveryTarget(FLIGHT, targetPlayer)) <= 1.5;
	}

	private void setLandingOpen(@Nullable ServerLevel level, @Nullable PhantomPortBlockEntity phantomPort, boolean open) {
		if (phantomPort != null) {
			phantomPort.setCourierLandingOpen(id, open);
		}
	}

	private void clearCaches() {
		cachedApproachGate = null;
		smoothedLandingTarget = null;
		approachGateTicksSinceUpdate = 0;
	}

	private record ResolvedTarget(
		ServerLevel level,
		@Nullable PhantomPortBlockEntity phantomPort,
		@Nullable ServerPlayer player
	) {}

	private @Nullable ResolvedTarget resolveTarget(MinecraftServer server) {
		ServerLevel level = resolveTargetLevel(server);
		if (level == null) return null;
		PhantomPortBlockEntity phantomPort = resolveTargetPhantomPort(level);
		ServerPlayer player = phantomPort == null ? resolveTargetPlayer(server) : null;
		if (phantomPort == null && player == null) return null;
		return new ResolvedTarget(level, phantomPort, player);
	}

	public AirCourierTaskSnapshot snapshot(MinecraftServer server) {
		int remainingTicks = estimateRemainingTicks(server);
		AirCourierHudStatus status = getHudStatus();
		return new AirCourierTaskSnapshot(id, getHudTrackingPlayerId(), currentDimension,
			position, box, remainingTicks, status, hudEntryId);
	}

	public int estimateRemainingTicks(MinecraftServer server) {
		ServerLevel targetLevel = resolveTargetLevel(server);
		if (targetLevel == null) return -1;

		PhantomPortBlockEntity phantomPort = resolveTargetPhantomPort(targetLevel);
		ServerPlayer targetPlayer = phantomPort == null ? resolveTargetPlayer(server) : null;
		if (phantomPort == null && targetPlayer == null) return -1;

		int forceRemaining = Math.max(0, FORCE_ARRIVAL_TICKS - deliveryElapsedTicks);

		boolean crossDimBeforeTeleport =
			!teleportedNearTarget && !targetLevel.dimension().equals(currentDimension);

		if (crossDimBeforeTeleport) {
			Vec3 teleportPreview = previewTeleportPosition(phantomPort, targetPlayer);
			int afterTeleport = estimateCruiseTicksFrom(teleportPreview, phantomPort, targetPlayer);
			int untilTeleport = Math.max(0, TELEPORT_AFTER_TICKS - deliveryElapsedTicks);
			return Math.min(untilTeleport + afterTeleport, forceRemaining);
		}

		int physicalEstimate = switch (phase) {
			case TAKEOFF -> estimateTakeoffTicks(phantomPort, targetPlayer);
			case EXITING_DIMENSION -> estimateExitDimensionTicks(phantomPort, targetPlayer);
			case CRUISE -> estimateCruiseTicksFrom(position, phantomPort, targetPlayer);
			case LANDING -> estimateLandingTicks(phantomPort, targetPlayer);
			case WAITING -> -1;
		};

		if (physicalEstimate < 0) {
			return forceRemaining;
		}

		int predicted = physicalEstimate;
		if (!teleportedNearTarget) {
			Vec3 teleportPreview = previewTeleportPosition(phantomPort, targetPlayer);
			int afterTeleport = estimateCruiseTicksFrom(teleportPreview, phantomPort, targetPlayer);
			int untilTeleport = Math.max(0, TELEPORT_AFTER_TICKS - deliveryElapsedTicks);
			predicted = Math.min(predicted, untilTeleport + afterTeleport);
		}

		return Math.min(predicted, forceRemaining);
	}

	private int estimateTakeoffTicks(@Nullable PhantomPortBlockEntity phantomPort, @Nullable ServerPlayer targetPlayer) {
		int remainingTakeoff = Math.max(0, FLIGHT.takeoffTicks() - phaseTicks);
		Vec3 projectedEnd = takeoffTarget;
		if (projectedEnd == null) {
			Vec3 hDir = AirCourierFlightMath.sanitizeNonNegativeDirection(new Vec3(motion.x, 0, motion.z));
			if (hDir.lengthSqr() < 1.0E-4) hDir = AirCourierFlightMath.sanitizeNonNegativeDirection(launchDirection);
			projectedEnd = position.add(hDir.scale(FLIGHT.takeoffForwardDistance()))
				.add(0, FLIGHT.takeoffAltitudeGain(), 0);
		}
		return remainingTakeoff + estimateCruiseTicksFrom(projectedEnd, phantomPort, targetPlayer);
	}

	private int estimateExitDimensionTicks(@Nullable PhantomPortBlockEntity phantomPort, @Nullable ServerPlayer targetPlayer) {
		Vec3 teleportPreview = previewTeleportPosition(phantomPort, targetPlayer);
		int afterTeleport = estimateCruiseTicksFrom(teleportPreview, phantomPort, targetPlayer);
		int untilTeleport = Math.max(0, TELEPORT_AFTER_TICKS - deliveryElapsedTicks);
		return Math.min(untilTeleport + afterTeleport, Math.max(0, FORCE_ARRIVAL_TICKS - deliveryElapsedTicks));
	}

	private int estimateCruiseTicksFrom(Vec3 from, @Nullable PhantomPortBlockEntity phantomPort,
		@Nullable ServerPlayer targetPlayer) {
		Vec3 cruiseTarget = AirCourierFlightTargets.cruiseTarget(FLIGHT, phantomPort, targetPlayer);
		Vec3 landingTarget = AirCourierFlightTargets.landingTarget(FLIGHT, phantomPort, targetPlayer);
		double completionDistance = AirCourierFlightTargets.completionDistance(FLIGHT, phantomPort, targetPlayer);
		return AirCourierFlightEstimate.cruiseAndLandingTicks(FLIGHT, from, cruiseTarget, landingTarget,
			completionDistance, targetPlayer != null);
	}

	private int estimateLandingTicks(@Nullable PhantomPortBlockEntity phantomPort, @Nullable ServerPlayer targetPlayer) {
		Vec3 landingTarget = AirCourierFlightTargets.landingTarget(FLIGHT, phantomPort, targetPlayer);
		double completionDistance = AirCourierFlightTargets.completionDistance(FLIGHT, phantomPort, targetPlayer);
		return AirCourierFlightEstimate.landingTicks(FLIGHT, position, landingTarget, completionDistance);
	}

	private AirCourierHudStatus getHudStatus() {
		if (mission == AirCourierEntity.Mission.CARRIER_RETURN_TO_PLAYER) {
			return AirCourierHudStatus.RETURNING;
		}
		return switch (phase) {
			case WAITING -> AirCourierHudStatus.PREPARING;
			case EXITING_DIMENSION -> AirCourierHudStatus.CROSS_DIMENSION;
		case TAKEOFF, CRUISE, LANDING -> AirCourierHudStatus.IN_TRANSIT;
		};
	}

	public UUID id() { return id; }
	public ItemStack box() { return box; }
	public ResourceKey<Level> currentDimension() { return currentDimension; }
	public ResourceKey<Level> targetDimension() { return targetDimension; }
	public @Nullable BlockPos sourcePhantomPortPos() { return sourcePhantomPortPos; }
	public @Nullable BlockPos targetPhantomPortPos() { return targetPhantomPortPos; }
	public @Nullable UUID targetPlayerId() { return targetPlayerId; }
	public @Nullable UUID hudPlayerId() { return hudPlayerId; }
	public @Nullable UUID hudEntryId() { return hudEntryId; }
	public @Nullable UUID sourcePlayerId() { return sourcePlayerId; }
	public @Nullable ResourceKey<Level> sourceDimension() { return sourceDimension; }
	public AirCourierEntity.Mission mission() { return mission; }
	public AirCourierEntity.Phase phase() { return phase; }
	public Vec3 position() { return position; }
	public Vec3 motion() { return motion; }
	public Vec3 launchDirection() { return launchDirection; }
	public int phaseTicks() { return phaseTicks; }
	public int deliveryElapsedTicks() { return deliveryElapsedTicks; }
	public boolean isRemoved() { return removed; }
	public void markRemoved() { removed = true; }

	public @Nullable UUID getHudTrackingPlayerId() {
		if (mission == AirCourierEntity.Mission.CARRIER_RETURN) return null;
		return hudPlayerId != null ? hudPlayerId : targetPlayerId;
	}

	public CompoundTag save(HolderLookup.Provider registries, CompoundTag tag) {
		tag.putUUID("Id", id);
		tag.put("Box", box.saveOptional(registries));
		tag.putString("CurrentDimension", currentDimension.location().toString());
		tag.putString("TargetDimension", targetDimension.location().toString());
		if (sourceDimension != null) {
			tag.putString("SourceDimension", sourceDimension.location().toString());
		}
		if (sourcePhantomPortPos != null) {
			tag.put("SourcePhantomPortPos", NbtUtils.writeBlockPos(sourcePhantomPortPos));
		}
		if (targetPhantomPortPos != null) {
			tag.put("TargetPhantomPortPos", NbtUtils.writeBlockPos(targetPhantomPortPos));
		}
		if (targetPlayerId != null) tag.putUUID("TargetPlayer", targetPlayerId);
		if (hudPlayerId != null) tag.putUUID("HudPlayer", hudPlayerId);
		if (hudEntryId != null) tag.putUUID("HudEntryId", hudEntryId);
		if (sourcePlayerId != null) tag.putUUID("SourcePlayer", sourcePlayerId);
		tag.putByte("Mission", (byte) mission.ordinal());
		tag.putByte("Phase", (byte) phase.ordinal());
		tag.put("Position", vecToTag(position));
		tag.put("Motion", vecToTag(motion));
		tag.put("LaunchDirection", vecToTag(launchDirection));
		tag.putInt("PhaseTicks", phaseTicks);
		tag.putInt("DeliveryElapsedTicks", deliveryElapsedTicks);
		tag.putBoolean("TeleportedNearTarget", teleportedNearTarget);
		if (takeoffTarget != null) tag.put("TakeoffTarget", vecToTag(takeoffTarget));
		if (takeoffMotion != null) tag.put("TakeoffMotion", vecToTag(takeoffMotion));
		if (takeoffStart != null) tag.put("TakeoffStart", vecToTag(takeoffStart));
		if (takeoffInitialMotion != null) tag.put("TakeoffInitialMotion", vecToTag(takeoffInitialMotion));
		if (cachedApproachGate != null) tag.put("CachedApproachGate", vecToTag(cachedApproachGate));
		if (smoothedLandingTarget != null) tag.put("SmoothedLandingTarget", vecToTag(smoothedLandingTarget));
		tag.putInt("ApproachGateTicksSinceUpdate", approachGateTicksSinceUpdate);
		return tag;
	}

	public static AirCourierTask load(HolderLookup.Provider registries, CompoundTag tag) {
		UUID id = tag.getUUID("Id");
		ItemStack box = ItemStack.parseOptional(registries, tag.getCompound("Box"));
		ResourceKey<Level> currentDim = ResourceKey.create(Registries.DIMENSION,
			net.minecraft.resources.ResourceLocation.parse(tag.getString("CurrentDimension")));
		ResourceKey<Level> targetDim = ResourceKey.create(Registries.DIMENSION,
			net.minecraft.resources.ResourceLocation.parse(tag.getString("TargetDimension")));
		ResourceKey<Level> sourceDim = tag.contains("SourceDimension")
			? ResourceKey.create(Registries.DIMENSION,
				net.minecraft.resources.ResourceLocation.parse(tag.getString("SourceDimension")))
			: null;
		BlockPos sourcePP = tag.contains("SourcePhantomPortPos")
			? NbtUtils.readBlockPos(tag, "SourcePhantomPortPos").orElse(null) : null;
		BlockPos targetPP = tag.contains("TargetPhantomPortPos")
			? NbtUtils.readBlockPos(tag, "TargetPhantomPortPos").orElse(null) : null;
		UUID targetPlayer = tag.hasUUID("TargetPlayer") ? tag.getUUID("TargetPlayer") : null;
		UUID hudPlayer = tag.hasUUID("HudPlayer") ? tag.getUUID("HudPlayer") : null;
		UUID hudEntry = tag.hasUUID("HudEntryId") ? tag.getUUID("HudEntryId") : null;
		UUID sourcePlayer = tag.hasUUID("SourcePlayer") ? tag.getUUID("SourcePlayer") : null;
		AirCourierEntity.Mission mission = AirCourierEntity.Mission.values()[tag.getByte("Mission")];
		AirCourierEntity.Phase phase = AirCourierEntity.Phase.values()[tag.getByte("Phase")];
		Vec3 position = vecFromTag(tag, "Position");
		Vec3 motion = vecFromTag(tag, "Motion");
		Vec3 launchDir = vecFromTag(tag, "LaunchDirection");

		AirCourierTask task = new AirCourierTask(id, box, currentDim, targetDim,
			sourcePP, targetPP, targetPlayer, hudPlayer, hudEntry,
			sourcePlayer, sourceDim, mission, position, motion, launchDir);
		task.phase = phase;
		task.phaseTicks = tag.getInt("PhaseTicks");
		task.deliveryElapsedTicks = tag.getInt("DeliveryElapsedTicks");
		task.teleportedNearTarget = tag.getBoolean("TeleportedNearTarget");
		task.takeoffTarget = tag.contains("TakeoffTarget") ? vecFromTag(tag, "TakeoffTarget") : null;
		task.takeoffMotion = tag.contains("TakeoffMotion") ? vecFromTag(tag, "TakeoffMotion") : null;
		task.takeoffStart = tag.contains("TakeoffStart") ? vecFromTag(tag, "TakeoffStart") : null;
		task.takeoffInitialMotion = tag.contains("TakeoffInitialMotion") ? vecFromTag(tag, "TakeoffInitialMotion") : null;
		task.cachedApproachGate = tag.contains("CachedApproachGate") ? vecFromTag(tag, "CachedApproachGate") : null;
		task.smoothedLandingTarget = tag.contains("SmoothedLandingTarget") ? vecFromTag(tag, "SmoothedLandingTarget") : null;
		task.approachGateTicksSinceUpdate = tag.getInt("ApproachGateTicksSinceUpdate");
		return task;
	}

	private static CompoundTag vecToTag(Vec3 v) {
		CompoundTag t = new CompoundTag();
		t.putDouble("X", v.x);
		t.putDouble("Y", v.y);
		t.putDouble("Z", v.z);
		return t;
	}

	private static Vec3 vecFromTag(CompoundTag tag, String key) {
		CompoundTag t = tag.getCompound(key);
		return new Vec3(t.getDouble("X"), t.getDouble("Y"), t.getDouble("Z"));
	}

	public record AirCourierTaskSnapshot(
		UUID taskId,
		@Nullable UUID hudTrackingPlayerId,
		ResourceKey<Level> currentDimension,
		Vec3 position,
		ItemStack box,
		int remainingTicks,
		AirCourierHudStatus status,
		@Nullable UUID hudEntryId
	) {}
}
