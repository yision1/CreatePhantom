package com.yision.phantom.logistics.courier;

import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;

public final class AirCourierLaunchRules {
	private static final float MIN_TAKEOFF_SPEED = 16.0f;

	private AirCourierLaunchRules() {}

	public static boolean canLaunchFrom(BeltBlockEntity belt, int insertedAt, boolean beltMovementPositive) {
		int requiredLength = requiredBeltLength(belt);
		return requiredLength > 0 && effectiveBeltLength(belt.beltLength, insertedAt, beltMovementPositive) >= requiredLength;
	}

	public static int requiredBeltLength(BeltBlockEntity belt) {
		return requiredBeltLengthForSpeed(Math.abs(belt.getSpeed()));
	}

	public static int effectiveBeltLength(int beltLength, int insertedAt, boolean beltMovementPositive) {
		if (beltLength <= 0) {
			return 0;
		}
		int clampedInsertedAt = Math.max(0, Math.min(insertedAt, beltLength - 1));
		return beltMovementPositive ? beltLength - clampedInsertedAt : clampedInsertedAt + 1;
	}

	public static int requiredBeltLengthForSpeed(float absoluteSpeed) {
		if (absoluteSpeed < MIN_TAKEOFF_SPEED) {
			return -1;
		}
		if (absoluteSpeed >= 256.0f) {
			return 2;
		}
		if (absoluteSpeed >= 128.0f) {
			return 3;
		}
		if (absoluteSpeed >= 64.0f) {
			return 4;
		}
		if (absoluteSpeed >= 32.0f) {
			return 5;
		}
		return 6;
	}
}
