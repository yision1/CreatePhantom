package com.yision.phantom.item.ticker;

import com.yision.phantom.item.storagecard.StorageChannelExtensionCardItem;
import com.yision.phantom.item.ticker.TunablePortableTickerItem;
import com.yision.phantom.item.ticker.access.TunablePortableTickerLocator;
import com.yision.phantom.registry.AllMenuTypes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

public class TunablePortableTickerCardMenu extends AbstractContainerMenu {
	private static final int PROXY_SLOT_COUNT = 1;
	private static final int NO_ACTIVE_CARD = -2;

	public boolean slotsActive;
	public final ItemStackHandler proxyInventory;
	public final Player player;
	public final Inventory playerInventory;
	public final ItemStack openedStack;
	public final TunablePortableTickerLocator locator;

	private final ItemStackHandler workingCards;
	private final List<ItemStack> initialCards;
	private final int ownerInventorySlot;
	private final int ownerMenuSlot;
	private final boolean serverTransaction;
	private int activeCardIndex = NO_ACTIVE_CARD;
	private boolean closed;

	public TunablePortableTickerCardMenu(MenuType<?> type, int id, Inventory playerInventory,
		RegistryFriendlyByteBuf extraData) {
		super(type, id);
		this.player = playerInventory.player;
		this.playerInventory = playerInventory;
		this.openedStack = ItemStack.STREAM_CODEC.decode(extraData);
		this.locator = TunablePortableTickerLocator.STREAM_CODEC.decode(extraData);
		this.proxyInventory = createProxyInventory();
		this.workingCards = new ItemStackHandler(TunablePortableTickerItem.MAX_CHANNELS);
		this.initialCards = readCards(openedStack);
		this.serverTransaction = false;
		this.ownerInventorySlot = computeOwnerInventorySlot();
		this.ownerMenuSlot = ownerInventorySlot >= 0 ? menuSlotForPlayerInventorySlot(ownerInventorySlot) : -1;
		addSlots();
	}

	public TunablePortableTickerCardMenu(MenuType<?> type, int id, Inventory playerInventory,
		ItemStack openedStack, TunablePortableTickerLocator locator) {
		super(type, id);
		this.player = playerInventory.player;
		this.playerInventory = playerInventory;
		this.openedStack = openedStack.copy();
		this.locator = locator;
		this.proxyInventory = createProxyInventory();
		this.workingCards = new ItemStackHandler(TunablePortableTickerItem.MAX_CHANNELS);
		this.initialCards = readCards(openedStack);
		this.serverTransaction = true;
		for (int i = 0; i < initialCards.size(); i++)
			workingCards.setStackInSlot(i, initialCards.get(i));
		TunablePortableTickerItem.setCards(openedStack, List.of());
		this.ownerInventorySlot = computeOwnerInventorySlot();
		this.ownerMenuSlot = ownerInventorySlot >= 0 ? menuSlotForPlayerInventorySlot(ownerInventorySlot) : -1;
		addSlots();
	}

	public static TunablePortableTickerCardMenu create(int id, Inventory playerInventory,
		ItemStack openedStack, TunablePortableTickerLocator locator) {
		return new TunablePortableTickerCardMenu(AllMenuTypes.TUNABLE_PORTABLE_TICKER_CARDS.get(), id,
			playerInventory, openedStack, locator);
	}

	private static ItemStackHandler createProxyInventory() {
		return new ItemStackHandler(PROXY_SLOT_COUNT) {
			@Override
			public int getSlotLimit(int slot) {
				return 1;
			}

			@Override
			public boolean isItemValid(int slot, ItemStack stack) {
				return stack.getItem() instanceof StorageChannelExtensionCardItem;
			}
		};
	}

	private static List<ItemStack> readCards(ItemStack openedStack) {
		List<ItemStack> cards = new ArrayList<>();
		for (ItemStack stack : TunablePortableTickerItem.getCards(openedStack)) {
			if (!stack.isEmpty())
				cards.add(stack.copy());
		}
		return cards;
	}

	public List<ItemStack> getInitialCards() {
		List<ItemStack> cards = new ArrayList<>();
		for (ItemStack stack : initialCards)
			cards.add(stack.copy());
		return cards;
	}

	public void beginEdit(int cardIndex) {
		if (!serverTransaction || activeCardIndex != NO_ACTIVE_CARD || !proxyInventory.getStackInSlot(0).isEmpty())
			return;
		int cardCount = cardCount();
		if (cardIndex < -1 || cardIndex >= cardCount)
			return;
		if (cardIndex == -1 && cardCount >= TunablePortableTickerItem.MAX_CHANNELS)
			return;

		if (cardIndex >= 0) {
			ItemStack card = workingCards.extractItem(cardIndex, 1, false);
			if (card.isEmpty())
				return;
			proxyInventory.setStackInSlot(0, card);
		}
		activeCardIndex = cardIndex;
		slotsActive = true;
		broadcastChanges();
	}

