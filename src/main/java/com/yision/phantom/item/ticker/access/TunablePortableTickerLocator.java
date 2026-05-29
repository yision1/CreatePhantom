package com.yision.phantom.item.ticker.access;

import com.yision.phantom.compat.curios.CuriosTickerCompat;
import com.yision.phantom.item.ticker.TunablePortableTickerItem;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public record TunablePortableTickerLocator(Source source, int slot) {
	public enum Source {
		MAIN_HAND,
		OFF_HAND,
		INVENTORY,
		CURIOS_BODY
	}

	public static final TunablePortableTickerLocator EMPTY = new TunablePortableTickerLocator(Source.INVENTORY, -1);

	public static final StreamCodec<RegistryFriendlyByteBuf, TunablePortableTickerLocator> STREAM_CODEC =
		StreamCodec.of(
			(buffer, locator) -> TunablePortableTickerLocator.write(buffer, locator),
			buffer -> TunablePortableTickerLocator.read(buffer));

	public static TunablePortableTickerLocator fromHand(InteractionHand hand) {
		return hand == InteractionHand.OFF_HAND
			? new TunablePortableTickerLocator(Source.OFF_HAND, -1)
			: new TunablePortableTickerLocator(Source.MAIN_HAND, -1);
	}

	public static TunablePortableTickerLocator findPreferred(Player player) {
		if (isTicker(player.getMainHandItem()))
			return new TunablePortableTickerLocator(Source.MAIN_HAND, -1);
		if (isTicker(player.getOffhandItem()))
			return new TunablePortableTickerLocator(Source.OFF_HAND, -1);

		int curiosSlot = CuriosTickerCompat.findBodySlot(player);
		if (curiosSlot >= 0)
			return new TunablePortableTickerLocator(Source.CURIOS_BODY, curiosSlot);

		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (isTicker(inventory.getItem(slot)))
				return new TunablePortableTickerLocator(Source.INVENTORY, slot);
		}

		return EMPTY;
	}

	public ItemStack resolve(Player player) {
		return switch (source) {
			case MAIN_HAND -> player.getMainHandItem();
			case OFF_HAND -> player.getOffhandItem();
			case INVENTORY -> slot >= 0 && slot < player.getInventory().getContainerSize()
				? player.getInventory().getItem(slot)
				: ItemStack.EMPTY;
			case CURIOS_BODY -> CuriosTickerCompat.resolveBody(player, slot);
		};
	}

	public boolean isEmpty() {
		return source == Source.INVENTORY && slot < 0;
	}

	private static boolean isTicker(ItemStack stack) {
		return stack.getItem() instanceof TunablePortableTickerItem;
	}

	private static void write(RegistryFriendlyByteBuf buffer, TunablePortableTickerLocator locator) {
		buffer.writeVarInt(locator.source.ordinal());
		buffer.writeVarInt(locator.slot);
	}

	private static TunablePortableTickerLocator read(RegistryFriendlyByteBuf buffer) {
		int sourceOrdinal = buffer.readVarInt();
		int slot = buffer.readVarInt();
		Source[] values = Source.values();
		if (sourceOrdinal < 0 || sourceOrdinal >= values.length)
			return EMPTY;
		return new TunablePortableTickerLocator(values[sourceOrdinal], slot);
	}
}
