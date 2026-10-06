package com.yision.phantom.content.logistics.tunablePortableTicker;

import com.simibubi.create.foundation.utility.AdventureUtil;
import com.simibubi.create.foundation.networking.BlockEntityConfigurationPacket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class TunablePortableTickerSession {
	private static final int MAX_ADDRESS_LENGTH = 64;
	private static final int MAX_HIDDEN_CATEGORIES = 64;
	private static final int MIN_STOCK_REQUEST_INTERVAL = 10;

	private static final Map<RequestKey, RequestStamp> LAST_STOCK_REQUEST = new HashMap<>();

	private TunablePortableTickerSession() {}

	public static @Nullable TunablePortableTickerMenu resolve(
		ServerPlayer player, TunablePortableTickerLocator locator, int channel, UUID sessionNetwork
	) {
		if (player.isSpectator() || AdventureUtil.isAdventure(player)) {
			return null;
		}
		if (!(player.containerMenu instanceof TunablePortableTickerMenu menu)
			|| !menu.locator.equals(locator)
			|| menu.channel != channel
			|| !sessionNetwork.equals(menu.sessionNetwork)
			|| !menu.stillValid(player)) {
			return null;
		}
		return menu;
	}

	static void applyNativePacket(ServerPlayer player, TunablePortableTickerMenu menu, CustomPacketPayload payload,
		int requestId) {
		if (requestId < 0 || !(payload instanceof BlockEntityConfigurationPacket<?> nativePacket))
			return;
		var position = ((StockTickerConfigurationPacketAccess) payload).createphantom$getPosition();
		boolean queryPosition = position.equals(menu.getStockData().getQueryPosition());
		if (!queryPosition && (requestId > 0 || !player.canInteractWithBlock(position, 20)))
			return;
		if (requestId > 0 && !allowStockRequest(player, menu, requestId))
			return;
		ItemStack stack = menu.locator.resolve(player);
		PortableTickerStockData data = PortableTickerStockData.forRequest(player, stack, menu.channel, menu.sessionNetwork, position);
		try (var ignored = PortableTickerPacketContext.handling(menu, player, requestId, data, position)) {
			nativePacket.handle(player);
		}
		if (requestId == 0) {
			TunablePortableTickerItem.saveAddress(stack, menu.sessionNetwork, sanitizeAddress(data.getAddress()));
			saveHiddenCategories(player, menu, stack, data.getHiddenCategories(player.getUUID()));
		}
	}

	private static void saveHiddenCategories(ServerPlayer player, TunablePortableTickerMenu menu, ItemStack stack,
		List<Integer> hiddenCategories) {
		int categoryCount = TunablePortableTickerItem.categoriesFromChannel(stack, menu.channel).size();
		List<Integer> sanitized = new ArrayList<>();
		for (int index : new LinkedHashSet<>(hiddenCategories)) {
			if (index < -1 || index >= categoryCount)
				continue;
			sanitized.add(index);
			if (sanitized.size() >= MAX_HIDDEN_CATEGORIES)
				break;
		}
		TunablePortableTickerItem.saveHiddenCategories(stack, player.getUUID(), menu.sessionNetwork, sanitized);
	}

	private static boolean allowStockRequest(ServerPlayer player, TunablePortableTickerMenu menu, int requestId) {
		long gameTime = player.serverLevel().getGameTime();
		RequestKey key = new RequestKey(player.getUUID(), menu.containerId, menu.channel, menu.sessionNetwork);
		RequestStamp previous = LAST_STOCK_REQUEST.get(key);
		if (previous != null && requestId == previous.requestId)
			return gameTime - previous.gameTime < MIN_STOCK_REQUEST_INTERVAL;
		if (previous != null && (requestId < previous.requestId
			|| gameTime - previous.gameTime < MIN_STOCK_REQUEST_INTERVAL)) {
			return false;
		}
		LAST_STOCK_REQUEST.put(key, new RequestStamp(gameTime, requestId));
		return true;
	}

	private static String sanitizeAddress(String address) {
		if (address == null) {
			return "";
		}
		String trimmed = address.trim();
		return trimmed.length() <= MAX_ADDRESS_LENGTH
			? trimmed : trimmed.substring(0, MAX_ADDRESS_LENGTH);
	}

	public static void clearPlayer(ServerPlayer player) {
		LAST_STOCK_REQUEST.keySet().removeIf(key -> key.playerId.equals(player.getUUID()));
	}

	public static void clearAll() {
		LAST_STOCK_REQUEST.clear();
	}

	private record RequestKey(UUID playerId, int containerId, int channel, UUID network) {}
	private record RequestStamp(long gameTime, int requestId) {}
}
