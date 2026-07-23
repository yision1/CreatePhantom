package com.yision.phantom.network.phantom;

import com.yision.phantom.item.miniphantom.MiniPhantomMenu;
import com.yision.phantom.network.AllPackets;
import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class MiniPhantomConfirmPacket implements ServerboundPacketPayload {
	public static final StreamCodec<RegistryFriendlyByteBuf, MiniPhantomConfirmPacket> STREAM_CODEC = StreamCodec
		.composite(ByteBufCodecs.stringUtf8(64), packet -> packet.address, MiniPhantomConfirmPacket::new);

	private final String address;

	public MiniPhantomConfirmPacket(String address) {
		this.address = address == null ? "" : address;
	}

	@Override
	public void handle(ServerPlayer sender) {
		if (!(sender.containerMenu instanceof MiniPhantomMenu menu)) {
			return;
		}
		if (menu.confirm(address)) {
			sender.closeContainer();
		}
	}

	@Override
	public PacketTypeProvider getTypeProvider() {
		return AllPackets.MINI_PHANTOM_CONFIRM;
	}
}
