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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class AirCourierTask {

	public static final int LONG_ROUTE_CHECK_TICKS = 120;
	public static final double LONG_ROUTE_REMAINING_DISTANCE = 64.0;
	public static final double PORT_REENTRY_DISTANCE = 32.0;
	public static final double PORT_REENTRY_HEIGHT = 8.0;
	public static final double PLAYER_REENTRY_DISTANCE = 24.0;
	public static final double PLAYER_REENTRY_HEIGHT = 4.0;
	public static final int DESTINATION_UNLOADED_TIMEOUT = 600;
	public static final int RECOVERY_WATCHDOG_TICKS = 2400;

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
	private int destinationUnavailableTicks;
	private boolean removed;

	private boolean teleportedNearTarget;
	private boolean returningUndeliveredPackage;
	private boolean recoveryTriggered;

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

		if (!recoveryTriggered && deliveryElapsedTicks > RECOVERY_WATCHDOG_TICKS) {
			recoveryTriggered = true;
			if (!tryReturnUndeliveredPackage(server)) {
				doFail(server, currentLevel);
			}
			return;
		}

		FlightTarget target = resolveFlightTarget(server);
		if (target == null) {
			waitForDestination();
			tickWaitingForDestination(server);
			return;
		}

		if (shouldTeleportNearTarget(target)) {
			if (!isDestinationAvailable(target)) {
				waitForDestination();
				tickWaitingForDestination(server);
				return;
			}
			teleportNearTarget(target);
			currentLevel = target.level();
		}

		switch (phase) {
			case TAKEOFF -> tickTakeoff(target);
			case EXITING_DIMENSION -> tickExitDimension();
			case CRUISE -> tickCruise(target);
			case LANDING -> tickLanding(server, currentLevel, target);
			case WAITING -> tickWaitingForDestination(server);
		}
	}

	private void tickTakeoff(FlightTarget target) {
		initializeTakeoffTarget();
		phaseTicks++;

		Vec3 exitTarget = getInitialApproachGate(target.landingTarget(), target.playerTarget());
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
			if (!target.level().dimension().equals(currentDimension) && !teleportedNearTarget) {
				phase = AirCourierEntity.Phase.EXITING_DIMENSION;
				phaseTicks = 0;
				clearCaches();
			} else {
				beginCruise();
			}
		}

		position = position.add(motion);
	}

	private void tickExitDimension() {
		Vec3 direction = AirCourierFlightMath.sanitizeNonNegativeDirection(new Vec3(motion.x, 0, motion.z));
		if (direction.lengthSqr() < 1.0E-6) {
			direction = AirCourierFlightMath.sanitizeNonNegativeDirection(new Vec3(launchDirection.x, 0, launchDirection.z));
		}
		motion = direction.scale(FLIGHT.cruiseSpeed());
		phaseTicks++;
		position = position.add(motion);
	}

	private void tickCruise(FlightTarget target) {
		phaseTicks++;

		Vec3 approachGate = getApproachGate(target.landingTarget(), target.playerTarget());
		AirCourierFlightPlanner.FlightStep step = AirCourierFlightPlanner.cruise(FLIGHT,
			position, motion, approachGate, target.landingTarget(), phaseTicks, target.playerTarget());
		motion = step.motion();

		if (step.complete()) {
			if (!isDestinationAvailable(target)) {
				waitForDestination();
				return;
			}
			phase = AirCourierEntity.Phase.LANDING;
			phaseTicks = 0;
			smoothedLandingTarget = target.landingTarget();
			setLandingOpen(resolveLoadedTargetPhantomPort(target.level()), true);
		}

		position = position.add(motion);
	}

	private void tickLanding(MinecraftServer server, ServerLevel currentLevel, FlightTarget target) {
		phaseTicks++;
		if (!isDestinationAvailable(target)) {
			setLandingOpen(resolveLoadedTargetPhantomPort(target.level()), false);
			waitForDestination();
			return;
		}
		setLandingOpen(resolveLoadedTargetPhantomPort(target.level()), true);

		if (target.player() != null && hasReachedPlayer(target.player())) {
			doFinishDelivery(server, currentLevel);
			return;
		}

		Vec3 landingTarget = getSmoothedLandingTarget(target.landingTarget(), target.playerTarget());

		AirCourierFlightPlanner.FlightStep step = AirCourierFlightPlanner.landing(FLIGHT,
			position, motion, landingTarget, target.completionDistance(), target.playerTarget());
		motion = step.motion();

		if (step.complete() || (target.player() != null && hasReachedPlayer(target.player()))) {
			doFinishDelivery(server, currentLevel);
			return;
		}

		position = position.add(motion);
	}

	private void teleportNearTarget(FlightTarget target) {
		Vec3 spawnPos = computeNearTargetSpawn(target);

		currentDimension = target.level().dimension();
		if (target.player() != null) {
			targetDimension = target.player().serverLevel().dimension();
		}
		position = spawnPos;

		Vec3 desired = target.cruiseTarget().subtract(position);
		if (desired.lengthSqr() > 1.0E-6) {
			motion = desired.normalize().scale(FLIGHT.cruiseSpeed());
		} else {
			Vec3 away = new Vec3(position.x - target.cruiseTarget().x, 0, position.z - target.cruiseTarget().z);
			if (away.lengthSqr() < 1.0E-6) away = new Vec3(-launchDirection.x, 0, -launchDirection.z);
			if (away.lengthSqr() < 1.0E-6) away = new Vec3(0, 0, 1);
			motion = away.normalize().scale(-FLIGHT.cruiseSpeed());
		}

		phase = AirCourierEntity.Phase.CRUISE;
		phaseTicks = 0;
		teleportedNearTarget = true;
		destinationUnavailableTicks = 0;
		clearCaches();
	}

	private Vec3 computeNearTargetSpawn(FlightTarget target) {
		Vec3 away = new Vec3(position.x - target.landingTarget().x, 0, position.z - target.landingTarget().z);
		if (away.lengthSqr() < 1.0E-6) {
			away = new Vec3(-launchDirection.x, 0, -launchDirection.z);
		}
		if (away.lengthSqr() < 1.0E-6) {
			away = new Vec3(0, 0, 1);
		}
		away = away.normalize();

		double distance = target.playerTarget() ? PLAYER_REENTRY_DISTANCE : PORT_REENTRY_DISTANCE;
		double yOffset = target.playerTarget() ? PLAYER_REENTRY_HEIGHT : PORT_REENTRY_HEIGHT;
		return new Vec3(
			target.cruiseTarget().x + away.x * distance,
			target.cruiseTarget().y + yOffset,
			target.cruiseTarget().z + away.z * distance
		);
	}

	private boolean shouldTeleportNearTarget(FlightTarget target) {
		if (teleportedNearTarget || deliveryElapsedTicks < LONG_ROUTE_CHECK_TICKS) {
			return false;
		}
		return !target.level().dimension().equals(currentDimension)
			|| position.distanceTo(target.landingTarget()) > LONG_ROUTE_REMAINING_DISTANCE;
	}

	private boolean isDestinationAvailable(FlightTarget target) {
		if (target.player() != null) {
			return target.player().isAlive() && target.player().serverLevel() == target.level();
		}
		return resolveLoadedTargetPhantomPort(target.level()) != null;
	}

	private @Nullable PhantomPortBlockEntity resolveLoadedTargetPhantomPort(@Nullable ServerLevel level) {
		if (level == null || targetPhantomPortPos == null
			|| !level.isPositionEntityTicking(targetPhantomPortPos)) {
			return null;
		}
		return level.getBlockEntity(targetPhantomPortPos) instanceof PhantomPortBlockEntity port ? port : null;
	}

	private void waitForDestination() {
		if (phase == AirCourierEntity.Phase.WAITING) {
			return;
		}
		phase = AirCourierEntity.Phase.WAITING;
		phaseTicks = 0;
		motion = Vec3.ZERO;
		clearCaches();
	}

	private void tickWaitingForDestination(MinecraftServer server) {
		FlightTarget target = resolveFlightTarget(server);
		if (target != null && isDestinationAvailable(target)) {
			destinationUnavailableTicks = 0;
			if (shouldTeleportNearTarget(target)) {
				teleportNearTarget(target);
			} else {
				beginCruise();
			}
			return;
		}

		destinationUnavailableTicks++;
		if (destinationUnavailableTicks >= DESTINATION_UNLOADED_TIMEOUT
			&& !returningUndeliveredPackage) {
			tryReturnUndeliveredPackage(server);
		}
	}

	private boolean tryReturnUndeliveredPackage(MinecraftServer server) {
		if (returningUndeliveredPackage || box.isEmpty()
			|| (mission != AirCourierEntity.Mission.PACKAGE_TO_AIRPORT
				&& mission != AirCourierEntity.Mission.PACKAGE_TO_PLAYER)) {
			return false;
		}

		ResourceKey<Level> returnDimension = sourceDimension;
		BlockPos returnPort = sourcePhantomPortPos;
		UUID returnPlayerId = sourcePlayerId;

		if (returnPort != null && returnDimension != null) {
			targetDimension = returnDimension;
			targetPhantomPortPos = returnPort;
			targetPlayerId = null;
			mission = AirCourierEntity.Mission.PACKAGE_TO_AIRPORT;
		} else {
			ServerPlayer returnPlayer = returnPlayerId != null
				? server.getPlayerList().getPlayer(returnPlayerId) : null;
			if (returnPlayer == null || !returnPlayer.isAlive()) {
				return false;
			}
			targetDimension = returnPlayer.serverLevel().dimension();
			targetPhantomPortPos = null;
			targetPlayerId = returnPlayer.getUUID();
			mission = AirCourierEntity.Mission.PACKAGE_TO_PLAYER;
		}

		sourceDimension = null;
		sourcePhantomPortPos = null;
		sourcePlayerId = null;
		returningUndeliveredPackage = true;
		recoveryTriggered = true;
		teleportedNearTarget = false;
		destinationUnavailableTicks = 0;
		deliveryElapsedTicks = 0;
		phase = AirCourierEntity.Phase.CRUISE;
		phaseTicks = 0;
		if (motion.lengthSqr() > 1.0E-6) {
			motion = motion.scale(-1).normalize().scale(FLIGHT.cruiseSpeed());
		} else {
			Vec3 reverseLaunch = launchDirection.scale(-1);
			motion = reverseLaunch.lengthSqr() > 1.0E-6
				? reverseLaunch.normalize().scale(FLIGHT.cruiseSpeed()) : Vec3.ZERO;
		}
		clearCaches();
		return true;
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

		setLandingOpen(rt != null ? rt.phantomPort : null, false);

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

		setLandingOpen(rt != null ? rt.phantomPort : null, false);
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
			ServerLevel currentLevel = server.getLevel(currentDimension);
			if (currentLevel == null || !AirCourierDispatchService.canReceiveCarrierTarget(
				currentLevel, targetDimension, targetPhantomPortPos)) {
				waitForDestination();
			}
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
		destinationUnavailableTicks = 0;
		teleportedNearTarget = false;
		returningUndeliveredPackage = false;
		recoveryTriggered = false;
		clearCaches();
		Vec3 direction = AirCourierFlightMath.sanitizeNonNegativeDirection(new Vec3(launchDirection.x, 0, launchDirection.z));
		motion = direction.scale(FLIGHT.takeoffSpeed()).add(0, 0.15, 0);
		takeoffStart = position;
		takeoffInitialMotion = motion;
	}

	private Vec3 previewTeleportPosition(FlightTarget target) {
		return computeNearTargetSpawn(target);
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

	private void setLandingOpen(@Nullable PhantomPortBlockEntity phantomPort, boolean open) {
		if (phantomPort != null) {
			phantomPort.setCourierLandingOpen(id, open);
		}
	}

	private void clearCaches() {
		cachedApproachGate = null;
		smoothedLandingTarget = null;
		approachGateTicksSinceUpdate = 0;
	}

	private record FlightTarget(
		ServerLevel level,
		@Nullable ServerPlayer player,
		Vec3 cruiseTarget,
		Vec3 landingTarget,
		double completionDistance
	) {
		boolean playerTarget() {
			return player != null;
		}
	}

	private @Nullable FlightTarget resolveFlightTarget(MinecraftServer server) {
		if (targetPhantomPortPos != null) {
			ServerLevel level = server.getLevel(targetDimension);
			if (level == null) {
				return null;
			}
			return new FlightTarget(level, null,
				AirCourierFlightTargets.cruiseTarget(FLIGHT, targetPhantomPortPos),
				AirCourierFlightTargets.landingTarget(FLIGHT, targetPhantomPortPos),
				FLIGHT.phantomPortCompletionDistance());
		}

		ServerPlayer player = resolveTargetPlayer(server);
		if (player == null) {
			return null;
		}
		return new FlightTarget(player.serverLevel(), player,
			AirCourierFlightTargets.cruiseTarget(FLIGHT, null, player),
			AirCourierFlightTargets.landingTarget(FLIGHT, null, player),
			FLIGHT.playerCompletionDistance());
	}

	private record ResolvedTarget(
		ServerLevel level,
		@Nullable PhantomPortBlockEntity phantomPort,
		@Nullable ServerPlayer player
	) {}

	private @Nullable ResolvedTarget resolveTarget(MinecraftServer server) {
		ServerLevel level = resolveTargetLevel(server);
		if (level == null) return null;
		if (targetPhantomPortPos != null) {
			PhantomPortBlockEntity phantomPort = resolveTargetPhantomPort(level);
			return phantomPort != null ? new ResolvedTarget(level, phantomPort, null) : null;
		}
		ServerPlayer player = resolveTargetPlayer(server);
		return player != null ? new ResolvedTarget(level, null, player) : null;
	}

	public AirCourierTaskSnapshot snapshot(MinecraftServer server) {
		int remainingTicks = estimateRemainingTicks(server);
		AirCourierHudStatus status = getHudStatus();
		return new AirCourierTaskSnapshot(id, getHudTrackingPlayerId(), currentDimension,
			position, box, remainingTicks, status, hudEntryId);
	}

	public int estimateRemainingTicks(MinecraftServer server) {
		FlightTarget target = resolveFlightTarget(server);
		if (target == null || phase == AirCourierEntity.Phase.WAITING) return -1;

		int physicalEstimate = switch (phase) {
			case TAKEOFF -> estimateTakeoffTicks(target);
			case EXITING_DIMENSION -> estimateExitDimensionTicks(target);
			case CRUISE -> estimateCruiseTicksFrom(position, target);
			case LANDING -> estimateLandingTicks(target);
			case WAITING -> -1;
		};

		if (!teleportedNearTarget
			&& (!target.level().dimension().equals(currentDimension)
				|| position.distanceTo(target.landingTarget()) > LONG_ROUTE_REMAINING_DISTANCE)) {
			Vec3 teleportPreview = previewTeleportPosition(target);
			int afterTeleport = estimateCruiseTicksFrom(teleportPreview, target);
			int untilTeleport = Math.max(0, LONG_ROUTE_CHECK_TICKS - deliveryElapsedTicks);
			return Math.min(physicalEstimate, untilTeleport + afterTeleport);
		}

		return physicalEstimate;
	}

	private int estimateTakeoffTicks(FlightTarget target) {
		int remainingTakeoff = Math.max(0, FLIGHT.takeoffTicks() - phaseTicks);
		Vec3 projectedEnd = takeoffTarget;
		if (projectedEnd == null) {
			Vec3 hDir = AirCourierFlightMath.sanitizeNonNegativeDirection(new Vec3(motion.x, 0, motion.z));
			if (hDir.lengthSqr() < 1.0E-4) hDir = AirCourierFlightMath.sanitizeNonNegativeDirection(launchDirection);
			projectedEnd = position.add(hDir.scale(FLIGHT.takeoffForwardDistance()))
				.add(0, FLIGHT.takeoffAltitudeGain(), 0);
		}
		return remainingTakeoff + estimateCruiseTicksFrom(projectedEnd, target);
	}

	private int estimateExitDimensionTicks(FlightTarget target) {
		Vec3 teleportPreview = previewTeleportPosition(target);
		int afterTeleport = estimateCruiseTicksFrom(teleportPreview, target);
		int untilTeleport = Math.max(0, LONG_ROUTE_CHECK_TICKS - deliveryElapsedTicks);
		return untilTeleport + afterTeleport;
	}

	private int estimateCruiseTicksFrom(Vec3 from, FlightTarget target) {
		return AirCourierFlightEstimate.cruiseAndLandingTicks(FLIGHT, from,
			target.cruiseTarget(), target.landingTarget(),
			target.completionDistance(), target.playerTarget());
	}

	private int estimateLandingTicks(FlightTarget target) {
		return AirCourierFlightEstimate.landingTicks(FLIGHT, position,
			target.landingTarget(), target.completionDistance());
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
		tag.putInt("DestinationUnavailableTicks", destinationUnavailableTicks);
		tag.putBoolean("TeleportedNearTarget", teleportedNearTarget);
		tag.putBoolean("ReturningUndeliveredPackage", returningUndeliveredPackage);
		tag.putBoolean("RecoveryTriggered", recoveryTriggered);
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
		task.destinationUnavailableTicks = tag.getInt("DestinationUnavailableTicks");
		task.teleportedNearTarget = tag.getBoolean("TeleportedNearTarget");
		task.returningUndeliveredPackage = tag.getBoolean("ReturningUndeliveredPackage");
		task.recoveryTriggered = tag.getBoolean("RecoveryTriggered");
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
