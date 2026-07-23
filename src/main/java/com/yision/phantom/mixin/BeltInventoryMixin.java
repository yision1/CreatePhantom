package com.yision.phantom.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.transport.BeltInventory;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.yision.phantom.logistics.courier.AirCourierBeltHooks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(BeltInventory.class)
public abstract class BeltInventoryMixin {
	@Shadow
	@Final
	private BeltBlockEntity belt;

	@Inject(method = "addItem(Lcom/simibubi/create/content/kinetics/belt/transport/TransportedItemStack;)V", at = @At("HEAD"))
	private void createphantom$alignCourierLaunchStack(TransportedItemStack stack, CallbackInfo ci) {
		AirCourierBeltHooks.alignCourierLaunchStack(belt, stack);
	}

	@WrapMethod(method = "eject(Lcom/simibubi/create/content/kinetics/belt/transport/TransportedItemStack;)V")
	private void createphantom$launchCourier(TransportedItemStack stack, Operation<Void> original) {
		if (!AirCourierBeltHooks.tryLaunchCourier(belt, stack)) {
			original.call(stack);
		}
	}
}
