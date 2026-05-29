package com.yision.phantom.network.ticker;

import com.yision.phantom.item.storagecard.StorageChannelExtensionCardItem;
import com.yision.phantom.item.ticker.TunablePortableTickerCardMenu;
import com.yision.phantom.network.AllPackets;
import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public record TunablePortableTickerCardSlotPacket(ItemStack item, int slot) implements ServerboundPacketPayload {
	public static final StreamCodec<RegistryFriendlyByteBuf, TunablePortableTickerCardSlotPacket> STREAM_CODEC =
		StreamCodec.composite(
			ItemStack.OPTIONAL_STREAM_CODEC, TunablePortableTickerCardSlotPacket::item,
			ByteBufCodecs.INT, TunablePortableTickerCardSlotPacket::slot,
			TunablePortableTickerCardSlotPacket::new);

	@Override
	public void handle(ServerPlayer player) {
		if (!(player.containerMenu instanceof TunablePortableTickerCardMenu menu))
			return;
		if (slot != 0)
			return;
		if (!item.isEmpty() && !(item.getItem() instanceof StorageChannelExtensionCardItem))
			return;

		ItemStack stack = item.copy();
		if (!stack.isEmpty())
			stack.setCount(1);
		menu.proxyInventory.setStackInSlot(slot, stack);
		menu.getSlot(slot)
			.setChanged();
	}

	@Override
	public PacketTypeProvider getTypeProvider() {
		return AllPackets.TUNABLE_PORTABLE_TICKER_CARD_SLOT;
	}
}
