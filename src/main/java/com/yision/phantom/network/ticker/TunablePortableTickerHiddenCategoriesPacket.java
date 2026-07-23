package com.yision.phantom.network.ticker;

import com.yision.phantom.item.ticker.access.ItemTunablePortableTickerAccess;
import com.yision.phantom.item.ticker.access.TunablePortableTickerLocator;
import com.yision.phantom.item.ticker.TunablePortableTickerSession;
import com.yision.phantom.network.AllPackets;
import java.util.List;
import java.util.UUID;
import net.createmod.catnip.codecs.stream.CatnipStreamCodecBuilders;
import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public record TunablePortableTickerHiddenCategoriesPacket(TunablePortableTickerLocator locator, int channel, UUID sessionNetwork, List<Integer> indices)
	implements ServerboundPacketPayload {
	public static final StreamCodec<RegistryFriendlyByteBuf, TunablePortableTickerHiddenCategoriesPacket> STREAM_CODEC =
		StreamCodec.composite(TunablePortableTickerLocator.STREAM_CODEC, TunablePortableTickerHiddenCategoriesPacket::locator,
			ByteBufCodecs.VAR_INT, TunablePortableTickerHiddenCategoriesPacket::channel,
			UUIDUtil.STREAM_CODEC, TunablePortableTickerHiddenCategoriesPacket::sessionNetwork,
			CatnipStreamCodecBuilders.list(ByteBufCodecs.INT), TunablePortableTickerHiddenCategoriesPacket::indices,
			TunablePortableTickerHiddenCategoriesPacket::new);

	@Override
	public void handle(ServerPlayer player) {
		ItemTunablePortableTickerAccess access =
			TunablePortableTickerSession.resolve(player, locator, channel, sessionNetwork);
		if (access != null) {
			access.saveHiddenCategories(player.getUUID(), indices);
		}
	}

	@Override
	public PacketTypeProvider getTypeProvider() {
		return AllPackets.TUNABLE_PORTABLE_TICKER_HIDDEN_CATEGORIES;
	}
}
