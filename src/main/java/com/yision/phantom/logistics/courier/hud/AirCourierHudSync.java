package com.yision.phantom.logistics.courier.hud;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.yision.phantom.logistics.courier.AirCourierTask;
import com.yision.phantom.logistics.courier.AirCourierTaskManager;
import com.yision.phantom.network.courier.AirCourierHudPacket;

import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class AirCourierHudSync {
	private static final int TERMINAL_DISPLAY_TICKS = 60;
	private static final int TRANSIENT_STATE_GRACE_TICKS = 60;
	private static final int ACTIVE_MISS_GRACE_TICKS = 25;
	private static final int GUARANTEED_REFRESH_TICKS = 100;

	private static final Map<UUID, PlayerHudState> HUD_STATES = new HashMap<>();

	private AirCourierHudSync() {}

	public static void onCourierPreparing(ServerPlayer player, ItemStack box, UUID hudEntryId) {
		int currentTick = player.server.getTickCount();
		track(player.getUUID(), hudEntryId, box, AirCourierHudStatus.PREPARING, -1,
			HudSourceType.TRANSIENT, currentTick);
		flushToPlayer(player);
	}

	public static void onCourierTaskStarted(MinecraftServer server, AirCourierTask task) {
		UUID playerId = task.getHudTrackingPlayerId();
		if (playerId == null)
			return;
		ServerPlayer player = server.getPlayerList().getPlayer(playerId);
		if (player == null)
			return;

		observeTask(task.snapshot(server), server.getTickCount());
		flushToPlayer(player);
	}

	public static void onCourierDelivered(ServerPlayer player, ItemStack box, @Nullable UUID hudEntryId) {
		onCourierTerminal(player, box, hudEntryId, AirCourierHudStatus.DELIVERED, 0);
	}

	public static void onCourierFailed(ServerPlayer player, ItemStack box, @Nullable UUID hudEntryId) {
		onCourierTerminal(player, box, hudEntryId, AirCourierHudStatus.FAILED, -1);
	}

	private static void onCourierTerminal(ServerPlayer player, ItemStack box, @Nullable UUID hudEntryId,
		AirCourierHudStatus status, int etaSeconds) {
		UUID id = hudEntryId != null ? hudEntryId : UUID.randomUUID();
		PlayerHudState playerState = HUD_STATES.get(player.getUUID());
		TrackedHudState existing = playerState == null ? null : playerState.entries.get(id);
		if (existing != null && !existing.wasVisible) {
			playerState.entries.remove(id);
			flushToPlayerOrHide(player);
			return;
		}
		track(player.getUUID(), id, box, status, etaSeconds, HudSourceType.TERMINAL, player.server.getTickCount());
		flushToPlayer(player);
	}

	private static void track(UUID playerId, UUID id, ItemStack box, AirCourierHudStatus status, int etaSeconds,
		HudSourceType sourceType, int currentTick) {
		PlayerHudState playerState = HUD_STATES.computeIfAbsent(playerId, key -> new PlayerHudState());
		TrackedHudState state = playerState.entries.computeIfAbsent(id, key -> new TrackedHudState());
		List<ItemStack> contents;
		if (state.entry != null && ItemStack.matches(state.box, box)) {
			contents = state.entry.displayStacks();
		} else {
			state.box = box.copy();
			contents = AirCourierPackagePreview.fromPackage(box);
		}
		state.entry = new AirCourierHudEntry(id, status, etaSeconds, contents);
		state.sourceType = sourceType;
		state.lastObservedTick = currentTick;
		state.expiresAtTick = switch (sourceType) {
			case TRANSIENT -> currentTick + TRANSIENT_STATE_GRACE_TICKS;
			case TERMINAL -> currentTick + TERMINAL_DISPLAY_TICKS;
			case TASK -> -1;
		};
	}

	private static UUID observeTask(AirCourierTask.AirCourierTaskSnapshot snapshot, int currentTick) {
		int remainingTicks = snapshot.remainingTicks();
		int etaSeconds = remainingTicks < 0 ? -1 : Math.max(0, Mth.ceil(remainingTicks / 20f));
		UUID id = snapshot.hudEntryId() != null ? snapshot.hudEntryId() : snapshot.taskId();
		track(snapshot.hudTrackingPlayerId(), id, snapshot.box(), snapshot.status(), etaSeconds,
			HudSourceType.TASK, currentTick);
		return id;
	}

	public static void onServerTick(ServerTickEvent.Post event) {
		MinecraftServer server = event.getServer();
		int currentTick = server.getTickCount();
		if (currentTick % 20 != 0)
			return;

		Map<UUID, Set<UUID>> observedByPlayer = new HashMap<>();
		for (AirCourierTask.AirCourierTaskSnapshot snapshot : AirCourierTaskManager.getHudSnapshots(server)) {
			UUID playerId = snapshot.hudTrackingPlayerId();
			if (playerId == null || server.getPlayerList().getPlayer(playerId) == null)
				continue;
			UUID id = observeTask(snapshot, currentTick);
			observedByPlayer.computeIfAbsent(playerId, key -> new HashSet<>()).add(id);
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID playerId = player.getUUID();
			PlayerHudState playerState = HUD_STATES.get(playerId);
			if (playerState == null)
				continue;
			Set<UUID> observed = observedByPlayer.getOrDefault(playerId, Set.of());
			Iterator<Map.Entry<UUID, TrackedHudState>> it = playerState.entries.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<UUID, TrackedHudState> entry = it.next();
				if (observed.contains(entry.getKey()))
					continue;
				TrackedHudState state = entry.getValue();
				boolean expired = switch (state.sourceType) {
					case TERMINAL -> !state.wasVisible || currentTick > state.expiresAtTick;
					case TASK -> currentTick - state.lastObservedTick > ACTIVE_MISS_GRACE_TICKS;
					case TRANSIENT -> currentTick > state.expiresAtTick;
				};
				if (expired)
					it.remove();
			}
			flushToPlayerOrHide(player);
		}

		HUD_STATES.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
	}

	public static void clearPlayer(ServerPlayer player) {
		HUD_STATES.remove(player.getUUID());
	}

	public static void clearAll() {
		HUD_STATES.clear();
	}

	private static void flushToPlayer(ServerPlayer player) {
		PlayerHudState playerState = HUD_STATES.get(player.getUUID());
		if (playerState == null || playerState.entries.isEmpty())
			return;

		List<TrackedHudState> visibleStates = playerState.entries.values().stream()
			.limit(AirCourierHudPayload.MAX_VISIBLE_ENTRIES)
			.toList();
		for (TrackedHudState state : visibleStates)
			state.wasVisible = true;
		AirCourierHudPayload payload = new AirCourierHudPayload(visibleStates.stream()
			.map(state -> state.entry).toList());
		int currentTick = player.server.getTickCount();
		boolean fullRefresh = playerState.lastFullSyncTick < 0
			|| currentTick - playerState.lastFullSyncTick >= GUARANTEED_REFRESH_TICKS;
		if (fullRefresh || !payloadEquals(playerState.lastSentPayload, payload)) {
			AirCourierHudPayload previous = fullRefresh ? AirCourierHudPayload.hidden() : playerState.lastSentPayload;
			CatnipServices.NETWORK.sendToClient(player, AirCourierHudPacket.of(payload, previous));
			playerState.lastSentPayload = payload;
			if (fullRefresh)
				playerState.lastFullSyncTick = currentTick;
		}
	}

	private static void flushToPlayerOrHide(ServerPlayer player) {
		PlayerHudState playerState = HUD_STATES.get(player.getUUID());
		if (playerState == null)
			return;
		if (playerState.entries.isEmpty()) {
			if (playerState.lastSentPayload.visible())
				CatnipServices.NETWORK.sendToClient(player, AirCourierHudPacket.hidden());
			HUD_STATES.remove(player.getUUID());
			return;
		}
		flushToPlayer(player);
	}

	private static boolean payloadEquals(AirCourierHudPayload first, AirCourierHudPayload second) {
		if (first.entries().size() != second.entries().size())
			return false;
		for (int i = 0; i < first.entries().size(); i++) {
			AirCourierHudEntry a = first.entries().get(i);
			AirCourierHudEntry b = second.entries().get(i);
			if (!a.id().equals(b.id()) || a.status() != b.status() || a.etaSeconds() != b.etaSeconds()
				|| !a.hasSameContents(b))
				return false;
		}
		return true;
	}

	private enum HudSourceType {
		TRANSIENT,
		TASK,
		TERMINAL
	}

	private static final class PlayerHudState {
		final Map<UUID, TrackedHudState> entries = new LinkedHashMap<>();
		AirCourierHudPayload lastSentPayload = AirCourierHudPayload.hidden();
		long lastFullSyncTick = -1;
	}

	private static final class TrackedHudState {
		ItemStack box = ItemStack.EMPTY;
		AirCourierHudEntry entry;
		HudSourceType sourceType;
		long expiresAtTick;
		long lastObservedTick;
		boolean wasVisible;
	}
}
