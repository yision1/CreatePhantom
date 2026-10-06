package com.yision.phantom.registry;

import com.simibubi.create.content.logistics.packagePort.PackagePortMenu;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestMenu;
import com.tterrag.registrate.util.entry.MenuEntry;
import com.yision.phantom.block.phantomport.PhantomPortMenu;
import com.yision.phantom.block.phantomport.PhantomPortScreen;
import com.yision.phantom.item.miniphantom.MiniPhantomMenu;
import com.yision.phantom.item.miniphantom.MiniPhantomScreen;
import com.yision.phantom.content.logistics.tunablePortableTicker.TunablePortableTickerCardMenu;
import com.yision.phantom.content.logistics.tunablePortableTicker.TunablePortableTickerCardScreen;
import com.yision.phantom.content.logistics.tunablePortableTicker.TunablePortableTickerMenu;
import com.yision.phantom.content.logistics.tunablePortableTicker.TunablePortableTickerScreen;
import static com.yision.phantom.CreatePhantom.REGISTRATE;

public final class AllMenuTypes {
	public static final MenuEntry<PackagePortMenu> PHANTOMPORT =
		REGISTRATE.menu("phantomport",
			PhantomPortMenu::new,
			() -> PhantomPortScreen::new)
			.register();

	public static final MenuEntry<StockKeeperRequestMenu> TUNABLE_PORTABLE_TICKER =
		REGISTRATE.<StockKeeperRequestMenu, TunablePortableTickerScreen>menu("tunable_portable_ticker",
			(menuType, containerId, playerInventory, extraData) ->
				TunablePortableTickerMenu.createOnClient(containerId, playerInventory, extraData),
			() -> (menu, inventory, title) -> new TunablePortableTickerScreen((TunablePortableTickerMenu) menu, inventory, title))
			.register();

	public static final MenuEntry<TunablePortableTickerCardMenu> TUNABLE_PORTABLE_TICKER_CARDS =
		REGISTRATE.menu("tunable_portable_ticker_cards",
			(menuType, containerId, playerInventory, extraData) ->
				TunablePortableTickerCardMenu.createOnClient(containerId, playerInventory, extraData),
			() -> TunablePortableTickerCardScreen::new)
			.register();

	public static final MenuEntry<MiniPhantomMenu> MINI_PHANTOM =
		REGISTRATE.menu("mini_phantom",
			MiniPhantomMenu::new,
			() -> MiniPhantomScreen::new)
			.register();

	private AllMenuTypes() {}

	public static void register() {}
}
