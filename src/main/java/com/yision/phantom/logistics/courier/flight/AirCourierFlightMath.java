package com.yision.phantom.logistics.courier.flight;

import net.minecraft.world.phys.Vec3;

public final class AirCourierFlightMath {

	private AirCourierFlightMath() {}

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
}
