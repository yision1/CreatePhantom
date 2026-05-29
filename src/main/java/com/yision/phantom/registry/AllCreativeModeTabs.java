package com.yision.phantom.registry;

import com.yision.phantom.CreatePhantom;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AllCreativeModeTabs {
	private static final DeferredRegister<CreativeModeTab> REGISTER =
		DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreatePhantom.MODID);

	public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = REGISTER.register("createphantom",
		() -> CreativeModeTab.builder()
			.title(Component.translatable("itemGroup.createphantom"))
			.icon(AllItems.MINI_PHANTOM::asStack)
			.build());

	private AllCreativeModeTabs() {}

	public static void register(IEventBus modEventBus) {
		REGISTER.register(modEventBus);
	}
}
