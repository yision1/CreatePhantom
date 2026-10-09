package com.yision.phantom.logistics.courier.flight;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class AirCourierFlightMath {

	private AirCourierFlightMath() {}

	public static Vec3 launchMotion(Vec3 direction, double horizontalSpeed) {
		return sanitizeHorizontalDirection(direction).scale(horizontalSpeed)
			.add(0, horizontalSpeed * 0.25, 0);
	}

	public static Vec3 sanitizeHorizontalDirection(Vec3 direction) {
		Vec3 horizontal = new Vec3(direction.x, 0, direction.z);
		if (horizontal.lengthSqr() < 1.0E-4) {
			return new Vec3(0, 0, 1);
		}
		Vec3 normalized = horizontal.normalize();
		return new Vec3(normalized.x, 0, normalized.z);
	}

	public static Vec3 sanitizeNonNegativeDirection(Vec3 direction) {
		if (direction.lengthSqr() < 1.0E-4) {
			return new Vec3(0, 0, 1);
		}
		Vec3 normalized = direction.normalize();
		return new Vec3(normalized.x, Math.max(normalized.y, 0), normalized.z);
	}

	public static double horizontalDistance(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	public static double pursuitClosingSpeed(double distance) {
		double distanceFactor = Mth.clamp((distance - 3.0) / 9.0, 0.0, 1.0);
		return Mth.lerp(distanceFactor, 0.12, 0.45);
	}

	public static double closingSpeed(AirCourierFlightProfile profile, double distance,
		double completionDistance, boolean landing, boolean playerTarget) {
		double baseSpeed = profile.cruiseSpeed();
		if (landing) {
			double factor = Mth.clamp((distance - completionDistance) / profile.landingDecelerationRange(), 0, 1);
			baseSpeed = Mth.lerp(factor, profile.landingMinSpeed(), profile.landingSpeed());
		}
		return playerTarget ? Math.max(baseSpeed, pursuitClosingSpeed(distance)) : baseSpeed;
	}

	public static double pursuitAcceleration(double distance) {
		return distance > 8.0 ? 0.05 : 0.015;
	}

	public static double takeoffScale(Vec3 origin, Vec3 target) {
		return Mth.clamp(horizontalDistance(origin, target) / 12.0, 0.35, 1.0);
	}

	public static Vec3 navigationTarget(Vec3 deliveryTarget, Vec3 targetMovement,
		double distance, double completionDistance) {
		double predictionTicks = 4.0 * Mth.clamp((distance - completionDistance)
			/ (12.0 - completionDistance), 0.0, 1.0);
		return deliveryTarget.add(targetMovement.scale(predictionTicks));
	}

	public static boolean reachesPlayer(Vec3 position, @Nullable Vec3 previousPosition,
		AABB playerBounds, Vec3 deliveryTarget, Vec3 playerMovement, double completionDistance) {
		AABB bounds = playerBounds.inflate(0.45, 0.6, 0.45);
		if (bounds.contains(position) || reachesTarget(position, previousPosition, deliveryTarget,
			playerMovement, completionDistance)) {
			return true;
		}
		if (previousPosition == null) {
			return false;
		}

		Vec3 start = previousPosition.add(playerMovement);
		return bounds.contains(start) || bounds.clip(start, position).isPresent();
	}

	public static boolean reachesTarget(Vec3 position, @Nullable Vec3 previousPosition,
		Vec3 deliveryTarget, Vec3 targetMovement, double completionDistance) {
		double completionDistanceSqr = completionDistance * completionDistance;
		if (position.distanceToSqr(deliveryTarget) <= completionDistanceSqr) return true;
		if (previousPosition == null) return false;
		Vec3 start = previousPosition.add(targetMovement);
		Vec3 segment = position.subtract(start);
		double lengthSqr = segment.lengthSqr();
		double t = lengthSqr > 1.0E-6
			? Mth.clamp(deliveryTarget.subtract(start).dot(segment) / lengthSqr, 0.0, 1.0) : 0;
		return start.add(segment.scale(t)).distanceToSqr(deliveryTarget) <= completionDistanceSqr;
	}
}
