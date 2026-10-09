package com.yision.phantom.logistics.courier;

import com.yision.phantom.block.phantomport.PhantomPortBlockEntity;
import com.yision.phantom.entity.courier.AirCourierEntity;
import com.yision.phantom.logistics.courier.flight.AirCourierFlightEstimate;
import com.yision.phantom.logistics.courier.flight.AirCourierFlightMath;
import com.yision.phantom.logistics.courier.flight.AirCourierFlightPlanner;
import com.yision.phantom.logistics.courier.flight.AirCourierFlightProfile;
import com.yision.phantom.logistics.courier.flight.AirCourierFlightTargets;
import com.yision.phantom.logistics.courier.flight.AirCourierFlightTracking;
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

	public static final int LONG_ROUTE_CHECK_TICKS = 120;
	public static final double LONG_ROUTE_REMAINING_DISTANCE = 64.0;
	public static final double PORT_REENTRY_DISTANCE = 32.0;
	public static final double PORT_REENTRY_HEIGHT = 8.0;
	public static final double PLAYER_REENTRY_DISTANCE = 24.0;
	public static final double PLAYER_REENTRY_HEIGHT = 4.0;
	public static final int PLAYER_REACQUIRE_COOLDOWN_TICKS = 40;
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
	private boolean removed;

	private boolean teleportedNearTarget;
	private State state = State.ACTIVE;
	private boolean packageDropped;

	private @Nullable Vec3 takeoffTarget;
	private @Nullable Vec3 takeoffStart;
	private @Nullable Vec3 takeoffInitialMotion;
	private int takeoffDuration = FLIGHT.takeoffTicks();
	private @Nullable Vec3 previousFlightPosition;
	private final AirCourierFlightTracking flightTracking = new AirCourierFlightTracking();
	private double smoothedEtaTicks = -1;
	private int lastEtaTick;

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
		if (state == State.DROP_PENDING) {
			tickDrop(server);
			return;
		}
		FlightTarget target = prepareFlightTarget(server);
		if (target == null) return;
		deliveryElapsedTicks++;
		if (deliveryElapsedTicks > RECOVERY_WATCHDOG_TICKS
			&& !flightTracking.usesDeliveryLimit(target.playerTarget())) {
			doFail(server);
			return;
		}
		boolean reposition = flightTracking.sample(
			target.player() != null ? target.player().position() : target.landingTarget(),
			target.level().dimension(), server.getTickCount());
		if (reposition) previousFlightPosition = null;
		if (flightTracking.shouldApproach(deliveryElapsedTicks, target.playerTarget())) {
			flightTracking.beginFinalApproach(position, target.landingTarget());
			tickFinalApproach(server, target);
			return;
		}
		if (reposition || shouldTeleportNearTarget(target)) {
			teleportNearTarget(target);
		}
		Vec3 tickStartPosition = position;
		switch (phase) {
			case TAKEOFF -> tickTakeoff(target);
			case EXITING_DIMENSION -> tickExitDimension();
			case CRUISE -> tickCruise(target);
			case LANDING -> tickLanding(server, server.getLevel(currentDimension), target);
		}
		if (isActive()) previousFlightPosition = tickStartPosition;
	}

	public void prepareForDispatch(MinecraftServer server) {
		if (!removed && state != State.DROP_PENDING) prepareFlightTarget(server);
	}

	private @Nullable FlightTarget prepareFlightTarget(MinecraftServer server) {
		AirCourierDeliveryService.TargetState targetState = AirCourierDeliveryService.targetState(
			server, mission, targetDimension, targetPhantomPortPos, targetPlayerId, box);
		if (targetState == AirCourierDeliveryService.TargetState.PLAYER_OFFLINE) {
			pauseForPlayer(server);
			return null;
		}
		ServerLevel currentLevel = server.getLevel(currentDimension);
		FlightTarget target = targetState == AirCourierDeliveryService.TargetState.AVAILABLE
			? resolveFlightTarget(server) : null;
		if (target == null || currentLevel == null || !isFlightTargetAllowed(currentLevel, target)) {
			doFail(server);
			return null;
		}
		if (state == State.PLAYER_OFFLINE || phase == AirCourierEntity.Phase.WAITING) {
			state = State.ACTIVE;
			deliveryElapsedTicks = 0;
			teleportedNearTarget = false;
			flightTracking.reset();
			clearCaches();
			beginCruise();
		}
		return target;
	}

	public void pauseForPlayer(MinecraftServer server) {
		if (removed || state != State.ACTIVE) return;
		setLandingOpen(resolveLoadedTargetPhantomPort(server.getLevel(targetDimension)), false);
		state = State.PLAYER_OFFLINE;
		motion = Vec3.ZERO;
		clearCaches();
	}

	private void tickTakeoff(FlightTarget target) {
		initializeTakeoffTarget(target);
		phaseTicks++;

		AirCourierFlightPlanner.FlightStep step = AirCourierFlightPlanner.takeoff(FLIGHT,
			position, motion, launchDirection, phaseTicks, takeoffDuration, takeoffTarget,
			takeoffStart, takeoffInitialMotion);

		motion = step.motion();

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
		Vec3 direction = AirCourierFlightMath.sanitizeHorizontalDirection(motion);
		motion = direction.scale(FLIGHT.cruiseSpeed());
		phaseTicks++;
		position = position.add(motion);
	}

	private void tickCruise(FlightTarget target) {
		phaseTicks++;

		Vec3 navigationTarget = getNavigationTarget(target);
		Vec3 approachGate = AirCourierFlightTargets.approachGate(FLIGHT,
			position, motion, navigationTarget, target.playerTarget());
		AirCourierFlightPlanner.FlightStep step = AirCourierFlightPlanner.cruise(FLIGHT,
			position, flightTracking.relativeMotion(motion), approachGate, target.landingTarget(),
			phaseTicks, target.playerTarget(), flightTracking.hasMoved());
		motion = flightTracking.inheritMovement(step.motion());

		if (step.complete()) {
			phase = AirCourierEntity.Phase.LANDING;
			phaseTicks = 0;
			setLandingOpen(resolveLoadedTargetPhantomPort(target.level()), true);
		}

		position = position.add(motion);
	}

	private void tickLanding(MinecraftServer server, ServerLevel currentLevel, FlightTarget target) {
		phaseTicks++;
		setLandingOpen(resolveLoadedTargetPhantomPort(target.level()), true);

		if (hasReachedTarget(target, previousFlightPosition, flightTracking.movement())) {
			doFinishDelivery(server, currentLevel);
			return;
		}

		if (flightTracking.hasMoved() && position.distanceTo(target.landingTarget()) > FLIGHT.landingDecelerationRange() * 2) {
			setLandingOpen(resolveLoadedTargetPhantomPort(target.level()), false);
			beginCruise();
			tickCruise(target);
			return;
		}

		Vec3 landingMotion = AirCourierFlightPlanner.landing(FLIGHT,
			position, flightTracking.relativeMotion(motion), target.landingTarget(),
			target.completionDistance(), target.playerTarget(), flightTracking.hasMoved());
		motion = flightTracking.inheritMovement(landingMotion);
		Vec3 start = position;
		position = position.add(motion);
		if (hasReachedTarget(target, start, Vec3.ZERO)) {
			doFinishDelivery(server, currentLevel);
		}
	}

	private void tickFinalApproach(MinecraftServer server, FlightTarget target) {
		Vec3 start = position;
		currentDimension = target.level().dimension();
		targetDimension = target.level().dimension();
		phase = AirCourierEntity.Phase.LANDING;
		phaseTicks++;
		setLandingOpen(resolveLoadedTargetPhantomPort(target.level()), true);
		position = flightTracking.finalApproachPosition(target.landingTarget(), deliveryElapsedTicks);
		motion = position.subtract(start);
		previousFlightPosition = null;
		if (deliveryElapsedTicks >= AirCourierFlightTracking.DELIVERY_LIMIT_TICKS
			|| hasReachedTarget(target, null, Vec3.ZERO)) {
			doFinishDelivery(server, target.level());
		}
	}

	private void teleportNearTarget(FlightTarget target) {
		Vec3 spawnPos = computeNearTargetSpawn(target);
		double reentrySpeed = Math.max(FLIGHT.cruiseSpeed(), flightTracking.relativeMotion(motion).length());

		currentDimension = target.level().dimension();
		if (target.player() != null) {
			targetDimension = target.player().serverLevel().dimension();
		}
		position = spawnPos;

		motion = flightTracking.inheritMovement(target.cruiseTarget().subtract(position).normalize().scale(reentrySpeed));

		phase = AirCourierEntity.Phase.CRUISE;
		phaseTicks = 0;
		teleportedNearTarget = true;
		previousFlightPosition = null;
		smoothedEtaTicks = -1;
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
		boolean differentDimension = !target.level().dimension().equals(currentDimension);
		if (teleportedNearTarget && flightTracking.usesDeliveryLimit(target.playerTarget())) {
			return differentDimension || phaseTicks >= PLAYER_REACQUIRE_COOLDOWN_TICKS
				&& position.distanceTo(target.landingTarget()) > LONG_ROUTE_REMAINING_DISTANCE;
		}
		if (teleportedNearTarget || deliveryElapsedTicks < LONG_ROUTE_CHECK_TICKS) {
			return false;
		}
		return differentDimension
			|| position.distanceTo(target.landingTarget()) > LONG_ROUTE_REMAINING_DISTANCE;
	}

	private Vec3 getNavigationTarget(FlightTarget target) {
		return AirCourierFlightMath.navigationTarget(target.landingTarget(), flightTracking.smoothedMovement(),
			position.distanceTo(target.landingTarget()), target.completionDistance());
	}

	private boolean isFlightTargetAllowed(ServerLevel currentLevel, FlightTarget target) {
		return (mission != AirCourierEntity.Mission.PACKAGE_TO_AIRPORT
			&& mission != AirCourierEntity.Mission.PACKAGE_TO_PLAYER)
			|| AirCourierDimensionRules.canTarget(currentLevel, target.level().dimension());
	}

	private @Nullable PhantomPortBlockEntity resolveLoadedTargetPhantomPort(@Nullable ServerLevel level) {
		return AirCourierDeliveryService.resolveTargetPhantomPort(level, targetPhantomPortPos);
	}

	private void beginCruise() {
		phase = AirCourierEntity.Phase.CRUISE;
		phaseTicks = 0;
		previousFlightPosition = null;
		smoothedEtaTicks = -1;
	}

	private void doFinishDelivery(MinecraftServer server, @Nullable ServerLevel level) {
		if (!isActive()) return;
		if (level == null) { doFail(server); return; }

		setLandingOpen(resolveLoadedTargetPhantomPort(server.getLevel(targetDimension)), false);

		boolean delivered = AirCourierDeliveryService.finishDelivery(
			server, box, mission, sourceDimension, sourcePhantomPortPos, sourcePlayerId,
			targetDimension, targetPhantomPortPos, targetPlayerId, hudPlayerId, hudEntryId);

		if (!delivered) {
			if (prepareFlightTarget(server) != null) doFail(server);
			return;
		}
		AirCourierDeliveryService.spawnDeliveryParticles(level, position);
		if (mission == AirCourierEntity.Mission.PACKAGE_TO_PLAYER && !box.isEmpty()) {
			startCarrierReturn(server);
			return;
		}
		markRemoved();
	}

	private void doFail(MinecraftServer server) {
		if (removed || state == State.DROP_PENDING) return;
		setLandingOpen(resolveLoadedTargetPhantomPort(server.getLevel(targetDimension)), false);
		state = State.DROP_PENDING;
		motion = Vec3.ZERO;
		clearCaches();
		AirCourierDeliveryService.notifyFailure(server, box, targetPlayerId, hudPlayerId, hudEntryId);
		tickDrop(server);
	}

	private void tickDrop(MinecraftServer server) {
		ServerLevel level = server.getLevel(currentDimension);
		if (level == null || !level.isPositionEntityTicking(BlockPos.containing(position))) return;
		if (!packageDropped) {
			packageDropped = mission == AirCourierEntity.Mission.CARRIER_RETURN
				|| mission == AirCourierEntity.Mission.CARRIER_RETURN_TO_PLAYER
				|| AirCourierDeliveryService.dropPackage(level, position, box);
			if (!packageDropped) return;
		}
		if (AirCourierDeliveryService.dropCarrier(level, position)) markRemoved();
	}

	private void startCarrierReturn(MinecraftServer server) {
		if (sourcePhantomPortPos != null && sourceDimension != null) {
			targetPhantomPortPos = sourcePhantomPortPos;
			targetDimension = sourceDimension;
			targetPlayerId = null;
			resetForReturn(AirCourierEntity.Mission.CARRIER_RETURN);
		} else {
			targetPhantomPortPos = null;
			targetPlayerId = sourcePlayerId;
			ServerPlayer sourcePlayer = AirCourierDeliveryService.resolvePlayer(server, sourcePlayerId);
			if (sourcePlayer != null) targetDimension = sourcePlayer.serverLevel().dimension();
			resetForReturn(AirCourierEntity.Mission.CARRIER_RETURN_TO_PLAYER);
		}
		prepareFlightTarget(server);
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
		state = State.ACTIVE;
		packageDropped = false;
		flightTracking.reset();
		clearCaches();
		takeoffTarget = null;
		launchDirection = takeoffDirection();
		motion = AirCourierFlightMath.launchMotion(launchDirection,
			Math.min(motion.horizontalDistance(), FLIGHT.takeoffSpeed()));
		takeoffStart = position;
		takeoffInitialMotion = motion;
		takeoffDuration = FLIGHT.takeoffTicks();
	}

	private Vec3 takeoffDirection() {
		return AirCourierFlightMath.sanitizeHorizontalDirection(
			motion.horizontalDistance() > 1.0E-4 ? motion : launchDirection);
	}

	private double takeoffScale(FlightTarget target, Vec3 origin) {
		return currentDimension.equals(target.level().dimension())
			? AirCourierFlightMath.takeoffScale(origin, target.landingTarget()) : 1.0;
	}

	private void initializeTakeoffTarget(FlightTarget target) {
		if (takeoffTarget != null) return;
		Vec3 hDir = takeoffDirection();
		Vec3 origin = takeoffStart != null ? takeoffStart : position;
		double scale = takeoffScale(target, origin);
		takeoffDuration = Mth.ceil(FLIGHT.takeoffTicks() * scale);
		takeoffTarget = origin.add(hDir.scale(FLIGHT.takeoffForwardDistance() * scale))
			.add(0, FLIGHT.takeoffAltitudeGain() * scale, 0);
	}

	private boolean hasReachedTarget(FlightTarget target, @Nullable Vec3 previousPosition, Vec3 targetMovement) {
		if (!currentDimension.equals(target.level().dimension())) return false;
		return target.player() != null
			? AirCourierFlightMath.reachesPlayer(position, previousPosition, target.player().getBoundingBox(),
				target.landingTarget(), targetMovement, target.completionDistance())
			: AirCourierFlightMath.reachesTarget(position, previousPosition, target.landingTarget(),
				targetMovement, target.completionDistance());
	}

	private void setLandingOpen(@Nullable PhantomPortBlockEntity phantomPort, boolean open) {
		if (phantomPort != null) {
			phantomPort.setCourierLandingOpen(id, open);
		}
	}

	private void clearCaches() {
		flightTracking.clearMotion();
		previousFlightPosition = null;
		smoothedEtaTicks = -1;
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
				AirCourierFlightTargets.cruiseTarget(FLIGHT, level, targetPhantomPortPos),
				AirCourierFlightTargets.landingTarget(FLIGHT, level, targetPhantomPortPos),
				FLIGHT.phantomPortCompletionDistance());
		}

		ServerPlayer player = AirCourierDeliveryService.resolvePlayer(server, targetPlayerId);
		if (player == null) {
			return null;
		}
		return new FlightTarget(player.serverLevel(), player,
			AirCourierFlightTargets.cruiseTarget(FLIGHT, null, player),
			AirCourierFlightTargets.landingTarget(FLIGHT, null, player),
			FLIGHT.playerCompletionDistance());
	}

	public AirCourierTaskSnapshot snapshot(MinecraftServer server) {
		int remainingTicks = estimateRemainingTicks(server);
		AirCourierHudStatus status = getHudStatus();
		return new AirCourierTaskSnapshot(id, getHudTrackingPlayerId(), currentDimension,
			position, box, remainingTicks, status, hudEntryId);
	}

	public int estimateRemainingTicks(MinecraftServer server) {
		FlightTarget target = isActive() ? resolveFlightTarget(server) : null;
		if (target == null || phase == AirCourierEntity.Phase.WAITING) {
			smoothedEtaTicks = -1;
			return -1;
		}
		int untilDeliveryLimit = Math.max(0, AirCourierFlightTracking.DELIVERY_LIMIT_TICKS - deliveryElapsedTicks);
		boolean limitedDelivery = flightTracking.usesDeliveryLimit(target.playerTarget());
		if (flightTracking.isFinalApproach()) return untilDeliveryLimit;

		int physicalEstimate = switch (phase) {
			case TAKEOFF -> estimateTakeoffTicks(target);
			case EXITING_DIMENSION -> estimateExitDimensionTicks(target);
			case CRUISE -> estimateFlightTicks(position, target, false);
			case LANDING -> estimateFlightTicks(position, target, true);
			case WAITING -> -1;
		};

		if (!teleportedNearTarget
			&& (!target.level().dimension().equals(currentDimension)
				|| position.distanceTo(target.landingTarget()) > LONG_ROUTE_REMAINING_DISTANCE)) {
			Vec3 teleportPreview = computeNearTargetSpawn(target);
			int afterTeleport = estimateFlightTicks(teleportPreview, target, false);
			int untilTeleport = Math.max(0, LONG_ROUTE_CHECK_TICKS - deliveryElapsedTicks);
			physicalEstimate = Math.min(physicalEstimate, untilTeleport + afterTeleport);
		}
		if (limitedDelivery) {
			physicalEstimate = Math.min(physicalEstimate, untilDeliveryLimit);
		}

		int tick = server.getTickCount();
		if (smoothedEtaTicks < 0) {
			smoothedEtaTicks = physicalEstimate;
		} else if (tick != lastEtaTick) {
			double remaining = Math.max(0, smoothedEtaTicks - Math.max(0, tick - lastEtaTick));
			smoothedEtaTicks = Mth.lerp(0.5, remaining, physicalEstimate);
		}
		lastEtaTick = tick;
		int result = Mth.ceil(smoothedEtaTicks);
		return limitedDelivery ? Math.min(result, untilDeliveryLimit) : result;
	}

	private int estimateTakeoffTicks(FlightTarget target) {
		int remainingTakeoff = Math.max(0, takeoffDuration - phaseTicks);
		Vec3 projectedEnd = takeoffTarget;
		if (projectedEnd == null) {
			Vec3 hDir = takeoffDirection();
			double scale = takeoffScale(target, position);
			remainingTakeoff = Math.max(0, Mth.ceil(FLIGHT.takeoffTicks() * scale) - phaseTicks);
			projectedEnd = position.add(hDir.scale(FLIGHT.takeoffForwardDistance() * scale))
				.add(0, FLIGHT.takeoffAltitudeGain() * scale, 0);
		}
		if (target.playerTarget() || flightTracking.hasMoved()) {
			Vec3 projectedMotion = AirCourierFlightMath.sanitizeHorizontalDirection(launchDirection).scale(FLIGHT.cruiseSpeed());
			Vec3 projectedTarget = target.landingTarget().add(flightTracking.smoothedMovement().scale(remainingTakeoff));
			return remainingTakeoff + AirCourierFlightEstimate.targetTicks(FLIGHT, projectedEnd,
				projectedMotion, projectedTarget, target.completionDistance(), false, target.playerTarget());
		}
		return remainingTakeoff + estimateFlightTicks(projectedEnd, target, false);
	}

	private int estimateExitDimensionTicks(FlightTarget target) {
		Vec3 teleportPreview = computeNearTargetSpawn(target);
		int afterTeleport = estimateFlightTicks(teleportPreview, target, false);
		int untilTeleport = Math.max(0, LONG_ROUTE_CHECK_TICKS - deliveryElapsedTicks);
		return untilTeleport + afterTeleport;
	}

	private int estimateFlightTicks(Vec3 from, FlightTarget target, boolean landing) {
		if (target.playerTarget() || flightTracking.hasMoved()) {
			return AirCourierFlightEstimate.targetTicks(FLIGHT, from,
				flightTracking.relativeMotion(motion), target.landingTarget(), target.completionDistance(), landing, target.playerTarget());
		}
		return landing
			? AirCourierFlightEstimate.landingTicks(FLIGHT, from, target.landingTarget(), target.completionDistance())
			: AirCourierFlightEstimate.cruiseAndLandingTicks(FLIGHT, from,
				target.cruiseTarget(), target.landingTarget(), target.completionDistance());
	}

	private AirCourierHudStatus getHudStatus() {
		if (state == State.DROP_PENDING) return AirCourierHudStatus.FAILED;
		if (state == State.PLAYER_OFFLINE) return AirCourierHudStatus.PREPARING;
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
	public @Nullable BlockPos targetPhantomPortPos() { return targetPhantomPortPos; }
	public @Nullable UUID targetPlayerId() { return targetPlayerId; }
	public @Nullable UUID hudEntryId() { return hudEntryId; }
	public AirCourierEntity.Mission mission() { return mission; }
	public AirCourierEntity.Phase phase() { return phase; }
	public Vec3 position() { return position; }
	public Vec3 motion() { return motion; }
	public Vec3 launchDirection() { return launchDirection; }
	public boolean isActive() { return !removed && state == State.ACTIVE; }
	public boolean isRemoved() { return removed; }
	private void markRemoved() { removed = true; }

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
		tag.putInt("TakeoffDuration", takeoffDuration);
		tag.putInt("DeliveryElapsedTicks", deliveryElapsedTicks);
		tag.putBoolean("TeleportedNearTarget", teleportedNearTarget);
		tag.putString("State", state.name());
		tag.putBoolean("PackageDropped", packageDropped);
		tag.put("FlightTracking", flightTracking.save());
		if (takeoffTarget != null) tag.put("TakeoffTarget", vecToTag(takeoffTarget));
		if (takeoffStart != null) tag.put("TakeoffStart", vecToTag(takeoffStart));
		if (takeoffInitialMotion != null) tag.put("TakeoffInitialMotion", vecToTag(takeoffInitialMotion));
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
		task.takeoffDuration = tag.contains("TakeoffDuration") ? tag.getInt("TakeoffDuration") : FLIGHT.takeoffTicks();
		task.deliveryElapsedTicks = tag.getInt("DeliveryElapsedTicks");
		task.teleportedNearTarget = tag.getBoolean("TeleportedNearTarget");
		task.state = tag.contains("State") ? State.valueOf(tag.getString("State")) : State.ACTIVE;
		task.packageDropped = tag.getBoolean("PackageDropped");
		task.flightTracking.load(tag.getCompound("FlightTracking"));
		task.takeoffTarget = tag.contains("TakeoffTarget") ? vecFromTag(tag, "TakeoffTarget") : null;
		task.takeoffStart = tag.contains("TakeoffStart") ? vecFromTag(tag, "TakeoffStart") : null;
		task.takeoffInitialMotion = tag.contains("TakeoffInitialMotion") ? vecFromTag(tag, "TakeoffInitialMotion") : null;
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

	private enum State { ACTIVE, PLAYER_OFFLINE, DROP_PENDING }

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
