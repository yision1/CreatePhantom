package com.yision.phantom.network.ticker;

import com.yision.phantom.item.ticker.TunablePortableTickerCardMenu;
import com.yision.phantom.network.AllPackets;
import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;

public record TunablePortableTickerCardSlotPacket(int cardIndex, boolean begin, String name)
	implements ServerboundPacketPayload {
	public static final StreamCodec<RegistryFriendlyByteBuf, TunablePortableTickerCardSlotPacket> STREAM_CODEC =
		StreamCodec.composite(
			ByteBufCodecs.INT, TunablePortableTickerCardSlotPacket::cardIndex,
			ByteBufCodecs.BOOL, TunablePortableTickerCardSlotPacket::begin,
			ByteBufCodecs.stringUtf8(28), TunablePortableTickerCardSlotPacket::name,
			TunablePortableTickerCardSlotPacket::new);

	@Override
	public void handle(ServerPlayer player) {
		if (!(player.containerMenu instanceof TunablePortableTickerCardMenu menu))
			return;
		if (begin)
			menu.beginEdit(cardIndex);
		else
			menu.finishEdit(cardIndex, name);
	}

	@Override
	public PacketTypeProvider getTypeProvider() {
		return AllPackets.TUNABLE_PORTABLE_TICKER_CARD_SLOT;
	}
}
