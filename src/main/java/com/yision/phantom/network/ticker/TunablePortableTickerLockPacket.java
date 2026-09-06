package com.yision.phantom.network.ticker;

import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.packagerLink.LogisticsNetwork;
import com.yision.phantom.item.ticker.TunablePortableTickerMenu;
import com.yision.phantom.item.ticker.access.TunablePortableTickerLocator;
import com.yision.phantom.network.AllPackets;
import java.util.UUID;
import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;

public record TunablePortableTickerLockPacket(TunablePortableTickerLocator locator, int channel,
	UUID sessionNetwork, boolean locked) implements ServerboundPacketPayload {
	public static final StreamCodec<RegistryFriendlyByteBuf, TunablePortableTickerLockPacket> STREAM_CODEC =
		StreamCodec.composite(
			TunablePortableTickerLocator.STREAM_CODEC, TunablePortableTickerLockPacket::locator,
			ByteBufCodecs.VAR_INT, TunablePortableTickerLockPacket::channel,
			UUIDUtil.STREAM_CODEC, TunablePortableTickerLockPacket::sessionNetwork,
			ByteBufCodecs.BOOL, TunablePortableTickerLockPacket::locked,
			TunablePortableTickerLockPacket::new);

	@Override
	public void handle(ServerPlayer player) {
		if (!(player.containerMenu instanceof TunablePortableTickerMenu menu)
			|| !menu.locator.equals(locator) || menu.channel != channel
			|| !sessionNetwork.equals(menu.sessionNetwork)
			|| !Create.LOGISTICS.isLockable(sessionNetwork)
			|| !Create.LOGISTICS.mayAdministrate(sessionNetwork, player))
			return;

		LogisticsNetwork network = Create.LOGISTICS.logisticsNetworks.get(sessionNetwork);
		if (network == null)
			return;
		network.locked = locked;
		menu.isLocked = locked;
		Create.LOGISTICS.markDirty();
	}

	@Override
	public PacketTypeProvider getTypeProvider() {
		return AllPackets.TUNABLE_PORTABLE_TICKER_LOCK;
	}
}
