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
			addSlot(new SlotItemHandler(phantomPortBlockEntity.getCarrierInventory(), 0, 12, 60) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return stack.is(AllItems.MINI_PHANTOM.get());
				}
			});
		}
	}

	@Override
	public @NotNull ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem()) {
			return super.quickMoveStack(player, index);
		}

		ItemStack stack = slot.getItem();
		int carrierSlotIndex = slots.size() - 1;

		if (index == carrierSlotIndex) {
			int originalCount = stack.getCount();
			if (moveItemStackTo(stack, 18, carrierSlotIndex, false)) {
				int moved = originalCount - stack.getCount();
				if (stack.isEmpty()) {
					slot.set(ItemStack.EMPTY);
				} else {
					slot.setChanged();
				}
				ItemStack result = stack.copy();
				result.setCount(moved);
				return result;
			}
		} else if (stack.is(AllItems.MINI_PHANTOM.get())) {
			Slot carrierSlot = slots.get(carrierSlotIndex);
			ItemStack targetStack = carrierSlot.getItem();

			int maxStackSize = stack.getMaxStackSize();
			int space = maxStackSize - (targetStack.isEmpty() ? 0 : targetStack.getCount());

			if (space > 0) {
				int toMove = Math.min(space, stack.getCount());
				if (targetStack.isEmpty()) {
					ItemStack moved = stack.copy();
					moved.setCount(toMove);
					carrierSlot.set(moved);
				} else {
					targetStack.grow(toMove);
					carrierSlot.setChanged();
				}
				stack.shrink(toMove);
				if (stack.isEmpty()) {
					slot.set(ItemStack.EMPTY);
				} else {
					slot.setChanged();
				}
				ItemStack result = stack.copy();
				result.setCount(toMove);
				return result;
			}
			return ItemStack.EMPTY;
		}

		return super.quickMoveStack(player, index);
	}

}
