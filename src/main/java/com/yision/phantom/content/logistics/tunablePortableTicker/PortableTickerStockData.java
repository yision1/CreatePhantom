package com.yision.phantom.content.logistics.tunablePortableTicker;

import java.util.List;
import java.util.UUID;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public final class PortableTickerStockData extends StockTickerBlockEntity {
	private final Player player;
	private TunablePortableTickerMenu menu;

	public PortableTickerStockData(Player player, ItemStack ticker, int channel, UUID network,
		BlockPos queryPosition) {
		super(AllBlockEntityTypes.STOCK_TICKER.get(), queryPosition, AllBlocks.STOCK_TICKER.getDefaultState());
		this.player = player;
		setLevel(player.level());
		activeLinks = -1;
		categories = TunablePortableTickerItem.categoriesFromChannel(ticker, channel);
		previouslyUsedAddress = network == null ? "" : TunablePortableTickerItem.loadAddress(ticker, network);
		if (network != null)
			hiddenCategoriesByPlayer.put(player.getUUID(),
				TunablePortableTickerItem.loadHiddenCategories(ticker, player.getUUID(), channel));
	}

	public static PortableTickerStockData forRequest(Player player, ItemStack ticker, int channel, UUID network,
		BlockPos queryPosition) {
		var data = new PortableTickerStockData(player, ticker, channel, network, queryPosition);
		data.behaviour = new LogisticallyLinkedBehaviour(data, false);
		data.behaviour.freqId = network;
		return data;
	}

	public static BlockPos queryPositionFor(Player player) {
		return new BlockPos(player.getBlockX(), player.level().getMinBuildHeight() - 1, player.getBlockZ());
	}

	public BlockPos getQueryPosition() {
		return super.getBlockPos();
	}

	@Override
	public BlockPos getBlockPos() {
		if (player == null)
			return super.getBlockPos();
		BlockPos queryPosition = PortableTickerPacketContext.queryPosition(this);
		return queryPosition != null ? queryPosition : player.blockPosition();
	}

	public String getAddress() {
		return previouslyUsedAddress;
	}

	public void attach(TunablePortableTickerMenu menu) {
		this.menu = menu;
	}

	public List<Integer> getHiddenCategories(UUID player) {
		return hiddenCategoriesByPlayer.getOrDefault(player, List.of());
	}

	public void resetReception() {
		newlyReceivedStockSnapshot = null;
	}

	public void advanceRefreshClock() {
		ticksSinceLastUpdate++;
	}

	public void resetRefreshClock() {
		ticksSinceLastUpdate = 0;
	}

	public int getActiveLinks() {
		return activeLinks;
	}

	public void setActiveLinks(int activeLinks) {
		this.activeLinks = activeLinks;
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public void refreshClientStockSnapshot() {
		ClientScreenStorage.request(menu, () -> super.refreshClientStockSnapshot());
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
	}

	@Override
	public void initialize() {
	}

	@Override
	public void tick() {
	}

	@Override
	public void notifyUpdate() {
	}

	@Override
	public void sendData() {
	}

	@Override
	public void setChanged() {
	}

	@Override
	public void playEffect() {
	}

	@Override
	public void invalidate() {
		resetReception();
	}

	@Override
	public void destroy() {
	}
}
