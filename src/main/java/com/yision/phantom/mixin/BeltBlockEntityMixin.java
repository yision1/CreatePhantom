package com.yision.phantom.mixin;

import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.yision.phantom.logistics.courier.AirCourierHelper;
import com.yision.phantom.item.miniphantom.MiniPhantomItem;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BeltBlockEntity.class)
public abstract class BeltBlockEntityMixin {
	@Inject(method = "tryInsertingFromSide", at = @At("HEAD"))
	private void createphantom$alignDirectInsertedCourierLaunchStack(TransportedItemStack transportedStack,
		Direction side, boolean simulate, CallbackInfoReturnable<ItemStack> cir) {
		if (!AirCourierHelper.isCourierLaunchStack(transportedStack.stack)) {
			return;
		}

		Direction heading = AirCourierHelper.resolveBeltHeading((BeltBlockEntity) (Object) this);
		MiniPhantomItem.setHeadingAngle(transportedStack.stack, AirCourierHelper.getHeadingAngle(heading));
		transportedStack.angle = 180;
		transportedStack.sideOffset = transportedStack.prevSideOffset = transportedStack.getTargetSideOffset();
	}
}
