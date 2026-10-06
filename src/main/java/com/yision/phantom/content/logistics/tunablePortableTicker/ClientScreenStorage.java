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
	private static TunablePortableTickerMenu activeMenu;
	private static int activeRequestId;

	private ClientScreenStorage() {}

	public static void request(TunablePortableTickerMenu menu, Runnable nativeRefresh) {
		if (!isOpen(menu))
			return;
		if (activeMenu != null && activeMenu != menu)
			activeMenu.getStockData().resetReception();
		activeMenu = menu;
		activeRequestId++;
		menu.getStockData().resetReception();
		try (var ignored = PortableTickerPacketContext.sending(menu, activeRequestId)) {
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
		UUID sessionNetwork, int requestId, BlockPos sourcePosition, CustomPacketPayload payload) {
		if (!isOpen(activeMenu) || player.containerMenu != activeMenu
			|| !locator.equals(activeMenu.getLocator()) || channel != activeMenu.getChannel()
			|| !sessionNetwork.equals(activeMenu.getSessionNetwork()) || requestId != activeRequestId
			|| !sourcePosition.equals(activeMenu.getStockData().getQueryPosition())
			|| !(payload instanceof ClientboundPacketPayload nativeResponse))
			return;
		try (var ignored = PortableTickerPacketContext.receiving(activeMenu, requestId, sourcePosition)) {
			nativeResponse.handleInternal(player);
		}
	}

	private static boolean isOpen(TunablePortableTickerMenu menu) {
		var player = Minecraft.getInstance().player;
		return menu != null && !menu.switchingChannel && player != null && player.containerMenu == menu
			&& menu.stillValid(player);
	}

	public static void close(TunablePortableTickerMenu menu) {
		if (activeMenu != menu)
			return;
		menu.getStockData().resetReception();
		activeMenu = null;
		activeRequestId++;
	}
}
