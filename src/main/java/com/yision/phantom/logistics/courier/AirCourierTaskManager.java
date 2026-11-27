package com.yision.phantom.logistics.courier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.yision.phantom.entity.courier.AirCourierEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class AirCourierTaskManager {
	private static @Nullable AirCourierTaskSavedData savedData;
	private static final Map<UUID, AirCourierEntity> visualEntities = new HashMap<>();

	private AirCourierTaskManager() {
	}

	public static void onServerStarting(MinecraftServer server) {
		clearRuntimeState();
		savedData = AirCourierTaskSavedData.getOrCreate(server);
	}

	public static void onServerTick(ServerTickEvent.Post event) {
		MinecraftServer server = event.getServer();
		AirCourierTaskSavedData data = getSavedData(server);
		boolean hasCompletedTasks = false;
		boolean changed = false;

		for (AirCourierTask task : data.getTasks()) {
			changed |= task.tick(server);
			hasCompletedTasks |= task.isRemoved();
			updateVisualEntity(server, task);
		}

		if (hasCompletedTasks)
			data.removeCompleted();
		else if (changed)
			data.setDirty();
	}

	public static void onServerStopping() {
		if (savedData != null) {
			savedData.getTasks().forEach(AirCourierTask::releaseChunkTicket);
			savedData.setDirty();
		}
		savedData = null;
		clearRuntimeState();
	}

	public static boolean addTask(MinecraftServer server, AirCourierTask task) {
		AirCourierTaskSavedData data = getSavedData(server);
		if (data.contains(task.id()) || !task.prepareForDispatch(server))
			return false;
		data.addTask(task);
		updateVisualEntity(server, task);
		return true;
	}

	private static AirCourierTaskSavedData getSavedData(MinecraftServer server) {
		if (savedData == null)
			savedData = AirCourierTaskSavedData.getOrCreate(server);
		return savedData;
	}

	public static void onPlayerAvailable(ServerPlayer player) {
		if (savedData != null)
			savedData.getTasks().forEach(task -> task.wakeForPlayer(player.getUUID()));
	}

	public static void onPlayerLoggedOut(ServerPlayer player) {
		if (savedData == null)
			return;
		for (AirCourierTask task : savedData.getTasks()) {
			if (task.pauseForPlayer(player.getUUID())) {
				removeVisualEntity(task);
				savedData.setDirty();
			}
		}
	}

	public static List<AirCourierTask.AirCourierTaskSnapshot> getHudSnapshots(MinecraftServer server) {
		if (savedData == null)
			return List.of();
		List<AirCourierTask.AirCourierTaskSnapshot> snapshots = new ArrayList<>();
		for (AirCourierTask task : savedData.getTasks()) {
			UUID playerId = task.getHudTrackingPlayerId();
			if (playerId == null || server.getPlayerList().getPlayer(playerId) == null)
				continue;
			snapshots.add(task.snapshot(server));
		}
		return snapshots;
	}

	private static void updateVisualEntity(MinecraftServer server, AirCourierTask task) {
		ServerLevel level = task.isActive() ? server.getLevel(task.currentDimension()) : null;
		if (level != null && level.isPositionEntityTicking(BlockPos.containing(task.position()))) {
			spawnOrSyncVisualEntity(level, task);
		} else {
			removeVisualEntity(task);
		}
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
		if (level.addFreshEntity(courier)) {
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
	}

	private static void clearRuntimeState() {
		for (AirCourierEntity entity : visualEntities.values()) {
			if (entity != null && entity.isAlive())
				entity.discard();
		}
		visualEntities.clear();
	}
}
