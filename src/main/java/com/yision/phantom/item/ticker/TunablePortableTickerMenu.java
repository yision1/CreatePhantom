package com.yision.phantom.item.ticker;

import com.yision.phantom.item.ticker.TunablePortableTickerItem;
import com.yision.phantom.item.ticker.access.TunablePortableTickerLocator;
import com.yision.phantom.registry.AllMenuTypes;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;

public class TunablePortableTickerMenu extends AbstractContainerMenu {
	public Object screenReference;
	public final Player player;
	public final Inventory playerInventory;
	public final TunablePortableTickerLocator locator;
	public int channel;
	public UUID sessionNetwork;
	public final ItemStack tickerStack;
	public final List<ItemStack> cards;
	public final List<ItemStack> categories;
	public final String initialAddress;
	public final Set<Integer> hiddenCategories;

	public TunablePortableTickerMenu(int id, Inventory playerInventory) {
		this(id, playerInventory, TunablePortableTickerLocator.findPreferred(playerInventory.player), 0);
	}

	private TunablePortableTickerMenu(int id, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
		this(id, playerInventory,
			TunablePortableTickerLocator.STREAM_CODEC.decode(extraData),
			ByteBufCodecs.INT.decode(extraData));
	}

	public TunablePortableTickerMenu(int id, Inventory playerInventory, TunablePortableTickerLocator locator,
		int channel) {
		this(id, playerInventory, locator, channel,
			TunablePortableTickerItem.networkFromChannel(locator.resolve(playerInventory.player), channel));
	}

	public TunablePortableTickerMenu(int id, Inventory playerInventory, TunablePortableTickerLocator locator,
		int channel, UUID sessionNetwork) {
		super(AllMenuTypes.TUNABLE_PORTABLE_TICKER.get(), id);
		this.playerInventory = playerInventory;
		this.player = playerInventory.player;
		this.locator = locator;
		this.channel = channel;
		this.sessionNetwork = sessionNetwork;

		ItemStack resolved = locator.resolve(player);
		this.tickerStack = resolved.getItem() instanceof TunablePortableTickerItem ? resolved : ItemStack.EMPTY;
		this.cards = TunablePortableTickerItem.getCards(resolved);

		if (tickerStack.isEmpty() || sessionNetwork == null) {
			this.categories = List.of();
			this.initialAddress = "";
			this.hiddenCategories = Set.of();
		} else {
			this.categories = TunablePortableTickerItem.categoriesFromChannel(tickerStack, channel);
			this.initialAddress = TunablePortableTickerItem.loadAddress(tickerStack, sessionNetwork);
			this.hiddenCategories = new HashSet<>(
				TunablePortableTickerItem.loadHiddenCategories(tickerStack, player.getUUID(), sessionNetwork));
		}

		addPlayerSlots(-1000, 0);
	}

	protected void addPlayerSlots(int x, int y) {
		for (int row = 0; row < 3; ++row)
			for (int col = 0; col < 9; ++col)
				addSlot(new Slot(playerInventory, col + row * 9 + 9, x + col * 18, y + row * 18));
		for (int hotbarSlot = 0; hotbarSlot < 9; ++hotbarSlot)
			addSlot(new Slot(playerInventory, hotbarSlot, x + hotbarSlot * 18, y + 58));
	}

	@Override
	public boolean stillValid(Player player) {
		ItemStack resolved = locator.resolve(player);
		if (!(resolved.getItem() instanceof TunablePortableTickerItem))
			return false;
		UUID network = TunablePortableTickerItem.networkFromChannel(resolved, channel);
		if (sessionNetwork == null || !sessionNetwork.equals(network))
			return false;
		return !(player instanceof ServerPlayer serverPlayer)
			|| TunablePortableTickerSession.mayInteract(serverPlayer, sessionNetwork);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	public boolean selectChannel(ServerPlayer player, int newChannel) {
		if (newChannel < 0 || newChannel >= TunablePortableTickerItem.MAX_CHANNELS)
			return false;
		ItemStack resolved = locator.resolve(player);
		if (!(resolved.getItem() instanceof TunablePortableTickerItem))
			return false;
		UUID newNetwork = TunablePortableTickerItem.networkFromChannel(resolved, newChannel);
		if (newNetwork == null || !TunablePortableTickerSession.mayInteract(player, newNetwork))
			return false;
		channel = newChannel;
		sessionNetwork = newNetwork;
		TunablePortableTickerItem.setSelectedChannel(resolved, newChannel);
		return true;
	}

	public static void writeMenuData(RegistryFriendlyByteBuf buffer, TunablePortableTickerLocator locator,
		int channel) {
		TunablePortableTickerLocator.STREAM_CODEC.encode(buffer, locator);
		ByteBufCodecs.INT.encode(buffer, channel);
	}

	public static TunablePortableTickerMenu createOnClient(int id, Inventory playerInventory,
		RegistryFriendlyByteBuf extraData) {
		return new TunablePortableTickerMenu(id, playerInventory, extraData);
	}
}
