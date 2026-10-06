package com.yision.phantom.content.logistics.tunablePortableTicker;

import com.yision.phantom.network.AllPackets;
import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public record TunablePortableTickerOpenPacket(boolean cardConfig) implements ServerboundPacketPayload {
	public static final StreamCodec<RegistryFriendlyByteBuf, TunablePortableTickerOpenPacket> STREAM_CODEC =
		StreamCodec.composite(
			ByteBufCodecs.BOOL, TunablePortableTickerOpenPacket::cardConfig,
			TunablePortableTickerOpenPacket::new);

	public static void send(boolean cardConfig) {
		CatnipServices.NETWORK.sendToServer(new TunablePortableTickerOpenPacket(cardConfig));
	}

	@Override
	public void handle(ServerPlayer player) {
		TunablePortableTickerItem.openMenu(player, TunablePortableTickerLocator.findPreferred(player), cardConfig);
	}

	@Override
	public PacketTypeProvider getTypeProvider() {
		return AllPackets.TUNABLE_PORTABLE_TICKER_OPEN;
	}
}
