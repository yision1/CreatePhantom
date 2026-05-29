package com.yision.phantom.logistics.courier.flight;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class AirCourierFlightPlanner {

	private AirCourierFlightPlanner() {}

	public record FlightStep(Vec3 motion, boolean complete) {}

	public static FlightStep takeoff(AirCourierFlightProfile profile,
		Vec3 position, Vec3 currentMotion, Vec3 launchDirection,
		int phaseTicks, @Nullable Vec3 cachedTakeoffTarget,
		@Nullable Vec3 takeoffStart, @Nullable Vec3 takeoffInitialMotion,
		@Nullable Vec3 exitTarget) {
		Vec3 takeoffTarget = cachedTakeoffTarget;
		if (takeoffTarget == null) {
			Vec3 horizontalDirection = AirCourierFlightMath.sanitizeHorizontalDirection(launchDirection);
			if (horizontalDirection.lengthSqr() < 1.0E-4) {
				return new FlightStep(currentMotion, false);
			}
			takeoffTarget = position.add(horizontalDirection.scale(profile.takeoffForwardDistance()))
				.add(0, profile.takeoffAltitudeGain(), 0);
		}

		Vec3 p0 = takeoffStart != null ? takeoffStart : position;
		Vec3 m0 = takeoffInitialMotion != null ? takeoffInitialMotion : currentMotion;
		Vec3 m1;
		if (exitTarget != null) {
			m1 = exitTarget.subtract(takeoffTarget);
			if (m1.lengthSqr() < 1.0E-6) {
				m1 = takeoffTarget.subtract(p0);
			}
		} else {
			m1 = takeoffTarget.subtract(p0);
		}
		double tangentScale = profile.takeoffSpeed() * profile.takeoffTicks() / 3.0;
		m0 = m0.lengthSqr() > 1.0E-6 ? m0.normalize().scale(tangentScale) : Vec3.ZERO;
		m1 = m1.lengthSqr() > 1.0E-6 ? m1.normalize().scale(tangentScale) : Vec3.ZERO;

		double u = Mth.clamp((double) phaseTicks / profile.takeoffTicks(), 0.0, 1.0);
		double eased = easeOutCubic(u);
		Vec3 desiredPos = hermite(p0, takeoffTarget, m0, m1, eased);
		Vec3 motion = desiredPos.subtract(position);

		double minSpeed = profile.takeoffSpeed() * 0.55;
		double maxSpeed = profile.takeoffSpeed() * 1.35;
		double motionLength = motion.length();
		if (motionLength > 1.0E-6) {
			motion = motion.normalize().scale(Mth.clamp(motionLength, minSpeed, maxSpeed));
		}

		boolean complete = phaseTicks >= profile.takeoffTicks()
			|| position.distanceTo(takeoffTarget) < profile.takeoffSwitchDistance();
		return new FlightStep(motion, complete);
	}

	private static double easeOutCubic(double t) {
		return 1.0 - Math.pow(1.0 - t, 3.0);
	}

	private static Vec3 hermite(Vec3 p0, Vec3 p1, Vec3 m0, Vec3 m1, double t) {
		double t2 = t * t;
		double t3 = t2 * t;
		double h00 = 2*t3 - 3*t2 + 1;
		double h10 = t3 - 2*t2 + t;
		double h01 = -2*t3 + 3*t2;
		double h11 = t3 - t2;
		return p0.scale(h00).add(m0.scale(h10)).add(p1.scale(h01)).add(m1.scale(h11));
	}

	public static FlightStep cruise(AirCourierFlightProfile profile,
		Vec3 position, Vec3 currentMotion, Vec3 approachGate, Vec3 landingTarget,
		int phaseTicks, boolean playerTarget) {
		double distanceToGate = approachGate.distanceTo(position);
		double distanceToLanding = landingTarget.distanceTo(position);
		double horizontalToLanding = AirCourierFlightMath.horizontalDistance(position, landingTarget);

		double curveAmount = distanceResponsiveCurve(profile, distanceToGate,
			profile.cruiseCurveNear(), profile.cruiseCurveFar(),
			Math.max(0, profile.cruiseStraightenTicks() - phaseTicks));
		Vec3 motion = steerTowards(position, currentMotion, approachGate,
			profile.cruiseSpeed(), curveAmount, profile.cruiseTurnDegrees());

		boolean complete = distanceToGate < profile.approachGateNearDistance()
			|| (horizontalToLanding < profile.approachGateHorizontalThreshold() && position.y > landingTarget.y)
			|| (playerTarget && distanceToLanding < profile.playerCompletionDistance());

		return new FlightStep(motion, complete);
	}

	public static FlightStep landing(AirCourierFlightProfile profile,
		Vec3 position, Vec3 currentMotion, Vec3 landingTarget,
		double completionDistance, boolean playerTarget) {
		double distance = landingTarget.distanceTo(position);
		double normalizedDistance = Math.max(0.0, distance - completionDistance);

		double speedFactor = Mth.clamp(normalizedDistance / profile.landingDecelerationRange(), 0.0, 1.0);
		double speed = Mth.lerp(speedFactor, profile.landingMinSpeed(), profile.landingSpeed());

		double curveAmount = distanceResponsiveCurve(profile, normalizedDistance,
			profile.landingCurveNear(), profile.landingCurveFar(), 0);
		Vec3 motion = steerTowards(position, currentMotion, landingTarget,
			speed, curveAmount, profile.landingTurnDegrees());

		motion = new Vec3(motion.x,
			Mth.clamp(motion.y, -profile.landingMaxDownSpeed(), profile.landingMaxUpSpeed()),
			motion.z);

		boolean complete = distance < completionDistance;
		return new FlightStep(motion, complete);
	}

	public static Vec3 steerTowards(Vec3 position, Vec3 currentMotion,
		Vec3 target, double speed, double steeringFactor, double maxTurnDegrees) {
		Vec3 desired = target.subtract(position);
		if (desired.lengthSqr() < 1.0E-6) {
			return currentMotion.lengthSqr() > 1.0E-6 ? currentMotion.normalize().scale(speed) : Vec3.ZERO;
		}
		Vec3 desiredDirection = desired.normalize();
		if (currentMotion.lengthSqr() < 1.0E-6) {
			return desiredDirection.scale(speed);
		}
		Vec3 currentDirection = currentMotion.normalize();
		double dot = Mth.clamp(currentDirection.dot(desiredDirection), -1.0, 1.0);
		double angleRadians = Math.acos(dot);
		if (angleRadians < 1.0E-4) {
			return desiredDirection.scale(speed);
		}
		double maxTurnRadians = Math.toRadians(maxTurnDegrees);
		double turnFactor = Math.min(1.0, maxTurnRadians / angleRadians);
		double blendFactor = Math.min(Mth.clamp(steeringFactor, 0.0, 1.0), turnFactor);
		Vec3 nextDirection = currentDirection.lerp(desiredDirection, blendFactor);
		if (nextDirection.lengthSqr() < 1.0E-6) {
			nextDirection = desiredDirection;
		}
		return nextDirection.normalize().scale(speed);
	}

	static double distanceResponsiveCurve(AirCourierFlightProfile profile, double distance,
		double nearCurve, double farCurve, int extraDistanceTicks) {
		double augmentedDistance = distance + Math.max(0, extraDistanceTicks);
		double clampedDistance = Mth.clamp(augmentedDistance, profile.curveDistanceMin(), profile.curveDistanceMax());
		double t = (clampedDistance - profile.curveDistanceMin()) / (profile.curveDistanceMax() - profile.curveDistanceMin());
		return Mth.lerp(t, nearCurve, farCurve);
	}

}
