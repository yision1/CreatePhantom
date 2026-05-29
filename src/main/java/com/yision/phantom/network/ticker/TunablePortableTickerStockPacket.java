package com.yision.phantom.network.ticker;

import com.simibubi.create.content.logistics.BigItemStack;
import com.yision.phantom.item.ticker.access.TunablePortableTickerLocator;
import com.yision.phantom.item.ticker.ClientScreenStorage;
import com.yision.phantom.network.AllPackets;
import java.util.List;
import java.util.UUID;
import net.createmod.catnip.codecs.stream.CatnipStreamCodecBuilders;
import net.createmod.catnip.net.base.ClientboundPacketPayload;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class TunablePortableTickerStockPacket implements ClientboundPacketPayload {
	public static final StreamCodec<RegistryFriendlyByteBuf, TunablePortableTickerStockPacket> STREAM_CODEC = StreamCodec
		.composite(TunablePortableTickerLocator.STREAM_CODEC, packet -> packet.locator,
			ByteBufCodecs.VAR_INT, packet -> packet.channel,
			UUIDUtil.STREAM_CODEC, packet -> packet.sessionNetwork,
			ByteBufCodecs.VAR_INT, packet -> packet.requestId,
			CatnipStreamCodecBuilders.list(BigItemStack.STREAM_CODEC), packet -> packet.stacks,
			ByteBufCodecs.BOOL, packet -> packet.last, TunablePortableTickerStockPacket::new);

	private final TunablePortableTickerLocator locator;
	private final int channel;
	private final UUID sessionNetwork;
	private final int requestId;
	private final List<BigItemStack> stacks;
	private final boolean last;

	public TunablePortableTickerStockPacket(TunablePortableTickerLocator locator, int channel, UUID sessionNetwork, int requestId,
		List<BigItemStack> stacks, boolean last) {
		this.locator = locator;
		this.channel = channel;
		this.sessionNetwork = sessionNetwork;
		this.requestId = requestId;
		this.stacks = stacks;
		this.last = last;
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public void handle(LocalPlayer player) {
		ClientScreenStorage.receiveChunk(locator, channel, sessionNetwork, requestId, stacks, last);
	}

	@Override
	public PacketTypeProvider getTypeProvider() {
		return AllPackets.TUNABLE_PORTABLE_TICKER_STOCK;
	}
}
