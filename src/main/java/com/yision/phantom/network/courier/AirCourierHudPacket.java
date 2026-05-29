package com.yision.phantom.network.courier;

import com.yision.phantom.client.gui.hud.AirCourierHudOverlay;
import com.yision.phantom.logistics.courier.hud.AirCourierHudEntry;
import com.yision.phantom.logistics.courier.hud.AirCourierHudPayload;
import com.yision.phantom.network.AllPackets;
import java.util.List;
import net.createmod.catnip.codecs.stream.CatnipStreamCodecBuilders;
import net.createmod.catnip.net.base.ClientboundPacketPayload;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class AirCourierHudPacket implements ClientboundPacketPayload {
	public static final StreamCodec<RegistryFriendlyByteBuf, AirCourierHudPacket> STREAM_CODEC =
		CatnipStreamCodecBuilders.list(AirCourierHudEntry.STREAM_CODEC)
			.map(AirCourierHudPacket::fromEntries, AirCourierHudPacket::toEntries);

	private final AirCourierHudPayload payload;

	private AirCourierHudPacket(AirCourierHudPayload payload) {
		this.payload = payload;
	}

	private static AirCourierHudPacket fromEntries(List<AirCourierHudEntry> entries) {
		return new AirCourierHudPacket(new AirCourierHudPayload(entries));
	}

	private List<AirCourierHudEntry> toEntries() {
		return payload.entries();
	}

	public static AirCourierHudPacket of(AirCourierHudPayload payload) {
		return new AirCourierHudPacket(payload);
	}

	public static AirCourierHudPacket hidden() {
		return new AirCourierHudPacket(AirCourierHudPayload.hidden());
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public void handle(LocalPlayer player) {
		AirCourierHudOverlay.updateState(payload);
	}

	@Override
	public PacketTypeProvider getTypeProvider() {
		return AllPackets.AIR_COURIER_HUD;
	}
}
