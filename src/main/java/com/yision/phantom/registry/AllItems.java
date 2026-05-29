package com.yision.phantom.registry;

import com.tterrag.registrate.providers.ProviderType;
import com.tterrag.registrate.util.entry.ItemEntry;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;
import com.yision.phantom.item.miniphantom.MiniPhantomItem;
import com.yision.phantom.item.storagecard.StorageChannelExtensionCardItem;
import com.yision.phantom.item.ticker.TunablePortableTickerItem;
import static com.yision.phantom.CreatePhantom.REGISTRATE;

public final class AllItems {
	public static final ItemEntry<MiniPhantomItem> MINI_PHANTOM;
	public static final ItemEntry<TunablePortableTickerItem> TUNABLE_PORTABLE_TICKER;
	public static final ItemEntry<StorageChannelExtensionCardItem> STORAGE_CHANNEL_EXTENSION_CARD;

	static {
		var defaultTab = REGISTRATE.getCreativeTab();

		MINI_PHANTOM = REGISTRATE.item("mini_phantom", MiniPhantomItem::new)
			.setData(ProviderType.LANG, NonNullBiConsumer.noop())
			.model((ctx, prov) -> {})
			.register();

		TUNABLE_PORTABLE_TICKER = REGISTRATE.item("tunable_portable_ticker", TunablePortableTickerItem::new)
			.setData(ProviderType.LANG, NonNullBiConsumer.noop())
			.model((ctx, prov) -> {})
			.register();

		STORAGE_CHANNEL_EXTENSION_CARD = REGISTRATE.item("storage_channel_extension_card", StorageChannelExtensionCardItem::new)
			.setData(ProviderType.LANG, NonNullBiConsumer.noop())
			.model((ctx, prov) -> {})
			.register();
	}

	private AllItems() {}

	public static void register() {}
}
