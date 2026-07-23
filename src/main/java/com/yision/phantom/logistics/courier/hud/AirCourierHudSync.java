package com.yision.phantom.logistics.courier.hud;

import com.yision.phantom.logistics.courier.AirCourierTask;
import com.yision.phantom.logistics.courier.AirCourierTaskManager;
import com.yision.phantom.network.courier.AirCourierHudPacket;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

public final class AirCourierHudSync {
	private static final int TERMINAL_DISPLAY_TICKS = 60;
	private static final int TRANSIENT_STATE_GRACE_TICKS = 60;
	private static final int ACTIVE_MISS_GRACE_TICKS = 25;
	private static final int GUARANTEED_REFRESH_TICKS = 100;

	private static final Map<UUID, PlayerHudState> HUD_STATES = new HashMap<>();
	private static final Map<ObservationKey, ObservedHudCandidate> OBSERVED_THIS_CYCLE = new HashMap<>();

	private AirCourierHudSync() {}

	public static void onCourierPreparing(ServerPlayer player, ItemStack box, UUID hudEntryId) {
		int currentTick = player.server.getTickCount();
		UUID playerId = player.getUUID();
		AirCourierHudEntry entry = new AirCourierHudEntry(AirCourierHudStatus.PREPARING, -1,
			AirCourierPackagePreview.fromPackage(box));
		HudSourceKey source = new HudSourceKey(hudEntryId);
		PlayerHudState playerState = HUD_STATES.computeIfAbsent(playerId, id -> new PlayerHudState());
		TrackedHudState existing = playerState.entries.get(source);
		long displayOrder = existing != null ? existing.displayOrder : playerState.nextDisplayOrder++;
		boolean wasVisible = existing != null && existing.wasVisible;
		playerState.entries.put(source, new TrackedHudState(entry, source, HudSourceType.TRANSIENT,
			currentTick + TRANSIENT_STATE_GRACE_TICKS, currentTick, -1, displayOrder, wasVisible));
		flushToPlayer(player);
	}

	public static void onCourierTaskStarted(MinecraftServer server, AirCourierTask task) {
		UUID playerId = task.getHudTrackingPlayerId();
		if (playerId == null) return;
		ServerPlayer player = server.getPlayerList().getPlayer(playerId);
		if (player == null) return;

		int currentTick = server.getTickCount();
		int etaSeconds = 0;
		int remainingTicks = task.estimateRemainingTicks(server);
		if (remainingTicks >= 0) {
			etaSeconds = Math.max(0, Mth.ceil(remainingTicks / 20f));
		}
		AirCourierHudEntry hudEntry = new AirCourierHudEntry(task.snapshot(server).status(), etaSeconds,
			AirCourierPackagePreview.fromPackage(task.box()));
		UUID effectiveHudId = task.hudEntryId() != null ? task.hudEntryId() : task.id();
		HudSourceKey source = new HudSourceKey(effectiveHudId);
		PlayerHudState playerState = HUD_STATES.computeIfAbsent(player.getUUID(), id -> new PlayerHudState());
		TrackedHudState existing = playerState.entries.get(source);
		long displayOrder = existing != null ? existing.displayOrder : playerState.nextDisplayOrder++;
		boolean wasVisible = existing != null && existing.wasVisible;
		playerState.entries.put(source, new TrackedHudState(hudEntry, source, HudSourceType.TASK,
			-1, currentTick, -1, displayOrder, wasVisible));
		flushToPlayer(player);
	}

	public static void onCourierDelivered(ServerPlayer player, ItemStack box, @Nullable UUID hudEntryId) {
		int currentTick = player.server.getTickCount();
		AirCourierHudEntry entry = new AirCourierHudEntry(AirCourierHudStatus.DELIVERED, 0,
			AirCourierPackagePreview.fromPackage(box));
		UUID effectiveId = hudEntryId != null ? hudEntryId : UUID.randomUUID();
		HudSourceKey source = new HudSourceKey(effectiveId);
		PlayerHudState playerState = HUD_STATES.computeIfAbsent(player.getUUID(), id -> new PlayerHudState());
		TrackedHudState existing = playerState.entries.get(source);
		if (existing != null && !existing.wasVisible) {
			playerState.entries.remove(source);
			flushToPlayerOrHide(player);
			return;
		}
		long displayOrder = existing != null ? existing.displayOrder : playerState.nextDisplayOrder++;
		boolean wasVisible = existing != null && existing.wasVisible;
		playerState.entries.put(source, new TrackedHudState(entry, source, HudSourceType.TERMINAL,
			-1, currentTick, currentTick + TERMINAL_DISPLAY_TICKS, displayOrder, wasVisible));
		flushToPlayer(player);
	}

