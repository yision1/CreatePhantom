package com.yision.phantom.logistics.courier;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;

import com.yision.phantom.CreatePhantom;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public class AirCourierTaskSavedData extends SavedData {
	private static final String DATA_NAME = "createphantom_courier_tasks";
	private static final Factory<AirCourierTaskSavedData> FACTORY = new Factory<>(
		AirCourierTaskSavedData::new, AirCourierTaskSavedData::load, null);

	private final Map<UUID, AirCourierTask> tasks = new LinkedHashMap<>();

	public AirCourierTaskSavedData() {
	}

	public static AirCourierTaskSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
		AirCourierTaskSavedData data = new AirCourierTaskSavedData();
		ListTag list = tag.getList("Tasks", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag taskTag = list.getCompound(i);
			try {
				AirCourierTask task = AirCourierTask.load(registries, taskTag);
				if (data.tasks.putIfAbsent(task.id(), task) != null)
					CreatePhantom.LOGGER.warn("Skipping duplicate saved courier task {}", task.id());
			} catch (Exception e) {
				CreatePhantom.LOGGER.warn("Skipping malformed courier task at saved-data index {}", i, e);
			}
		}
		return data;
	}

	@Override
	public @NotNull CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (AirCourierTask task : tasks.values()) {
			if (!task.isRemoved()) {
				list.add(task.save(registries, new CompoundTag()));
			}
		}
		tag.put("Tasks", list);
		return tag;
	}

	public Collection<AirCourierTask> getTasks() {
		return tasks.values();
	}

	public boolean contains(UUID id) {
		return tasks.containsKey(id);
	}

	public void addTask(AirCourierTask task) {
		tasks.put(task.id(), task);
		setDirty();
	}

	public void removeCompleted() {
		if (tasks.values().removeIf(AirCourierTask::isRemoved))
			setDirty();
	}

	public static AirCourierTaskSavedData getOrCreate(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
	}
}
