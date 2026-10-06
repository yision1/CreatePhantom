package com.yision.phantom.client.gui.hud;

import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.yision.phantom.content.logistics.tunablePortableTicker.StockKeeperRequestScreenAccess;
import com.yision.phantom.content.logistics.tunablePortableTicker.TunablePortableTickerLocator;
import com.yision.phantom.content.logistics.tunablePortableTicker.TunablePortableTickerMenu;
import com.yision.phantom.content.logistics.tunablePortableTicker.TunablePortableTickerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

final class StockTickerItemRenderer extends TunablePortableTickerScreen {
	private static StockTickerItemRenderer renderer;

	private StockTickerItemRenderer(Inventory inventory) {
		super(new TunablePortableTickerMenu(0, inventory, TunablePortableTickerLocator.EMPTY, 0, null),
			inventory, Component.empty());
		// Only the native entry renderer is used; no screen is initialized or opened.
		minecraft = Minecraft.getInstance();
		font = minecraft.font;
	}

	static void render(GuiGraphics graphics, ItemStack stack, int x, int y) {
		var minecraft = Minecraft.getInstance();
		var player = minecraft.player;
		if (renderer == null || renderer.getMenu().player != player
			|| renderer.getMenu().getStockData().getLevel() != minecraft.level)
			renderer = new StockTickerItemRenderer(player.getInventory());
		renderer.font = minecraft.font;
		InventorySummary summary = new InventorySummary();
		summary.add(stack);
		graphics.pose().pushPose();
		try {
			graphics.pose().translate(x - 1, y - 1, 0);
			for (var entry : summary.getStacksByCount())
				((StockKeeperRequestScreenAccess) (Object) renderer).createphantom$renderItemEntry(graphics, 1, entry, false, true);
		} finally {
			graphics.pose().popPose();
		}
	}
}
