package com.yision.phantom.item.ticker;

import com.simibubi.create.content.logistics.BigItemStack;
import com.yision.phantom.network.ticker.TunablePortableTickerRequestPacket;
import com.yision.phantom.item.ticker.access.TunablePortableTickerLocator;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.createmod.catnip.platform.CatnipServices;

public final class ClientScreenStorage {
	public static final int TICKS_BETWEEN_UPDATES = 100;

	private static List<BigItemStack> stacks = List.of();
	private static List<BigItemStack> collectionBuffer = new ArrayList<>();
	private static TunablePortableTickerLocator activeLocator = TunablePortableTickerLocator.EMPTY;
	private static int activeChannel;
	private static UUID activeNetwork;
	private static int activeRequestId;
	private static int ticks;
	private static int version;

	private ClientScreenStorage() {}

	public static void tick(TunablePortableTickerLocator locator, int channel, UUID sessionNetwork) {
		if (++ticks > TICKS_BETWEEN_UPDATES)
			manualUpdate(locator, channel, sessionNetwork);
	}

	public static void manualUpdate(TunablePortableTickerLocator locator, int channel, UUID sessionNetwork) {
		boolean changedSession = !locator.equals(activeLocator) || channel != activeChannel
			|| !java.util.Objects.equals(sessionNetwork, activeNetwork);
		activeLocator = locator;
		activeChannel = channel;
		activeNetwork = sessionNetwork;
		activeRequestId++;
		ticks = 0;
		collectionBuffer = new ArrayList<>();
		if (changedSession) {
			stacks = List.of();
			version++;
		}
		if (sessionNetwork == null)
			return;
		CatnipServices.NETWORK.sendToServer(new TunablePortableTickerRequestPacket(locator, channel, sessionNetwork, activeRequestId));
	}

	public static void receiveChunk(TunablePortableTickerLocator locator, int channel, UUID sessionNetwork, int requestId,
		List<BigItemStack> chunks, boolean last) {
		if (!locator.equals(activeLocator) || channel != activeChannel
			|| !java.util.Objects.equals(sessionNetwork, activeNetwork) || requestId != activeRequestId)
			return;
		collectionBuffer.addAll(chunks);
		if (!last)
			return;
		stacks = List.copyOf(collectionBuffer);
		collectionBuffer = new ArrayList<>();
		version++;
	}

	public static List<BigItemStack> getStacks() {
		return stacks;
	}

	public static int getVersion() {
		return version;
	}
}
