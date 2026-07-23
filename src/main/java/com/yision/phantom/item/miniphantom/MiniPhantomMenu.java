package com.yision.phantom.item.miniphantom;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.yision.phantom.item.miniphantom.MiniPhantomItem;
import com.yision.phantom.registry.AllAttachmentTypes;
import com.yision.phantom.registry.AllItems;
import com.yision.phantom.registry.AllMenuTypes;
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
	private final boolean serverTransaction;
	private final boolean openedWithCargo;
	private final ItemStack initialCargoPackage;

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
		this.serverTransaction = false;
		this.openedWithCargo = MiniPhantomItem.hasCargo(openedStack);
		this.initialCargoPackage = MiniPhantomItem.copyCargoPackage(openedStack);
		this.initialAddress = readInitialContents(openedStack, clipboardInventory.getAddress());
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
		this.serverTransaction = true;
		this.openedWithCargo = MiniPhantomItem.hasCargo(openedStack);
		this.initialCargoPackage = MiniPhantomItem.copyCargoPackage(openedStack);
		this.initialAddress = readInitialContents(this.openedStack, clipboardInventory.getAddress());
		if (openedWithCargo) {
			ItemStack untouchedRemainder = ItemStack.EMPTY;
			if (openedStack.getCount() > 1) {
				untouchedRemainder = openedStack.copy();
				untouchedRemainder.setCount(openedStack.getCount() - 1);
				openedStack.setCount(1);
			}
			MiniPhantomItem.clearCargo(openedStack);
			if (!untouchedRemainder.isEmpty())
				playerInventory.placeItemBackInInventory(untouchedRemainder);
		}
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
		if (normalizedAddress.length() > 64)
			normalizedAddress = normalizedAddress.substring(0, 64);
		player.getData(AllAttachmentTypes.MINI_PHANTOM_CLIPBOARD).setAddress(normalizedAddress);
		ItemStack packageBox = createPackageBox();
		if (packageBox.isEmpty()) {
			clearPackageInventory();
			confirmed = true;
			broadcastChanges();
			return true;
		}

		if (!normalizedAddress.isEmpty()) {
			PackageItem.addAddress(packageBox, normalizedAddress);
		}

		ItemStack heldStack = player.getItemInHand(hand);
		if (!heldStack.is(AllItems.MINI_PHANTOM.get()) || heldStack.isEmpty()) {
			return false;
		}
		if (openedWithCargo || heldStack.getCount() == 1) {
			MiniPhantomItem.loadCargo(heldStack, packageBox);
		} else {
			ItemStack loadedPhantom = heldStack.copy();
			loadedPhantom.setCount(1);
			MiniPhantomItem.loadCargo(loadedPhantom, packageBox);
			heldStack.shrink(1);
			player.getInventory().placeItemBackInInventory(loadedPhantom);
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

	@Override
	public void removed(Player player) {
		super.removed(player);
		if (player.level().isClientSide || confirmed || !serverTransaction) {
			return;
		}
		closeTransaction(player);
	}

	private void closeTransaction(Player player) {
		if (openedWithCargo) {
			ItemStack heldStack = player.getItemInHand(hand);
			if (heldStack.is(AllItems.MINI_PHANTOM.get()) && !heldStack.isEmpty()) {
				boolean unchanged = contentsMatchInitialPackage();
				ItemStack packageBox = unchanged ? initialCargoPackage.copy() : createPackageBox();
				if (!packageBox.isEmpty()) {
					if (!unchanged && !initialAddress.isBlank())
						PackageItem.addAddress(packageBox, initialAddress);
					MiniPhantomItem.loadCargo(heldStack, packageBox);
				}
				clearPackageInventory();
				return;
			}
		}
		returnPackageContents(player);
	}

	private boolean contentsMatchInitialPackage() {
		if (!PackageItem.isPackage(initialCargoPackage))
			return false;
		ItemStackHandler initialContents = PackageItem.getContents(initialCargoPackage);
		for (int slot = 0; slot < packageInventory.getSlots(); slot++) {
			if (!ItemStack.matches(
				packageInventory.getStackInSlot(slot), initialContents.getStackInSlot(slot))) {
				return false;
			}
		}
		return true;
	}

	private void returnPackageContents(Player player) {
		for (int slot = 0; slot < packageInventory.getSlots(); slot++) {
			ItemStack stack = packageInventory.extractItem(slot, packageInventory.getSlotLimit(slot), false);
			if (!stack.isEmpty())
				player.getInventory().placeItemBackInInventory(stack);
		}
	}

	private void clearPackageInventory() {
		for (int slot = 0; slot < packageInventory.getSlots(); slot++) {
			if (!packageInventory.getStackInSlot(slot).isEmpty())
				packageInventory.setStackInSlot(slot, ItemStack.EMPTY);
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
		if (clickType == ClickType.SWAP && hand == InteractionHand.OFF_HAND && dragType == 40)
			return true;
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
		return super.canTakeItemForPickAll(stack, slot)
			&& !(slot.container == playerInventory && slot.getSlotIndex() == ownerHotbarSlot);
	}

	@Override
	public @NotNull ItemStack quickMoveStack(Player player, int index) {
		if (index < 0 || index >= slots.size())
			return ItemStack.EMPTY;
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
