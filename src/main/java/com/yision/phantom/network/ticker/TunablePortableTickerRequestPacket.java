package com.yision.phantom.network.ticker;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.yision.phantom.item.ticker.access.ItemTunablePortableTickerAccess;
import com.yision.phantom.item.ticker.access.TunablePortableTickerLocator;
import com.yision.phantom.network.AllPackets;
import java.util.List;
import java.util.UUID;
import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public record TunablePortableTickerRequestPacket(TunablePortableTickerLocator locator, int channel, UUID sessionNetwork, int requestId)
	implements ServerboundPacketPayload {
	private static final int STOCK_RESPONSE_BATCH_SIZE = 128;

	public static final StreamCodec<RegistryFriendlyByteBuf, TunablePortableTickerRequestPacket> STREAM_CODEC =
		StreamCodec.composite(TunablePortableTickerLocator.STREAM_CODEC, TunablePortableTickerRequestPacket::locator,
			ByteBufCodecs.VAR_INT, TunablePortableTickerRequestPacket::channel,
			UUIDUtil.STREAM_CODEC, TunablePortableTickerRequestPacket::sessionNetwork,
			ByteBufCodecs.VAR_INT, TunablePortableTickerRequestPacket::requestId,
			TunablePortableTickerRequestPacket::new);

	@Override
	public void handle(ServerPlayer player) {
		if (player == null)
			return;

		ItemTunablePortableTickerAccess access = ItemTunablePortableTickerAccess.resolve(player, locator, channel, sessionNetwork);
		InventorySummary summary = access.fetchAccurateSummary();
		List<BigItemStack> allStacks = summary.getStacksByCount();
		if (allStacks.isEmpty()) {
			sendStockSnapshot(player, List.of(), true);
			return;
		}

		int nextStart = 0;
		while (nextStart < allStacks.size()) {
			int nextEnd = Math.min(nextStart + STOCK_RESPONSE_BATCH_SIZE, allStacks.size());
			sendStockSnapshot(player, List.copyOf(allStacks.subList(nextStart, nextEnd)), nextEnd == allStacks.size());
			nextStart = nextEnd;
		}
	}

	private void sendStockSnapshot(ServerPlayer player, List<BigItemStack> stacks, boolean complete) {
		CatnipServices.NETWORK.sendToClient(player,
			new TunablePortableTickerStockPacket(locator, channel, sessionNetwork, requestId, stacks, complete));
	}

	@Override
	public PacketTypeProvider getTypeProvider() {
		return AllPackets.TUNABLE_PORTABLE_TICKER_REQUEST;
	}
}