	public static void onCourierFailed(ServerPlayer player, ItemStack box, @Nullable UUID hudEntryId) {
		int currentTick = player.server.getTickCount();
		AirCourierHudEntry entry = new AirCourierHudEntry(AirCourierHudStatus.FAILED, -1,
			AirCourierPackagePreview.fromPackage(box));
		UUID effectiveId = hudEntryId != null ? hudEntryId : UUID.randomUUID();
		HudSourceKey source = new HudSourceKey(effectiveId);
		PlayerHudState playerState = HUD_STATES.computeIfAbsent(player.getUUID(), id -> new PlayerHudState());
		TrackedHudState existing = playerState.entries.get(source);
		if (existing != null && !existing.wasVisible) {
			playerState.entries.remove(source);
			flushToPlayerOrHide(player);
			return;
		}
		long displayOrder = existing != null ? existing.displayOrder : playerState.nextDisplayOrder++;
		boolean wasVisible = existing != null && existing.wasVisible;
		playerState.entries.put(source, new TrackedHudState(entry, source, HudSourceType.TERMINAL,
			-1, currentTick, currentTick + TERMINAL_DISPLAY_TICKS, displayOrder, wasVisible));
		flushToPlayer(player);
	}

	private static void observeCandidate(UUID playerId, HudSourceKey source, HudSourceType sourceType, AirCourierHudEntry entry,
		long currentTick) {
		OBSERVED_THIS_CYCLE.put(new ObservationKey(playerId, source),
			new ObservedHudCandidate(playerId, source, sourceType, entry, currentTick));
	}

	public static void onServerTick(ServerTickEvent.Post event) {
		int currentTick = event.getServer().getTickCount();
		if (currentTick % 20 != 0)
			return;

		for (AirCourierTask.AirCourierTaskSnapshot snapshot : AirCourierTaskManager.getSnapshots(event.getServer())) {
			UUID hudPlayerId = snapshot.hudTrackingPlayerId();
			if (hudPlayerId == null) continue;
			int remainingTicks = snapshot.remainingTicks();
			if (remainingTicks < 0) continue;
			int etaSeconds = Math.max(0, Mth.ceil(remainingTicks / 20f));
			AirCourierHudEntry entry = new AirCourierHudEntry(snapshot.status(), etaSeconds,
				AirCourierPackagePreview.fromPackage(snapshot.box()));
			UUID effectiveId = snapshot.hudEntryId() != null ? snapshot.hudEntryId() : snapshot.taskId();
			observeCandidate(hudPlayerId, new HudSourceKey(effectiveId), HudSourceType.TASK, entry, currentTick);
		}

		for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
			UUID playerId = player.getUUID();
			PlayerHudState playerState = HUD_STATES.get(playerId);

			List<ObservedHudCandidate> playerObservations = new ArrayList<>();
			Iterator<ObservedHudCandidate> it = OBSERVED_THIS_CYCLE.values().iterator();
			while (it.hasNext()) {
				ObservedHudCandidate candidate = it.next();
				if (candidate.playerId.equals(playerId)) {
					playerObservations.add(candidate);
					it.remove();
				}
			}

			if (playerState == null && playerObservations.isEmpty()) {
				continue;
			}

			if (playerState == null) {
				playerState = new PlayerHudState();
				HUD_STATES.put(playerId, playerState);
			}

			for (ObservedHudCandidate observed : playerObservations) {
				TrackedHudState existing = playerState.entries.get(observed.source);
				long displayOrder = existing != null ? existing.displayOrder : playerState.nextDisplayOrder++;
				long expiresAt = observed.sourceType == HudSourceType.TRANSIENT
					? currentTick + TRANSIENT_STATE_GRACE_TICKS : -1;
				boolean wasVisible = existing != null && existing.wasVisible;
				playerState.entries.put(observed.source, new TrackedHudState(
					observed.entry, observed.source, observed.sourceType, expiresAt, observed.observedAtTick,
					-1, displayOrder, wasVisible));
			}

			Iterator<Map.Entry<HudSourceKey, TrackedHudState>> entryIt = playerState.entries.entrySet().iterator();
			while (entryIt.hasNext()) {
				Map.Entry<HudSourceKey, TrackedHudState> mapEntry = entryIt.next();
				TrackedHudState state = mapEntry.getValue();
				boolean wasObserved = playerObservations.stream()
					.anyMatch(o -> o.source.equals(mapEntry.getKey()));

				if (wasObserved) {
					continue;
				}

				switch (state.sourceType) {
					case TERMINAL -> {
						if (!state.wasVisible || (state.hideAfterTick >= 0 && currentTick > state.hideAfterTick)) {
							entryIt.remove();
						}
					}
					case TASK -> {
						long missTicks = currentTick - state.lastObservedTick;
						if (missTicks > ACTIVE_MISS_GRACE_TICKS) {
							entryIt.remove();
						}
					}
					case TRANSIENT -> {
						if (state.expiresAtTick >= 0 && currentTick > state.expiresAtTick) {
							entryIt.remove();
						}
					}
				}
			}

			flushToPlayerOrHide(player);
		}

		OBSERVED_THIS_CYCLE.clear();

