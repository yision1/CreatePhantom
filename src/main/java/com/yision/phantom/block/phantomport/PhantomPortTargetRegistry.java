package com.yision.phantom.block.phantomport;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.yision.phantom.compat.sable.SableCompat;
import com.yision.phantom.logistics.address.PhantomAddressRules;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class PhantomPortTargetRegistry {
	private static final int ENTRY_TIMEOUT_TICKS = 60;
	private static final Map<ResourceKey<Level>, Map<BlockPos, Entry>> TARGETS = new HashMap<>();

	private static final Comparator<RankedTarget> TARGET_ORDER = Comparator
		.comparing(RankedTarget::otherDimension)
		.thenComparing(target -> !target.exact())
		.thenComparingDouble(RankedTarget::distance)
		.thenComparing(target -> target.location().dimension().location().toString())
		.thenComparing(target -> target.location().address())
		.thenComparingLong(target -> target.location().pos().asLong());

	private PhantomPortTargetRegistry() {
	}

	public static void update(ServerLevel level, BlockPos pos, String address) {
		Map<BlockPos, Entry> entries = TARGETS.computeIfAbsent(level.dimension(), key -> new HashMap<>());
		String normalized = address == null ? "" : address.trim();
		String canonical = PhantomAddressRules.canonical(normalized);
		if (canonical.isBlank()) {
			entries.remove(pos);
			if (entries.isEmpty()) {
				TARGETS.remove(level.dimension());
			}
			return;
		}
		entries.put(pos.immutable(), new Entry(normalized, canonical, level.getServer().getTickCount()));
	}

	public static @Nullable TargetLocation findMatchingAnyDimension(ServerLevel level, String address,
		@Nullable Vec3 origin, @Nullable ResourceKey<Level> excludedDimension, @Nullable BlockPos excludedPos,
		Predicate<TargetLocation> predicate) {
		String canonical = PhantomAddressRules.canonical(address);
		if (canonical.isBlank())
			return null;

		Vec3 worldOrigin = origin == null ? null : SableCompat.projectOutOfSubLevel(level, origin);
		RankedTarget best = null;
		for (var dimensionEntry : TARGETS.entrySet()) {
			ResourceKey<Level> dimension = dimensionEntry.getKey();
			boolean sameDimension = dimension.equals(level.dimension());
			for (var entry : dimensionEntry.getValue().entrySet()) {
				BlockPos pos = entry.getKey();
				Entry port = entry.getValue();
				if (isExcluded(dimension, pos, excludedDimension, excludedPos)
					|| !PackageItem.matchAddress(canonical, port.canonicalAddress())) {
					continue;
				}
				double distance = sameDimension && worldOrigin != null
					? worldOrigin.distanceToSqr(SableCompat.projectOutOfSubLevel(level, Vec3.atCenterOf(pos)))
					: 0;
				TargetLocation location = new TargetLocation(dimension, pos, port.address());
				RankedTarget candidate = new RankedTarget(location, !sameDimension,
					canonical.equalsIgnoreCase(port.canonicalAddress()), distance);
				if ((best == null || TARGET_ORDER.compare(candidate, best) < 0) && predicate.test(location))
					best = candidate;
			}
		}
		return best == null ? null : best.location();
	}

	public static void onServerTick(ServerTickEvent.Post event) {
		int currentTick = event.getServer().getTickCount();
		if (currentTick % 20 != 0) {
			return;
		}
		if (currentTick % 200 == 0 && SableCompat.isLoaded())
			PhantomPortLocations.get(event.getServer()).removeMissingPlots(event.getServer());
		TARGETS.entrySet().removeIf(levelEntry -> {
			levelEntry.getValue().entrySet()
				.removeIf(entry -> currentTick - entry.getValue().lastSeenTick > ENTRY_TIMEOUT_TICKS);
			return levelEntry.getValue().isEmpty();
		});
	}

	public static void clearAll() {
		TARGETS.clear();
	}

	private static boolean isExcluded(ResourceKey<Level> dimension, BlockPos pos,
		@Nullable ResourceKey<Level> excludedDimension, @Nullable BlockPos excludedPos) {
		return excludedDimension != null && excludedPos != null
			&& dimension.equals(excludedDimension) && pos.equals(excludedPos);
	}

	private record Entry(String address, String canonicalAddress, int lastSeenTick) {
	}

	private record RankedTarget(TargetLocation location, boolean otherDimension, boolean exact, double distance) {
	}

	public record TargetLocation(ResourceKey<Level> dimension, BlockPos pos, String address) {
	}
}
