package com.yision.phantom.block.phantomport;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.yision.phantom.logistics.address.PhantomAddressRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

final class PhantomPortAutomation {

	private final PhantomPortBlockEntity port;
	private final PhantomPortInventory inventory;
	private final PhantomPortBeltAccess beltAccess;

	PhantomPortAutomation(PhantomPortBlockEntity port,
						  PhantomPortInventory inventory,
						  PhantomPortBeltAccess beltAccess) {
		this.port = port;
		this.inventory = inventory;
		this.beltAccess = beltAccess;
	}

	void tick() {
		if (tryPushingToAdjacentPackagers()) {
			return;
		}
		tryPullingFromAdjacentPackagers();
	}

	private boolean tryPushingToAdjacentPackagers() {
		String filterString = port.getFilterString();
		if (filterString == null || PhantomAddressRules.isBlank(filterString)) {
			return false;
		}

		for (int packageSlot = 0; packageSlot < port.inventory.getSlots(); packageSlot++) {
			ItemStack packageStack = port.inventory.getStackInSlot(packageSlot);
			if (packageStack.isEmpty() || !PackageItem.isPackage(packageStack)) {
				continue;
			}
			if (!PhantomAddressRules.matchesPackage(packageStack, filterString)) {
				continue;
			}
			if (tryPushPackageSlotToAdjacentContainers(packageSlot, packageStack)) {
				return true;
			}
		}
		return false;
	}

	private boolean tryPushPackageSlotToAdjacentContainers(int packageSlot, ItemStack packageStack) {
		ItemStack singlePackage = packageStack.copy();
		singlePackage.setCount(1);

		for (Direction side : Direction.values()) {
			IItemHandler adjacentInventory = getAdjacentPackagerInventory(side);
			if (adjacentInventory == null) {
				continue;
			}
			if (tryInsertPackageIntoAdjacent(packageSlot, singlePackage, adjacentInventory)) {
				return true;
			}
		}
		return false;
	}

	private boolean tryInsertPackageIntoAdjacent(int packageSlot, ItemStack singlePackage,
												 IItemHandler adjacentInventory) {
		for (int slot = 0; slot < adjacentInventory.getSlots(); slot++) {
			ItemStack remainder = adjacentInventory.insertItem(slot, singlePackage.copy(), true);
			if (!remainder.isEmpty()) {
				continue;
			}

			ItemStack extracted = port.inventory.extractItem(packageSlot, 1, false);
			if (extracted.isEmpty()) {
				return false;
			}

			ItemStack actualRemainder = adjacentInventory.insertItem(slot, extracted, false);
			if (actualRemainder.isEmpty()) {
				return true;
			}

			port.inventory.insertItem(packageSlot, actualRemainder, false);
			return false;
		}
		return false;
	}

	private void tryPullingFromAdjacentPackagers() {
		for (Direction side : Direction.values()) {
			IItemHandler adjacentInventory = getAdjacentPackagerInventory(side);
			if (adjacentInventory == null) {
				continue;
			}
			if (tryPullPackage(adjacentInventory)) {
				return;
			}
		}
	}

	private boolean tryPullPackage(IItemHandler adjacentInventory) {
		String filterString = port.getFilterString();
		for (int slot = 0; slot < adjacentInventory.getSlots(); slot++) {
			ItemStack extractedSimulated = adjacentInventory.extractItem(slot, 1, true);
			if (extractedSimulated.isEmpty() || !PackageItem.isPackage(extractedSimulated)) {
				continue;
			}
			if (filterString != null && !PhantomAddressRules.isBlank(filterString)
				&& PhantomAddressRules.matchesPackage(extractedSimulated, filterString)) {
				continue;
			}
			if (!inventory.addPackage(extractedSimulated, true)) {
				return false;
			}

			ItemStack extracted = adjacentInventory.extractItem(slot, 1, false);
			if (extracted.isEmpty()) {
				continue;
			}
			return inventory.addPackage(extracted, false);
		}
		return false;
	}

	private @Nullable IItemHandler getAdjacentPackagerInventory(Direction side) {
		if (port.getLevel() == null || side == beltAccess.specialSide()) {
			return null;
		}
		BlockPos adjacentPos = port.getBlockPos().relative(side);
		if (!port.getLevel().hasChunkAt(adjacentPos)) {
			return null;
		}
		if (!(port.getLevel().getBlockEntity(adjacentPos) instanceof PackagerBlockEntity)) {
			return null;
		}
		return port.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, adjacentPos, side.getOpposite());
	}
}
