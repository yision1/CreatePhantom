package com.yision.phantom.registry;

import com.simibubi.create.content.logistics.packagePort.PackagePortMenu;
import com.tterrag.registrate.util.entry.MenuEntry;
import com.yision.phantom.block.phantomport.PhantomPortMenu;
import com.yision.phantom.block.phantomport.PhantomPortScreen;
import com.yision.phantom.item.miniphantom.MiniPhantomMenu;
import com.yision.phantom.item.miniphantom.MiniPhantomScreen;
import com.yision.phantom.item.ticker.TunablePortableTickerCardMenu;
import com.yision.phantom.item.ticker.TunablePortableTickerCardScreen;
import com.yision.phantom.item.ticker.TunablePortableTickerMenu;
import com.yision.phantom.item.ticker.TunablePortableTickerScreen;
import static com.yision.phantom.CreatePhantom.REGISTRATE;

public final class AllMenuTypes {
	public static final MenuEntry<PackagePortMenu> PHANTOMPORT =
		REGISTRATE.menu("phantomport",
			PhantomPortMenu::new,
			() -> PhantomPortScreen::new)
			.register();

	public static final MenuEntry<TunablePortableTickerMenu> TUNABLE_PORTABLE_TICKER =
		REGISTRATE.menu("tunable_portable_ticker",
			(menuType, containerId, playerInventory, extraData) ->
				TunablePortableTickerMenu.createOnClient(containerId, playerInventory, extraData),
			() -> TunablePortableTickerScreen::new)
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
