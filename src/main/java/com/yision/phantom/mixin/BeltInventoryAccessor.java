package com.yision.phantom.mixin;

import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.transport.BeltInventory;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BeltInventory.class)
public interface BeltInventoryAccessor {
	@Accessor("belt")
	BeltBlockEntity createphantom$getBelt();

	@Accessor("beltMovementPositive")
	boolean createphantom$isBeltMovementPositive();
}
