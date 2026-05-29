package com.yision.phantom.network.ticker;

import com.yision.phantom.item.storagecard.StorageChannelExtensionCardItem;
import com.yision.phantom.item.ticker.TunablePortableTickerCardMenu;
import com.yision.phantom.network.AllPackets;
import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public record TunablePortableTickerCardRefundPacket(ItemStack card) implements ServerboundPacketPayload {
	public static final StreamCodec<RegistryFriendlyByteBuf, TunablePortableTickerCardRefundPacket> STREAM_CODEC =
		StreamCodec.composite(
			ItemStack.STREAM_CODEC, TunablePortableTickerCardRefundPacket::card,
			TunablePortableTickerCardRefundPacket::new);

	@Override
	public void handle(ServerPlayer player) {
		if (!(player.containerMenu instanceof TunablePortableTickerCardMenu))
			return;
		if (card.isEmpty() || !(card.getItem() instanceof StorageChannelExtensionCardItem))
			return;

		ItemStack refunded = card.copy();
		refunded.setCount(1);
		player.getInventory()
			.placeItemBackInInventory(refunded);
	}

	@Override
	public PacketTypeProvider getTypeProvider() {
		return AllPackets.TUNABLE_PORTABLE_TICKER_CARD_REFUND;
	}
}
