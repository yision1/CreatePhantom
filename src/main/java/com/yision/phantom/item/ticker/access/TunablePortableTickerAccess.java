package com.yision.phantom.item.ticker.access;

import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public interface TunablePortableTickerAccess {
	boolean isAvailable();

	ItemStack icon();

	UUID networkId();

	String address();

	List<ItemStack> categories();

	Set<Integer> hiddenCategories(UUID playerId);

	void saveAddress(String address);

	void saveHiddenCategories(UUID playerId, List<Integer> hiddenCategories);

	InventorySummary fetchSummary();

	boolean submitOrder(PackageOrderWithCrafts order, String address, ServerPlayer player);
}
