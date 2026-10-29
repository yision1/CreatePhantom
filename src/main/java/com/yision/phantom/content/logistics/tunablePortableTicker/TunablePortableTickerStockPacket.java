package com.yision.phantom.content.logistics.tunablePortableTicker;

import java.util.UUID;

import com.yision.phantom.network.AllPackets;

import net.createmod.catnip.net.base.ClientboundPacketPayload;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public record TunablePortableTickerStockPacket(TunablePortableTickerLocator locator, int channel, UUID sessionNetwork,
	UUID sessionId, int requestId, BlockPos sourcePosition, int activeLinks,
	CustomPacketPayload payload) implements ClientboundPacketPayload {
	public static final StreamCodec<RegistryFriendlyByteBuf, TunablePortableTickerStockPacket> STREAM_CODEC =
		StreamCodec
			.of((buffer, packet) -> {
				TunablePortableTickerLocator.STREAM_CODEC.encode(buffer, packet.locator);
				ByteBufCodecs.VAR_INT.encode(buffer, packet.channel);
				UUIDUtil.STREAM_CODEC.encode(buffer, packet.sessionNetwork);
				UUIDUtil.STREAM_CODEC.encode(buffer, packet.sessionId);
				ByteBufCodecs.VAR_INT.encode(buffer, packet.requestId);
				BlockPos.STREAM_CODEC.encode(buffer, packet.sourcePosition);
				ByteBufCodecs.VAR_INT.encode(buffer, packet.activeLinks);
				NativeTickerPayloadCodec.forFlow(PacketFlow.CLIENTBOUND).encode(buffer, packet.payload);
			}, buffer -> new TunablePortableTickerStockPacket(TunablePortableTickerLocator.STREAM_CODEC.decode(buffer),
				ByteBufCodecs.VAR_INT.decode(buffer), UUIDUtil.STREAM_CODEC.decode(buffer),
				UUIDUtil.STREAM_CODEC.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer),
				BlockPos.STREAM_CODEC.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer),
				NativeTickerPayloadCodec.forFlow(PacketFlow.CLIENTBOUND).decode(buffer)));

	@Override
	@OnlyIn(Dist.CLIENT)
	public void handle(LocalPlayer player) {
		ClientScreenStorage.receiveNative(player, locator, channel, sessionNetwork, sessionId, requestId,
			sourcePosition, activeLinks, payload);
	}

	@Override
	public PacketTypeProvider getTypeProvider() {
		return AllPackets.TUNABLE_PORTABLE_TICKER_STOCK;
	}
}
