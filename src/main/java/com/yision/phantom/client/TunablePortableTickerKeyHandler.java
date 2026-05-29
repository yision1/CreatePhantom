package com.yision.phantom.client;

import com.yision.phantom.network.ticker.TunablePortableTickerOpenPacket;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

public class TunablePortableTickerKeyHandler {
	public static void register() {
		NeoForge.EVENT_BUS.addListener(TunablePortableTickerKeyHandler::onClientTick);
	}

	private static void onClientTick(ClientTickEvent.Post event) {
		while (AllKeys.OPEN_TUNABLE_PORTABLE_TICKER.consumeClick())
			TunablePortableTickerOpenPacket.send(Screen.hasShiftDown());
	}
}
