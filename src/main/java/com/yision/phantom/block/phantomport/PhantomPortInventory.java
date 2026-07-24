package com.yision.phantom.block.phantomport;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.yision.phantom.item.miniphantom.MiniPhantomItem;
import com.yision.phantom.logistics.address.PhantomAddressRules;
import com.yision.phantom.registry.AllItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

final class PhantomPortInventory {

	private final PhantomPortBlockEntity port;
	final ItemStackHandler carrierInventory;
	final IItemHandler combinedHandler;

	PhantomPortInventory(PhantomPortBlockEntity port) {
		this.port = port;

		this.carrierInventory = new ItemStackHandler(1) {
			@Override
			public boolean isItemValid(int slot, @NotNull ItemStack stack) {
				return isEmptyCarrier(stack);
			}

			@Override
			protected void onContentsChanged(int slot) {
				port.onCarrierContentsChanged();
			}
		};

		this.combinedHandler = new IItemHandler() {
			@Override
			public int getSlots() {
				return port.inventory.getSlots() + carrierInventory.getSlots();
			}

			@Override
			public @NotNull ItemStack getStackInSlot(int slot) {
				return getCombinedStackInSlot(slot);
			}

			@Override
			public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
				return insertIntoCombinedSlot(slot, stack, simulate);
			}

			@Override
			public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
				return extractFromCombinedSlot(slot, amount, simulate);
			}

			@Override
			public int getSlotLimit(int slot) {
				return getCombinedSlotLimit(slot);
			}

