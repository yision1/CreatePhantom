package com.yision.phantom.network.ticker;

import com.yision.phantom.item.ticker.TunablePortableTickerCardMenu;
import com.yision.phantom.network.AllPackets;
import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;

public record TunablePortableTickerCardRefundPacket(int cardIndex) implements ServerboundPacketPayload {
	public static final StreamCodec<RegistryFriendlyByteBuf, TunablePortableTickerCardRefundPacket> STREAM_CODEC =
		StreamCodec.composite(
			ByteBufCodecs.INT, TunablePortableTickerCardRefundPacket::cardIndex,
			TunablePortableTickerCardRefundPacket::new);

	@Override
	public void handle(ServerPlayer player) {
		if (!(player.containerMenu instanceof TunablePortableTickerCardMenu menu))
			return;
		menu.removeCard(cardIndex);
	}

	@Override
	public PacketTypeProvider getTypeProvider() {
		return AllPackets.TUNABLE_PORTABLE_TICKER_CARD_REFUND;
	}
}
