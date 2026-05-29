package com.yision.phantom.logistics.courier;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class AirCourierTaskSavedData extends SavedData {

	private static final String DATA_NAME = "createphantom_courier_tasks";

	private final List<AirCourierTask> tasks = new ArrayList<>();

	public AirCourierTaskSavedData() {}

	public static AirCourierTaskSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
		AirCourierTaskSavedData data = new AirCourierTaskSavedData();
		ListTag list = tag.getList("Tasks", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag taskTag = list.getCompound(i);
			try {
				AirCourierTask task = AirCourierTask.load(registries, taskTag);
				data.tasks.add(task);
			} catch (Exception e) {
			}
		}
		return data;
	}

	@Override
	public @NotNull CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (AirCourierTask task : tasks) {
			if (!task.isRemoved()) {
				list.add(task.save(registries, new CompoundTag()));
			}
		}
		tag.put("Tasks", list);
		return tag;
	}

	public List<AirCourierTask> getTasks() {
		return tasks;
	}

	public void addTask(AirCourierTask task) {
		tasks.add(task);
		setDirty();
	}

	public void removeCompleted() {
		tasks.removeIf(AirCourierTask::isRemoved);
		setDirty();
	}

	public void markDirty() {
		setDirty();
	}

	public static AirCourierTaskSavedData getOrCreate(MinecraftServer server) {
		return server.getLevel(net.minecraft.world.level.Level.OVERWORLD)
			.getDataStorage()
			.computeIfAbsent(
				new SavedData.Factory<AirCourierTaskSavedData>(
					AirCourierTaskSavedData::new,
					AirCourierTaskSavedData::load,
					null
				),
				DATA_NAME
			);
	}
}
