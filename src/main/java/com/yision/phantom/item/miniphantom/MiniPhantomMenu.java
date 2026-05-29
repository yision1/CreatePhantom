package com.yision.phantom.item.miniphantom;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.yision.phantom.item.miniphantom.MiniPhantomItem;
import com.yision.phantom.registry.AllAttachmentTypes;
import com.yision.phantom.registry.AllItems;
import com.yision.phantom.registry.AllMenuTypes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

public class MiniPhantomMenu extends AbstractContainerMenu {
	private static final int PACKAGE_SLOT_COUNT = 9;
	private static final int CLIPBOARD_SLOT_INDEX = PACKAGE_SLOT_COUNT;
	private static final int PLAYER_SLOT_START = PACKAGE_SLOT_COUNT + 1;
	private static final int SLOT_X = 27;
	private static final int SLOT_Y = 28;
	private static final int CLIPBOARD_SLOT_X = 13;
	private static final int CLIPBOARD_SLOT_Y = 96;

	private final ItemStackHandler packageInventory = new ItemStackHandler(PackageItem.SLOTS);
	private final PlayerMiniPhantomClipboardInventory clipboardInventory;
	private final int ownerHotbarSlot;
	private final int ownerMenuSlot;
	private final List<ItemStack> initialPackageContents;

	public final Player player;
	public final Inventory playerInventory;
	public final ItemStack openedStack;
	public final InteractionHand hand;
	public final String initialAddress;

	private boolean confirmed;

	public MiniPhantomMenu(MenuType<?> type, int id, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
		super(type, id);
		this.player = playerInventory.player;
		this.playerInventory = playerInventory;
		this.openedStack = ItemStack.STREAM_CODEC.decode(extraData);
		this.hand = extraData.readEnum(InteractionHand.class);
		this.clipboardInventory = new PlayerMiniPhantomClipboardInventory();
		this.clipboardInventory.setStackInSlot(0, ItemStack.OPTIONAL_STREAM_CODEC.decode(extraData));
		this.clipboardInventory.setAddress(extraData.readUtf());
		this.initialAddress = readInitialContents(openedStack, clipboardInventory.getAddress());
		this.initialPackageContents = snapshotPackageInventory();
		this.ownerHotbarSlot = hand == InteractionHand.MAIN_HAND ? playerInventory.selected : -1;
		this.ownerMenuSlot = ownerHotbarSlot >= 0 ? PLAYER_SLOT_START + 27 + ownerHotbarSlot : -1;
		addSlots();
	}

	public MiniPhantomMenu(MenuType<?> type, int id, Inventory playerInventory, ItemStack openedStack, InteractionHand hand) {
		super(type, id);
		this.player = playerInventory.player;
		this.playerInventory = playerInventory;
		this.openedStack = openedStack.copy();
		this.hand = hand;
		this.clipboardInventory = player.getData(AllAttachmentTypes.MINI_PHANTOM_CLIPBOARD);
		this.initialAddress = readInitialContents(this.openedStack, clipboardInventory.getAddress());
		this.initialPackageContents = snapshotPackageInventory();
		this.ownerHotbarSlot = hand == InteractionHand.MAIN_HAND ? playerInventory.selected : -1;
		this.ownerMenuSlot = ownerHotbarSlot >= 0 ? PLAYER_SLOT_START + 27 + ownerHotbarSlot : -1;
		addSlots();
	}

	public static MiniPhantomMenu create(int id, Inventory playerInventory, ItemStack openedStack, InteractionHand hand) {
		return new MiniPhantomMenu(AllMenuTypes.MINI_PHANTOM.get(), id, playerInventory, openedStack, hand);
	}

	private void addSlots() {
		for (int slot = 0; slot < PACKAGE_SLOT_COUNT; slot++) {
			addSlot(new SlotItemHandler(packageInventory, slot, SLOT_X + 20 * slot, SLOT_Y) {
				@Override
				public boolean mayPlace(@NotNull ItemStack stack) {
					return !PackageItem.isPackage(stack) && !stack.is(AllItems.MINI_PHANTOM.get());
				}
			});
		}

		addSlot(new SlotItemHandler(clipboardInventory, 0, CLIPBOARD_SLOT_X, CLIPBOARD_SLOT_Y) {
			@Override
			public boolean mayPlace(@NotNull ItemStack stack) {
				return AllBlocks.CLIPBOARD.isIn(stack);
			}

			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});

		for (int row = 0; row < 3; ++row) {
			for (int col = 0; col < 9; ++col) {
				int slot = col + row * 9 + 9;
				addSlot(createPlayerSlot(slot, 33 + col * 18, 142 + row * 18));
			}
		}
		for (int hotbarSlot = 0; hotbarSlot < 9; ++hotbarSlot) {
			addSlot(createPlayerSlot(hotbarSlot, 33 + hotbarSlot * 18, 200));
		}
	}

