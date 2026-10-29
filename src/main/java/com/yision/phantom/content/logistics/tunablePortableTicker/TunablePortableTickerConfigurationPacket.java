package com.yision.phantom.content.logistics.tunablePortableTicker;

import java.util.UUID;

import com.yision.phantom.network.AllPackets;

import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public record TunablePortableTickerConfigurationPacket(TunablePortableTickerLocator locator, int channel,
	UUID sessionNetwork, UUID sessionId, int requestId, CustomPacketPayload payload)
	implements ServerboundPacketPayload {
	public static final StreamCodec<RegistryFriendlyByteBuf, TunablePortableTickerConfigurationPacket> STREAM_CODEC =
		StreamCodec
			.composite(TunablePortableTickerLocator.STREAM_CODEC,
				TunablePortableTickerConfigurationPacket::locator,
				ByteBufCodecs.VAR_INT, TunablePortableTickerConfigurationPacket::channel,
				UUIDUtil.STREAM_CODEC, TunablePortableTickerConfigurationPacket::sessionNetwork,
				UUIDUtil.STREAM_CODEC, TunablePortableTickerConfigurationPacket::sessionId,
				ByteBufCodecs.VAR_INT, TunablePortableTickerConfigurationPacket::requestId,
				NativeTickerPayloadCodec.forFlow(PacketFlow.SERVERBOUND),
				TunablePortableTickerConfigurationPacket::payload,
				TunablePortableTickerConfigurationPacket::new);

	@Override
	public void handle(ServerPlayer player) {
		TunablePortableTickerMenu menu = TunablePortableTickerSession.resolve(player, locator, channel,
			sessionNetwork);
		if (menu != null && sessionId.equals(menu.sessionId))
			TunablePortableTickerSession.applyNativePacket(player, menu, payload, requestId);
	}

	@Override
	public PacketTypeProvider getTypeProvider() {
		return AllPackets.TUNABLE_PORTABLE_TICKER_CONFIGURATION;
	}
}
