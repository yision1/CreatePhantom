package com.yision.phantom.content.logistics.tunablePortableTicker;

import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import com.yision.phantom.CreatePhantom;
import com.yision.phantom.item.storagecard.StorageChannelExtensionCardItem;
import java.util.ArrayList;
import java.util.List;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class TunablePortableTickerScreen extends StockKeeperRequestScreen {
	private static final ResourceLocation CHANNELS =
		CreatePhantom.asResource("textures/gui/tunable_portable_ticker_channels.png");
	private static final int CHANNELS_TEXTURE_WIDTH = 128;
	private static final int CHANNELS_TEXTURE_HEIGHT = 128;
	private static final int CHANNEL_SEGMENT_X = 15;
	private static final int CHANNEL_ACTIVE_SEGMENT_X = 54;
	private static final int CHANNEL_SEGMENT_WIDTH = 29;
	private static final int CHANNEL_TOP_Y = 11;
	private static final int CHANNEL_TOP_HEIGHT = 26;
	private static final int CHANNEL_MIDDLE_Y = 40;
	private static final int CHANNEL_MIDDLE_HEIGHT = 23;
	private static final int CHANNEL_BOTTOM_Y = 66;
	private static final int CHANNEL_BOTTOM_HEIGHT = 31;
	private static final int STOCK_KEEPER_VISIBLE_RIGHT = 231;

	private final List<Integer> visibleChannels = new ArrayList<>();
	private int channelBarX;
	private int channelBarY;
	private boolean savedOnClose;

	public TunablePortableTickerScreen(TunablePortableTickerMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		for (int slot = 0; slot < menu.getCards().size(); slot++)
			if (TunablePortableTickerItem.isValidCard(menu.getCards().get(slot)))
				visibleChannels.add(slot);
	}

	@Override
	public TunablePortableTickerMenu getMenu() {
		return (TunablePortableTickerMenu) super.getMenu();
	}

	@Override
	protected void init() {
		super.init();
		savedOnClose = false;
		channelBarX = getGuiLeft() - 15 + STOCK_KEEPER_VISIBLE_RIGHT;
		channelBarY = getGuiTop() + 30;
		getMenu().getStockData().refreshClientStockSnapshot();
	}

	public PortableTickerAddressEditBox createAddressBox(Font font, int x, int y, int width, int height,
		boolean anchorToBottom) {
		ItemStack card = TunablePortableTickerItem.getCard(getMenu().getTickerStack(), getMenu().getChannel());
		List<String> addresses = card.isEmpty() ? List.of() : StorageChannelExtensionCardItem.loadAddressesFromStack(card);
		return new PortableTickerAddressEditBox(this, font, x, y, width, height, anchorToBottom,
			"@" + getMenu().player.getName().getString(), addresses);
	}

	@Override
	protected void containerTick() {
		if (!getMenu().stillValid(getMenu().player)) {
			getMenu().player.closeContainer();
			return;
		}
		if (getMenu().switchingChannel)
			return;
		getMenu().getStockData().advanceRefreshClock();
		super.containerTick();
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
		super.renderBg(graphics, partialTicks, mouseX, mouseY);
		if (this != minecraft.screen)
			return;
		graphics.pose().pushPose();
		graphics.pose().translate(getGuiLeft() - 50, getGuiTop() + imageHeight - 70, -100);
		graphics.pose().scale(3.5f, 3.5f, 3.5f);
		if (!getMenu().getTickerStack().isEmpty())
			GuiGameElement.of(getMenu().getTickerStack().getItem()).render(graphics);
		graphics.pose().popPose();
		renderChannelBar(graphics);
	}

	@Override
	protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
		super.renderForeground(graphics, mouseX, mouseY, partialTicks);
		int hoveredChannel = getClickedChannel(mouseX, mouseY);
		if (hoveredChannel != -1)
			renderChannelTooltip(graphics, hoveredChannel, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (getMenu().switchingChannel)
			return true;
		int clickedChannel = getClickedChannel(mouseX, mouseY);
		if (clickedChannel != -1 && button == 0) {
			int slot = visibleChannels.get(clickedChannel);
			if (slot != getMenu().getChannel()) {
				switchChannel(slot);
				playUiSound(SoundEvents.UI_BUTTON_CLICK.value(), 1, 1);
			}
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private void switchChannel(int slot) {
		saveSession();
		getMenu().switchingChannel = true;
		ClientScreenStorage.close(getMenu());
		CatnipServices.NETWORK.sendToServer(new TunablePortableTickerSelectChannelPacket(getMenu().getLocator(), slot));
	}

	private StockKeeperRequestScreenAccess nativeState() {
		return (StockKeeperRequestScreenAccess) this;
	}

	private boolean hasOpenSession() {
		return !getMenu().switchingChannel && minecraft.player != null && minecraft.player.containerMenu == getMenu()
			&& getMenu().stillValid(minecraft.player);
	}

	private void saveSession() {
		if (hasOpenSession()) {
			TunablePortableTickerItem.saveHiddenCategories(getMenu().getTickerStack(), getMenu().player.getUUID(),
				getMenu().getSessionNetwork(), new ArrayList<>(nativeState().createphantom$getHiddenCategories()));
			super.removed();
		}
	}

	private void saveRemovedSession() {
		if (!savedOnClose) {
			saveSession();
			savedOnClose = true;
		}
	}

	@Override
	public void onClose() {
		saveRemovedSession();
		super.onClose();
	}

	@Override
	public void removed() {
		// JEI may temporarily replace the screen while retaining the same menu.
		saveRemovedSession();
		ClientScreenStorage.close(getMenu());
	}

	@Override
	public List<Rect2i> getExtraAreas() {
		List<Rect2i> areas = new ArrayList<>(super.getExtraAreas());
		areas.add(new Rect2i(getGuiLeft() - 50, getGuiTop() + imageHeight - 70, 56, 56));
		if (visibleChannels.size() >= 2)
			areas.add(new Rect2i(channelBarX, channelBarY, CHANNEL_SEGMENT_WIDTH, getChannelBarHeight()));
		return areas;
	}

	private void renderChannelBar(GuiGraphics graphics) {
		int count = visibleChannels.size();
		if (count < 2)
			return;
		for (int i = 0; i < count; i++) {
			int sourceX = visibleChannels.get(i) == getMenu().getChannel() ? CHANNEL_ACTIVE_SEGMENT_X : CHANNEL_SEGMENT_X;
			int sourceY = i == 0 ? CHANNEL_TOP_Y : i == count - 1 ? CHANNEL_BOTTOM_Y : CHANNEL_MIDDLE_Y;
			int sourceHeight = i == 0 ? CHANNEL_TOP_HEIGHT : i == count - 1 ? CHANNEL_BOTTOM_HEIGHT : CHANNEL_MIDDLE_HEIGHT;
			graphics.blit(CHANNELS, channelBarX, channelBarY + getChannelSegmentY(i), sourceX, sourceY,
				CHANNEL_SEGMENT_WIDTH, sourceHeight, CHANNELS_TEXTURE_WIDTH, CHANNELS_TEXTURE_HEIGHT);
		}
	}

	private int getClickedChannel(double mouseX, double mouseY) {
		if (visibleChannels.size() < 2 || mouseX < channelBarX || mouseX >= channelBarX + CHANNEL_SEGMENT_WIDTH)
			return -1;
		double relativeY = mouseY - channelBarY;
		if (relativeY < 0 || relativeY >= getChannelBarHeight())
			return -1;
		if (relativeY < CHANNEL_TOP_HEIGHT)
			return 0;
		relativeY -= CHANNEL_TOP_HEIGHT;
		int middleHeight = (visibleChannels.size() - 2) * CHANNEL_MIDDLE_HEIGHT;
		return relativeY < middleHeight ? 1 + (int) relativeY / CHANNEL_MIDDLE_HEIGHT : visibleChannels.size() - 1;
	}

	private int getChannelBarHeight() {
		return CHANNEL_TOP_HEIGHT + (visibleChannels.size() - 2) * CHANNEL_MIDDLE_HEIGHT + CHANNEL_BOTTOM_HEIGHT;
	}

	private int getChannelSegmentY(int channel) {
		return channel == 0 ? 0 : CHANNEL_TOP_HEIGHT + (channel - 1) * CHANNEL_MIDDLE_HEIGHT;
	}

	private void renderChannelTooltip(GuiGraphics graphics, int channel, int mouseX, int mouseY) {
		ItemStack card = getMenu().getCards().get(visibleChannels.get(channel));
		Component note = card.has(DataComponents.CUSTOM_NAME) ? card.getHoverName()
			: Component.translatable("gui.createphantom.tunable_portable_ticker.unnamed_card");
		graphics.renderComponentTooltip(font, List.of(note,
			Component.translatable("gui.createphantom.tunable_portable_ticker.rmb_switch_channel")
				.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)), mouseX, mouseY);
	}
}
