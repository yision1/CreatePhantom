package com.yision.phantom.network.courier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.yision.phantom.client.gui.hud.AirCourierHudOverlay;
import com.yision.phantom.logistics.courier.hud.AirCourierHudEntry;
import com.yision.phantom.logistics.courier.hud.AirCourierHudPayload;
import com.yision.phantom.logistics.courier.hud.AirCourierHudStatus;
import com.yision.phantom.logistics.courier.hud.AirCourierPackagePreview;
import com.yision.phantom.network.AllPackets;

import net.createmod.catnip.net.base.ClientboundPacketPayload;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public record AirCourierHudPacket(List<EntryUpdate> updates) implements ClientboundPacketPayload {
	public static final StreamCodec<RegistryFriendlyByteBuf, AirCourierHudPacket> STREAM_CODEC =
		EntryUpdate.STREAM_CODEC
			.apply(ByteBufCodecs.list(AirCourierHudPayload.MAX_VISIBLE_ENTRIES))
			.map(AirCourierHudPacket::new, AirCourierHudPacket::updates);

	public AirCourierHudPacket {
		updates = List.copyOf(updates);
	}

	public static AirCourierHudPacket of(AirCourierHudPayload payload, AirCourierHudPayload previous) {
		var previousEntries = new HashMap<UUID, AirCourierHudEntry>();
		for (AirCourierHudEntry entry : previous.entries())
			previousEntries.put(entry.id(), entry);
		List<EntryUpdate> updates = new ArrayList<>();
		for (AirCourierHudEntry entry : payload.entries()) {
			AirCourierHudEntry old = previousEntries.get(entry.id());
			Optional<List<ItemStack>> contents = old != null && entry.hasSameContents(old)
				? Optional.empty()
				: Optional.of(entry.displayStacks());
			updates.add(new EntryUpdate(entry.id(), entry.status(), entry.etaSeconds(), contents));
		}
		return new AirCourierHudPacket(updates);
	}

	public static AirCourierHudPacket hidden() {
		return new AirCourierHudPacket(List.of());
	}

	public AirCourierHudPayload applyTo(AirCourierHudPayload previous) {
		var previousEntries = new HashMap<UUID, AirCourierHudEntry>();
		for (AirCourierHudEntry entry : previous.entries())
			previousEntries.put(entry.id(), entry);
		List<AirCourierHudEntry> entries = new ArrayList<>();
		for (EntryUpdate update : updates) {
			AirCourierHudEntry old = previousEntries.get(update.id());
			if (update.contents().isEmpty() && old == null)
				continue;
			List<ItemStack> contents = update.contents().isPresent()
				? update.contents().orElseThrow()
				: old.displayStacks();
			entries.add(new AirCourierHudEntry(update.id(), update.status(), update.etaSeconds(), contents));
		}
		return new AirCourierHudPayload(entries);
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public void handle(LocalPlayer player) {
		AirCourierHudOverlay.updateState(this);
	}

	@Override
	public PacketTypeProvider getTypeProvider() {
		return AllPackets.AIR_COURIER_HUD;
	}

	public record EntryUpdate(UUID id, AirCourierHudStatus status, int etaSeconds,
		Optional<List<ItemStack>> contents) {
		public static final StreamCodec<RegistryFriendlyByteBuf, EntryUpdate> STREAM_CODEC = StreamCodec.composite(
			UUIDUtil.STREAM_CODEC, EntryUpdate::id,
			ByteBufCodecs.VAR_INT, update -> update.status().ordinal(),
			ByteBufCodecs.VAR_INT, EntryUpdate::etaSeconds,
			ByteBufCodecs.optional(ItemStack.OPTIONAL_STREAM_CODEC.apply(
				ByteBufCodecs.list(AirCourierPackagePreview.MAX_DISPLAY_STACKS))),
			EntryUpdate::contents,
			(id, status, eta, contents) -> new EntryUpdate(id, AirCourierHudStatus.byId(status), eta, contents));

		public EntryUpdate {
			contents = contents.map(AirCourierPackagePreview::copyDisplayStacks);
		}
	}
}
