package com.yision.phantom.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yision.phantom.content.logistics.tunablePortableTicker.PortableTickerPacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Level.class)
public abstract class LevelMixin {
	@WrapMethod(method = "getBlockEntity")
	private BlockEntity createphantom$portableData(BlockPos pos, Operation<BlockEntity> original) {
		BlockEntity data = PortableTickerPacketContext.find((Level) (Object) this, pos);
		return data != null ? data : original.call(pos);
	}
}
