package com.yision.phantom.compat.sable;

import java.util.Optional;
import java.util.UUID;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

final class SableLoadedCompat {
	private SableLoadedCompat() {
	}

	static Vec3 projectOutOfSubLevel(Level level, Vec3 position) {
		return Sable.HELPER.projectOutOfSubLevel(level, position);
	}

	static Optional<UUID> subLevelId(Level level, BlockPos pos) {
		return Optional.ofNullable(Sable.HELPER.getContaining(level, pos)).map(SubLevel::getUniqueId);
	}

	static boolean isSubLevelPlot(Level level, BlockPos pos) {
		SubLevelContainer container = SubLevelContainer.getContainer(level);
		return container != null && container.inBounds(pos);
	}

	static boolean isOccupiedPlot(Level level, BlockPos pos) {
		SubLevelContainer container = SubLevelContainer.getContainer(level);
		if (container == null || !container.inBounds(pos))
			return false;
		var origin = container.getOrigin();
		int x = (pos.getX() >> 4 >> container.getLogPlotSize()) - origin.x;
		int z = (pos.getZ() >> 4 >> container.getLogPlotSize()) - origin.y;
		return container.getOccupancy().get(container.getIndex(x, z));
	}

	static Vec3 projectVector(Level level, Vec3 origin, Vec3 vector) {
		Vec3 projectedOrigin = projectOutOfSubLevel(level, origin);
		Vec3 projectedEnd = projectOutOfSubLevel(level, origin.add(vector));
		return projectedEnd.subtract(projectedOrigin);
	}
}
