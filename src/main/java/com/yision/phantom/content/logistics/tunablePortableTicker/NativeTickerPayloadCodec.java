package com.yision.phantom.content.logistics.tunablePortableTicker;

import io.netty.handler.codec.DecoderException;
import com.yision.phantom.network.AllPackets;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

public final class NativeTickerPayloadCodec {

	private NativeTickerPayloadCodec() {}

	public static StreamCodec<RegistryFriendlyByteBuf, CustomPacketPayload> forFlow(PacketFlow flow) {
		return new StreamCodec<>() {
			@Override
			public CustomPacketPayload decode(RegistryFriendlyByteBuf buffer) {
				return registeredCodec(buffer.readResourceLocation(), flow).decode(buffer);
			}

			@Override
			public void encode(RegistryFriendlyByteBuf buffer, CustomPacketPayload payload) {
				buffer.writeResourceLocation(payload.type().id());
				registeredCodec(payload.type().id(), flow).encode(buffer, payload);
			}
		};
	}

	@SuppressWarnings("unchecked")
	private static StreamCodec<RegistryFriendlyByteBuf, CustomPacketPayload> registeredCodec(ResourceLocation id,
		PacketFlow flow) {
		if (id.equals(AllPackets.TUNABLE_PORTABLE_TICKER_CONFIGURATION.getType().id())
			|| id.equals(AllPackets.TUNABLE_PORTABLE_TICKER_STOCK.getType().id()))
			throw new DecoderException("Nested portable ticker envelope: " + id);
		var codec = NetworkRegistry.getCodec(id, ConnectionProtocol.PLAY, flow);
		if (codec == null)
			throw new DecoderException("Unregistered native ticker payload: " + id);
		return (StreamCodec<RegistryFriendlyByteBuf, CustomPacketPayload>) (StreamCodec<?, ?>) codec;
	}
}
