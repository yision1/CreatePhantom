package com.yision.phantom.mixin;

import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.yision.phantom.entity.courier.AirCourierEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BeltBlock.class)
public abstract class BeltBlockMixin {
	@Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
	private void createphantom$ignoreAirCouriers(BlockState state, Level level, BlockPos pos, Entity entity,
		CallbackInfo ci) {
		if (entity instanceof AirCourierEntity) {
			ci.cancel();
		}
	}
}
