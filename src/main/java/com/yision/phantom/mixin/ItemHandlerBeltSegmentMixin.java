package com.yision.phantom.mixin;

import com.simibubi.create.content.kinetics.belt.transport.BeltInventory;
import com.simibubi.create.content.kinetics.belt.transport.ItemHandlerBeltSegment;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.item.ItemHelper;
import com.yision.phantom.logistics.courier.AirCourierHelper;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ItemHandlerBeltSegment.class)
public abstract class ItemHandlerBeltSegmentMixin {
	@Shadow
	@Final
	private BeltInventory beltInventory;

	@Shadow
	int offset;

	@Inject(method = "insertItem", at = @At("HEAD"), cancellable = true)
	private void createphantom$alignCourierLaunchStack(int slot, ItemStack stack, boolean simulate,
		CallbackInfoReturnable<ItemStack> cir) {
		if (!AirCourierHelper.isCourierLaunchStack(stack)) {
			return;
		}
		if (!this.beltInventory.canInsertAt(offset)) {
			cir.setReturnValue(stack);
			return;
		}

		BeltInventoryAccessor inventoryAccessor = (BeltInventoryAccessor) this.beltInventory;
		ItemStack remainder = ItemHelper.limitCountToMaxStackSize(stack, simulate);
		if (!simulate) {
			TransportedItemStack newStack =
				AirCourierHelper.createAlignedTransportedStack(stack,
					AirCourierHelper.resolveBeltHeading(inventoryAccessor.createphantom$getBelt()));
			newStack.insertedAt = offset;
			newStack.beltPosition =
				offset + .5f + (inventoryAccessor.createphantom$isBeltMovementPositive() ? -1 : 1) / 16f;
			newStack.prevBeltPosition = newStack.beltPosition;
			this.beltInventory.addItem(newStack);
			inventoryAccessor.createphantom$getBelt().setChanged();
			inventoryAccessor.createphantom$getBelt().sendData();
		}
		cir.setReturnValue(remainder);
	}
}
