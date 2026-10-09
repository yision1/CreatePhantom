package com.yision.phantom.logistics.courier.flight;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class AirCourierFlightPlanner {
	private static final double SPEED_ACCELERATION = 0.015;
	private static final double SPEED_DECELERATION = 0.02;

	private AirCourierFlightPlanner() {}

	public record FlightStep(Vec3 motion, boolean complete) {}

	public static FlightStep takeoff(AirCourierFlightProfile profile,
		Vec3 position, Vec3 currentMotion, Vec3 launchDirection,
		int phaseTicks, int takeoffDuration, @Nullable Vec3 cachedTakeoffTarget,
		@Nullable Vec3 takeoffStart, @Nullable Vec3 takeoffInitialMotion) {
		Vec3 takeoffTarget = cachedTakeoffTarget;
		if (takeoffTarget == null) {
			Vec3 horizontalDirection = AirCourierFlightMath.sanitizeHorizontalDirection(launchDirection);
			takeoffTarget = position.add(horizontalDirection.scale(profile.takeoffForwardDistance()))
				.add(0, profile.takeoffAltitudeGain(), 0);
		}

		Vec3 p0 = takeoffStart != null ? takeoffStart : position;
		Vec3 m0 = takeoffInitialMotion != null ? takeoffInitialMotion : currentMotion;
		Vec3 m1 = AirCourierFlightMath.sanitizeHorizontalDirection(takeoffTarget.subtract(p0));
		double tangentScale = profile.takeoffSpeed() * takeoffDuration / 3.0;
		m0 = m0.lengthSqr() > 1.0E-6 ? m0.normalize().scale(tangentScale) : Vec3.ZERO;
		m1 = m1.scale(tangentScale);

		double u = Mth.clamp((double) phaseTicks / takeoffDuration, 0.0, 1.0);
		Vec3 tangent = hermiteTangent(p0, takeoffTarget, m0, m1, u);
		double speed = smoothSpeed(currentMotion, Mth.lerp(u, profile.takeoffSpeed(), profile.cruiseSpeed()), SPEED_ACCELERATION);
		Vec3 motion = steerTowards(position, currentMotion, position.add(tangent), speed, 0.4, 3.0);

		boolean complete = phaseTicks >= takeoffDuration
			|| position.distanceTo(takeoffTarget) < profile.takeoffSwitchDistance();
		return new FlightStep(motion, complete);
	}

	private static Vec3 hermiteTangent(Vec3 p0, Vec3 p1, Vec3 m0, Vec3 m1, double t) {
		double t2 = t * t;
		return p1.subtract(p0).scale(6 * t - 6 * t2)
			.add(m0.scale(3 * t2 - 4 * t + 1))
			.add(m1.scale(3 * t2 - 2 * t));
	}

	private static double smoothSpeed(Vec3 currentMotion, double targetSpeed, double acceleration) {
		double currentSpeed = currentMotion.length();
		return Mth.clamp(targetSpeed, Math.max(0, currentSpeed - SPEED_DECELERATION),
			currentSpeed + acceleration);
	}

	public static FlightStep cruise(AirCourierFlightProfile profile,
		Vec3 position, Vec3 currentMotion, Vec3 approachGate, Vec3 landingTarget,
		int phaseTicks, boolean playerTarget, boolean movingTarget) {
		double distanceToGate = approachGate.distanceTo(position);
		double distanceToLanding = landingTarget.distanceTo(position);
		double horizontalToLanding = AirCourierFlightMath.horizontalDistance(position, landingTarget);

		double curveAmount = distanceResponsiveCurve(profile, distanceToGate,
			profile.cruiseCurveNear(), profile.cruiseCurveFar(),
			Math.max(0, profile.cruiseStraightenTicks() - phaseTicks));
		Vec3 motion = steerTowards(position, currentMotion, approachGate,
			smoothSpeed(currentMotion, AirCourierFlightMath.closingSpeed(profile, distanceToLanding,
				playerTarget ? profile.playerCompletionDistance() : profile.phantomPortCompletionDistance(), false, playerTarget),
				playerTarget || movingTarget ? AirCourierFlightMath.pursuitAcceleration(distanceToLanding) : SPEED_ACCELERATION),
			curveAmount, profile.cruiseTurnDegrees());

		boolean complete = distanceToGate < profile.approachGateNearDistance()
			|| (horizontalToLanding < profile.approachGateHorizontalThreshold() && position.y > landingTarget.y)
			|| (playerTarget && distanceToLanding < profile.playerCompletionDistance());

		return new FlightStep(motion, complete);
	}

	public static Vec3 landing(AirCourierFlightProfile profile,
		Vec3 position, Vec3 currentMotion, Vec3 landingTarget,
		double completionDistance, boolean playerTarget, boolean movingTarget) {
		double distance = landingTarget.distanceTo(position);
		double normalizedDistance = Math.max(0.0, distance - completionDistance);

		double pursuitSpeed = playerTarget ? AirCourierFlightMath.pursuitClosingSpeed(distance) : 0;
		double speed = smoothSpeed(currentMotion,
			AirCourierFlightMath.closingSpeed(profile, distance, completionDistance, true, playerTarget),
			playerTarget || movingTarget ? AirCourierFlightMath.pursuitAcceleration(distance) : SPEED_ACCELERATION);
		double flareFactor = 1.0 - Mth.clamp(normalizedDistance / 2.0, 0.0, 1.0);
		double flareHeight = Math.min(Math.max(0, position.y - landingTarget.y) * flareFactor * 0.35, 0.3);
		Vec3 aimTarget = landingTarget.add(0, flareHeight, 0);

		double curveAmount = distanceResponsiveCurve(profile, normalizedDistance,
			profile.landingCurveNear(), profile.landingCurveFar(), 0);
		Vec3 motion = steerTowards(position, currentMotion, aimTarget,
			speed, curveAmount, profile.landingTurnDegrees());

		double maxDownSpeed = playerTarget ? Math.max(profile.landingMaxDownSpeed(), pursuitSpeed) : profile.landingMaxDownSpeed();
		double maxUpSpeed = playerTarget ? Math.max(profile.landingMaxUpSpeed(), pursuitSpeed) : profile.landingMaxUpSpeed();
		double verticalSpeed = Mth.clamp(motion.y, -maxDownSpeed, maxUpSpeed);
		verticalSpeed = Mth.clamp(verticalSpeed, currentMotion.y - SPEED_DECELERATION,
			currentMotion.y + SPEED_DECELERATION);
		verticalSpeed = Mth.clamp(verticalSpeed, -speed, speed);
		double horizontalSpeed = Math.sqrt(Math.max(0, speed * speed - verticalSpeed * verticalSpeed));
		double yaw = Math.atan2(motion.z, motion.x);
		return new Vec3(Math.cos(yaw) * horizontalSpeed, verticalSpeed, Math.sin(yaw) * horizontalSpeed);
	}

	public static Vec3 steerTowards(Vec3 position, Vec3 currentMotion,
		Vec3 target, double speed, double steeringFactor, double maxTurnDegrees) {
		Vec3 desired = target.subtract(position);
		if (desired.lengthSqr() < 1.0E-6) {
			return currentMotion.lengthSqr() > 1.0E-6 ? currentMotion.normalize().scale(speed) : Vec3.ZERO;
		}
		if (currentMotion.lengthSqr() < 1.0E-6) {
			return desired.normalize().scale(speed);
		}
		double steering = Mth.clamp(steeringFactor, 0.0, 1.0);
		double yaw = Math.atan2(currentMotion.z, currentMotion.x);
		double yawDelta = Mth.wrapDegrees(Math.toDegrees(Math.atan2(desired.z, desired.x) - yaw));
		yaw += Math.toRadians(Mth.clamp(yawDelta * steering, -maxTurnDegrees, maxTurnDegrees));

		double pitch = Math.atan2(currentMotion.y, currentMotion.horizontalDistance());
		double pitchDelta = Math.atan2(desired.y, desired.horizontalDistance()) - pitch;
		double maxTurnRadians = Math.toRadians(maxTurnDegrees);
		pitch += Mth.clamp(pitchDelta * steering, -maxTurnRadians, maxTurnRadians);

		double horizontalSpeed = speed * Math.cos(pitch);
		return new Vec3(Math.cos(yaw) * horizontalSpeed, Math.sin(pitch) * speed,
			Math.sin(yaw) * horizontalSpeed);
	}

	static double distanceResponsiveCurve(AirCourierFlightProfile profile, double distance,
		double nearCurve, double farCurve, int extraDistanceTicks) {
		double augmentedDistance = distance + Math.max(0, extraDistanceTicks);
		double clampedDistance = Mth.clamp(augmentedDistance, profile.curveDistanceMin(), profile.curveDistanceMax());
		double t = (clampedDistance - profile.curveDistanceMin()) / (profile.curveDistanceMax() - profile.curveDistanceMin());
		return Mth.lerp(t, nearCurve, farCurve);
	}

}
