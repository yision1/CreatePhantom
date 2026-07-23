package com.yision.phantom.network.ticker;

import com.yision.phantom.item.ticker.TunablePortableTickerMenu;
import com.yision.phantom.item.ticker.access.TunablePortableTickerLocator;
import com.yision.phantom.network.AllPackets;
import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public record TunablePortableTickerSelectChannelPacket(TunablePortableTickerLocator locator, int channel)
	implements ServerboundPacketPayload {
	public static final StreamCodec<RegistryFriendlyByteBuf, TunablePortableTickerSelectChannelPacket> STREAM_CODEC =
		StreamCodec.composite(
			TunablePortableTickerLocator.STREAM_CODEC, TunablePortableTickerSelectChannelPacket::locator,
			ByteBufCodecs.VAR_INT, TunablePortableTickerSelectChannelPacket::channel,
			TunablePortableTickerSelectChannelPacket::new);

	@Override
	public void handle(ServerPlayer player) {
		if (player == null)
			return;
		if (player.containerMenu instanceof TunablePortableTickerMenu menu && menu.locator.equals(locator))
			menu.selectChannel(player, channel);
	}

	@Override
	public PacketTypeProvider getTypeProvider() {
		return AllPackets.TUNABLE_PORTABLE_TICKER_SELECT_CHANNEL;
	}
}
