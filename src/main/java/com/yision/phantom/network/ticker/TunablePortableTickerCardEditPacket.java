package com.yision.phantom.network.ticker;

import com.yision.phantom.item.ticker.TunablePortableTickerCardMenu;
import com.yision.phantom.item.ticker.TunablePortableTickerItem;
import com.yision.phantom.item.ticker.access.TunablePortableTickerLocator;
import com.yision.phantom.network.AllPackets;
import java.util.List;
import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public record TunablePortableTickerCardEditPacket(TunablePortableTickerLocator locator, List<ItemStack> cards) implements ServerboundPacketPayload {
	public static final StreamCodec<RegistryFriendlyByteBuf, TunablePortableTickerCardEditPacket> STREAM_CODEC =
		StreamCodec.composite(
			TunablePortableTickerLocator.STREAM_CODEC, TunablePortableTickerCardEditPacket::locator,
			ItemStack.OPTIONAL_LIST_STREAM_CODEC, TunablePortableTickerCardEditPacket::cards,
			TunablePortableTickerCardEditPacket::new);

	@Override
	public void handle(ServerPlayer player) {
		if (player.containerMenu instanceof TunablePortableTickerCardMenu menu && menu.locator.equals(locator)) {
			menu.applyCards(cards);
			return;
		}

		ItemStack ticker = locator.resolve(player);
		if (ticker.getItem() instanceof TunablePortableTickerItem)
			TunablePortableTickerItem.setCards(ticker, TunablePortableTickerCardMenu.sanitizeCards(cards));
	}

	@Override
	public PacketTypeProvider getTypeProvider() {
		return AllPackets.TUNABLE_PORTABLE_TICKER_CARD_EDIT;
	}
}
