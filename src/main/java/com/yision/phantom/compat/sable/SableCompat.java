package com.yision.phantom.compat.sable;

import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import net.neoforged.fml.ModList;

public final class SableCompat {
	private static final String SABLE_MOD_ID = "sable";

	private SableCompat() {
	}

	public static Vec3 projectOutOfSubLevel(@Nullable Level level, Vec3 position) {
		if (level == null || !isLoaded()) {
			return position;
		}
		return SableLoadedCompat.projectOutOfSubLevel(level, position);
	}

	public static Vec3 projectVector(@Nullable Level level, Vec3 origin, Vec3 vector) {
		if (level == null || vector.lengthSqr() < 1.0E-12 || !isLoaded()) {
			return vector;
		}
		return SableLoadedCompat.projectVector(level, origin, vector);
	}

	public static Optional<UUID> subLevelId(Level level, BlockPos pos) {
		return isLoaded() ? SableLoadedCompat.subLevelId(level, pos) : Optional.empty();
	}

	public static boolean isSubLevelPlot(Level level, BlockPos pos) {
		return isLoaded() && SableLoadedCompat.isSubLevelPlot(level, pos);
	}

	public static boolean isOccupiedPlot(Level level, BlockPos pos) {
		return isLoaded() && SableLoadedCompat.isOccupiedPlot(level, pos);
	}

	public static boolean isLoaded() {
		return ModList.get().isLoaded(SABLE_MOD_ID);
	}
}
