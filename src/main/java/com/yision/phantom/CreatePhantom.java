package com.yision.phantom;

import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;
import com.yision.phantom.block.phantomport.PhantomPortTargetRegistry;
import com.yision.phantom.config.AllConfigs;
import com.yision.phantom.logistics.courier.AirCourierTaskManager;
import com.yision.phantom.logistics.courier.hud.AirCourierHudSync;
import com.yision.phantom.network.AllPackets;
import com.yision.phantom.registry.AllAttachmentTypes;
import com.yision.phantom.registry.AllBlockEntityTypes;
import com.yision.phantom.registry.AllCapabilities;
import com.yision.phantom.registry.AllBlocks;
import com.yision.phantom.registry.AllCreativeModeTabs;
import com.yision.phantom.registry.AllDataComponents;
import com.yision.phantom.registry.AllEntityTypes;
import com.yision.phantom.registry.AllItems;
import com.yision.phantom.registry.AllMenuTypes;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(CreatePhantom.MODID)
public class CreatePhantom {
	public static final String MODID = "createphantom";
	public static final String NAME = "Create: Phantom";
	public static final Logger LOGGER = LogUtils.getLogger();
	public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MODID)
		.defaultCreativeTab((ResourceKey<CreativeModeTab>) null)
		.setTooltipModifierFactory(item -> new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE)
			.andThen(TooltipModifier.mapNull(KineticStats.create(item))));

	public CreatePhantom(IEventBus modEventBus, ModContainer modContainer) {
		AllConfigs.register(modContainer);
		AllCreativeModeTabs.register(modEventBus);
		REGISTRATE.defaultCreativeTab(AllCreativeModeTabs.MAIN.getKey());
		REGISTRATE.registerEventListeners(modEventBus);
		AllDataComponents.register(modEventBus);

		AllBlocks.register();
		AllItems.register();
		AllBlockEntityTypes.register();
		AllEntityTypes.register();
		AllMenuTypes.register();
		AllPackets.register();
		AllAttachmentTypes.register(modEventBus);
		modEventBus.addListener(AllCapabilities::register);

		NeoForge.EVENT_BUS.addListener(AirCourierHudSync::onServerTick);
		NeoForge.EVENT_BUS.addListener(AirCourierTaskManager::onServerTick);
		NeoForge.EVENT_BUS.addListener(PhantomPortTargetRegistry::onServerTick);

		NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStartingEvent event) ->
			AirCourierTaskManager.onServerStarting(event.getServer()));
	}

	public static ResourceLocation asResource(String path) {
		return ResourceLocation.fromNamespaceAndPath(MODID, path);
	}
}
