package com.yision.phantom.network.ticker;

import com.yision.phantom.item.ticker.TunablePortableTickerMenu;
import com.yision.phantom.item.ticker.access.TunablePortableTickerLocator;
import com.yision.phantom.network.AllPackets;
import java.util.UUID;
import net.createmod.catnip.net.base.ClientboundPacketPayload;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public record TunablePortableTickerNetworkStatePacket(TunablePortableTickerLocator locator, int channel,
	UUID sessionNetwork, boolean isAdmin, boolean isLocked) implements ClientboundPacketPayload {
	public static final StreamCodec<RegistryFriendlyByteBuf, TunablePortableTickerNetworkStatePacket> STREAM_CODEC =
		StreamCodec.composite(
			TunablePortableTickerLocator.STREAM_CODEC, TunablePortableTickerNetworkStatePacket::locator,
			ByteBufCodecs.VAR_INT, TunablePortableTickerNetworkStatePacket::channel,
			UUIDUtil.STREAM_CODEC, TunablePortableTickerNetworkStatePacket::sessionNetwork,
			ByteBufCodecs.BOOL, TunablePortableTickerNetworkStatePacket::isAdmin,
			ByteBufCodecs.BOOL, TunablePortableTickerNetworkStatePacket::isLocked,
			TunablePortableTickerNetworkStatePacket::new);

	@Override
	@OnlyIn(Dist.CLIENT)
	public void handle(LocalPlayer player) {
		if (!(player.containerMenu instanceof TunablePortableTickerMenu menu)
			|| !menu.locator.equals(locator) || menu.channel != channel
			|| !sessionNetwork.equals(menu.sessionNetwork))
			return;
		menu.isAdmin = isAdmin;
		menu.isLocked = isLocked;
	}

	@Override
	public PacketTypeProvider getTypeProvider() {
		return AllPackets.TUNABLE_PORTABLE_TICKER_NETWORK_STATE;
	}
}