	public void finishEdit(int cardIndex, String name) {
		if (!serverTransaction || activeCardIndex == NO_ACTIVE_CARD || activeCardIndex != cardIndex)
			return;

		ItemStack card = proxyInventory.extractItem(0, 1, false);
		if (!card.isEmpty() && card.getItem() instanceof StorageChannelExtensionCardItem) {
			String sanitizedName = name == null ? "" : name.trim();
			if (sanitizedName.length() > 28)
				sanitizedName = sanitizedName.substring(0, 28);
			card.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
				sanitizedName.isBlank() ? null : net.minecraft.network.chat.Component.literal(sanitizedName));
			int destination = activeCardIndex == -1 ? firstEmptyWorkingSlot() : activeCardIndex;
			if (destination >= 0)
				workingCards.setStackInSlot(destination, card);
			else
				playerInventory.placeItemBackInInventory(card);
		} else if (!card.isEmpty()) {
			playerInventory.placeItemBackInInventory(card);
		}

		activeCardIndex = NO_ACTIVE_CARD;
		compactWorkingCards();
		slotsActive = false;
		broadcastChanges();
	}

	public void removeCard(int cardIndex) {
		if (!serverTransaction || activeCardIndex != NO_ACTIVE_CARD
			|| cardIndex < 0 || cardIndex >= cardCount())
			return;
		ItemStack removed = workingCards.extractItem(cardIndex, 1, false);
		if (!removed.isEmpty())
			playerInventory.placeItemBackInInventory(removed);
		compactWorkingCards();
		broadcastChanges();
	}

	public void moveCard(int fromIndex, int toIndex) {
		if (!serverTransaction || activeCardIndex != NO_ACTIVE_CARD)
			return;
		List<ItemStack> cards = drainWorkingCards();
		if (fromIndex < 0 || fromIndex >= cards.size() || toIndex < 0 || toIndex >= cards.size()) {
			restoreWorkingCards(cards);
			return;
		}
		ItemStack moved = cards.remove(fromIndex);
		cards.add(toIndex, moved);
		restoreWorkingCards(cards);
	}

	private int cardCount() {
		int count = 0;
		for (int i = 0; i < workingCards.getSlots(); i++)
			if (!workingCards.getStackInSlot(i).isEmpty())
				count++;
		return count;
	}

	private int firstEmptyWorkingSlot() {
		for (int i = 0; i < workingCards.getSlots(); i++)
			if (workingCards.getStackInSlot(i).isEmpty())
				return i;
		return -1;
	}

	private void compactWorkingCards() {
		restoreWorkingCards(drainWorkingCards());
	}

	private List<ItemStack> drainWorkingCards() {
		List<ItemStack> cards = new ArrayList<>();
		for (int i = 0; i < workingCards.getSlots(); i++) {
			ItemStack card = workingCards.extractItem(i, 1, false);
			if (!card.isEmpty())
				cards.add(card);
		}
		return cards;
	}

	private void restoreWorkingCards(List<ItemStack> cards) {
		for (int i = 0; i < workingCards.getSlots(); i++)
			workingCards.setStackInSlot(i, ItemStack.EMPTY);
		for (int i = 0; i < Math.min(cards.size(), workingCards.getSlots()); i++)
			workingCards.setStackInSlot(i, cards.get(i));
	}

	private void closeTransaction() {
		if (!serverTransaction || closed)
			return;
		closed = true;

		if (activeCardIndex != NO_ACTIVE_CARD) {
			ItemStack card = proxyInventory.extractItem(0, 1, false);
			if (!card.isEmpty() && card.getItem() instanceof StorageChannelExtensionCardItem) {
				int destination = activeCardIndex == -1 ? firstEmptyWorkingSlot() : activeCardIndex;
				if (destination >= 0)
					workingCards.setStackInSlot(destination, card);
				else
					playerInventory.placeItemBackInInventory(card);
			} else if (!card.isEmpty()) {
				playerInventory.placeItemBackInInventory(card);
			}
			activeCardIndex = NO_ACTIVE_CARD;
		}

		compactWorkingCards();
		List<ItemStack> cards = drainWorkingCards();
		ItemStack liveStack = locator.resolve(player);
		if (liveStack.getItem() instanceof TunablePortableTickerItem) {
			TunablePortableTickerItem.setCards(liveStack, cards);
			return;
		}
		for (ItemStack card : cards)
			playerInventory.placeItemBackInInventory(card);
	}

	private void addSlots() {
		addSlot(new InactiveCardSlot(proxyInventory, 0, 16, 24));

		for (int row = 0; row < 3; ++row) {
			for (int col = 0; col < 9; ++col) {
				int slot = col + row * 9 + 9;
				addSlot(createPlayerSlot(slot, 18 + col * 18, 106 + row * 18));
			}
		}
		for (int hotbarSlot = 0; hotbarSlot < 9; ++hotbarSlot) {
			addSlot(createPlayerSlot(hotbarSlot, 18 + hotbarSlot * 18, 164));
		}
	}

	private Slot createPlayerSlot(int index, int x, int y) {
		return new InactivePlayerSlot(playerInventory, index, x, y);
	}

	private int computeOwnerInventorySlot() {
		return switch (locator.source()) {
			case MAIN_HAND -> playerInventory.selected;
			case INVENTORY -> locator.slot() >= 0 && locator.slot() < playerInventory.getContainerSize()
				? locator.slot()
				: -1;
			case OFF_HAND, CURIOS_BODY -> -1;
		};
	}

	private static int menuSlotForPlayerInventorySlot(int inventorySlot) {
		if (inventorySlot >= 9 && inventorySlot < 36)
			return PROXY_SLOT_COUNT + inventorySlot - 9;
		if (inventorySlot >= 0 && inventorySlot < 9)
			return PROXY_SLOT_COUNT + 27 + inventorySlot;
		return -1;
	}

	@Override
	public boolean stillValid(Player player) {
		ItemStack heldStack = locator.resolve(player);
		return heldStack.getItem() instanceof TunablePortableTickerItem;
	}

	@Override
	public void removed(Player player) {
		closeTransaction();
		super.removed(player);
	}

	@Override
	public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
		if (isOwnerInteraction(slotId, dragType, clickType))
			return;
		super.clicked(slotId, dragType, clickType, player);
	}

	private boolean isOwnerInteraction(int slotId, int dragType, ClickType clickType) {
		if (clickType == ClickType.SWAP
			&& locator.source() == TunablePortableTickerLocator.Source.OFF_HAND && dragType == 40)
			return true;
		if (ownerMenuSlot < 0)
			return false;
		if (slotId == ownerMenuSlot)
			return true;
		return clickType == ClickType.SWAP && dragType == ownerInventorySlot;
	}

	@Override
	public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
		return super.canTakeItemForPickAll(stack, slot) && !isOwnerPlayerInventorySlot(slot);
	}

	@Override
	public @NotNull ItemStack quickMoveStack(Player player, int index) {
		if (!slotsActive)
			return ItemStack.EMPTY;
		if (index < 0 || index >= slots.size())
			return ItemStack.EMPTY;

		Slot clickedSlot = slots.get(index);
		if (!clickedSlot.hasItem())
			return ItemStack.EMPTY;

		ItemStack stack = clickedSlot.getItem();
		ItemStack copy = stack.copy();
		if (index < PROXY_SLOT_COUNT) {
			if (!moveItemStackTo(stack, PROXY_SLOT_COUNT, slots.size(), true))
				return ItemStack.EMPTY;
		} else if (stack.getItem() instanceof StorageChannelExtensionCardItem) {
			if (!moveItemStackTo(stack, 0, PROXY_SLOT_COUNT, false))
				return ItemStack.EMPTY;
		} else
			return ItemStack.EMPTY;

		if (stack.isEmpty())
			clickedSlot.set(ItemStack.EMPTY);
		else
			clickedSlot.setChanged();

		return copy;
	}

	@OnlyIn(Dist.CLIENT)
	public static TunablePortableTickerCardMenu createOnClient(int id, Inventory playerInventory,
		RegistryFriendlyByteBuf extraData) {
		return new TunablePortableTickerCardMenu(AllMenuTypes.TUNABLE_PORTABLE_TICKER_CARDS.get(), id,
			playerInventory, extraData);
	}

	class InactivePlayerSlot extends Slot {
		public InactivePlayerSlot(Inventory inventory, int index, int x, int y) {
			super(inventory, index, x, y);
		}

		@Override
		public boolean mayPickup(Player player) {
			return slotsActive && !isOwnerPlayerInventorySlot(this);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return slotsActive && !isOwnerPlayerInventorySlot(this);
		}

		@Override
		public boolean isActive() {
			return slotsActive;
		}
	}

	private boolean isOwnerPlayerInventorySlot(Slot slot) {
		return ownerInventorySlot >= 0 && slot.container == playerInventory && slot.getSlotIndex() == ownerInventorySlot;
	}

	class InactiveCardSlot extends SlotItemHandler {
		public InactiveCardSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
			super(itemHandler, index, xPosition, yPosition);
		}

		@Override
		public boolean mayPlace(@NotNull ItemStack stack) {
			return slotsActive && stack.getItem() instanceof StorageChannelExtensionCardItem;
		}

		@Override
		public boolean mayPickup(Player player) {
			return slotsActive;
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}

		@Override
		public boolean isActive() {
			return slotsActive;
		}
	}
}
