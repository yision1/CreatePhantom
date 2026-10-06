package com.yision.phantom.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.foundation.networking.BlockEntityConfigurationPacket;
import com.yision.phantom.content.logistics.tunablePortableTicker.StockTickerConfigurationPacketAccess;
import com.yision.phantom.content.logistics.tunablePortableTicker.PortableTickerPacketContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = BlockEntityConfigurationPacket.class, remap = false)
public abstract class BlockEntityConfigurationPacketMixin implements StockTickerConfigurationPacketAccess {
	@Override
	@Accessor("pos")
	public abstract BlockPos createphantom$getPosition();

	@WrapOperation(method = "handle", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/Level;isLoaded(Lnet/minecraft/core/BlockPos;)Z"))
	private boolean createphantom$loadedPortableData(Level level, BlockPos pos, Operation<Boolean> original) {
		return PortableTickerPacketContext.find(level, pos) != null || original.call(level, pos);
	}

	@WrapOperation(method = "handle", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/server/level/ServerPlayer;canInteractWithBlock(Lnet/minecraft/core/BlockPos;D)Z"))
	private boolean createphantom$portableRange(ServerPlayer player, BlockPos pos, double range, Operation<Boolean> original) {
		return PortableTickerPacketContext.handles(player, pos) || original.call(player, pos, range);
	}
}
