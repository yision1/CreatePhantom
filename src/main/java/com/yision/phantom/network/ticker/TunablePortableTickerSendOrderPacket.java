package com.yision.phantom.network.ticker;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.logistics.packagerLink.WiFiEffectPacket;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.foundation.utility.AdventureUtil;
import com.yision.phantom.item.ticker.access.ItemTunablePortableTickerAccess;
import com.yision.phantom.item.ticker.access.TunablePortableTickerLocator;
import com.yision.phantom.item.ticker.TunablePortableTickerSession;
import com.yision.phantom.network.AllPackets;
import java.util.UUID;
import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public class TunablePortableTickerSendOrderPacket implements ServerboundPacketPayload {
	public static final StreamCodec<RegistryFriendlyByteBuf, TunablePortableTickerSendOrderPacket> STREAM_CODEC =
		StreamCodec.composite(TunablePortableTickerLocator.STREAM_CODEC, packet -> packet.locator,
			ByteBufCodecs.VAR_INT, packet -> packet.channel,
			UUIDUtil.STREAM_CODEC, packet -> packet.sessionNetwork,
			PackageOrderWithCrafts.STREAM_CODEC, packet -> packet.order,
			ByteBufCodecs.stringUtf8(TunablePortableTickerSession.MAX_ADDRESS_LENGTH),
			packet -> packet.address, TunablePortableTickerSendOrderPacket::new);

	private final TunablePortableTickerLocator locator;
	private final int channel;
	private final UUID sessionNetwork;
	private final PackageOrderWithCrafts order;
	private final String address;

	public TunablePortableTickerSendOrderPacket(TunablePortableTickerLocator locator, int channel, UUID sessionNetwork,
		PackageOrderWithCrafts order, String address) {
		this.locator = locator;
		this.channel = channel;
		this.sessionNetwork = sessionNetwork;
		this.order = order;
		this.address = address;
	}

	protected void applySettings(ServerPlayer player) {
		ItemTunablePortableTickerAccess access =
			TunablePortableTickerSession.resolve(player, locator, channel, sessionNetwork);
		if (access == null) {
			return;
		}

		String sanitizedAddress = TunablePortableTickerSession.sanitizeAddress(address);
		access.saveAddress(sanitizedAddress);
		if (order.isEmpty())
			return;
		if (!TunablePortableTickerSession.isOrderWithinBounds(order)) {
			return;
		}

		AllSoundEvents.STOCK_TICKER_REQUEST.playOnServer(player.level(), player.blockPosition());
		AllAdvancements.STOCK_TICKER.awardTo(player);
		WiFiEffectPacket.send(player.level(), player.blockPosition());

		access.submitOrder(order, sanitizedAddress, player);
	}

	@Override
	public void handle(ServerPlayer player) {
		if (player == null || player.isSpectator() || AdventureUtil.isAdventure(player))
			return;
		Level world = player.level();
		if (!world.isLoaded(player.blockPosition()))
			return;
		applySettings(player);
	}

	@Override
	public PacketTypeProvider getTypeProvider() {
		return AllPackets.TUNABLE_PORTABLE_TICKER_SEND_ORDER;
	}
}
