package com.yision.phantom.content.logistics.tunablePortableTicker;

import java.util.UUID;

import net.createmod.catnip.net.base.ClientboundPacketPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientScreenStorage {
	private ClientScreenStorage() {
	}

	public static void request(TunablePortableTickerMenu menu, Runnable nativeRefresh) {
		if (!isOpen(menu))
			return;
		try (var ignored = PortableTickerPacketContext.sending(menu, ++menu.stockRequestId)) {
			nativeRefresh.run();
		}
	}

	public static void send(TunablePortableTickerMenu menu, Runnable nativeAction) {
		if (!isOpen(menu))
			return;
		try (var ignored = PortableTickerPacketContext.sending(menu, 0)) {
			nativeAction.run();
		}
	}

	public static void receiveNative(LocalPlayer player, TunablePortableTickerLocator locator, int channel,
		UUID sessionNetwork, UUID sessionId, int requestId, BlockPos sourcePosition, int activeLinks,
		CustomPacketPayload payload) {
		if (!(player.containerMenu instanceof TunablePortableTickerMenu menu) || !isOpen(menu)
			|| !locator.equals(menu.getLocator()) || channel != menu.getChannel()
			|| !sessionNetwork.equals(menu.getSessionNetwork()) || !sessionId.equals(menu.sessionId)
			|| requestId <= 0 || requestId > menu.stockRequestId
			|| requestId < menu.receivingStockRequestId
			|| !sourcePosition.equals(menu.getStockData().getQueryPosition())
			|| !(payload instanceof ClientboundPacketPayload nativeResponse))
			return;
		if (requestId != menu.receivingStockRequestId) {
			menu.getStockData().resetReception();
			menu.receivingStockRequestId = requestId;
		}
		try (var ignored = PortableTickerPacketContext.receiving(menu, requestId, sourcePosition)) {
			nativeResponse.handleInternal(player);
		}
		if (activeLinks >= 0)
			menu.getStockData().setActiveLinks(activeLinks);
	}

	private static boolean isOpen(TunablePortableTickerMenu menu) {
		var player = Minecraft.getInstance().player;
		return menu != null && !menu.switchingChannel && player != null && player.containerMenu == menu
			&& menu.stillValid(player);
	}

	public static void close(TunablePortableTickerMenu menu) {
		if (isOpen(menu))
			return;
		menu.getStockData().resetReception();
	}
}
