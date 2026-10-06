package com.yision.phantom.content.logistics.tunablePortableTicker;

import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestMenu;
import com.yision.phantom.registry.AllMenuTypes;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;

public class TunablePortableTickerMenu extends StockKeeperRequestMenu {
	public final TunablePortableTickerLocator locator;
	public final int channel;
	public final UUID sessionNetwork;
	public final ItemStack tickerStack;
	public final List<ItemStack> cards;
	public boolean switchingChannel;

	private TunablePortableTickerMenu(int id, Inventory inventory, RegistryFriendlyByteBuf extraData) {
		this(id, inventory, readMenuData(extraData));
	}

	private TunablePortableTickerMenu(int id, Inventory inventory, MenuData data) {
		this(id, inventory, data.locator, data.channel,
			TunablePortableTickerItem.networkFromChannel(data.stack, data.channel), data.queryPosition, data.stack);
		setNetworkState(data.admin, data.locked);
	}

	public TunablePortableTickerMenu(int id, Inventory inventory, TunablePortableTickerLocator locator,
		int channel, UUID sessionNetwork) {
		this(id, inventory, locator, channel, sessionNetwork, PortableTickerStockData.queryPositionFor(inventory.player));
	}

	public TunablePortableTickerMenu(int id, Inventory inventory, TunablePortableTickerLocator locator,
		int channel, UUID sessionNetwork, BlockPos queryPosition) {
		this(id, inventory, locator, channel, sessionNetwork, queryPosition, locator.resolve(inventory.player));
	}

	private TunablePortableTickerMenu(int id, Inventory inventory, TunablePortableTickerLocator locator,
		int channel, UUID sessionNetwork, BlockPos queryPosition, ItemStack stack) {
		super(AllMenuTypes.TUNABLE_PORTABLE_TICKER.get(), id, inventory,
			new PortableTickerStockData(inventory.player, stack, channel, sessionNetwork, queryPosition));
		this.locator = locator;
		this.channel = channel;
		this.sessionNetwork = sessionNetwork;
		refreshNetworkState(player);
		tickerStack = stack.getItem() instanceof TunablePortableTickerItem ? stack : ItemStack.EMPTY;
		cards = TunablePortableTickerItem.getCards(stack);
		getStockData().attach(this);
	}

	public PortableTickerStockData getStockData() {
		return (PortableTickerStockData) contentHolder;
	}

	public TunablePortableTickerLocator getLocator() {
		return locator;
	}

	public int getChannel() {
		return channel;
	}

	public UUID getSessionNetwork() {
		return sessionNetwork;
	}

	public ItemStack getTickerStack() {
		return tickerStack;
	}

	public List<ItemStack> getCards() {
		return cards;
	}

	public boolean isAdmin() {
		return ((StockKeeperRequestMenuAccess) this).createphantom$isAdmin();
	}

	public boolean isLocked() {
		return ((StockKeeperRequestMenuAccess) this).createphantom$isLocked();
	}

	public void setNetworkState(boolean admin, boolean locked) {
		StockKeeperRequestMenuAccess state = (StockKeeperRequestMenuAccess) this;
		state.createphantom$setAdmin(admin);
		state.createphantom$setLocked(locked);
	}

	@Override
	public boolean stillValid(Player player) {
		if (getStockData().getLevel() != player.level())
			return false;
		ItemStack resolved = locator.resolve(player);
		if (!(resolved.getItem() instanceof TunablePortableTickerItem))
			return false;
		UUID network = TunablePortableTickerItem.networkFromChannel(resolved, channel);
		if (sessionNetwork == null || !sessionNetwork.equals(network))
			return false;
		return !(player instanceof ServerPlayer serverPlayer)
			|| Create.LOGISTICS.mayInteract(sessionNetwork, serverPlayer);
	}

	public boolean selectChannel(ServerPlayer player, int newChannel) {
		if (newChannel < 0 || newChannel >= TunablePortableTickerItem.MAX_CHANNELS)
			return false;
		ItemStack resolved = locator.resolve(player);
		if (!(resolved.getItem() instanceof TunablePortableTickerItem))
			return false;
		UUID newNetwork = TunablePortableTickerItem.networkFromChannel(resolved, newChannel);
		if (newNetwork == null || !Create.LOGISTICS.mayInteract(newNetwork, player))
			return false;
		TunablePortableTickerItem.setSelectedChannel(resolved, newChannel);
		return true;
	}

	public static void writeMenuData(RegistryFriendlyByteBuf buffer, TunablePortableTickerLocator locator,
		int channel, ServerPlayer player, BlockPos queryPosition) {
		TunablePortableTickerLocator.STREAM_CODEC.encode(buffer, locator);
		ByteBufCodecs.INT.encode(buffer, channel);
		BlockPos.STREAM_CODEC.encode(buffer, queryPosition);
		ItemStack stack = locator.resolve(player);
		ItemStack.STREAM_CODEC.encode(buffer, stack);
		UUID network = TunablePortableTickerItem.networkFromChannel(stack, channel);
		buffer.writeBoolean(network != null && Create.LOGISTICS.isLockable(network)
			&& Create.LOGISTICS.mayAdministrate(network, player));
		buffer.writeBoolean(network != null && Create.LOGISTICS.isLocked(network));
	}

	private static MenuData readMenuData(RegistryFriendlyByteBuf buffer) {
		return new MenuData(TunablePortableTickerLocator.STREAM_CODEC.decode(buffer), ByteBufCodecs.INT.decode(buffer),
			BlockPos.STREAM_CODEC.decode(buffer), ItemStack.STREAM_CODEC.decode(buffer), buffer.readBoolean(), buffer.readBoolean());
	}

	private record MenuData(TunablePortableTickerLocator locator, int channel, BlockPos queryPosition,
		ItemStack stack, boolean admin, boolean locked) {}

	private void refreshNetworkState(Player player) {
		setNetworkState(sessionNetwork != null && Create.LOGISTICS.isLockable(sessionNetwork)
			&& Create.LOGISTICS.mayAdministrate(sessionNetwork, player),
			sessionNetwork != null && Create.LOGISTICS.isLocked(sessionNetwork));
	}

	public static TunablePortableTickerMenu createOnClient(int id, Inventory inventory,
		RegistryFriendlyByteBuf extraData) {
		return new TunablePortableTickerMenu(id, inventory, extraData);
	}
}