		HUD_STATES.entrySet().removeIf(entry ->
			event.getServer().getPlayerList().getPlayer(entry.getKey()) == null);
	}

	public static void clearPlayer(ServerPlayer player) {
		HUD_STATES.remove(player.getUUID());
		OBSERVED_THIS_CYCLE.entrySet().removeIf(entry -> entry.getKey().playerId.equals(player.getUUID()));
	}

	public static void clearAll() {
		HUD_STATES.clear();
		OBSERVED_THIS_CYCLE.clear();
	}

	private static void flushToPlayer(ServerPlayer player) {
		UUID playerId = player.getUUID();
		PlayerHudState playerState = HUD_STATES.get(playerId);
		if (playerState == null || playerState.entries.isEmpty()) {
			return;
		}

		List<TrackedHudState> visibleStates = playerState.entries.values().stream()
			.sorted(Comparator.comparingLong(TrackedHudState::displayOrder))
			.limit(AirCourierHudPayload.MAX_VISIBLE_ENTRIES)
			.toList();
		for (TrackedHudState state : visibleStates) {
			if (!state.wasVisible) {
				playerState.entries.put(state.source, state.withWasVisible());
			}
		}

		List<AirCourierHudEntry> visibleEntries = visibleStates.stream()
			.map(s -> s.entry)
			.toList();

		AirCourierHudPayload newPayload = new AirCourierHudPayload(visibleEntries);

		boolean shouldSend = !payloadEquals(playerState.lastSentPayload, newPayload)
			|| (playerState.lastSentTick < 0 || player.server.getTickCount() - playerState.lastSentTick >= GUARANTEED_REFRESH_TICKS);

		if (shouldSend) {
			CatnipServices.NETWORK.sendToClient(player, AirCourierHudPacket.of(newPayload));
			playerState.lastSentPayload = newPayload;
			playerState.lastSentTick = player.server.getTickCount();
		}
	}

	private static void flushToPlayerOrHide(ServerPlayer player) {
		UUID playerId = player.getUUID();
		PlayerHudState playerState = HUD_STATES.get(playerId);
		if (playerState == null) {
			return;
		}
		if (playerState.entries.isEmpty()) {
			if (playerState.lastSentPayload != null && playerState.lastSentPayload.visible()) {
				CatnipServices.NETWORK.sendToClient(player, AirCourierHudPacket.hidden());
			}
			HUD_STATES.remove(playerId);
			return;
		}
		flushToPlayer(player);
	}

	private static boolean payloadEquals(@Nullable AirCourierHudPayload a, @Nullable AirCourierHudPayload b) {
		if (a == b) return true;
		if (a == null || b == null) return false;
		if (!a.visible() && !b.visible()) return true;
		if (a.visible() != b.visible()) return false;
		List<AirCourierHudEntry> ae = a.entries();
		List<AirCourierHudEntry> be = b.entries();
		if (ae.size() != be.size()) return false;
		for (int i = 0; i < ae.size(); i++) {
			AirCourierHudEntry ea = ae.get(i);
			AirCourierHudEntry eb = be.get(i);
			if (ea.status() != eb.status()) return false;
			if (ea.etaSeconds() != eb.etaSeconds()) return false;
			if (ea.displayStacks().size() != eb.displayStacks().size()) return false;
			for (int j = 0; j < ea.displayStacks().size(); j++) {
				if (!ItemStack.matches(ea.displayStacks().get(j), eb.displayStacks().get(j))) {
					return false;
				}
			}
		}
		return true;
	}

	enum HudSourceType {
		TRANSIENT,
		TASK,
		TERMINAL
	}

	record HudSourceKey(UUID id) {}

	record ObservationKey(UUID playerId, HudSourceKey source) {}

	private static final class PlayerHudState {
		final Map<HudSourceKey, TrackedHudState> entries = new LinkedHashMap<>();
		long nextDisplayOrder;
		@Nullable AirCourierHudPayload lastSentPayload;
		long lastSentTick = -1;
	}

	record ObservedHudCandidate(
		UUID playerId,
		HudSourceKey source,
		HudSourceType sourceType,
		AirCourierHudEntry entry,
		long observedAtTick
	) {}

	private static final class TrackedHudState {
		final AirCourierHudEntry entry;
		final HudSourceKey source;
		final HudSourceType sourceType;
		final long expiresAtTick;
		final long lastObservedTick;
		final long hideAfterTick;
		final long displayOrder;
		final boolean wasVisible;

		TrackedHudState(AirCourierHudEntry entry, HudSourceKey source, HudSourceType sourceType, long expiresAtTick,
			long lastObservedTick, long hideAfterTick, long displayOrder, boolean wasVisible) {
			this.entry = entry;
			this.source = source;
			this.sourceType = sourceType;
			this.expiresAtTick = expiresAtTick;
			this.lastObservedTick = lastObservedTick;
			this.hideAfterTick = hideAfterTick;
			this.displayOrder = displayOrder;
			this.wasVisible = wasVisible;
		}

		long displayOrder() {
			return displayOrder;
		}

		TrackedHudState withWasVisible() {
			return new TrackedHudState(entry, source, sourceType, expiresAtTick, lastObservedTick,
				hideAfterTick, displayOrder, true);
		}
	}
}
