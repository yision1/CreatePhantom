package com.yision.phantom.item.ticker.access;

import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour.RequestType;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.yision.phantom.item.ticker.TunablePortableTickerItem;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class ItemTunablePortableTickerAccess implements TunablePortableTickerAccess {
	private final ServerPlayer player;
	private final TunablePortableTickerLocator locator;
	private final int channel;
	private final UUID expectedNetwork;
	private final ItemStack stack;
	private final UUID network;

	public ItemTunablePortableTickerAccess(ServerPlayer player, TunablePortableTickerLocator locator, int channel,
		UUID expectedNetwork) {
		this.player = player;
		this.locator = locator;
		this.channel = channel;
		this.expectedNetwork = expectedNetwork;
		ItemStack resolved = locator.resolve(player);
		this.stack = resolved;
		if (resolved.getItem() instanceof TunablePortableTickerItem) {
			this.network = TunablePortableTickerItem.networkFromChannel(resolved, channel);
		} else {
			this.network = null;
		}
	}

	public static ItemTunablePortableTickerAccess resolve(ServerPlayer player, TunablePortableTickerLocator locator, int channel,
		UUID expectedNetwork) {
		return new ItemTunablePortableTickerAccess(player, locator, channel, expectedNetwork);
	}

	@Override
	public boolean isAvailable() {
		return network != null && network.equals(expectedNetwork);
	}

	@Override
	public ItemStack icon() {
		return stack;
	}

	@Override
	public UUID networkId() {
		return network;
	}

	@Override
	public String address() {
		return isAvailable() ? TunablePortableTickerItem.loadAddress(stack, network) : "";
	}

	@Override
	public List<ItemStack> categories() {
		return isAvailable() ? TunablePortableTickerItem.categoriesFromChannel(stack, channel) : List.of();
	}

	@Override
	public Set<Integer> hiddenCategories(UUID playerId) {
		if (!isAvailable())
			return Set.of();
		return new HashSet<>(TunablePortableTickerItem.loadHiddenCategories(stack, playerId, network));
	}

	@Override
	public void saveAddress(String address) {
		if (isAvailable())
			TunablePortableTickerItem.saveAddress(stack, network, address);
	}

	@Override
	public void saveHiddenCategories(UUID playerId, List<Integer> hiddenCategories) {
		if (!isAvailable())
			return;
		TunablePortableTickerItem.saveHiddenCategories(stack, playerId, network, hiddenCategories);
	}

	@Override
	public InventorySummary fetchAccurateSummary() {
		return isAvailable() ? LogisticsManager.getSummaryOfNetwork(network, true) : new InventorySummary();
	}

	@Override
	public boolean submitOrder(PackageOrderWithCrafts order, String address, ServerPlayer player) {
		if (!isAvailable())
			return false;
		return LogisticsManager.broadcastPackageRequest(network, RequestType.PLAYER, order, null, address);
	}
}
