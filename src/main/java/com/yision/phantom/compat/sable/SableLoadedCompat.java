package com.yision.phantom.compat.sable;

import dev.ryanhcode.sable.Sable;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

final class SableLoadedCompat {
	private SableLoadedCompat() {}

	static Vec3 projectOutOfSubLevel(Level level, Vec3 position) {
		return Sable.HELPER.projectOutOfSubLevel(level, position);
	}

	static Vec3 projectVector(Level level, Vec3 origin, Vec3 vector) {
		Vec3 projectedOrigin = projectOutOfSubLevel(level, origin);
		Vec3 projectedEnd = projectOutOfSubLevel(level, origin.add(vector));
		return projectedEnd.subtract(projectedOrigin);
	}
}
