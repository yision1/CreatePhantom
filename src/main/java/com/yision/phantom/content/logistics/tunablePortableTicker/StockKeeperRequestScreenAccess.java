package com.yision.phantom.content.logistics.tunablePortableTicker;

import com.simibubi.create.content.logistics.BigItemStack;
import net.minecraft.client.gui.GuiGraphics;
import java.util.Set;

public interface StockKeeperRequestScreenAccess {
	void createphantom$renderItemEntry(GuiGraphics graphics, float scale, BigItemStack entry,
		boolean hovered, boolean renderingOrders);
	Set<Integer> createphantom$getHiddenCategories();
}
