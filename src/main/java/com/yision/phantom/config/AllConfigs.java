package com.yision.phantom.config;

import java.util.EnumMap;
import java.util.function.Supplier;
import java.util.Map;
import java.util.Map.Entry;
import net.createmod.catnip.config.ConfigBase;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

@EventBusSubscriber
public class AllConfigs {

	private static final Map<ModConfig.Type, ConfigBase> CONFIGS = new EnumMap<>(ModConfig.Type.class);

	private static CPClient client;
	private static CPServer server;

	public static CPClient client() {
		return client;
	}

	public static CPServer server() {
		return server;
	}

	private static <T extends ConfigBase> T register(Supplier<T> factory, ModConfig.Type side) {
		Pair<T, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(builder -> {
			T config = factory.get();
			config.registerAll(builder);
			return config;
		});

		T config = specPair.getLeft();
		config.specification = specPair.getRight();
		CONFIGS.put(side, config);
		return config;
	}

	public static void register(ModContainer container) {
		client = register(CPClient::new, ModConfig.Type.CLIENT);
		server = register(CPServer::new, ModConfig.Type.SERVER);

		for (Entry<ModConfig.Type, ConfigBase> entry : CONFIGS.entrySet()) {
			container.registerConfig(entry.getKey(), entry.getValue().specification);
		}
	}

	@SubscribeEvent
	public static void onLoad(ModConfigEvent.Loading event) {
		for (ConfigBase config : CONFIGS.values()) {
			if (config.specification == event.getConfig().getSpec()) {
				config.onLoad();
			}
		}
	}

	@SubscribeEvent
	public static void onReload(ModConfigEvent.Reloading event) {
		for (ConfigBase config : CONFIGS.values()) {
			if (config.specification == event.getConfig().getSpec()) {
				config.onReload();
			}
		}
	}
}
