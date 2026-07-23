package com.yision.phantom.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.yision.phantom.entity.courier.AirCourierEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BeltBlock.class)
public abstract class BeltBlockMixin {
	@WrapMethod(method = "entityInside(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)V")
	private void createphantom$ignoreAirCouriers(BlockState state, Level level, BlockPos pos, Entity entity,
		Operation<Void> original) {
		if (!(entity instanceof AirCourierEntity)) {
			original.call(state, level, pos, entity);
		}
	}
}