			@Override
			public boolean isItemValid(int slot, @NotNull ItemStack stack) {
				return isValidForCombinedSlot(slot, stack);
			}
		};
	}

	ItemStackHandler carrierInventory() {
		return carrierInventory;
	}

	IItemHandler combinedHandler() {
		return combinedHandler;
	}

	private boolean isPackageSlot(int slot) {
		return slot >= 0 && slot < port.inventory.getSlots();
	}

	private int toCarrierSlot(int slot) {
		return slot - port.inventory.getSlots();
	}

	private @NotNull ItemStack getCombinedStackInSlot(int slot) {
		if (isPackageSlot(slot)) {
			return port.inventory.getStackInSlot(slot);
		}
		return carrierInventory.getStackInSlot(toCarrierSlot(slot));
	}

	private @NotNull ItemStack insertIntoCombinedSlot(int slot, @NotNull ItemStack stack, boolean simulate) {
		if (stack.is(AllItems.MINI_PHANTOM.get())) {
			if (isPackageSlot(slot) || !isEmptyCarrier(stack)) {
				return stack;
			}
			return carrierInventory.insertItem(toCarrierSlot(slot), stack, simulate);
		}
		if (!PackageItem.isPackage(stack) || !isPackageSlot(slot)) {
			return stack;
		}
		String filterString = port.getFilterString();
		if (filterString != null && !PhantomAddressRules.isBlank(filterString)
			&& PhantomAddressRules.matchesPackage(stack, filterString)) {
			return stack;
		}
		return port.inventory.insertItem(slot, stack, simulate);
	}

	private @NotNull ItemStack extractFromCombinedSlot(int slot, int amount, boolean simulate) {
		if (amount <= 0) {
			return ItemStack.EMPTY;
		}
		if (isPackageSlot(slot)) {
			ItemStack preview = port.inventory.extractItem(slot, 64, true);
			String filterString = port.getFilterString();
			if (!PackageItem.isPackage(preview) || filterString == null || PhantomAddressRules.isBlank(filterString)
				|| !PhantomAddressRules.matchesPackage(preview, filterString)) {
				return ItemStack.EMPTY;
			}
		}
		ItemStack extracted = isPackageSlot(slot)
			? port.inventory.extractItem(slot, amount, simulate)
			: carrierInventory.extractItem(toCarrierSlot(slot), amount, simulate);
		return extracted;
	}

	private int getCombinedSlotLimit(int slot) {
		if (isPackageSlot(slot)) {
			return port.inventory.getSlotLimit(slot);
		}
		return carrierInventory.getSlotLimit(toCarrierSlot(slot));
	}

	private boolean isValidForCombinedSlot(int slot, @NotNull ItemStack stack) {
		if (isPackageSlot(slot)) {
			return PackageItem.isPackage(stack);
		}
		return isEmptyCarrier(stack);
	}

	static boolean isEmptyCarrier(ItemStack stack) {
		return stack.is(AllItems.MINI_PHANTOM.get())
			&& !MiniPhantomItem.hasCargo(stack)
			&& MiniPhantomItem.getReturnTarget(stack).isEmpty()
			&& MiniPhantomItem.getPlayerReturnTarget(stack).isEmpty();
	}

	boolean hasUsableCarrier() {
		return isEmptyCarrier(carrierInventory.getStackInSlot(0));
	}

	ItemStack extractOneCarrier(boolean simulate) {
		if (!hasUsableCarrier()) {
			return ItemStack.EMPTY;
		}
		return carrierInventory.extractItem(0, 1, simulate);
	}

	boolean addPackage(ItemStack stack, boolean simulate) {
		if (!PackageItem.isPackage(stack)) {
			return false;
		}
		for (int slot = 0; slot < port.inventory.getSlots(); slot++) {
			ItemStack remainder = port.inventory.insertItem(slot, stack, simulate);
			if (!remainder.isEmpty()) {
				continue;
			}
			return true;
		}
		return false;
	}

	boolean canReceiveCourier(ItemStack box) {
		ItemStack carrier = AllItems.MINI_PHANTOM.asStack();
		return carrierInventory.insertItem(0, carrier, true).isEmpty() && addPackage(box.copy(), true);
	}

	boolean receiveCourier(ItemStack box) {
		if (!canReceiveCourier(box)) {
			return false;
		}
		ItemStack carrier = AllItems.MINI_PHANTOM.asStack();
		carrierInventory.insertItem(0, carrier.copy(), false);
		addPackage(box.copy(), false);
		return true;
	}

	boolean canReceivePackage(ItemStack box) {
		return addPackage(box.copy(), true);
	}

	boolean receivePackage(ItemStack box) {
		if (!canReceivePackage(box)) {
			return false;
		}
		addPackage(box.copy(), false);
		return true;
	}

	boolean canReceiveCarrier() {
		ItemStack carrier = AllItems.MINI_PHANTOM.asStack();
		return carrierInventory.insertItem(0, carrier, true).isEmpty();
	}

	boolean receiveCarrier() {
		if (!canReceiveCarrier()) {
			return false;
		}
		carrierInventory.insertItem(0, AllItems.MINI_PHANTOM.asStack().copy(), false);
		return true;
	}

	void returnCarrier(ItemStack carrier) {
		carrierInventory.insertItem(0, carrier, false);
	}

	void dropOneCarrier() {
		ItemStack carrier = extractOneCarrier(false);
		if (carrier.isEmpty() || port.getLevel() == null) {
			return;
		}
		BlockPos pos = port.getBlockPos();
		port.getLevel().addFreshEntity(
			new ItemEntity(port.getLevel(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
				carrier.copy()));
	}

	void dropAllCarriers() {
		if (port.getLevel() == null) {
			return;
		}
		int carrierCount = carrierInventory.getStackInSlot(0).getCount();
		ItemStack carrier = carrierInventory.extractItem(0, carrierCount, false);
		if (carrier.isEmpty()) {
			return;
		}
		BlockPos pos = port.getBlockPos();
		port.getLevel().addFreshEntity(
			new ItemEntity(port.getLevel(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
				carrier));
	}

	void write(CompoundTag tag, HolderLookup.Provider registries) {
		tag.put("CarrierInventory", carrierInventory.serializeNBT(registries));
	}

	void read(CompoundTag tag, HolderLookup.Provider registries) {
		if (tag.contains("CarrierInventory")) {
			carrierInventory.deserializeNBT(registries, tag.getCompound("CarrierInventory"));
		}
	}
}
