package com.yision.phantom.network.ticker;

import com.yision.phantom.item.ticker.TunablePortableTickerItem;
import com.yision.phantom.item.ticker.access.TunablePortableTickerLocator;
import com.yision.phantom.item.ticker.TunablePortableTickerCardMenu;
import com.yision.phantom.item.ticker.TunablePortableTickerMenu;
import com.yision.phantom.item.ticker.TunablePortableTickerSession;
import com.yision.phantom.network.AllPackets;
import java.util.OptionalInt;
import java.util.UUID;
import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.SimpleMenuProvider;

public record TunablePortableTickerOpenPacket(boolean cardConfig) implements ServerboundPacketPayload {
	public static final StreamCodec<RegistryFriendlyByteBuf, TunablePortableTickerOpenPacket> STREAM_CODEC =
		StreamCodec.composite(
			ByteBufCodecs.BOOL, TunablePortableTickerOpenPacket::cardConfig,
			TunablePortableTickerOpenPacket::new);

	public static void send() {
		CatnipServices.NETWORK.sendToServer(new TunablePortableTickerOpenPacket(false));
	}

	public static void send(boolean cardConfig) {
		CatnipServices.NETWORK.sendToServer(new TunablePortableTickerOpenPacket(cardConfig));
	}

	@Override
	public void handle(ServerPlayer player) {
		TunablePortableTickerLocator locator = TunablePortableTickerLocator.findPreferred(player);
		ItemStack stack = locator.resolve(player);
		if (stack.isEmpty())
			return;

		if (!(stack.getItem() instanceof TunablePortableTickerItem))
			return;

		if (cardConfig) {
			ItemStack openedSnapshot = stack.copy();
			player.openMenu(new SimpleMenuProvider(
				(id, inv, p) -> TunablePortableTickerCardMenu.create(id, inv, stack, locator),
				Component.translatable("gui.createphantom.tunable_portable_ticker.cards")),
				buffer -> {
					ItemStack.STREAM_CODEC.encode(buffer, openedSnapshot);
					TunablePortableTickerLocator.STREAM_CODEC.encode(buffer, locator);
				});
			return;
		}

		int channel = TunablePortableTickerItem.getSelectedChannel(stack);
		UUID network = TunablePortableTickerItem.networkFromChannel(stack, channel);

		if (network == null) {
			OptionalInt first = TunablePortableTickerItem.firstLinkedChannel(stack);
			if (first.isPresent()) {
				channel = first.getAsInt();
				network = TunablePortableTickerItem.networkFromChannel(stack, channel);
			}
		}

		if (network == null) {
			player.displayClientMessage(
				Component.translatable("item.createphantom.tunable_portable_ticker.not_linked"), true);
			return;
		}
		if (!TunablePortableTickerSession.mayInteract(player, network)) {
			player.displayClientMessage(Component.translatable("create.stock_keeper.locked"), true);
			return;
		}

		int finalChannel = channel;
		player.openMenu(new SimpleMenuProvider(
			(id, inv, p) -> new TunablePortableTickerMenu(id, inv, locator, finalChannel),
			Component.translatable("item.createphantom.tunable_portable_ticker")),
			buffer -> TunablePortableTickerMenu.writeMenuData(buffer, locator, finalChannel));
	}

	@Override
	public PacketTypeProvider getTypeProvider() {
		return AllPackets.TUNABLE_PORTABLE_TICKER_OPEN;
	}
}