	private Slot createPlayerSlot(int index, int x, int y) {
		return new Slot(playerInventory, index, x, y) {
			@Override
			public boolean mayPickup(Player player) {
				return hand != InteractionHand.MAIN_HAND || index != ownerHotbarSlot;
			}

			@Override
			public boolean mayPlace(ItemStack stack) {
				return hand != InteractionHand.MAIN_HAND || index != ownerHotbarSlot;
			}
		};
	}

	private String readInitialContents(ItemStack stack, String cachedAddress) {
		if (!MiniPhantomItem.hasCargo(stack)) {
			return cachedAddress;
		}

		ItemStack box = MiniPhantomItem.copyCargoPackage(stack);
		if (!PackageItem.isPackage(box)) {
			return cachedAddress;
		}

		ItemStackHandler contents = PackageItem.getContents(box);
		for (int slot = 0; slot < Math.min(packageInventory.getSlots(), contents.getSlots()); slot++) {
			packageInventory.setStackInSlot(slot, contents.getStackInSlot(slot).copy());
		}
		return PackageItem.getAddress(box);
	}

	public boolean confirm(String address) {
		if (player.level().isClientSide || confirmed || !stillValid(player)) {
			return false;
		}

		String normalizedAddress = address == null ? "" : address.trim();
		player.getData(AllAttachmentTypes.MINI_PHANTOM_CLIPBOARD).setAddress(normalizedAddress);
		ItemStack packageBox = createPackageBox();
		if (packageBox.isEmpty()) {
			if (MiniPhantomItem.hasCargo(openedStack)) {
				player.setItemInHand(hand, AllItems.MINI_PHANTOM.asStack());
			}
			clearPackageInventory();
			confirmed = true;
			broadcastChanges();
			return true;
		}

		if (!normalizedAddress.isEmpty()) {
			PackageItem.addAddress(packageBox, normalizedAddress);
		}

		ItemStack loadedPhantom = MiniPhantomItem.createLoaded(packageBox);
		if (MiniPhantomItem.hasCargo(openedStack)) {
			player.setItemInHand(hand, loadedPhantom);
		} else {
			ItemStack heldStack = player.getItemInHand(hand);
			if (!heldStack.is(AllItems.MINI_PHANTOM.get()) || heldStack.isEmpty()) {
				return false;
			}
			if (heldStack.getCount() == 1) {
				player.setItemInHand(hand, loadedPhantom);
			} else {
				heldStack.shrink(1);
				player.getInventory().placeItemBackInInventory(loadedPhantom);
			}
		}
		clearPackageInventory();
		confirmed = true;
		broadcastChanges();
		return true;
	}

	private ItemStack createPackageBox() {
		ItemStackHandler handler = new ItemStackHandler(PackageItem.SLOTS);
		boolean hasAnyContents = false;
		for (int slot = 0; slot < PACKAGE_SLOT_COUNT; slot++) {
			ItemStack stack = slots.get(slot).getItem();
			if (stack.isEmpty()) {
				continue;
			}
			handler.setStackInSlot(slot, stack.copy());
			hasAnyContents = true;
		}
		return hasAnyContents ? PackageItem.containing(handler) : ItemStack.EMPTY;
	}

	private void clearPackageInventory() {
		for (int slot = 0; slot < packageInventory.getSlots(); slot++) {
			packageInventory.setStackInSlot(slot, ItemStack.EMPTY);
		}
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		if (player.level().isClientSide || confirmed) {
			return;
		}
		if (MiniPhantomItem.hasCargo(openedStack)) {
			cancelPackagedPhantomChanges(player);
			return;
		}
		for (int slot = 0; slot < packageInventory.getSlots(); slot++) {
			player.getInventory().placeItemBackInInventory(packageInventory.getStackInSlot(slot));
			packageInventory.setStackInSlot(slot, ItemStack.EMPTY);
		}
	}

	private void cancelPackagedPhantomChanges(Player player) {
		List<ItemStack> currentContents = snapshotPackageInventory();
		List<ItemStack> insertedContents = subtractStacks(currentContents, initialPackageContents);
		List<ItemStack> removedContents = subtractStacks(initialPackageContents, currentContents);
		if (insertedContents.isEmpty() && removedContents.isEmpty()) {
			clearPackageInventory();
			return;
		}

		for (ItemStack stack : insertedContents) {
			player.getInventory().placeItemBackInInventory(stack);
		}
		for (ItemStack stack : removedContents) {
			consumeFromPlayerInventory(player, stack);
		}
		clearPackageInventory();
	}

