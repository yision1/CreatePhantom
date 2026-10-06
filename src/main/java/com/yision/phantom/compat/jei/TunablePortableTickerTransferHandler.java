package com.yision.phantom.compat.jei;

import com.simibubi.create.compat.jei.StockKeeperTransferHandler;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestMenu;
import com.yision.phantom.content.logistics.tunablePortableTicker.TunablePortableTickerMenu;
import com.yision.phantom.registry.AllMenuTypes;
import java.util.Optional;
import mezz.jei.api.helpers.IJeiHelpers;
import net.minecraft.world.inventory.MenuType;

public class TunablePortableTickerTransferHandler extends StockKeeperTransferHandler {
	public TunablePortableTickerTransferHandler(IJeiHelpers helpers) {
		super(helpers);
	}

	@Override
	public Class<? extends StockKeeperRequestMenu> getContainerClass() {
		return TunablePortableTickerMenu.class;
	}

	@Override
	public Optional<MenuType<StockKeeperRequestMenu>> getMenuType() {
		return Optional.of(AllMenuTypes.TUNABLE_PORTABLE_TICKER.get());
	}
}
