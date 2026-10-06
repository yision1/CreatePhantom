package com.yision.phantom.content.logistics.tunablePortableTicker;

import com.yision.phantom.network.AllPackets;
import java.util.UUID;
import net.createmod.catnip.net.base.ClientboundPacketPayload;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public record TunablePortableTickerStockPacket(TunablePortableTickerLocator locator, int channel, UUID sessionNetwork,
	int requestId, BlockPos sourcePosition, CustomPacketPayload payload) implements ClientboundPacketPayload {
	public static final StreamCodec<RegistryFriendlyByteBuf, TunablePortableTickerStockPacket> STREAM_CODEC = StreamCodec
		.composite(TunablePortableTickerLocator.STREAM_CODEC, TunablePortableTickerStockPacket::locator,
			ByteBufCodecs.VAR_INT, TunablePortableTickerStockPacket::channel,
			UUIDUtil.STREAM_CODEC, TunablePortableTickerStockPacket::sessionNetwork,
			ByteBufCodecs.VAR_INT, TunablePortableTickerStockPacket::requestId,
			BlockPos.STREAM_CODEC, TunablePortableTickerStockPacket::sourcePosition,
			NativeTickerPayloadCodec.forFlow(PacketFlow.CLIENTBOUND), TunablePortableTickerStockPacket::payload,
			TunablePortableTickerStockPacket::new);

	@Override
	@OnlyIn(Dist.CLIENT)
	public void handle(LocalPlayer player) {
		ClientScreenStorage.receiveNative(player, locator, channel, sessionNetwork, requestId, sourcePosition, payload);
	}

	@Override
	public PacketTypeProvider getTypeProvider() {
		return AllPackets.TUNABLE_PORTABLE_TICKER_STOCK;
	}
}
