package com.yision.phantom.item.ticker;

import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.foundation.utility.AdventureUtil;
import com.yision.phantom.item.ticker.access.ItemTunablePortableTickerAccess;
import com.yision.phantom.item.ticker.access.TunablePortableTickerLocator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public final class TunablePortableTickerSession {
	public static final int MAX_ADDRESS_LENGTH = 64;
	public static final int MAX_HIDDEN_CATEGORIES = 64;
	public static final int MAX_ORDER_ENTRIES = 256;
	public static final int MAX_STOCK_RESPONSE_ENTRIES = 4096;
	private static final int MIN_STOCK_REQUEST_INTERVAL = 10;

	private static final Map<RequestKey, Long> LAST_STOCK_REQUEST = new HashMap<>();

	private TunablePortableTickerSession() {}

	public static @Nullable ItemTunablePortableTickerAccess resolve(
		ServerPlayer player, TunablePortableTickerLocator locator, int channel, UUID sessionNetwork
	) {
		if (player.isSpectator() || AdventureUtil.isAdventure(player)) {
			return null;
		}
		if (!(player.containerMenu instanceof TunablePortableTickerMenu menu)
			|| !menu.locator.equals(locator)
			|| menu.channel != channel
			|| !menu.sessionNetwork.equals(sessionNetwork)) {
			return null;
		}
		ItemTunablePortableTickerAccess access =
			ItemTunablePortableTickerAccess.resolve(player, locator, channel, sessionNetwork);
		return access.isAvailable() ? access : null;
	}

	public static boolean mayInteract(ServerPlayer player, UUID network) {
		return Create.LOGISTICS.mayInteract(network, player);
	}

	public static boolean allowStockRequest(ServerPlayer player, int channel, UUID network) {
		long gameTime = player.serverLevel().getGameTime();
		RequestKey key = new RequestKey(player.getUUID(), channel, network);
		Long previous = LAST_STOCK_REQUEST.get(key);
		if (previous != null && gameTime - previous < MIN_STOCK_REQUEST_INTERVAL) {
			return false;
		}
		LAST_STOCK_REQUEST.put(key, gameTime);
		return true;
	}

	public static String sanitizeAddress(String address) {
		if (address == null) {
			return "";
		}
		String trimmed = address.trim();
		return trimmed.length() <= MAX_ADDRESS_LENGTH
			? trimmed : trimmed.substring(0, MAX_ADDRESS_LENGTH);
	}

	public static boolean isOrderWithinBounds(PackageOrderWithCrafts order) {
		if (order == null)
			return false;
		int entries = order.orderedStacks().stacks().size();
		if (entries > MAX_ORDER_ENTRIES || !hasValidAmounts(order.orderedStacks().stacks()))
			return false;
		for (PackageOrderWithCrafts.CraftingEntry craft : order.orderedCrafts()) {
			if (++entries > MAX_ORDER_ENTRIES || craft.count() <= 0 || craft.count() > BigItemStack.INF)
				return false;
			if (craft.pattern().stacks().size() > MAX_ORDER_ENTRIES - entries
				|| !hasValidAmounts(craft.pattern().stacks()))
				return false;
			for (BigItemStack ingredient : craft.pattern().stacks()) {
				if ((long) ingredient.count * craft.count() > BigItemStack.INF)
					return false;
			}
			entries += craft.pattern().stacks().size();
		}
		return true;
	}

	private static boolean hasValidAmounts(java.util.List<BigItemStack> stacks) {
		for (BigItemStack stack : stacks) {
			if (stack.count <= 0 || stack.count > BigItemStack.INF)
				return false;
		}
		return true;
	}

	public static void clearPlayer(ServerPlayer player) {
		LAST_STOCK_REQUEST.keySet().removeIf(key -> key.playerId.equals(player.getUUID()));
	}

	public static void clearAll() {
		LAST_STOCK_REQUEST.clear();
	}

	private record RequestKey(UUID playerId, int channel, UUID network) {}
}
