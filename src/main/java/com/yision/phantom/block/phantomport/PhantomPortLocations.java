package com.yision.phantom.block.phantomport;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.yision.phantom.compat.sable.SableCompat;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class PhantomPortLocations extends SavedData {
	private static final Factory<PhantomPortLocations> FACTORY = new Factory<>(
		PhantomPortLocations::new, PhantomPortLocations::load, null);

	private final Map<UUID, PhantomPortReference> locations = new HashMap<>();

	public static PhantomPortLocations get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "createphantom_port_locations");
	}

	private static PhantomPortLocations load(CompoundTag tag, HolderLookup.Provider registries) {
		PhantomPortLocations data = new PhantomPortLocations();
		if (tag.contains("Locations")) {
			PhantomPortReference.CODEC.listOf().parse(NbtOps.INSTANCE, tag.get("Locations"))
				.resultOrPartial().ifPresent(entries ->
					entries.forEach(entry -> data.locations.put(entry.id(), entry)));
		}
		return data;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		PhantomPortReference.CODEC.listOf().encodeStart(NbtOps.INSTANCE, locations.values().stream().toList())
			.result().ifPresent(entries -> tag.put("Locations", entries));
		return tag;
	}

	@Nullable
	PhantomPortReference find(UUID id) {
		return locations.get(id);
	}

	void update(PhantomPortReference reference) {
		if (!reference.equals(locations.put(reference.id(), reference)))
			setDirty();
	}

	void remove(UUID id, GlobalPos position) {
		PhantomPortReference reference = locations.get(id);
		if (reference != null && reference.location().equals(position)) {
			locations.remove(id);
			setDirty();
		}
	}

	void removeMissingPlots(MinecraftServer server) {
		if (locations.values().removeIf(reference -> isMissingPlot(server, reference)))
			setDirty();
	}

	private static boolean isMissingPlot(MinecraftServer server, PhantomPortReference reference) {
		if (reference.subLevelId().isEmpty())
			return false;
		ServerLevel level = server.getLevel(reference.dimension());
		if (level == null || !SableCompat.isSubLevelPlot(level, reference.pos()))
			return false;
		var current = SableCompat.subLevelId(level, reference.pos());
		return current.isPresent() ? !current.equals(reference.subLevelId())
			: !SableCompat.isOccupiedPlot(level, reference.pos());
	}

	public PhantomPortReference latest(PhantomPortReference reference) {
		return locations.getOrDefault(reference.id(), reference);
	}
}
