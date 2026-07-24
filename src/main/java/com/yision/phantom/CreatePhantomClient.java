package com.yision.phantom;

import com.yision.phantom.client.AllKeys;
import com.yision.phantom.client.TunablePortableTickerKeyHandler;
import com.yision.phantom.client.gui.hud.AirCourierHudOverlay;
import com.yision.phantom.config.AllConfigs;
import com.yision.phantom.item.storagecard.StorageChannelExtensionCardItem;
import com.yision.phantom.ponder.CreatePhantomPonderPlugin;
import com.yision.phantom.registry.AllEntityTypes;
import com.yision.phantom.registry.AllItems;
import com.yision.phantom.client.render.AirCourierEntityRenderer;
import com.yision.phantom.item.ticker.TunablePortableTickerScreen;
import net.createmod.catnip.config.ui.BaseConfigScreen;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = CreatePhantom.MODID, dist = Dist.CLIENT)
public class CreatePhantomClient {
	public CreatePhantomClient(IEventBus modEventBus, ModContainer modContainer) {
		modEventBus.addListener(this::onClientSetup);
		modEventBus.addListener(this::registerAdditionalModels);
		modEventBus.addListener(this::registerEntityRenderers);
		modEventBus.addListener(AllKeys::register);

		NeoForge.EVENT_BUS.addListener(AirCourierHudOverlay::render);
		TunablePortableTickerKeyHandler.register();

		registerConfigScreen(modContainer);
	}

	private static void registerConfigScreen(ModContainer modContainer) {
		BaseConfigScreen.setDefaultActionFor(CreatePhantom.MODID, base -> base
			.withButtonLabels("Client Settings", null, null)
			.withSpecs(AllConfigs.client().specification, null, AllConfigs.server().specification));

		IConfigScreenFactory factory = (container, parent) -> new BaseConfigScreen(parent, CreatePhantom.MODID);
		modContainer.registerExtensionPoint(IConfigScreenFactory.class, factory);
	}

	private void onClientSetup(FMLClientSetupEvent event) {
		PonderIndex.addPlugin(new CreatePhantomPonderPlugin());
		event.enqueueWork(() -> {
			ItemProperties.register(
				AllItems.STORAGE_CHANNEL_EXTENSION_CARD.get(),
				ResourceLocation.fromNamespaceAndPath(CreatePhantom.MODID, "linked"),
				(stack, level, entity, seed) -> StorageChannelExtensionCardItem.isLinked(stack) ? 1.0F : 0.0F
			);
			ItemProperties.register(
				AllItems.TUNABLE_PORTABLE_TICKER.get(),
				ResourceLocation.fromNamespaceAndPath(CreatePhantom.MODID, "open"),
				(stack, level, entity, seed) -> Minecraft.getInstance().screen instanceof TunablePortableTickerScreen ? 1.0F : 0.0F
			);
		});
		CreatePhantom.LOGGER.debug("Initialized client hooks for {}", CreatePhantom.NAME);
	}

	private void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerEntityRenderer(AllEntityTypes.AIR_COURIER.get(), AirCourierEntityRenderer::new);
	}

	private void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
		event.register(ModelResourceLocation.standalone(
			ResourceLocation.fromNamespaceAndPath(CreatePhantom.MODID, "item/mini_phantom_package")));
	}
}
