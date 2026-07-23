package com.yision.phantom.network.ticker;

import com.yision.phantom.item.ticker.TunablePortableTickerCardMenu;
import com.yision.phantom.network.AllPackets;
import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public record TunablePortableTickerCardEditPacket(int fromIndex, int toIndex)
	implements ServerboundPacketPayload {
	public static final StreamCodec<RegistryFriendlyByteBuf, TunablePortableTickerCardEditPacket> STREAM_CODEC =
		StreamCodec.composite(
			ByteBufCodecs.INT, TunablePortableTickerCardEditPacket::fromIndex,
			ByteBufCodecs.INT, TunablePortableTickerCardEditPacket::toIndex,
			TunablePortableTickerCardEditPacket::new);

	@Override
	public void handle(ServerPlayer player) {
		if (player.containerMenu instanceof TunablePortableTickerCardMenu menu)
			menu.moveCard(fromIndex, toIndex);
	}

	@Override
	public PacketTypeProvider getTypeProvider() {
		return AllPackets.TUNABLE_PORTABLE_TICKER_CARD_EDIT;
	}
}
