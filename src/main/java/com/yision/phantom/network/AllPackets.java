package com.yision.phantom.network;

import com.yision.phantom.CreatePhantom;
import com.yision.phantom.network.courier.AirCourierHudPacket;
import com.yision.phantom.network.phantom.MiniPhantomConfirmPacket;
import com.yision.phantom.content.logistics.tunablePortableTicker.TunablePortableTickerCardEditPacket;
import com.yision.phantom.content.logistics.tunablePortableTicker.TunablePortableTickerCardRefundPacket;
import com.yision.phantom.content.logistics.tunablePortableTicker.TunablePortableTickerCardSlotPacket;
import com.yision.phantom.content.logistics.tunablePortableTicker.TunablePortableTickerLockPacket;
import com.yision.phantom.content.logistics.tunablePortableTicker.TunablePortableTickerNetworkStatePacket;
import com.yision.phantom.content.logistics.tunablePortableTicker.TunablePortableTickerOpenPacket;
import com.yision.phantom.content.logistics.tunablePortableTicker.TunablePortableTickerSelectChannelPacket;
import com.yision.phantom.content.logistics.tunablePortableTicker.TunablePortableTickerConfigurationPacket;
import com.yision.phantom.content.logistics.tunablePortableTicker.TunablePortableTickerStockPacket;
import java.util.Locale;
import net.createmod.catnip.net.base.BasePacketPayload;
import net.createmod.catnip.net.base.CatnipPacketRegistry;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;

public enum AllPackets implements BasePacketPayload.PacketTypeProvider {
	TUNABLE_PORTABLE_TICKER_CONFIGURATION(TunablePortableTickerConfigurationPacket.class,
		TunablePortableTickerConfigurationPacket.STREAM_CODEC),
	TUNABLE_PORTABLE_TICKER_STOCK(TunablePortableTickerStockPacket.class, TunablePortableTickerStockPacket.STREAM_CODEC),
	TUNABLE_PORTABLE_TICKER_OPEN(TunablePortableTickerOpenPacket.class, TunablePortableTickerOpenPacket.STREAM_CODEC),
	MINI_PHANTOM_CONFIRM(MiniPhantomConfirmPacket.class, MiniPhantomConfirmPacket.STREAM_CODEC),
	TUNABLE_PORTABLE_TICKER_CARD_EDIT(TunablePortableTickerCardEditPacket.class, TunablePortableTickerCardEditPacket.STREAM_CODEC),
	TUNABLE_PORTABLE_TICKER_CARD_REFUND(TunablePortableTickerCardRefundPacket.class, TunablePortableTickerCardRefundPacket.STREAM_CODEC),
	TUNABLE_PORTABLE_TICKER_CARD_SLOT(TunablePortableTickerCardSlotPacket.class, TunablePortableTickerCardSlotPacket.STREAM_CODEC),
	TUNABLE_PORTABLE_TICKER_SELECT_CHANNEL(TunablePortableTickerSelectChannelPacket.class, TunablePortableTickerSelectChannelPacket.STREAM_CODEC),
	AIR_COURIER_HUD(AirCourierHudPacket.class, AirCourierHudPacket.STREAM_CODEC),
	TUNABLE_PORTABLE_TICKER_LOCK(TunablePortableTickerLockPacket.class, TunablePortableTickerLockPacket.STREAM_CODEC),
	TUNABLE_PORTABLE_TICKER_NETWORK_STATE(TunablePortableTickerNetworkStatePacket.class,
		TunablePortableTickerNetworkStatePacket.STREAM_CODEC);

	private final CatnipPacketRegistry.PacketType<?> type;

	<T extends BasePacketPayload> AllPackets(Class<T> payloadClass,
		StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec) {
		String name = name().toLowerCase(Locale.ROOT);
		type = new CatnipPacketRegistry.PacketType<>(new CustomPacketPayload.Type<>(CreatePhantom.asResource(name)),
			payloadClass, streamCodec);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends CustomPacketPayload> CustomPacketPayload.Type<T> getType() {
		return (CustomPacketPayload.Type<T>) type.type();
	}

	public static void register() {
		CatnipPacketRegistry packetRegistry = new CatnipPacketRegistry(CreatePhantom.MODID, 3);
		for (AllPackets packet : values())
			packetRegistry.registerPacket(packet.type);
		packetRegistry.registerAllPackets();
	}
}
