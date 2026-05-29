package com.yision.phantom.logistics.courier;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public sealed interface AirCourierTarget permits AirCourierTarget.PlayerTarget, AirCourierTarget.PhantomPortTarget {
	ResourceKey<Level> dimension();

	record PlayerTarget(UUID playerId, ResourceKey<Level> dimension) implements AirCourierTarget {}
	record PhantomPortTarget(ResourceKey<Level> dimension, BlockPos pos) implements AirCourierTarget {}
}
