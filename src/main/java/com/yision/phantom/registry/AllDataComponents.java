package com.yision.phantom.registry;

import com.mojang.serialization.Codec;
import com.yision.phantom.item.miniphantom.MiniPhantomCargo;
import com.yision.phantom.item.miniphantom.MiniPhantomReturnTarget;
import com.yision.phantom.CreatePhantom;
import java.util.function.Supplier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.createmod.catnip.codecs.stream.CatnipStreamCodecBuilders;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AllDataComponents {
	private static final DeferredRegister.DataComponents REGISTER =
		DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, CreatePhantom.MODID);

	public static final Supplier<DataComponentType<MiniPhantomCargo>> MINI_PHANTOM_CARGO =
		REGISTER.registerComponentType("mini_phantom_cargo",
			builder -> builder.persistent(MiniPhantomCargo.CODEC)
				.networkSynchronized(MiniPhantomCargo.STREAM_CODEC));

	public static final Supplier<DataComponentType<Integer>> MINI_PHANTOM_HEADING =
		REGISTER.registerComponentType("mini_phantom_heading",
			builder -> builder.persistent(Codec.INT)
				.networkSynchronized(ByteBufCodecs.INT));

	public static final Supplier<DataComponentType<MiniPhantomReturnTarget>> MINI_PHANTOM_RETURN_TARGET =
		REGISTER.registerComponentType("mini_phantom_return_target",
			builder -> builder.persistent(MiniPhantomReturnTarget.CODEC)
				.networkSynchronized(MiniPhantomReturnTarget.STREAM_CODEC));

	public static final Supplier<DataComponentType<UUID>> MINI_PHANTOM_PLAYER_RETURN_TARGET =
		REGISTER.registerComponentType("mini_phantom_player_return_target",
			builder -> builder.persistent(UUIDUtil.STRING_CODEC)
				.networkSynchronized(UUIDUtil.STREAM_CODEC));

	public static final Supplier<DataComponentType<UUID>> MINI_PHANTOM_HUD_ID =
		REGISTER.registerComponentType("mini_phantom_hud_id",
			builder -> builder.persistent(UUIDUtil.STRING_CODEC)
				.networkSynchronized(UUIDUtil.STREAM_CODEC));

	public static final Supplier<DataComponentType<CustomData>> STORAGE_CHANNEL_EXTENSION_CARD_FREQ =
		REGISTER.registerComponentType("storage_channel_extension_card_freq",
			builder -> builder.persistent(CustomData.CODEC)
				.networkSynchronized(CustomData.STREAM_CODEC));

	public static final Supplier<DataComponentType<List<ItemStack>>> STORAGE_CHANNEL_EXTENSION_CARD_CATEGORIES =
		REGISTER.registerComponentType("storage_channel_extension_card_categories",
			builder -> builder.persistent(ItemStack.CODEC.listOf())
				.networkSynchronized(CatnipStreamCodecBuilders.list(ItemStack.OPTIONAL_STREAM_CODEC)));

	public static final Supplier<DataComponentType<List<String>>> STORAGE_CHANNEL_EXTENSION_CARD_ADDRESSES =
		REGISTER.registerComponentType("storage_channel_extension_card_addresses",
			builder -> builder.persistent(Codec.STRING.listOf())
				.networkSynchronized(CatnipStreamCodecBuilders.list(ByteBufCodecs.STRING_UTF8)));

	public static final Supplier<DataComponentType<ItemContainerContents>> TUNABLE_PORTABLE_TICKER_CARDS =
		REGISTER.registerComponentType("tunable_portable_ticker_cards",
			builder -> builder.persistent(ItemContainerContents.CODEC)
				.networkSynchronized(ItemContainerContents.STREAM_CODEC));

	public static final Supplier<DataComponentType<Integer>> TUNABLE_PORTABLE_TICKER_SELECTED_CHANNEL =
		REGISTER.registerComponentType("tunable_portable_ticker_selected_channel",
			builder -> builder.persistent(Codec.INT)
				.networkSynchronized(ByteBufCodecs.INT));

	public static final Supplier<DataComponentType<Map<UUID, String>>> TUNABLE_PORTABLE_TICKER_ADDRESSES =
		REGISTER.registerComponentType("tunable_portable_ticker_addresses",
			builder -> builder.persistent(Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING))
				.networkSynchronized(ByteBufCodecs.map(java.util.HashMap::new, UUIDUtil.STREAM_CODEC, ByteBufCodecs.STRING_UTF8)));

	public static final Supplier<DataComponentType<Map<UUID, Map<UUID, List<Integer>>>>> TUNABLE_PORTABLE_TICKER_HIDDEN_CATEGORIES =
		REGISTER.registerComponentType("tunable_portable_ticker_hidden_categories",
			builder -> builder.persistent(Codec.unboundedMap(UUIDUtil.STRING_CODEC,
					Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT.listOf())))
				.networkSynchronized(ByteBufCodecs.map(HashMap::new, UUIDUtil.STREAM_CODEC,
					ByteBufCodecs.map(HashMap::new, UUIDUtil.STREAM_CODEC,
						CatnipStreamCodecBuilders.list(ByteBufCodecs.INT)))));

	private AllDataComponents() {}

	public static void register(IEventBus modEventBus) {
		REGISTER.register(modEventBus);
	}
}
