package com.yision.phantom.block.phantomport;

import com.yision.phantom.logistics.address.PhantomAddressRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.function.Predicate;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

public final class PhantomPortTargetRegistry {
	private static final int ENTRY_TIMEOUT_TICKS = 60;
	private static final Map<ResourceKey<Level>, Map<BlockPos, Entry>> TARGETS = new HashMap<>();

	private PhantomPortTargetRegistry() {}

	public static void update(ServerLevel level, BlockPos pos, String address) {
		Map<BlockPos, Entry> entries = TARGETS.computeIfAbsent(level.dimension(), key -> new HashMap<>());
		String normalized = normalize(address);
		if (PhantomAddressRules.isBlank(normalized)) {
			entries.remove(pos);
			if (entries.isEmpty()) {
				TARGETS.remove(level.dimension());
			}
			return;
		}
		entries.put(pos.immutable(), new Entry(normalized, level.getServer().getTickCount()));
	}

	public static @Nullable BlockPos findMatching(ServerLevel level, String address, @Nullable Vec3 origin) {
		String normalized = normalize(address);
		if (PhantomAddressRules.isBlank(normalized)) {
			return null;
		}

		Map<BlockPos, Entry> entries = TARGETS.get(level.dimension());
		if (entries == null || entries.isEmpty()) {
			return null;
		}

		return entries.entrySet()
			.stream()
			.filter(entry -> PhantomAddressRules.matches(normalized, entry.getValue().address))
			.sorted(Comparator
				.<Map.Entry<BlockPos, Entry>>comparingInt(entry -> exactMatch(normalized, entry.getValue().address) ? 0 : 1)
				.thenComparingDouble(entry -> distanceTo(origin, entry.getKey()))
				.thenComparing(entry -> entry.getValue().address))
			.map(Map.Entry::getKey)
			.findFirst()
			.orElse(null);
	}

	public static @Nullable TargetLocation findMatchingAnyDimension(ServerLevel level, String address, @Nullable Vec3 origin) {
		return findMatchingAnyDimension(level, address, origin, null, null);
	}

	public static @Nullable TargetLocation findMatchingAnyDimension(ServerLevel level, String address, @Nullable Vec3 origin,
		@Nullable ResourceKey<Level> excludedDimension, @Nullable BlockPos excludedPos) {
		return findMatchingAnyDimension(level, address, origin, excludedDimension, excludedPos, target -> true);
	}

	public static @Nullable TargetLocation findMatchingAnyDimension(ServerLevel level, String address, @Nullable Vec3 origin,
		@Nullable ResourceKey<Level> excludedDimension, @Nullable BlockPos excludedPos, Predicate<TargetLocation> predicate) {
		String normalized = normalize(address);
		if (PhantomAddressRules.isBlank(normalized)) {
			return null;
		}

		return TARGETS.entrySet()
			.stream()
			.flatMap(levelEntry -> levelEntry.getValue()
				.entrySet()
				.stream()
				.filter(entry -> PhantomAddressRules.matches(normalized, entry.getValue().address))
				.filter(entry -> !isExcluded(levelEntry.getKey(), entry.getKey(), excludedDimension, excludedPos))
				.map(entry -> new TargetLocation(levelEntry.getKey(), entry.getKey(), entry.getValue().address)))
			.filter(predicate)
			.sorted(Comparator
				.<TargetLocation>comparingInt(target -> target.dimension().equals(level.dimension()) ? 0 : 1)
				.thenComparingInt(target -> exactMatch(normalized, target.address()) ? 0 : 1)
				.thenComparingDouble(target -> target.dimension().equals(level.dimension()) ? distanceTo(origin, target.pos()) : 0)
				.thenComparing(target -> target.dimension().location().toString())
				.thenComparing(TargetLocation::address))
			.findFirst()
			.orElse(null);
	}

	public static List<String> getKnownNames(ServerLevel level) {
		Set<String> uniqueNames = new LinkedHashSet<>();
		TARGETS.values()
			.stream()
			.flatMap(entries -> entries.values().stream())
			.map(entry -> entry.address)
			.sorted(String::compareToIgnoreCase)
			.forEach(uniqueNames::add);
		return new ArrayList<>(uniqueNames);
	}

	public static void onServerTick(ServerTickEvent.Post event) {
		int currentTick = event.getServer().getTickCount();
		TARGETS.entrySet().removeIf(levelEntry -> {
			levelEntry.getValue().entrySet().removeIf(entry -> currentTick - entry.getValue().lastSeenTick > ENTRY_TIMEOUT_TICKS);
			return levelEntry.getValue().isEmpty();
		});
	}

	private static String normalize(String address) {
		return address == null ? "" : address.trim();
	}

	private static boolean exactMatch(String left, String right) {
		return PhantomAddressRules.canonical(left).equalsIgnoreCase(PhantomAddressRules.canonical(right));
	}

	private static double distanceTo(@Nullable Vec3 origin, BlockPos pos) {
		if (origin == null) {
			return 0;
		}
		return origin.distanceToSqr(Vec3.atCenterOf(pos));
	}

	private static boolean isExcluded(ResourceKey<Level> dimension, BlockPos pos,
		@Nullable ResourceKey<Level> excludedDimension, @Nullable BlockPos excludedPos) {
		return excludedDimension != null && excludedPos != null
			&& dimension.equals(excludedDimension) && pos.equals(excludedPos);
	}

	private record Entry(String address, int lastSeenTick) {}

	public record TargetLocation(ResourceKey<Level> dimension, BlockPos pos, String address) {}
}
