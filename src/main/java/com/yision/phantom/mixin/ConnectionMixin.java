package com.yision.phantom.mixin;

import com.yision.phantom.content.logistics.tunablePortableTicker.PortableTickerPacketContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Connection.class)
public abstract class ConnectionMixin {
	@ModifyVariable(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;Z)V",
		at = @At("HEAD"), argsOnly = true)
	private Packet<?> createphantom$portablePacket(Packet<?> packet) {
		return PortableTickerPacketContext.wrap((Connection) (Object) this, packet);
	}
}
