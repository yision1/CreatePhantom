package com.yision.phantom.content.logistics.tunablePortableTicker;

import com.simibubi.create.foundation.networking.BlockEntityConfigurationPacket;
import java.util.ArrayList;
import java.util.List;
import net.createmod.catnip.net.base.ClientboundPacketPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Routes native packets only while a validated portable operation is executing. */
public final class PortableTickerPacketContext implements AutoCloseable {
	private enum Phase { SEND, SERVER, RECEIVE }
	private static final ThreadLocal<PortableTickerPacketContext> ACTIVE = new ThreadLocal<>();

	private final @Nullable PortableTickerPacketContext previous;
	private final Phase phase;
	private final TunablePortableTickerMenu menu;
	private final int requestId;
	private final PortableTickerStockData data;
	private final BlockPos position;
	private final @Nullable ServerPlayer serverPlayer;

	private PortableTickerPacketContext(Phase phase, TunablePortableTickerMenu menu, int requestId,
		PortableTickerStockData data, BlockPos position, @Nullable ServerPlayer serverPlayer) {
		this.previous = ACTIVE.get();
		this.phase = phase;
		this.menu = menu;
		this.requestId = requestId;
		this.data = data;
		this.position = position.immutable();
		this.serverPlayer = serverPlayer;
		ACTIVE.set(this);
	}

	public static PortableTickerPacketContext sending(TunablePortableTickerMenu menu, int requestId) {
		return new PortableTickerPacketContext(Phase.SEND, menu, requestId, menu.getStockData(),
			menu.getStockData().getQueryPosition(), null);
	}

	public static PortableTickerPacketContext handling(TunablePortableTickerMenu menu, ServerPlayer player,
		int requestId, PortableTickerStockData data, BlockPos position) {
		return new PortableTickerPacketContext(Phase.SERVER, menu, requestId, data, position, player);
	}

	public static PortableTickerPacketContext receiving(TunablePortableTickerMenu menu, int requestId, BlockPos position) {
		return new PortableTickerPacketContext(Phase.RECEIVE, menu, requestId, menu.getStockData(), position, null);
	}

	public static @Nullable PortableTickerStockData find(Level level, BlockPos position) {
		PortableTickerPacketContext context = ACTIVE.get();
		return context != null && context.phase != Phase.SEND && context.data.getLevel() == level
			&& context.position.equals(position) ? context.data : null;
	}

	public static boolean handles(ServerPlayer player, BlockPos position) {
		PortableTickerPacketContext context = ACTIVE.get();
		return context != null && context.phase == Phase.SERVER && context.serverPlayer == player
			&& context.position.equals(position);
	}

	public static @Nullable BlockPos queryPosition(PortableTickerStockData data) {
		PortableTickerPacketContext context = ACTIVE.get();
		return context != null && context.requestId > 0 && context.data == data ? context.position : null;
	}

	public static Packet<?> wrap(Connection connection, Packet<?> packet) {
		PortableTickerPacketContext context = ACTIVE.get();
		if (context == null)
			return packet;
		if (context.phase == Phase.SEND && packet instanceof ServerboundCustomPayloadPacket custom
			&& custom.payload() instanceof BlockEntityConfigurationPacket<?>) {
			return new ServerboundCustomPayloadPacket(new TunablePortableTickerConfigurationPacket(
				context.menu.locator, context.menu.channel, context.menu.sessionNetwork, context.requestId, custom.payload()));
		}
		if (context.phase != Phase.SERVER || context.requestId <= 0
			|| !(connection.getPacketListener() instanceof ServerGamePacketListenerImpl listener)
			|| listener.player != context.serverPlayer)
			return packet;
		if (packet instanceof ClientboundCustomPayloadPacket custom
			&& custom.payload() instanceof ClientboundPacketPayload
			&& !(custom.payload() instanceof TunablePortableTickerStockPacket)) {
			return new ClientboundCustomPayloadPacket(new TunablePortableTickerStockPacket(context.menu.locator,
				context.menu.channel, context.menu.sessionNetwork, context.requestId, context.position, custom.payload()));
		}
		if (packet instanceof ClientboundBundlePacket bundle) {
			List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
			for (var bundled : bundle.subPackets())
				packets.add(wrapClientPacket(connection, bundled));
			return new ClientboundBundlePacket(packets);
		}
		return packet;
	}

	@SuppressWarnings("unchecked")
	private static Packet<? super ClientGamePacketListener> wrapClientPacket(
		Connection connection, Packet<?> packet) {
		return (Packet<? super ClientGamePacketListener>) wrap(connection, packet);
	}

	@Override
	public void close() {
		if (previous == null)
			ACTIVE.remove();
		else
			ACTIVE.set(previous);
	}
}
