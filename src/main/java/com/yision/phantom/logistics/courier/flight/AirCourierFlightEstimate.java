package com.yision.phantom.logistics.courier.flight;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class AirCourierFlightEstimate {

	private AirCourierFlightEstimate() {}

	public static int cruiseAndLandingTicks(AirCourierFlightProfile profile,
		Vec3 from, Vec3 cruiseTarget, Vec3 landingTarget, double completionDistance) {
		Vec3 initialMotion = cruiseTarget.subtract(from);
		Vec3 approachGate = AirCourierFlightTargets.approachGate(profile, from, initialMotion, landingTarget, false);
		boolean alreadyLanding = from.distanceTo(approachGate) < profile.approachGateNearDistance()
			|| (AirCourierFlightMath.horizontalDistance(from, landingTarget) < profile.approachGateHorizontalThreshold()
				&& from.y > landingTarget.y);
		if (alreadyLanding) {
			return landingTicks(profile, from, landingTarget, completionDistance);
		}

		double cruiseDistance = Math.max(0, from.distanceTo(approachGate) - profile.approachGateNearDistance());
		int cruiseTicks = Mth.ceil(cruiseDistance / profile.cruiseSpeed());
		return cruiseTicks + landingTicks(profile, approachGate, landingTarget, completionDistance);
	}

	public static int landingTicks(AirCourierFlightProfile profile,
		Vec3 from, Vec3 landingTarget, double completionDistance) {
		double landingDistance = Math.max(0, from.distanceTo(landingTarget) - completionDistance);
		double decelerationDistance = Math.min(landingDistance, profile.landingDecelerationRange());
		double speedDifference = profile.landingSpeed() - profile.landingMinSpeed();
		double endSpeed = profile.landingMinSpeed()
			+ speedDifference * decelerationDistance / profile.landingDecelerationRange();
		double ticks = profile.landingDecelerationRange() / speedDifference
			* Math.log(endSpeed / profile.landingMinSpeed());
		ticks += Math.max(0, landingDistance - decelerationDistance) / profile.landingSpeed();
		double verticalDistance = Math.max(0, Math.abs(from.y - landingTarget.y) - completionDistance);
		double verticalSpeed = from.y > landingTarget.y ? profile.landingMaxDownSpeed() : profile.landingMaxUpSpeed();
		return Mth.ceil(Math.max(ticks, verticalDistance / verticalSpeed));
	}

	public static int targetTicks(AirCourierFlightProfile profile, Vec3 from, Vec3 currentMotion,
		Vec3 landingTarget, double completionDistance, boolean landing, boolean playerTarget) {
		Vec3 toTarget = landingTarget.subtract(from);
		double distance = toTarget.length();
		double remainingDistance = Math.max(0, distance - completionDistance);
		if (remainingDistance == 0) return 0;
		Vec3 direction = toTarget.normalize();
		double segmentDistance = remainingDistance / 16.0;
		double ticks = 0;
		for (int i = 0; i < 16; i++) {
			double sampleDistance = completionDistance + (i + 0.5) * segmentDistance;
			double speed = AirCourierFlightMath.closingSpeed(profile, sampleDistance, completionDistance,
				landing || sampleDistance <= 8.0, playerTarget);
			ticks += segmentDistance / speed;
		}
		double targetSpeed = AirCourierFlightMath.closingSpeed(profile, distance, completionDistance,
			landing || distance <= 8.0, playerTarget);
		double speedIncrease = Math.max(0, targetSpeed - currentMotion.length());
		double accelerationTicks = speedIncrease / AirCourierFlightMath.pursuitAcceleration(distance);
		ticks += speedIncrease * accelerationTicks * 0.5 / targetSpeed;
		if (currentMotion.lengthSqr() > 1.0E-6) {
			double angle = Math.acos(Mth.clamp(currentMotion.normalize().dot(direction), -1.0, 1.0));
			ticks += Math.toDegrees(angle) / (landing ? profile.landingTurnDegrees() : profile.cruiseTurnDegrees());
		}
		return Mth.ceil(ticks);
	}
}