	private List<ItemStack> snapshotPackageInventory() {
		List<ItemStack> snapshot = new ArrayList<>();
		for (int slot = 0; slot < packageInventory.getSlots(); slot++) {
			ItemStack stack = packageInventory.getStackInSlot(slot);
			if (!stack.isEmpty()) {
				snapshot.add(stack.copy());
			}
		}
		return snapshot;
	}

	private static List<ItemStack> subtractStacks(List<ItemStack> source, List<ItemStack> toSubtract) {
		List<ItemStack> remainder = new ArrayList<>(source.size());
		for (ItemStack stack : source) {
			if (!stack.isEmpty()) {
				remainder.add(stack.copy());
			}
		}
		for (ItemStack subtract : toSubtract) {
			if (subtract.isEmpty()) {
				continue;
			}
			int remainingCount = subtract.getCount();
			for (ItemStack candidate : remainder) {
				if (remainingCount <= 0) {
					break;
				}
				if (!ItemStack.isSameItemSameComponents(candidate, subtract)) {
					continue;
				}
				int consumed = Math.min(remainingCount, candidate.getCount());
				candidate.shrink(consumed);
				remainingCount -= consumed;
			}
		}

		List<ItemStack> difference = new ArrayList<>();
		for (ItemStack stack : remainder) {
			if (!stack.isEmpty()) {
				difference.add(stack);
			}
		}
		return difference;
	}

	private static void consumeFromPlayerInventory(Player player, ItemStack targetStack) {
		int remainingCount = targetStack.getCount();
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			if (remainingCount <= 0) {
				return;
			}
			ItemStack inventoryStack = player.getInventory().getItem(slot);
			if (!ItemStack.isSameItemSameComponents(inventoryStack, targetStack)) {
				continue;
			}
			int consumed = Math.min(remainingCount, inventoryStack.getCount());
			inventoryStack.shrink(consumed);
			remainingCount -= consumed;
		}
	}

	@Override
	public boolean stillValid(Player player) {
		ItemStack heldStack = player.getItemInHand(hand);
		return heldStack.is(AllItems.MINI_PHANTOM.get());
	}

	@Override
	public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
		if (isOwnerInteraction(slotId, dragType, clickType)) {
			return;
		}
		super.clicked(slotId, dragType, clickType, player);
	}

	private boolean isOwnerInteraction(int slotId, int dragType, ClickType clickType) {
		if (ownerMenuSlot < 0) {
			return false;
		}
		if (slotId == ownerMenuSlot) {
			return true;
		}
		return clickType == ClickType.SWAP && dragType == ownerHotbarSlot;
	}

	@Override
	public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
		return super.canTakeItemForPickAll(stack, slot) && slot.index != ownerHotbarSlot;
	}

	@Override
	public @NotNull ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}

		ItemStack stack = slot.getItem();
		ItemStack copy = stack.copy();

		if (index == CLIPBOARD_SLOT_INDEX) {
			if (!moveItemStackTo(stack, PLAYER_SLOT_START, slots.size(), false)) {
				return ItemStack.EMPTY;
			}
		} else if (index >= PLAYER_SLOT_START && AllBlocks.CLIPBOARD.isIn(stack)) {
			if (!moveItemStackTo(stack, CLIPBOARD_SLOT_INDEX, CLIPBOARD_SLOT_INDEX + 1, false)) {
				if (!moveItemStackTo(stack, 0, PACKAGE_SLOT_COUNT, false)) {
					return ItemStack.EMPTY;
				}
			}
		} else if (index < PACKAGE_SLOT_COUNT) {
			if (!moveItemStackTo(stack, PLAYER_SLOT_START, slots.size(), false)) {
				return ItemStack.EMPTY;
			}
		} else {
			if (!moveItemStackTo(stack, 0, PACKAGE_SLOT_COUNT, false)) {
				return ItemStack.EMPTY;
			}
		}

		if (stack.isEmpty()) {
			slot.set(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}

		return copy;
	}

	public ItemStack getClipboardStack() {
		return clipboardInventory.getStackInSlot(0);
	}

	@OnlyIn(Dist.CLIENT)
	public static MiniPhantomMenu createOnClient(int id, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
		return new MiniPhantomMenu(AllMenuTypes.MINI_PHANTOM.get(), id, playerInventory, extraData);
	}
}
