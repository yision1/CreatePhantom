package com.yision.phantom.block.phantomport;

import com.simibubi.create.content.logistics.packagePort.PackagePortMenu;
import com.yision.phantom.registry.AllItems;
import com.yision.phantom.registry.AllMenuTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

public class PhantomPortMenu extends PackagePortMenu {
	public PhantomPortMenu(MenuType<?> type, int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
		super(type, id, inv, extraData);
	}

	public PhantomPortMenu(MenuType<?> type, int id, Inventory inv, PhantomPortBlockEntity blockEntity) {
		super(type, id, inv, blockEntity);
	}

	@Override
	protected PhantomPortBlockEntity createOnClient(RegistryFriendlyByteBuf extraData) {
		BlockPos readBlockPos = extraData.readBlockPos();
		ClientLevel world = Minecraft.getInstance().level;
		BlockEntity blockEntity = world != null ? world.getBlockEntity(readBlockPos) : null;
		if (blockEntity instanceof PhantomPortBlockEntity phantomPortBlockEntity) {
			return phantomPortBlockEntity;
		}
		return null;
	}

	public static PhantomPortMenu create(int id, Inventory inv, PhantomPortBlockEntity blockEntity) {
		return new PhantomPortMenu(AllMenuTypes.PHANTOMPORT.get(), id, inv, blockEntity);
	}

	@Override
	protected void addSlots() {
		super.addSlots();
		if (contentHolder instanceof PhantomPortBlockEntity phantomPortBlockEntity) {
			addSlot(new SlotItemHandler(phantomPortBlockEntity.getCarrierInventory(), 0, 12, 60));
		}
	}

	@Override
	public @NotNull ItemStack quickMoveStack(Player player, int index) {
		if (index < 0 || index >= slots.size()) {
			return ItemStack.EMPTY;
		}
		Slot slot = slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}

		int packageSlotCount = contentHolder.inventory.getSlots();
		int carrierSlotIndex = slots.size() - 1;
		ItemStack sourceStack = slot.getItem();

		if (index == carrierSlotIndex) {
			ItemStack stack = sourceStack.copy();
			ItemStack moved = stack.copy();
			if (!moveItemStackTo(stack, packageSlotCount, carrierSlotIndex, true)) {
				return ItemStack.EMPTY;
			}
			slot.setByPlayer(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
			return moved;
		}

		if (sourceStack.is(AllItems.MINI_PHANTOM.get())) {
			if (!PhantomPortInventory.isEmptyCarrier(sourceStack)) {
				return ItemStack.EMPTY;
			}
			ItemStack stack = sourceStack.copy();
			ItemStack moved = stack.copy();
			if (!moveItemStackTo(stack, carrierSlotIndex, carrierSlotIndex + 1, false)) {
				return ItemStack.EMPTY;
			}
			slot.setByPlayer(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
			return moved;
		}

		return super.quickMoveStack(player, index);
	}

}
