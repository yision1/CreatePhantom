package com.yision.phantom.item.ticker;

import com.yision.phantom.item.storagecard.StorageChannelExtensionCardItem;
import com.yision.phantom.item.ticker.access.TunablePortableTickerLocator;
import com.yision.phantom.item.ticker.TunablePortableTickerCardMenu;
import com.yision.phantom.item.ticker.TunablePortableTickerMenu;
import com.yision.phantom.registry.AllDataComponents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.SimpleMenuProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TunablePortableTickerItem extends Item {
	public static final int MAX_CHANNELS = 6;

	public TunablePortableTickerItem(Properties properties) {
		super(properties.stacksTo(1));
	}

	// --- Static helpers for card / channel access ---

	public static ItemStack find(Inventory playerInventory) {
		ItemStack mainHand = playerInventory.player.getMainHandItem();
		if (mainHand.getItem() instanceof TunablePortableTickerItem)
			return mainHand;

		ItemStack offHand = playerInventory.player.getOffhandItem();
		if (offHand.getItem() instanceof TunablePortableTickerItem)
			return offHand;

		for (int i = 0; i < playerInventory.getContainerSize(); i++) {
			ItemStack stack = playerInventory.getItem(i);
			if (stack.getItem() instanceof TunablePortableTickerItem)
				return stack;
		}

		return ItemStack.EMPTY;
	}

	public static List<ItemStack> getCards(ItemStack ticker) {
		List<ItemStack> cards = new ArrayList<>();
		ItemContainerContents container = ticker.getOrDefault(AllDataComponents.TUNABLE_PORTABLE_TICKER_CARDS,
			ItemContainerContents.EMPTY);
		for (int i = 0; i < Math.min(container.getSlots(), MAX_CHANNELS); i++) {
			ItemStack stack = container.getStackInSlot(i);
			cards.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
		}
		while (cards.size() < MAX_CHANNELS)
			cards.add(ItemStack.EMPTY);
		return cards;
	}

	public static void setCards(ItemStack ticker, List<ItemStack> cards) {
		List<ItemStack> trimmed = new ArrayList<>();
		for (int i = 0; i < Math.min(cards.size(), MAX_CHANNELS); i++) {
			ItemStack card = cards.get(i);
			trimmed.add(card.isEmpty() ? ItemStack.EMPTY : card);
		}
		ticker.set(AllDataComponents.TUNABLE_PORTABLE_TICKER_CARDS,
			ItemContainerContents.fromItems(trimmed));
	}

	public static int getSelectedChannel(ItemStack ticker) {
		return ticker.getOrDefault(AllDataComponents.TUNABLE_PORTABLE_TICKER_SELECTED_CHANNEL, 0);
	}

	public static void setSelectedChannel(ItemStack ticker, int index) {
		ticker.set(AllDataComponents.TUNABLE_PORTABLE_TICKER_SELECTED_CHANNEL,
			Math.max(0, Math.min(index, MAX_CHANNELS - 1)));
	}

	public static ItemStack getCard(ItemStack ticker, int index) {
		if (index < 0 || index >= MAX_CHANNELS)
			return ItemStack.EMPTY;
		return getCards(ticker).get(index);
	}

	@Nullable
	public static UUID networkFromChannel(ItemStack ticker, int index) {
		ItemStack card = getCard(ticker, index);
		if (card.isEmpty())
			return null;
		return StorageChannelExtensionCardItem.networkFromStack(card);
	}

	public static List<ItemStack> categoriesFromChannel(ItemStack ticker, int index) {
		ItemStack card = getCard(ticker, index);
		if (card.isEmpty())
			return List.of();
		return StorageChannelExtensionCardItem.loadCategoriesFromStack(card);
	}

	public static OptionalInt firstLinkedChannel(ItemStack ticker) {
		List<ItemStack> cards = getCards(ticker);
		for (int i = 0; i < cards.size(); i++) {
			if (!cards.get(i).isEmpty() && StorageChannelExtensionCardItem.isLinked(cards.get(i)))
				return OptionalInt.of(i);
		}
		return OptionalInt.empty();
	}

	// --- Address helpers ---

	public static void saveAddress(ItemStack ticker, UUID network, String address) {
		if (network == null)
			return;
		Map<UUID, String> existing = ticker.getOrDefault(AllDataComponents.TUNABLE_PORTABLE_TICKER_ADDRESSES, Map.of());
		Map<UUID, String> addresses = new HashMap<>(existing);
		if (address == null || address.isBlank())
			addresses.remove(network);
		else
			addresses.put(network, address);
		ticker.set(AllDataComponents.TUNABLE_PORTABLE_TICKER_ADDRESSES, addresses);
	}

	public static String loadAddress(ItemStack ticker, UUID network) {
		if (network == null)
			return "";
		Map<UUID, String> addresses = ticker.getOrDefault(AllDataComponents.TUNABLE_PORTABLE_TICKER_ADDRESSES, Map.of());
		return addresses.getOrDefault(network, "");
	}

	// --- Hidden categories helpers ---

	public static void saveHiddenCategories(ItemStack ticker, UUID playerUUID, UUID network, List<Integer> indices) {
		Map<UUID, Map<UUID, List<Integer>>> existing =
			ticker.getOrDefault(AllDataComponents.TUNABLE_PORTABLE_TICKER_HIDDEN_CATEGORIES, Map.of());
		Map<UUID, Map<UUID, List<Integer>>> hidden = new HashMap<>();
		existing.forEach((k, v) -> hidden.put(k, new HashMap<>(v)));
		Map<UUID, List<Integer>> playerMap = new HashMap<>(hidden.getOrDefault(playerUUID, Map.of()));
		if (indices.isEmpty())
			playerMap.remove(network);
		else
			playerMap.put(network, indices);
		if (playerMap.isEmpty())
			hidden.remove(playerUUID);
		else
			hidden.put(playerUUID, playerMap);
		ticker.set(AllDataComponents.TUNABLE_PORTABLE_TICKER_HIDDEN_CATEGORIES, hidden);
	}

	public static List<Integer> loadHiddenCategories(ItemStack ticker, UUID playerUUID, UUID network) {
		Map<UUID, Map<UUID, List<Integer>>> hidden =
			ticker.getOrDefault(AllDataComponents.TUNABLE_PORTABLE_TICKER_HIDDEN_CATEGORIES, Map.of());
		Map<UUID, List<Integer>> playerMap = hidden.getOrDefault(playerUUID, Map.of());
		return new ArrayList<>(playerMap.getOrDefault(network, List.of()));
	}

	// --- Item behaviour ---

	@Override
	public @NotNull InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player == null)
			return InteractionResult.FAIL;

		if (player.isShiftKeyDown()) {
			if (!context.getLevel().isClientSide && player instanceof ServerPlayer serverPlayer) {
				openCardConfigMenu(serverPlayer, context.getItemInHand(), TunablePortableTickerLocator.fromHand(context.getHand()));
			}
			return InteractionResult.SUCCESS;
		}

		return InteractionResult.PASS;
	}

	@Override
	public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, Player player,
		@NotNull InteractionHand usedHand) {
		ItemStack stack = player.getItemInHand(usedHand);

		if (level.isClientSide)
			return InteractionResultHolder.success(stack);

		if (player.isShiftKeyDown()) {
			openCardConfigMenu((ServerPlayer) player, stack, TunablePortableTickerLocator.fromHand(usedHand));
			return InteractionResultHolder.success(stack);
		}

		int channel = getSelectedChannel(stack);
		UUID network = networkFromChannel(stack, channel);

		if (network == null) {
			OptionalInt first = firstLinkedChannel(stack);
			if (first.isPresent()) {
				channel = first.getAsInt();
				network = networkFromChannel(stack, channel);
			}
		}

		if (network == null) {
			player.displayClientMessage(
				Component.translatable("item.createphantom.tunable_portable_ticker.not_linked"), true);
			return InteractionResultHolder.success(stack);
		}

		TunablePortableTickerLocator locator = TunablePortableTickerLocator.fromHand(usedHand);
		if (player instanceof ServerPlayer serverPlayer) {
			if (!TunablePortableTickerSession.mayInteract(serverPlayer, network)) {
				player.displayClientMessage(Component.translatable("create.stock_keeper.locked"), true);
				return InteractionResultHolder.success(stack);
			}
			int finalChannel = channel;
			serverPlayer.openMenu(new SimpleMenuProvider(
				(id, inv, p) -> new TunablePortableTickerMenu(id, inv, locator, finalChannel),
				Component.translatable("item.createphantom.tunable_portable_ticker")),
				buffer -> TunablePortableTickerMenu.writeMenuData(buffer, locator, finalChannel));
		}

		return InteractionResultHolder.success(stack);
	}

	private void openCardConfigMenu(ServerPlayer player, ItemStack stack, TunablePortableTickerLocator locator) {
		ItemStack openedSnapshot = stack.copy();
		player.openMenu(new SimpleMenuProvider(
			(id, inv, p) -> TunablePortableTickerCardMenu.create(id, inv, stack, locator),
			Component.translatable("gui.createphantom.tunable_portable_ticker.cards")),
			buffer -> {
				ItemStack.STREAM_CODEC.encode(buffer, openedSnapshot);
				TunablePortableTickerLocator.STREAM_CODEC.encode(buffer, locator);
			});
	}
}
