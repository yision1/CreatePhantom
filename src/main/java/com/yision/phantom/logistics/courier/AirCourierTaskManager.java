package com.yision.phantom.logistics.courier;

import com.yision.phantom.entity.courier.AirCourierEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AirCourierTaskManager {

	private static @Nullable AirCourierTaskSavedData savedData;
	private static final Map<UUID, AirCourierEntity> visualEntities = new HashMap<>();

	private AirCourierTaskManager() {}

	public static void onServerStarting(MinecraftServer server) {
		savedData = AirCourierTaskSavedData.getOrCreate(server);
	}

	public static void onServerTick(ServerTickEvent.Post event) {
		MinecraftServer server = event.getServer();
		if (savedData == null) {
			savedData = AirCourierTaskSavedData.getOrCreate(server);
		}

		List<AirCourierTask> tasks = savedData.getTasks();
		List<AirCourierTask> completed = new ArrayList<>();

		for (AirCourierTask task : tasks) {
			task.tick(server);

			if (task.isRemoved()) {
				removeVisualEntity(task);
				completed.add(task);
				continue;
			}

			ServerLevel level = server.getLevel(task.currentDimension());
			if (level == null) {
				removeVisualEntity(task);
				continue;
			}

			if (canShowEntity(level, task.position())) {
				spawnOrSyncVisualEntity(level, task);
			} else {
				removeVisualEntity(task);
			}
		}

		if (!completed.isEmpty()) {
			savedData.removeCompleted();
		} else if (!tasks.isEmpty()) {
			savedData.markDirty();
		}
	}

	public static void addTask(MinecraftServer server, AirCourierTask task) {
		if (savedData == null) {
			savedData = AirCourierTaskSavedData.getOrCreate(server);
		}
		savedData.addTask(task);
		ServerLevel level = server.getLevel(task.currentDimension());
		if (level != null && canShowEntity(level, task.position())) {
			spawnOrSyncVisualEntity(level, task);
		}
	}

	public static List<AirCourierTask.AirCourierTaskSnapshot> getSnapshots(MinecraftServer server) {
		if (savedData == null) return List.of();
		List<AirCourierTask.AirCourierTaskSnapshot> snapshots = new ArrayList<>();
		for (AirCourierTask task : savedData.getTasks()) {
			if (task.isRemoved()) continue;
			snapshots.add(task.snapshot(server));
		}
		return snapshots;
	}

	private static boolean canShowEntity(ServerLevel level, net.minecraft.world.phys.Vec3 pos) {
		return level.isPositionEntityTicking(BlockPos.containing(pos));
	}

	private static void spawnOrSyncVisualEntity(ServerLevel level, AirCourierTask task) {
		AirCourierEntity existing = visualEntities.get(task.id());
		if (existing != null && existing.isAlive() && existing.level() == level) {
			syncEntityFromTask(existing, task);
			return;
		}
		if (existing != null && existing.isAlive()) {
			existing.discard();
		}
		AirCourierEntity courier = AirCourierEntity.createFromTask(level, task);
		if (courier != null && level.addFreshEntity(courier)) {
			visualEntities.put(task.id(), courier);
		}
	}

	private static void removeVisualEntity(AirCourierTask task) {
		AirCourierEntity entity = visualEntities.remove(task.id());
		if (entity != null && entity.isAlive()) {
			entity.discard();
		}
	}

	private static void syncEntityFromTask(AirCourierEntity entity, AirCourierTask task) {
		entity.setPackage(task.box());
		entity.setPhase(task.phase());
		entity.setMission(task.mission());
		entity.setDeltaMovement(task.motion());
		entity.setPos(task.position());
		entity.hurtMarked = true;
	}
}
