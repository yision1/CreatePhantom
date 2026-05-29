package com.yision.phantom.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public final class AllKeys {
	public static final KeyMapping OPEN_TUNABLE_PORTABLE_TICKER = new KeyMapping(
		"key.createphantom.open_tunable_portable_ticker",
		InputConstants.Type.KEYSYM,
		GLFW.GLFW_KEY_G,
		"key.categories.createphantom");

	private AllKeys() {}

	public static void register(RegisterKeyMappingsEvent event) {
		event.register(OPEN_TUNABLE_PORTABLE_TICKER);
	}
}
