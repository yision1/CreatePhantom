package com.yision.phantom.mixin;

import java.util.Iterator;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.transport.BeltInventory;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.yision.phantom.logistics.courier.AirCourierBeltHooks;

@Mixin(BeltInventory.class)
public abstract class BeltInventoryMixin {
	@Shadow
	@Final
	private BeltBlockEntity belt;

	@Inject(method = "addItem(Lcom/simibubi/create/content/kinetics/belt/transport/TransportedItemStack;)V",
		at = @At("HEAD"))
	private void createphantom$alignCourierLaunchStack(TransportedItemStack stack, CallbackInfo ci) {
		AirCourierBeltHooks.alignCourierLaunchStack(belt, stack);
	}

	@WrapOperation(method = "tick", at = @At(value = "INVOKE",
		target = "Lcom/simibubi/create/content/kinetics/belt/transport/BeltFunnelInteractionHandler;checkForFunnels"
			+ "(Lcom/simibubi/create/content/kinetics/belt/transport/BeltInventory;"
			+ "Lcom/simibubi/create/content/kinetics/belt/transport/TransportedItemStack;F)Z"))
	private boolean createphantom$launchCourierAtEnd(BeltInventory inventory, TransportedItemStack stack,
		float nextOffset, Operation<Boolean> original, @Local Iterator<TransportedItemStack> iterator) {
		if (AirCourierBeltHooks.tryLaunchCourierAtEnd(belt, stack, nextOffset)) {
			iterator.remove();
			belt.notifyUpdate();
			return true;
		}
		return original.call(inventory, stack, nextOffset);
	}

	@Inject(method = "eject(Lcom/simibubi/create/content/kinetics/belt/transport/TransportedItemStack;)V",
		at = @At("RETURN"))
	private void createphantom$onCourierEjected(TransportedItemStack stack, CallbackInfo ci) {
		AirCourierBeltHooks.onCourierEjected(belt, stack);
	}
}
