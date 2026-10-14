package com.yision.phantom.content.logistics.tunablePortableTicker;

import java.util.Set;

import com.simibubi.create.content.logistics.BigItemStack;

import net.minecraft.client.gui.GuiGraphics;

public interface StockKeeperRequestScreenAccess {
	void createphantom$renderItemEntry(GuiGraphics graphics, float scale, BigItemStack entry,
		boolean hovered, boolean renderingOrders);

	Set<Integer> createphantom$getHiddenCategories();
}
