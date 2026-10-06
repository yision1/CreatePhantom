package com.yision.phantom.content.logistics.tunablePortableTicker;

import com.yision.phantom.network.AllPackets;
import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.createmod.catnip.platform.CatnipServices;
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
		if (!(player.containerMenu instanceof TunablePortableTickerMenu menu) || !menu.locator.equals(locator))
			return;
		if (!menu.stillValid(player)) {
			player.closeContainer();
			return;
		}
		if (menu.selectChannel(player, channel)) {
			TunablePortableTickerItem.openMenu(player, locator, false);
		} else {
			CatnipServices.NETWORK.sendToClient(player, new TunablePortableTickerNetworkStatePacket(
				locator, menu.channel, menu.sessionNetwork, menu.isAdmin(), menu.isLocked()));
		}
	}

	@Override
	public PacketTypeProvider getTypeProvider() {
		return AllPackets.TUNABLE_PORTABLE_TICKER_SELECT_CHANNEL;
	}
}
