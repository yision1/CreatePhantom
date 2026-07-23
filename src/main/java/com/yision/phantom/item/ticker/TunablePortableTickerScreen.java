package com.yision.phantom.item.ticker;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.compat.Mods;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.stockTicker.CraftableBigItemStack;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen.SearchSyncMode;
import com.simibubi.create.content.trains.station.NoShadowFontWrapper;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.utility.CreateLang;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.yision.phantom.compat.jecharacters.JechSearchBridge;
import com.yision.phantom.client.gui.address.AddressSuggestionEditBox;
import com.yision.phantom.compat.fluidlogistics.FluidLogisticsTickerCompat;
import com.yision.phantom.compat.jei.CPJEI;
import com.yision.phantom.CreatePhantom;
import com.yision.phantom.item.storagecard.StorageChannelExtensionCardItem;
import com.yision.phantom.item.ticker.TunablePortableTickerItem;
import com.yision.phantom.network.ticker.TunablePortableTickerHiddenCategoriesPacket;
import com.yision.phantom.network.ticker.TunablePortableTickerSelectChannelPacket;
import com.yision.phantom.network.ticker.TunablePortableTickerSendOrderPacket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.function.Function;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.platform.CatnipServices;
import net.createmod.catnip.theme.Color;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public class TunablePortableTickerScreen extends AbstractSimiContainerScreen<TunablePortableTickerMenu> {
	private static final int MAX_CLIENT_REPORTED_AMOUNT = 1000;
	private static final AllGuiTextures NUMBERS = AllGuiTextures.NUMBERS;
	private static final AllGuiTextures HEADER = AllGuiTextures.STOCK_KEEPER_REQUEST_HEADER;
	private static final AllGuiTextures BODY = AllGuiTextures.STOCK_KEEPER_REQUEST_BODY;
	private static final AllGuiTextures FOOTER = AllGuiTextures.STOCK_KEEPER_REQUEST_FOOTER;

	private static class CategoryEntry {
		final int targetCategory;
		final String name;
		int y;
		boolean hidden;

		CategoryEntry(int targetCategory, String name) {
			this.targetCategory = targetCategory;
			this.name = name;
		}
	}

	private final int cols = 9;
	private final int rowHeight = 20;
	private final int colWidth = 20;
	private final Couple<Integer> noneHovered = Couple.create(-1, -1);
	private final Inventory playerInventory;
	private final Set<Integer> hiddenCategories;

	public LerpedFloat itemScroll = LerpedFloat.linear().startWithValue(0);
	public EditBox searchBox;
	public AddressSuggestionEditBox addressBox;

	private int itemsX;
	private int itemsY;
	private int orderY;
	private int windowWidth;
	private int windowHeight;
	private final Map<UUID, String> requestedAddresses = new HashMap<>();
	private int emptyTicks;
	private int successTicks;
	private boolean scrollHandleActive;

	public List<List<BigItemStack>> currentItemSource = Collections.emptyList();
	public List<List<BigItemStack>> displayedItems = new ArrayList<>();
	public List<CategoryEntry> categories = new ArrayList<>();
	public List<BigItemStack> itemsToOrder = new ArrayList<>();
	public List<CraftableBigItemStack> recipesToOrder = new ArrayList<>();
	private InventorySummary forcedEntries = new InventorySummary();
	private int lastSeenStockVersion = -1;
	private List<BigItemStack> lastSeenStacks = List.of();
	private InventorySummary cachedSummary;
	private InventorySummary cachedPlanningSummary;
	private List<Rect2i> extraAreas = Collections.emptyList();

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
	private int activeChannel;
	private List<ItemStack> activeCards;
	private UUID activeSessionNetwork;
	private List<ItemStack> activeCategories = List.of();
	private int channelBarX;
	private int channelBarY;

	public boolean refreshSearchNextTick;
	public boolean moveToTopNextTick;

	public TunablePortableTickerScreen(TunablePortableTickerMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		this.playerInventory = playerInventory;
		this.hiddenCategories = new HashSet<>(menu.hiddenCategories);
		menu.screenReference = this;
	}

	@Override
	protected void init() {
		int appropriateHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight() - 10;
		appropriateHeight -=
			Mth.positiveModulo(appropriateHeight - HEADER.getHeight() - FOOTER.getHeight(), BODY.getHeight());
		appropriateHeight =
			Math.min(appropriateHeight, HEADER.getHeight() + FOOTER.getHeight() + BODY.getHeight() * 17);

		setWindowSize(windowWidth = 226, windowHeight = appropriateHeight);
		imageWidth = windowWidth;
		imageHeight = windowHeight;
		super.init();
		clearWidgets();

		int x = getGuiLeft();
		int y = getGuiTop();
		itemsX = x + (windowWidth - cols * colWidth) / 2 + 1;
		itemsY = y + 33;
		orderY = y + windowHeight - 72;

		searchBox = new EditBox(new NoShadowFontWrapper(font), x + 71, y + 22, 100, 9,
			CreateLang.translateDirect("gui.stock_keeper.search_items"));
		searchBox.setMaxLength(50);
		searchBox.setBordered(false);
		searchBox.setTextColor(0x4A2D31);
		addWidget(searchBox);

		this.activeChannel = menu.channel;
		this.activeCards = menu.cards;
		this.activeSessionNetwork = menu.sessionNetwork;
		this.activeCategories = menu.categories;

		boolean initial = addressBox == null;
		String currentAddress = initial ? "" : addressBox.getValue();
		if (initial && activeSessionNetwork != null && !menu.initialAddress.isEmpty())
			requestedAddresses.put(activeSessionNetwork, menu.initialAddress);
		String address = initial ? addressForActiveChannel() : currentAddress;

		List<String> cardAddresses = currentCardAddresses();
		addressBox = new AddressSuggestionEditBox(this, new NoShadowFontWrapper(font), x + 27, y + windowHeight - 36, 92, 10,
			true, "@" + playerInventory.player.getName().getString(), cardAddresses);
		addressBox.setTextColor(0x714A40);
		addressBox.setValue(address);
		addRenderableWidget(addressBox);
		this.channelBarX = getGuiLeft() - 15 + STOCK_KEEPER_VISIBLE_RIGHT;
		this.channelBarY = getGuiTop() + 30;
		int channelBarHeight = getChannelBarHeight();
		extraAreas = channelBarHeight > 0
			? List.of(new Rect2i(channelBarX, channelBarY, CHANNEL_SEGMENT_WIDTH, channelBarHeight))
			: Collections.emptyList();
		ClientScreenStorage.manualUpdate(menu.locator, activeChannel, activeSessionNetwork);

		if (initial) {
			playUiSound(SoundEvents.WOOD_HIT, 0.5f, 1.5f);
			playUiSound(SoundEvents.BOOK_PAGE_TURN, 1, 1);
			syncRecipeViewers();
		}
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		addressBox.tick();

		ClientScreenStorage.tick(menu.locator, activeChannel, activeSessionNetwork);

		if (!forcedEntries.isEmpty()) {
			InventorySummary summary = getLatestSummary();
			for (BigItemStack stack : new ArrayList<>(forcedEntries.getStacks())) {
				int limited = -stack.count - 1;
				if (summary.getCountOf(stack.stack) <= limited)
					forcedEntries.erase(stack.stack);
			}
		}

		boolean allEmpty = displayedItems.stream().allMatch(List::isEmpty);
		emptyTicks = allEmpty ? emptyTicks + 1 : 0;
		successTicks = successTicks > 0 && itemsToOrder.isEmpty() ? successTicks + 1 : 0;

		if (ClientScreenStorage.getVersion() != lastSeenStockVersion) {
			lastSeenStockVersion = ClientScreenStorage.getVersion();
			lastSeenStacks = new ArrayList<>(ClientScreenStorage.getStacks());
			cachedSummary = null;
			cachedPlanningSummary = null;
			sortAndCategorize(lastSeenStacks);
			refreshSearchResults(false);
			revalidateOrders();
		}

		if (refreshSearchNextTick) {
			refreshSearchNextTick = false;
			refreshSearchResults(moveToTopNextTick);
		}

		itemScroll.tickChaser();
		if (Math.abs(itemScroll.getValue() - itemScroll.getChaseTarget()) < 1 / 16f)
			itemScroll.setValue(itemScroll.getChaseTarget());

		if (!menu.stillValid(menu.player))
			menu.player.closeContainer();
	}

	private void sortAndCategorize(List<BigItemStack> stacks) {
		List<BigItemStack> sorted = new ArrayList<>(stacks);
		sorted.sort(Comparator.comparingInt((BigItemStack entry) -> -entry.count));
		currentItemSource = convertToCategoryList(sorted);
	}

	private List<List<BigItemStack>> convertToCategoryList(List<BigItemStack> stacks) {
		List<List<BigItemStack>> output = new ArrayList<>();
		List<FilterItemStack> filters = new ArrayList<>();
		for (ItemStack filter : activeCategories) {
			output.add(new ArrayList<>());
			filters.add(filter.isEmpty() ? null : FilterItemStack.of(filter));
		}

		List<BigItemStack> unsorted = new ArrayList<>();
		output.add(unsorted);

		Level level = playerInventory.player.level();
		for (BigItemStack entry : stacks) {
			boolean matched = false;
			for (int i = 0; i < filters.size(); i++) {
				FilterItemStack filter = filters.get(i);
				if (filter != null && filter.test(level, entry.stack)) {
					output.get(i).add(entry);
					matched = true;
					break;
				}
			}
			if (!matched)
				unsorted.add(entry);
		}
		return output;
	}

	private void refreshSearchResults(boolean scrollBackUp) {
		if (scrollBackUp)
			itemScroll.startWithValue(0);

		categories = new ArrayList<>();
		for (int i = 0; i < activeCategories.size(); i++) {
			ItemStack stack = activeCategories.get(i);
			CategoryEntry entry = new CategoryEntry(i, stack.isEmpty() ? "" : stack.getHoverName().getString());
			entry.hidden = hiddenCategories.contains(i);
			categories.add(entry);
		}
		CategoryEntry unsorted =
			new CategoryEntry(-1, CreateLang.translate("gui.stock_keeper.unsorted_category").string());
		unsorted.hidden = hiddenCategories.contains(-1);
		categories.add(unsorted);

		String value = Objects.requireNonNullElse(searchBox.getValue(), "");
		boolean modSearch = value.startsWith("@");
		boolean tagSearch = value.startsWith("#");
		if (modSearch || tagSearch)
			value = value.substring(1);
		value = value.toLowerCase(Locale.ROOT);

		displayedItems = new ArrayList<>();
		for (List<BigItemStack> ignored : currentItemSource)
			displayedItems.add(new ArrayList<>());

		int categoryY = 0;
		boolean anyItemsInCategory = false;
		for (int i = 0; i < currentItemSource.size(); i++) {
			List<BigItemStack> source = currentItemSource.get(i);
			List<BigItemStack> target = displayedItems.get(i);
			categories.get(i).y = categoryY;

			for (BigItemStack entry : source)
				if (value.isBlank() || matchesSearch(entry.stack, value, modSearch, tagSearch))
					target.add(entry);

			if (target.isEmpty())
				continue;
			if (i < currentItemSource.size() - 1)
				anyItemsInCategory = true;
			categoryY += rowHeight;
			if (!categories.get(i).hidden)
				categoryY += Math.ceil(target.size() / (float) cols) * rowHeight;
		}

		if (!anyItemsInCategory)
			categories.clear();
		clampScrollBar();
		updateCraftableAmounts();
	}

	private boolean matchesSearch(ItemStack stack, String value, boolean modSearch, boolean tagSearch) {
		if (modSearch)
			return JechSearchBridge.containsIgnoreCase(
				BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace(),
				value);
		if (tagSearch)
			return stack.getTags()
				.anyMatch(key -> JechSearchBridge.containsIgnoreCase(key.location().toString(), value));
		return JechSearchBridge.containsIgnoreCase(stack.getHoverName().getString(), value)
			|| JechSearchBridge.containsIgnoreCase(
				BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(),
				value);
	}

	@Override
	protected void renderBg(@NotNull GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
		if (minecraft != null && this != minecraft.screen)
			return;

		PoseStack ms = graphics.pose();
		float currentScroll = itemScroll.getValue(partialTicks);
		Couple<Integer> hovered = getHoveredSlot(mouseX, mouseY);
		int x = getGuiLeft();
		int y = getGuiTop();

		HEADER.render(graphics, x - 15, y);
		y += HEADER.getHeight();
		for (int i = 0; i < (windowHeight - HEADER.getHeight() - FOOTER.getHeight()) / BODY.getHeight(); i++) {
			BODY.render(graphics, x - 15, y);
			y += BODY.getHeight();
		}
		FOOTER.render(graphics, x - 15, y);
		y = getGuiTop();

		if (addressBox.getValue().isBlank() && !addressBox.isFocused())
			graphics.drawString(font, CreateLang.translate("gui.stock_keeper.package_address")
				.style(ChatFormatting.ITALIC).component(), addressBox.getX(), addressBox.getY(), 0xff_CDBCA8, false);

		ms.pushPose();
		ms.translate(x - 50, y + windowHeight - 70, -100);
		ms.scale(3.5f, 3.5f, 3.5f);
		if (!menu.tickerStack.isEmpty())
			GuiGameElement.of(menu.tickerStack.getItem()).render(graphics);
		ms.popPose();

		for (int i = 0; i < itemsToOrder.size() && i < cols; i++) {
			ms.pushPose();
			ms.translate(itemsX + i * colWidth, orderY, 0);
			renderItemEntry(graphics, itemsToOrder.get(i), hovered.getFirst() == -1 && hovered.getSecond() == i, true);
			ms.popPose();
		}

		if (!recipesToOrder.isEmpty()) {
			int jeiX = x + (windowWidth - colWidth * recipesToOrder.size()) / 2 + 1;
			int jeiY = orderY - 31;
			ms.pushPose();
			ms.translate(jeiX, jeiY, 200);

			int xOffset = -3;
			AllGuiTextures.STOCK_KEEPER_REQUEST_BLUEPRINT_LEFT.render(graphics, xOffset, -3);
			xOffset += 10;
			for (int i = 0; i <= (recipesToOrder.size() - 1) * 5; i++) {
				AllGuiTextures.STOCK_KEEPER_REQUEST_BLUEPRINT_MIDDLE.render(graphics, xOffset, -3);
				xOffset += 4;
			}
			AllGuiTextures.STOCK_KEEPER_REQUEST_BLUEPRINT_RIGHT.render(graphics, xOffset, -3);

			for (int i = 0; i < recipesToOrder.size(); i++) {
				ms.pushPose();
				ms.translate(i * colWidth, 0, 0);
				renderItemEntry(graphics, recipesToOrder.get(i), hovered.getFirst() == -2 && hovered.getSecond() == i, true);
				ms.popPose();
			}
			ms.popPose();
		}

		boolean justSent = itemsToOrder.isEmpty() && successTicks > 0;
		if (isConfirmHovered(mouseX, mouseY) && !justSent)
			AllGuiTextures.STOCK_KEEPER_REQUEST_SEND_HOVER.render(graphics, x + windowWidth - 81, y + windowHeight - 41);

		MutableComponent title = Component.translatable("item.createphantom.tunable_portable_ticker.screen_title");
		graphics.drawString(font, title, x + windowWidth / 2 - font.width(title) / 2, y + 4, 0x714A40, false);
		MutableComponent send = CreateLang.translate("gui.stock_keeper.send").component();
		if (justSent) {
			float alpha = Mth.clamp((successTicks + partialTicks - 5f) / 5f, 0f, 1f);
			ms.pushPose();
			ms.translate(alpha * alpha * 50, 0, 0);
			if (successTicks < 10)
				graphics.drawString(font, send, x + windowWidth - 42 - font.width(send) / 2, y + windowHeight - 35,
					new Color(0x252525).setAlpha(1 - alpha * alpha).getRGB(), false);
			ms.popPose();
		} else {
			graphics.drawString(font, send, x + windowWidth - 42 - font.width(send) / 2, y + windowHeight - 35,
				0x252525, false);
		}

		if (justSent) {
			Component msg = CreateLang.translateDirect("gui.stock_keeper.request_sent");
			float alpha = Mth.clamp((successTicks + partialTicks - 10f) / 5f, 0f, 1f);
			int msgX = x + windowWidth / 2 - (font.width(msg) + 10) / 2;
			int msgY = orderY + 5;
			if (alpha > 0) {
				int color = new Color(0x8C5D4B).setAlpha(alpha).getRGB();
				int width = font.width(msg) + 14;
				AllGuiTextures.STOCK_KEEPER_REQUEST_BANNER_L.render(graphics, msgX - 8, msgY - 4);
				UIRenderHelper.drawStretched(graphics, msgX, msgY - 4, width, 16, 0,
					AllGuiTextures.STOCK_KEEPER_REQUEST_BANNER_M);
				AllGuiTextures.STOCK_KEEPER_REQUEST_BANNER_R.render(graphics, msgX + font.width(msg) + 10, msgY - 4);
				graphics.drawString(font, msg, msgX + 5, msgY, color, false);
			}
		}

		int itemWindowX = x + 21;
		int itemWindowY = y + 17;
		graphics.enableScissor(itemWindowX - 5, itemWindowY, itemWindowX + 194, y + windowHeight - 80);
		ms.pushPose();
		ms.translate(0, -currentScroll * rowHeight, 0);

		for (int sliceY = -2; sliceY < getMaxScroll() * rowHeight + windowHeight - 72;
			 sliceY += AllGuiTextures.STOCK_KEEPER_REQUEST_BG.getHeight()) {
			if (sliceY - currentScroll * rowHeight < -20 || sliceY - currentScroll * rowHeight > windowHeight - 72)
				continue;
			AllGuiTextures.STOCK_KEEPER_REQUEST_BG.render(graphics, x + 22, y + sliceY + 18);
		}

		AllGuiTextures.STOCK_KEEPER_REQUEST_SEARCH.render(graphics, x + 42, searchBox.getY() - 5);
		searchBox.render(graphics, mouseX, mouseY, partialTicks);
		if (searchBox.getValue().isBlank() && !searchBox.isFocused())
			graphics.drawString(font, searchBox.getMessage(),
				x + windowWidth / 2 - font.width(searchBox.getMessage()) / 2, searchBox.getY(), 0xff4A2D31, false);

		renderTroubleshooting(graphics, x);
		renderItemGrid(graphics, hovered, x, y, currentScroll);

		ms.popPose();
		graphics.disableScissor();
		renderScrollBar(graphics, y, currentScroll);
		renderChannelBar(graphics);
	}

	private void renderTroubleshooting(GuiGraphics graphics, int x) {
		if (!displayedItems.stream().allMatch(List::isEmpty))
			return;

		float alpha = Mth.clamp((emptyTicks - 10f) / 5f, 0f, 1f);
		if (alpha <= 0)
			return;

		List<FormattedCharSequence> lines = font.split(getTroubleshootingMessage(), 160);
		for (int i = 0; i < lines.size(); i++) {
			FormattedCharSequence line = lines.get(i);
			int width = font.width(line);
			graphics.drawString(font, line, x + windowWidth / 2 - width / 2, itemsY + 20 + i * (font.lineHeight + 1),
				new Color(0xF8F8EC).setAlpha(alpha).getRGB(), false);
		}
	}

	private void renderItemGrid(GuiGraphics graphics, Couple<Integer> hovered, int x, int y, float currentScroll) {
		PoseStack ms = graphics.pose();
		for (int categoryIndex = 0; categoryIndex < displayedItems.size(); categoryIndex++) {
			List<BigItemStack> category = displayedItems.get(categoryIndex);
			if (category.isEmpty())
				continue;

			CategoryEntry categoryEntry = categories.isEmpty() ? null : categories.get(categoryIndex);
			int categoryY = categories.isEmpty() ? 0 : categoryEntry.y;

			if (categoryEntry != null) {
				(categoryEntry.hidden ? AllGuiTextures.STOCK_KEEPER_CATEGORY_HIDDEN
					: AllGuiTextures.STOCK_KEEPER_CATEGORY_SHOWN).render(graphics, itemsX, itemsY + categoryY + 6);
				graphics.drawString(font, categoryEntry.name, itemsX + 9, itemsY + categoryY + 7, 0xF8F8EC, false);
				if (categoryEntry.hidden)
					continue;
			}

			for (int index = 0; index < category.size(); index++) {
				int pY = itemsY + categoryY + (categories.isEmpty() ? 4 : rowHeight) + (index / cols) * rowHeight;
				float cullY = pY - currentScroll * rowHeight;
				if (cullY < y)
					continue;
				if (cullY > y + windowHeight - 72)
					break;

				ms.pushPose();
				ms.translate(itemsX + (index % cols) * colWidth, pY, 0);
				renderItemEntry(graphics, category.get(index),
					hovered.getFirst() == categoryIndex && hovered.getSecond() == index, false);
				ms.popPose();
			}
		}
	}

	private void renderScrollBar(GuiGraphics graphics, int y, float currentScroll) {
		int windowH = windowHeight - 92;
		int totalH = getMaxScroll() * rowHeight + windowH;
		int barSize = Math.max(5, Mth.floor((float) windowH / totalH * (windowH - 2)));
		if (barSize >= windowH - 2)
			return;

		int barX = itemsX + cols * colWidth;
		int barY = y + 15;
		PoseStack ms = graphics.pose();
		ms.pushPose();
		ms.translate(0, (currentScroll * rowHeight) / totalH * (windowH - 2), 0);
		AllGuiTextures pad = AllGuiTextures.STOCK_KEEPER_REQUEST_SCROLL_PAD;
		graphics.blit(pad.location, barX, barY, pad.getWidth(), barSize, pad.getStartX(), pad.getStartY(), pad.getWidth(),
			pad.getHeight(), 256, 256);
		AllGuiTextures.STOCK_KEEPER_REQUEST_SCROLL_TOP.render(graphics, barX, barY);
		if (barSize > 16)
			AllGuiTextures.STOCK_KEEPER_REQUEST_SCROLL_MID.render(graphics, barX, barY + barSize / 2 - 4);
		AllGuiTextures.STOCK_KEEPER_REQUEST_SCROLL_BOT.render(graphics, barX, barY + barSize - 5);
		ms.popPose();
	}
	private void renderChannelBar(GuiGraphics graphics) {
		int channelCount = getVisibleChannelCount();
		if (channelCount < 2)
			return;
		for (int i = 0; i < channelCount; i++) {
			boolean active = i == activeChannel;
			int sourceX = active ? CHANNEL_ACTIVE_SEGMENT_X : CHANNEL_SEGMENT_X;
			int sourceY;
			int sourceHeight;
			if (i == 0) {
				sourceY = CHANNEL_TOP_Y;
				sourceHeight = CHANNEL_TOP_HEIGHT;
			} else if (i == channelCount - 1) {
				sourceY = CHANNEL_BOTTOM_Y;
				sourceHeight = CHANNEL_BOTTOM_HEIGHT;
			} else {
				sourceY = CHANNEL_MIDDLE_Y;
				sourceHeight = CHANNEL_MIDDLE_HEIGHT;
			}
			graphics.blit(CHANNELS, channelBarX, channelBarY + getChannelSegmentY(i, channelCount), sourceX, sourceY,
				CHANNEL_SEGMENT_WIDTH, sourceHeight, CHANNELS_TEXTURE_WIDTH, CHANNELS_TEXTURE_HEIGHT);
		}
	}

	private void renderItemEntry(GuiGraphics graphics, BigItemStack entry, boolean hovered, boolean orderRow) {
		int available = entry.count;
		if (!orderRow) {
			BigItemStack ordered = getOrderForItem(entry.stack);
			if (entry.count < BigItemStack.INF) {
				int forcedCount = forcedEntries.getCountOf(entry.stack);
				if (forcedCount != 0)
					available = Math.min(available, -forcedCount - 1);
				if (ordered != null)
					available -= ordered.count;
				available = Math.max(0, available);
			}
			AllGuiTextures.STOCK_KEEPER_REQUEST_SLOT.render(graphics, 0, 0);
		}

		PoseStack ms = graphics.pose();
		ms.pushPose();
		ms.translate((colWidth - 18) / 2.0, (rowHeight - 18) / 2.0, 0);
		ms.translate(18 / 2.0, 18 / 2.0, 0);
		float scale = hovered ? 1.075f : 1f;
		ms.scale(scale, scale, scale);
		ms.translate(-18 / 2.0, -18 / 2.0, 0);
		if (available > 0)
			GuiGameElement.of(entry.stack).render(graphics);
		ms.popPose();

		if (available > 0) {
			ms.pushPose();
			ms.translate(0, 0, 190);
			if (FluidLogisticsTickerCompat.isVirtualFluidStack(entry.stack)) {
				ms.translate(1, 1, 0);
				FluidLogisticsTickerCompat.renderAmountInTicker(graphics, available);
			} else {
				graphics.renderItemDecorations(font, entry.stack, 1, 1, "");
				ms.translate(0, 0, 10);
				if (available > 1)
					drawItemCount(graphics, available);
			}
			ms.popPose();
		}
	}

	private void drawItemCount(GuiGraphics graphics, int count) {
		String text = count >= 1_000_000 ? (count / 1_000_000) + "m"
			: count >= 10_000 ? (count / 1000) + "k"
			: count >= 1000 ? ((count * 10) / 1000) / 10f + "k"
			: count >= 100 ? Integer.toString(count)
			: " " + count;
		if (count >= BigItemStack.INF)
			text = "+";

		int x = (int) Math.floor(-text.length() * 2.5);
		for (char c : text.toCharArray()) {
			int xOffset = (c - '0') * 6;
			int spriteWidth = NUMBERS.getWidth();
			if (c == ' ') {
				x += 4;
				continue;
			}
			if (c == '.') {
				xOffset = 60;
				spriteWidth = 3;
			} else if (c == 'k') {
				xOffset = 64;
			} else if (c == 'm') {
				xOffset = 70;
				spriteWidth = 7;
			} else if (c == '+') {
				xOffset = 84;
				spriteWidth = 9;
			}
			RenderSystem.enableBlend();
			graphics.blit(NUMBERS.location, 14 + x, 10, 0, NUMBERS.getStartX() + xOffset, NUMBERS.getStartY(), spriteWidth,
				NUMBERS.getHeight(), 256, 256);
			x += spriteWidth - 1;
		}
	}

	@Override
	protected void renderForeground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
		super.renderForeground(graphics, mouseX, mouseY, partialTicks);
		Couple<Integer> hovered = getHoveredSlot(mouseX, mouseY);
		if (hovered != noneHovered) {
			boolean recipeHovered = hovered.getFirst() == -2;
			boolean orderHovered = hovered.getFirst() == -1;
			BigItemStack entry = recipeHovered ? recipesToOrder.get(hovered.getSecond())
				: orderHovered ? itemsToOrder.get(hovered.getSecond())
				: displayedItems.get(hovered.getFirst()).get(hovered.getSecond());

			if ((recipeHovered || orderHovered) && FluidLogisticsTickerCompat.isVirtualFluidEntry(entry)) {
				List<Component> fluidTooltip = FluidLogisticsTickerCompat.tooltipLines(entry, recipeHovered);
				if (!fluidTooltip.isEmpty()) {
					graphics.renderComponentTooltip(font, fluidTooltip, mouseX, mouseY);
				} else {
					graphics.renderTooltip(font, entry.stack, mouseX, mouseY);
				}
			} else if (recipeHovered && minecraft != null) {
				ArrayList<Component> lines = new ArrayList<>(
					entry.stack.getTooltipLines(TooltipContext.of(minecraft.level), minecraft.player, TooltipFlag.NORMAL));
				if (!lines.isEmpty())
					lines.set(0, CreateLang.translateDirect("gui.stock_keeper.craft", lines.get(0).copy()));
				graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
			} else {
				graphics.renderTooltip(font, entry.stack, mouseX, mouseY);
			}
		}

		if (addressBox.getValue().isBlank() && !addressBox.isFocused() && addressBox.isHovered()) {
			graphics.renderComponentTooltip(font, List.of(
				CreateLang.translate("gui.factory_panel.restocker_address").color(ScrollInput.HEADER_RGB).component(),
				CreateLang.translate("gui.schedule.lmb_edit").style(ChatFormatting.DARK_GRAY).style(ChatFormatting.ITALIC)
					.component()),
				mouseX, mouseY);
		}

		int hoveredChannel = getClickedChannel(mouseX, mouseY);
		if (hoveredChannel != -1)
			renderChannelTooltip(graphics, hoveredChannel, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		boolean lmb = button == GLFW.GLFW_MOUSE_BUTTON_LEFT;
		boolean rmb = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			int clickedChannel = getClickedChannel(mouseX, mouseY);
			if (clickedChannel >= 0 && clickedChannel < TunablePortableTickerItem.MAX_CHANNELS && clickedChannel != activeChannel) {
				sendHiddenCategories();
				setActiveChannel(clickedChannel, true);
				itemsToOrder = new ArrayList<>();
				recipesToOrder = new ArrayList<>();
				CatnipServices.NETWORK.sendToServer(new TunablePortableTickerSelectChannelPacket(menu.locator, activeChannel));
				ClientScreenStorage.manualUpdate(menu.locator, activeChannel, activeSessionNetwork);
			}
			if (clickedChannel != -1)
				return true;
		}

		if (rmb && searchBox.isMouseOver(mouseX, mouseY)) {
			searchBox.setValue("");
			refreshSearchNextTick = true;
			moveToTopNextTick = true;
			searchBox.setFocused(true);
			syncRecipeViewers();
			return true;
		}

		if (addressBox.isFocused()) {
			if (addressBox.isHovered())
				return addressBox.mouseClicked(mouseX, mouseY, button);
			addressBox.setFocused(false);
		}
		if (searchBox.isFocused()) {
			if (searchBox.isHovered())
				return searchBox.mouseClicked(mouseX, mouseY, button);
			searchBox.setFocused(false);
		}

		int barX = itemsX + cols * colWidth - 1;
		if (getMaxScroll() > 0 && lmb && mouseX > barX && mouseX <= barX + 8 && mouseY > getGuiTop() + 15
			&& mouseY < getGuiTop() + windowHeight - 82) {
			scrollHandleActive = true;
			if (minecraft != null && minecraft.isWindowActive())
				GLFW.glfwSetInputMode(minecraft.getWindow().getWindow(), 208897, GLFW.GLFW_CURSOR_HIDDEN);
			return true;
		}

		if (lmb && isConfirmHovered(mouseX, mouseY)) {
			sendIt();
			playUiSound(SoundEvents.UI_BUTTON_CLICK.value(), 1, 1);
			return true;
		}

		if (handleCategoryToggle(mouseX, mouseY))
			return true;

		Couple<Integer> hovered = getHoveredSlot((int) mouseX, (int) mouseY);
		if (hovered == noneHovered || !lmb && !rmb)
			return super.mouseClicked(mouseX, mouseY, button);

		boolean recipeClicked = hovered.getFirst() == -2;
		boolean orderClicked = hovered.getFirst() == -1;
		BigItemStack entry = recipeClicked ? recipesToOrder.get(hovered.getSecond())
			: orderClicked ? itemsToOrder.get(hovered.getSecond())
			: displayedItems.get(hovered.getFirst()).get(hovered.getSecond());
		boolean isVirtualFluid = FluidLogisticsTickerCompat.isVirtualFluidStack(entry.stack) && !recipeClicked;
		int transfer = hasShiftDown() ? entry.stack.getMaxStackSize() : hasControlDown() ? 10 : 1;

		if (recipeClicked && entry instanceof CraftableBigItemStack cbis) {
			if (FluidLogisticsTickerCompat.hasCustomRecipeData(cbis)) {
				if (rmb && cbis.count == 0) {
					recipesToOrder.remove(cbis);
					return true;
				}
				int delta = FluidLogisticsTickerCompat.recipeStep(cbis, hasShiftDown(), hasControlDown());
				handleCustomFluidCraftableRequest(cbis, rmb ? -delta : delta);
				return true;
			}
			if (rmb && cbis.count == 0) {
				recipesToOrder.remove(cbis);
				return true;
			}
			requestCraftable(cbis, rmb ? -transfer : transfer);
			return true;
		}

		BigItemStack existing = orderClicked ? entry : getOrderForItem(entry.stack);

		if (isVirtualFluid) {
			if (existing == null) {
				if (!(rmb || orderClicked) && itemsToOrder.size() >= cols)
					return true;
				if (rmb || orderClicked)
					return true;
				existing = new BigItemStack(entry.stack.copyWithCount(1), 0);
				itemsToOrder.add(existing);
			}
			int maxAvailable = getLatestSummary().getCountOf(entry.stack);
			int newAmount = orderClicked
				? FluidLogisticsTickerCompat.adjustFluidRequestAmount(
					existing.count, !(rmb || orderClicked), hasShiftDown(), hasControlDown(), 0, Math.max(0, maxAvailable), 1)
				: FluidLogisticsTickerCompat.adjustStockTickerFluidRequestAmount(
					existing.count, !(rmb || orderClicked), hasShiftDown(), hasControlDown(), 0, Math.max(0, maxAvailable), 1);
			if (newAmount <= 0) {
				itemsToOrder.remove(existing);
			} else {
				existing.count = newAmount;
			}
			return true;
		}
		if (existing == null) {
			if (itemsToOrder.size() >= cols || rmb)
				return true;
			itemsToOrder.add(existing = new BigItemStack(entry.stack.copyWithCount(1), 0));
		}

		if (rmb || orderClicked) {
			existing.count -= transfer;
			if (existing.count <= 0)
				itemsToOrder.remove(existing);
			return true;
		}

		existing.count += Math.min(transfer, entry.count - existing.count);
		return true;
	}

	private int getClickedChannel(double mouseX, double mouseY) {
		if (mouseX < channelBarX || mouseX >= channelBarX + CHANNEL_SEGMENT_WIDTH)
			return -1;

		int relativeY = (int) (mouseY - channelBarY);
		int channelCount = getVisibleChannelCount();
		if (channelCount < 2)
			return -1;
		if (relativeY < 0 || relativeY >= getChannelBarHeight(channelCount))
			return -1;
		if (relativeY < CHANNEL_TOP_HEIGHT)
			return 0;

		relativeY -= CHANNEL_TOP_HEIGHT;
		int middleCount = Math.max(0, channelCount - 2);
		int middleHeight = middleCount * CHANNEL_MIDDLE_HEIGHT;
		if (relativeY < middleHeight)
			return 1 + relativeY / CHANNEL_MIDDLE_HEIGHT;
		return channelCount - 1;
	}

	private int getVisibleChannelCount() {
		if (activeCards == null)
			return 0;
		int count = 0;
		for (ItemStack card : activeCards) {
			if (!card.isEmpty())
				count++;
		}
		return count;
	}

	private void setActiveChannel(int channel, boolean updateAddress) {
		activeChannel = channel;
		ItemStack card = getVisibleCard(channel);
		activeSessionNetwork = StorageChannelExtensionCardItem.networkFromStack(card);
		activeCategories = card.isEmpty() ? List.of() : StorageChannelExtensionCardItem.loadCategoriesFromStack(card);
		hiddenCategories.clear();
		if (activeSessionNetwork != null)
			hiddenCategories.addAll(TunablePortableTickerItem.loadHiddenCategories(menu.tickerStack,
				playerInventory.player.getUUID(), activeSessionNetwork));

		rebuildAddressBoxKeepingValue();

		if (updateAddress) {
			String address = addressForActiveChannel();
			addressBox.setValue(address);
		}

		sortAndCategorize(List.of());
		refreshSearchResults(true);
	}

	private String addressForActiveChannel() {
		if (activeSessionNetwork == null)
			return "";
		String cached = requestedAddresses.get(activeSessionNetwork);
		if (cached != null)
			return cached;

		String saved = TunablePortableTickerItem.loadAddress(menu.tickerStack, activeSessionNetwork);
		if (!saved.isEmpty())
			requestedAddresses.put(activeSessionNetwork, saved);
		return saved;
	}

	private List<String> currentCardAddresses() {
		ItemStack card = getVisibleCard(activeChannel);
		if (card.isEmpty())
			return List.of();
		return StorageChannelExtensionCardItem.loadAddressesFromStack(card);
	}

	private void rebuildAddressBoxKeepingValue() {
		String value = addressBox == null ? "" : addressBox.getValue();
		removeWidget(addressBox);
		addressBox = new AddressSuggestionEditBox(this, new NoShadowFontWrapper(font),
			getGuiLeft() + 27, getGuiTop() + windowHeight - 36, 92, 10,
			true, "@" + playerInventory.player.getName().getString(), currentCardAddresses());
		addressBox.setValue(value);
		addressBox.setTextColor(0x714A40);
		addRenderableWidget(addressBox);
	}

	private ItemStack getVisibleCard(int channel) {
		if (activeCards == null || channel < 0)
			return ItemStack.EMPTY;
		int visibleIndex = 0;
		for (ItemStack card : activeCards) {
			if (card.isEmpty())
				continue;
			if (visibleIndex == channel)
				return card;
			visibleIndex++;
		}
		return ItemStack.EMPTY;
	}

	private void renderChannelTooltip(GuiGraphics graphics, int channel, int mouseX, int mouseY) {
		ItemStack card = getVisibleCard(channel);
		Component note = card.has(DataComponents.CUSTOM_NAME)
			? card.getHoverName()
			: Component.translatable("gui.createphantom.tunable_portable_ticker.unnamed_card");
		graphics.renderComponentTooltip(font, List.of(
			note,
			Component.translatable("gui.createphantom.tunable_portable_ticker.rmb_switch_channel")
				.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)), mouseX, mouseY);
	}

	private void sendHiddenCategories() {
		if (activeSessionNetwork == null)
			return;
		CatnipServices.NETWORK.sendToServer(
			new TunablePortableTickerHiddenCategoriesPacket(menu.locator, activeChannel, activeSessionNetwork, new ArrayList<>(hiddenCategories)));
	}

	private int getChannelBarHeight() {
		return getChannelBarHeight(getVisibleChannelCount());
	}

	private int getChannelBarHeight(int channelCount) {
		if (channelCount < 2)
			return 0;
		return CHANNEL_TOP_HEIGHT + Math.max(0, channelCount - 2) * CHANNEL_MIDDLE_HEIGHT + CHANNEL_BOTTOM_HEIGHT;
	}

	private int getChannelSegmentY(int channel, int channelCount) {
		if (channel <= 0)
			return 0;
		if (channel == channelCount - 1)
			return CHANNEL_TOP_HEIGHT + Math.max(0, channelCount - 2) * CHANNEL_MIDDLE_HEIGHT;
		return CHANNEL_TOP_HEIGHT + (channel - 1) * CHANNEL_MIDDLE_HEIGHT;
	}

	private boolean handleCategoryToggle(double mouseX, double mouseY) {
		int localY = (int) (mouseY - itemsY);
		if (!itemScroll.settled() || categories.isEmpty() || mouseX < itemsX || mouseX >= itemsX + cols * colWidth
			|| mouseY < getGuiTop() + 16 || mouseY > getGuiTop() + windowHeight - 80)
			return false;

		for (int i = 0; i < displayedItems.size(); i++) {
			CategoryEntry entry = categories.get(i);
			if (Mth.floor((localY - entry.y) / (float) rowHeight + itemScroll.getChaseTarget()) != 0)
				continue;
			if (displayedItems.get(i).isEmpty())
				continue;
			if (entry.targetCategory >= activeCategories.size())
				continue;

			if (entry.hidden)
				hiddenCategories.remove(entry.targetCategory);
			else
				hiddenCategories.add(entry.targetCategory);

			refreshSearchNextTick = true;
			moveToTopNextTick = false;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (addressBox.mouseScrolled(mouseX, mouseY, scrollX, scrollY))
			return true;

		Couple<Integer> hovered = getHoveredSlot((int) mouseX, (int) mouseY);
		if (hovered == noneHovered || hovered.getFirst() >= 0 && !hasShiftDown() && getMaxScroll() != 0) {
			int direction = (int) (Math.ceil(Math.abs(scrollY)) * -Math.signum(scrollY));
			itemScroll.chase(Mth.clamp(Math.round(itemScroll.getChaseTarget() + direction), 0, getMaxScroll()), 0.5,
				LerpedFloat.Chaser.EXP);
			return true;
		}

		boolean recipeClicked = hovered.getFirst() == -2;
		boolean orderClicked = hovered.getFirst() == -1;
		BigItemStack entry = recipeClicked ? recipesToOrder.get(hovered.getSecond())
			: orderClicked ? itemsToOrder.get(hovered.getSecond())
			: displayedItems.get(hovered.getFirst()).get(hovered.getSecond());
		boolean remove = scrollY < 0;
		int steps = Mth.ceil(Math.abs(scrollY));
		boolean isVirtualFluidScroll = FluidLogisticsTickerCompat.isVirtualFluidStack(entry.stack) && !recipeClicked;
		int transfer = steps * (hasControlDown() ? 10 : 1);

		if (recipeClicked && entry instanceof CraftableBigItemStack cbis) {
			if (FluidLogisticsTickerCompat.hasCustomRecipeData(cbis)) {
				int delta = FluidLogisticsTickerCompat.recipeStep(cbis, hasShiftDown(), hasControlDown()) * steps;
				handleCustomFluidCraftableRequest(cbis, remove ? -delta : delta);
				return true;
			}
			requestCraftable(cbis, remove ? -transfer : transfer);
			return true;
		}

		if (isVirtualFluidScroll) {
			BigItemStack existing = orderClicked ? entry : getOrderForItem(entry.stack);
			if (existing == null) {
				if (remove || itemsToOrder.size() >= cols)
					return true;
				itemsToOrder.add(existing = new BigItemStack(entry.stack.copyWithCount(1), 0));
			}
			int maxAvailable = getLatestSummary().getCountOf(entry.stack);
			int newAmount;
			if (orderClicked) {
				newAmount = FluidLogisticsTickerCompat.adjustFluidRequestAmount(
					existing.count, !remove, hasShiftDown(), hasControlDown(),
					0, Math.max(0, maxAvailable), steps);
			} else {
				newAmount = FluidLogisticsTickerCompat.adjustStockTickerFluidRequestAmount(
					existing.count, !remove, hasShiftDown(), hasControlDown(),
					0, Math.max(0, maxAvailable), steps);
			}
			if (newAmount <= 0) {
				itemsToOrder.remove(existing);
			} else {
				existing.count = newAmount;
			}
			return true;
		}
		BigItemStack existing = orderClicked ? entry : getOrderForItem(entry.stack);
		if (existing == null) {
			if (itemsToOrder.size() >= cols || remove)
				return true;
			itemsToOrder.add(existing = new BigItemStack(entry.stack.copyWithCount(1), 0));
			playUiSound(SoundEvents.WOOL_STEP, 0.75f, 1.2f);
			playUiSound(SoundEvents.BAMBOO_WOOD_STEP, 0.75f, 0.8f);
		}

		if (remove) {
			int current = existing.count;
			existing.count -= transfer;
			if (existing.count <= 0) {
				itemsToOrder.remove(existing);
				playUiSound(SoundEvents.WOOL_STEP, 0.75f, 1.8f);
				playUiSound(SoundEvents.BAMBOO_WOOD_STEP, 0.75f, 1.8f);
			} else if (existing.count != current) {
				playUiSound(AllSoundEvents.SCROLL_VALUE.getMainEvent(), 0.25f, 1.2f);
			}
		} else {
			int current = existing.count;
			existing.count += Math.min(transfer, getLatestSummary().getCountOf(entry.stack) - existing.count);
			if (existing.count != current && current != 0)
				playUiSound(AllSoundEvents.SCROLL_VALUE.getMainEvent(), 0.25f, 1.2f);
		}
		return true;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && scrollHandleActive) {
			scrollHandleActive = false;
			if (minecraft != null && minecraft.isWindowActive())
				GLFW.glfwSetInputMode(minecraft.getWindow().getWindow(), 208897, GLFW.GLFW_CURSOR_NORMAL);
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !scrollHandleActive)
			return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
		if (minecraft == null)
			return false;

		Window window = minecraft.getWindow();
		double scaleX = window.getGuiScaledWidth() / (double) window.getScreenWidth();
		double scaleY = window.getGuiScaledHeight() / (double) window.getScreenHeight();

		int windowH = windowHeight - 92;
		int totalH = getMaxScroll() * rowHeight + windowH;
		int barSize = Math.max(5, Mth.floor((float) windowH / totalH * (windowH - 2)));
		if (barSize >= windowH - 2)
			return true;

		int barX = itemsX + cols * colWidth;
		double target = (mouseY - getGuiTop() - 15 - barSize / 2.0) * totalH / (windowH - 2) / rowHeight;
		itemScroll.chase(Mth.clamp(target, 0, getMaxScroll()), 0.8, LerpedFloat.Chaser.EXP);

		if (minecraft.isWindowActive()) {
			double forceX = (barX + 2) / scaleX;
			double forceY = Mth.clamp(mouseY, getGuiTop() + 15 + barSize / 2,
				getGuiTop() + 15 + windowH - barSize / 2) / scaleY;
			GLFW.glfwSetCursorPos(window.getWindow(), forceX, forceY);
		}
		return true;
	}

	@Override
	public boolean charTyped(char codePoint, int modifiers) {
		if (addressBox.isFocused() && addressBox.charTyped(codePoint, modifiers))
			return true;
		String value = searchBox.getValue();
		if (!searchBox.charTyped(codePoint, modifiers))
			return false;
		if (!Objects.equals(value, searchBox.getValue())) {
			refreshSearchNextTick = true;
			moveToTopNextTick = true;
			syncRecipeViewers();
		}
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ENTER && searchBox.isFocused()) {
			searchBox.setFocused(false);
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ENTER && hasShiftDown()) {
			sendIt();
			return true;
		}
		if (addressBox.isFocused() && addressBox.keyPressed(keyCode, scanCode, modifiers))
			return true;

		String value = searchBox.getValue();
		if (!searchBox.keyPressed(keyCode, scanCode, modifiers))
			return searchBox.isFocused() && searchBox.isVisible() && keyCode != 256
				|| super.keyPressed(keyCode, scanCode, modifiers);
		if (!Objects.equals(value, searchBox.getValue())) {
			refreshSearchNextTick = true;
			moveToTopNextTick = true;
			syncRecipeViewers();
		}
		return true;
	}

	private void sendIt() {
		revalidateOrders();
		if (itemsToOrder.isEmpty() || activeSessionNetwork == null)
			return;

		forcedEntries = new InventorySummary();
		InventorySummary summary = getLatestSummary();
		for (BigItemStack toOrder : itemsToOrder) {
			int count = summary.getCountOf(toOrder.stack);
			if (count == BigItemStack.INF)
				continue;
			forcedEntries.add(toOrder.stack.copy(), -1 - Math.max(0, count - toOrder.count));
		}

		CatnipServices.NETWORK.sendToServer(
			new TunablePortableTickerSendOrderPacket(menu.locator, activeChannel, activeSessionNetwork,
				PackageOrderWithCrafts.simple(new ArrayList<>(itemsToOrder)), addressBox.getValue()));
		saveRequestedAddressForActiveChannel(addressBox.getValue());
		itemsToOrder = new ArrayList<>();
		recipesToOrder = new ArrayList<>();
		successTicks = 1;
		ClientScreenStorage.manualUpdate(menu.locator, activeChannel, activeSessionNetwork);
	}

	private void saveRequestedAddressForActiveChannel(String address) {
		if (activeSessionNetwork == null)
			return;
		if (address == null || address.isBlank())
			requestedAddresses.remove(activeSessionNetwork);
		else
			requestedAddresses.put(activeSessionNetwork, address);
	}

	private void revalidateOrders() {
		InventorySummary summary = getLatestSummary();
		itemsToOrder.removeIf(entry -> {
			entry.count = Math.min(summary.getCountOf(entry.stack), entry.count);
			return entry.count <= 0;
		});
	}

	private InventorySummary getLatestSummary() {
		if (cachedSummary != null)
			return cachedSummary;

		cachedSummary = new InventorySummary();
		if (lastSeenStacks != null)
			cachedSummary.addAllBigItemStacks(lastSeenStacks);
		return cachedSummary;
	}

	private InventorySummary getPlanningSummary() {
		if (cachedPlanningSummary != null)
			return cachedPlanningSummary;

		cachedPlanningSummary = new InventorySummary();
		if (lastSeenStacks != null)
			for (BigItemStack entry : lastSeenStacks)
				cachedPlanningSummary.add(entry.stack, Math.min(entry.count, MAX_CLIENT_REPORTED_AMOUNT));
		return cachedPlanningSummary;
	}

	public List<BigItemStack> getTransferCandidates(List<Ingredient> ingredients) {
		InventorySummary summary = getPlanningSummary();
		Set<BigItemStack> added = Collections.newSetFromMap(new IdentityHashMap<>());
		List<BigItemStack> candidates = new ArrayList<>();

		for (Ingredient ingredient : ingredients) {
			if (ingredient.isEmpty())
				continue;

			for (BigItemStack entry : getMatchingStacks(summary, ingredient))
				if (added.add(entry))
					candidates.add(entry);
		}

		candidates.sort(BigItemStack.comparator());
		return candidates;
	}

	public void requestCraftable(CraftableBigItemStack cbis, int requestedDifference) {
		if (FluidLogisticsTickerCompat.hasCustomRecipeData(cbis)) {
			handleCustomFluidCraftableRequest(cbis, requestedDifference);
			return;
		}
		boolean takeOrdersAway = requestedDifference < 0;
		if (takeOrdersAway)
			requestedDifference = Math.max(-cbis.count, requestedDifference);
		if (requestedDifference == 0)
			return;

		InventorySummary availableItems = getPlanningSummary();
		Function<ItemStack, Integer> countModifier = stack -> {
			BigItemStack ordered = getOrderForItem(stack);
			return ordered == null ? 0 : -ordered.count;
		};

		if (takeOrdersAway) {
			availableItems = new InventorySummary();
			for (BigItemStack ordered : itemsToOrder)
				availableItems.add(ordered.stack, ordered.count);
			countModifier = stack -> 0;
		}

		Pair<Integer, List<List<BigItemStack>>> craftingResult =
			maxCraftable(cbis, availableItems, countModifier, takeOrdersAway ? -1 : cols - itemsToOrder.size());
		int outputCount = cbis.getOutputCount(playerInventory.player.level());
		int adjustToRecipeAmount = Mth.ceil(Math.abs(requestedDifference) / (float) outputCount) * outputCount;
		int maxCraftable = Math.min(adjustToRecipeAmount, craftingResult.getFirst());

		if (maxCraftable == 0)
			return;

		cbis.count += takeOrdersAway ? -maxCraftable : maxCraftable;

		List<List<BigItemStack>> validEntriesByIngredient = craftingResult.getSecond();
		for (List<BigItemStack> list : validEntriesByIngredient) {
			int remaining = maxCraftable / outputCount;
			for (BigItemStack entry : list) {
				if (remaining <= 0)
					break;

				int toTransfer = Math.min(remaining, entry.count);
				BigItemStack order = getOrderForItem(entry.stack);

				if (takeOrdersAway) {
					if (order != null) {
						order.count -= toTransfer;
						if (order.count == 0)
							itemsToOrder.remove(order);
					}
				} else {
					if (order == null)
						itemsToOrder.add(order = new BigItemStack(entry.stack.copyWithCount(1), 0));
					order.count += toTransfer;
				}

				remaining -= entry.count;
			}
		}

		if (cbis.count == 0)
			recipesToOrder.remove(cbis);

		updateCraftableAmounts();
	}

	public CraftableBigItemStack getRecipeOrderFor(Recipe<?> recipe) {
		for (CraftableBigItemStack cbis : recipesToOrder)
			if (cbis.recipe == recipe)
				return cbis;
		return null;
	}

	public InventorySummary getTransferPlanningSummary() {
		return getPlanningSummary();
	}

	public BigItemStack getExistingOrderFor(ItemStack stack) {
		return getOrderForItem(stack);
	}

	public boolean canFitNewRequirementTypes(List<BigItemStack> requirements) {
		int totalTypes = itemsToOrder.size();
		List<ItemStack> newTypes = new ArrayList<>();
		for (BigItemStack req : requirements) {
			boolean found = false;
			for (BigItemStack ordered : itemsToOrder) {
				if (ItemStack.isSameItemSameComponents(ordered.stack, req.stack)) {
					found = true;
					break;
				}
			}
			if (found)
				continue;
			for (ItemStack nt : newTypes) {
				if (ItemStack.isSameItemSameComponents(nt, req.stack)) {
					found = true;
					break;
				}
			}
			if (found)
				continue;
			newTypes.add(req.stack);
			totalTypes++;
			if (totalTypes > cols)
				return false;
		}
		return true;
	}

	private void handleCustomFluidCraftableRequest(CraftableBigItemStack cbis, int requestedDifference) {
		int outputCount = FluidLogisticsTickerCompat.customOutputCount(cbis);
		if (outputCount <= 0)
			return;

		boolean takeOrdersAway = requestedDifference < 0;
		if (takeOrdersAway)
			requestedDifference = Math.max(-cbis.count, requestedDifference);
		if (requestedDifference == 0)
			return;

		int requestedSets = Mth.ceil(Math.abs(requestedDifference) / (float) outputCount);
		int applicableSets;

		if (takeOrdersAway) {
			applicableSets = Math.min(requestedSets, cbis.count / outputCount);
		} else {
			InventorySummary availableItems = getPlanningSummary();
			List<BigItemStack> customReqs = FluidLogisticsTickerCompat.customRequirements(cbis);
			if (!canFitNewRequirementTypes(customReqs))
				return;
			applicableSets = FluidLogisticsTickerCompat.getCustomCraftableSets(availableItems, itemsToOrder, customReqs);
			applicableSets = Math.min(requestedSets, applicableSets);
		}

		if (applicableSets <= 0)
			return;

		int amountDelta = applicableSets * outputCount;
		cbis.count += takeOrdersAway ? -amountDelta : amountDelta;

		List<BigItemStack> customReqs = FluidLogisticsTickerCompat.customRequirements(cbis);
		for (BigItemStack requirement : customReqs) {
			int delta = requirement.count * applicableSets;
			BigItemStack existingOrder = getOrderForItem(requirement.stack);

			if (takeOrdersAway) {
				if (existingOrder == null)
					continue;
				existingOrder.count -= delta;
				if (existingOrder.count <= 0)
					itemsToOrder.remove(existingOrder);
				continue;
			}

			if (existingOrder == null) {
				existingOrder = new BigItemStack(requirement.stack.copyWithCount(1), 0);
				itemsToOrder.add(existingOrder);
			}
			existingOrder.count += delta;
		}

		if (cbis.count <= 0)
			recipesToOrder.remove(cbis);
		updateCraftableAmountsWithCustomEntries();
	}

	private void updateCraftableAmountsWithCustomEntries() {
		InventorySummary usedItems = new InventorySummary();
		InventorySummary availableItems = new InventorySummary();

		for (BigItemStack ordered : itemsToOrder)
			availableItems.add(ordered.stack, ordered.count);

		for (CraftableBigItemStack cbis : recipesToOrder) {
			if (FluidLogisticsTickerCompat.hasCustomRecipeData(cbis)) {
				int outputCount = FluidLogisticsTickerCompat.customOutputCount(cbis);
				if (outputCount <= 0) {
					cbis.count = 0;
					continue;
				}

				int maxSets = FluidLogisticsTickerCompat.getCustomCraftableSets(
					availableItems, usedItems, FluidLogisticsTickerCompat.customRequirements(cbis));
				cbis.count = Math.min(cbis.count, maxSets * outputCount);

				int committedSets = cbis.count / outputCount;
				for (BigItemStack req : FluidLogisticsTickerCompat.customRequirements(cbis)) {
					usedItems.add(req.stack, req.count * committedSets);
				}
				continue;
			}

			Pair<Integer, List<List<BigItemStack>>> craftingResult =
				maxCraftable(cbis, availableItems, stack -> -usedItems.getCountOf(stack), -1);
			int maxCraftable = craftingResult.getFirst();
			List<List<BigItemStack>> validEntriesByIngredient = craftingResult.getSecond();
			int outputCount = cbis.getOutputCount(playerInventory.player.level());

			cbis.count = Math.min(cbis.count, maxCraftable);

			for (List<BigItemStack> list : validEntriesByIngredient) {
				int remaining = cbis.count / outputCount;
				for (BigItemStack entry : list) {
					if (remaining <= 0)
						break;
					usedItems.add(entry.stack, Math.min(remaining, entry.count));
					remaining -= entry.count;
				}
			}
		}
	}

	private void updateCraftableAmounts() {
		for (CraftableBigItemStack cbis : recipesToOrder) {
			if (FluidLogisticsTickerCompat.hasCustomRecipeData(cbis)) {
				updateCraftableAmountsWithCustomEntries();
				return;
			}
		}
		InventorySummary usedItems = new InventorySummary();
		InventorySummary availableItems = new InventorySummary();

		for (BigItemStack ordered : itemsToOrder)
			availableItems.add(ordered.stack, ordered.count);

		for (CraftableBigItemStack cbis : recipesToOrder) {
			Pair<Integer, List<List<BigItemStack>>> craftingResult =
				maxCraftable(cbis, availableItems, stack -> -usedItems.getCountOf(stack), -1);
			int maxCraftable = craftingResult.getFirst();
			List<List<BigItemStack>> validEntriesByIngredient = craftingResult.getSecond();
			int outputCount = cbis.getOutputCount(playerInventory.player.level());

			cbis.count = Math.min(cbis.count, maxCraftable);

			for (List<BigItemStack> list : validEntriesByIngredient) {
				int remaining = cbis.count / outputCount;
				for (BigItemStack entry : list) {
					if (remaining <= 0)
						break;
					usedItems.add(entry.stack, Math.min(remaining, entry.count));
					remaining -= entry.count;
				}
			}
		}
	}

	private Pair<Integer, List<List<BigItemStack>>> maxCraftable(CraftableBigItemStack cbis, InventorySummary summary,
		Function<ItemStack, Integer> countModifier, int newTypeLimit) {
		List<Ingredient> ingredients = cbis.getIngredients();
		List<List<BigItemStack>> validEntriesByIngredient = new ArrayList<>();
		List<BigItemStack> alreadyCreated = new ArrayList<>();

		for (Ingredient ingredient : ingredients) {
			if (ingredient.isEmpty())
				continue;

			List<BigItemStack> valid = new ArrayList<>();
			Entries: for (BigItemStack entry : getMatchingStacks(summary, ingredient)) {
				for (BigItemStack visitedStack : alreadyCreated) {
					if (!ItemStack.isSameItemSameComponents(visitedStack.stack, entry.stack))
						continue;
					valid.add(visitedStack);
					continue Entries;
				}
				BigItemStack asBis = new BigItemStack(entry.stack,
					summary.getCountOf(entry.stack) + countModifier.apply(entry.stack));
				if (asBis.count > 0) {
					valid.add(asBis);
					alreadyCreated.add(asBis);
				}
			}

			if (valid.isEmpty())
				return Pair.of(0, List.of());

			valid.sort((bis1, bis2) -> -Integer.compare(summary.getCountOf(bis1.stack), summary.getCountOf(bis2.stack)));
			validEntriesByIngredient.add(valid);
		}

		if (newTypeLimit != -1) {
			int toRemove = (int) validEntriesByIngredient.stream()
				.flatMap(List::stream)
				.filter(entry -> getOrderForItem(entry.stack) == null)
				.distinct()
				.count() - newTypeLimit;
			for (int i = 0; i < toRemove; i++)
				removeLeastEssentialItemStack(validEntriesByIngredient);
		}

		validEntriesByIngredient = resolveIngredientAmounts(validEntriesByIngredient);

		int minCount = Integer.MAX_VALUE;
		for (List<BigItemStack> list : validEntriesByIngredient) {
			int sum = 0;
			for (BigItemStack entry : list)
				sum += entry.count;
			minCount = Math.min(sum, minCount);
		}

		if (minCount == 0)
			return Pair.of(0, List.of());

		int outputCount = cbis.getOutputCount(playerInventory.player.level());
		return Pair.of(minCount * outputCount, validEntriesByIngredient);
	}

	private List<BigItemStack> getMatchingStacks(InventorySummary summary, Ingredient ingredient) {
		Set<BigItemStack> matches = Collections.newSetFromMap(new IdentityHashMap<>());
		List<BigItemStack> orderedMatches = new ArrayList<>();

		for (ItemStack matchingStack : ingredient.getItems()) {
			List<BigItemStack> availableStacks = summary.getItemMap().get(matchingStack.getItem());
			if (availableStacks == null)
				continue;
			for (BigItemStack entry : availableStacks) {
				if (!ingredient.test(entry.stack) || !matches.add(entry))
					continue;
				orderedMatches.add(entry);
			}
		}

		return orderedMatches;
	}

	private void removeLeastEssentialItemStack(List<List<BigItemStack>> validIngredients) {
		List<BigItemStack> longest = null;
		int most = 0;
		for (List<BigItemStack> list : validIngredients) {
			int count = (int) list.stream()
				.filter(entry -> getOrderForItem(entry.stack) == null)
				.count();
			if (longest != null && count <= most)
				continue;
			longest = list;
			most = count;
		}

		if (longest == null || longest.isEmpty())
			return;

		BigItemStack chosen = null;
		for (int i = 0; i < longest.size(); i++) {
			BigItemStack entry = longest.get(longest.size() - 1 - i);
			if (getOrderForItem(entry.stack) != null)
				continue;
			chosen = entry;
			break;
		}

		for (List<BigItemStack> list : validIngredients)
			list.remove(chosen);
	}

	private List<List<BigItemStack>> resolveIngredientAmounts(List<List<BigItemStack>> validIngredients) {
		List<List<BigItemStack>> resolvedIngredients = new ArrayList<>();
		for (int i = 0; i < validIngredients.size(); i++)
			resolvedIngredients.add(new ArrayList<>());

		boolean everythingTaken = false;
		while (!everythingTaken) {
			everythingTaken = true;
			Ingredients:
			for (int i = 0; i < validIngredients.size(); i++) {
				List<BigItemStack> list = validIngredients.get(i);
				List<BigItemStack> resolvedList = resolvedIngredients.get(i);
				for (BigItemStack bigItemStack : list) {
					if (bigItemStack.count == 0)
						continue;

					bigItemStack.count -= 1;
					everythingTaken = false;

					for (BigItemStack resolvedItemStack : resolvedList) {
						if (resolvedItemStack.stack == bigItemStack.stack) {
							resolvedItemStack.count++;
							continue Ingredients;
						}
					}

					resolvedList.add(new BigItemStack(bigItemStack.stack, 1));
					continue Ingredients;
				}
			}
		}

		return resolvedIngredients;
	}

	@Nullable
	private BigItemStack getOrderForItem(ItemStack stack) {
		for (BigItemStack entry : itemsToOrder)
			if (ItemStack.isSameItemSameComponents(stack, entry.stack))
				return entry;
		return null;
	}

	private Couple<Integer> getHoveredSlot(int x, int y) {
		x += 1;
		if (x < itemsX || x >= itemsX + cols * colWidth)
			return noneHovered;
		if (y >= orderY && y < orderY + rowHeight) {
			int col = (x - itemsX) / colWidth;
			return col >= 0 && col < itemsToOrder.size() ? Couple.create(-1, col) : noneHovered;
		}
		if (y >= orderY - 31 && y < orderY - 31 + rowHeight) {
			int jeiX = getGuiLeft() + (windowWidth - colWidth * recipesToOrder.size()) / 2 + 1;
			int col = Mth.floorDiv(x - jeiX, colWidth);
			if (col >= 0 && col < recipesToOrder.size())
				return Couple.create(-2, col);
		}
		if (y < getGuiTop() + 16 || y > getGuiTop() + windowHeight - 80 || !itemScroll.settled())
			return noneHovered;

		int localY = y - itemsY;
		for (int categoryIndex = 0; categoryIndex < displayedItems.size(); categoryIndex++) {
			CategoryEntry entry = categories.isEmpty() ? new CategoryEntry(0, "") : categories.get(categoryIndex);
			if (entry.hidden)
				continue;
			int row =
				Mth.floor((localY - (categories.isEmpty() ? 4 : rowHeight) - entry.y) / (float) rowHeight + itemScroll.getChaseTarget());
			int col = (x - itemsX) / colWidth;
			int slot = row * cols + col;
			if (slot >= 0 && slot < displayedItems.get(categoryIndex).size())
				return Couple.create(categoryIndex, slot);
		}
		return noneHovered;
	}

	private void clampScrollBar() {
		float clamped = Mth.clamp(itemScroll.getChaseTarget(), 0, getMaxScroll());
		if (clamped != itemScroll.getChaseTarget())
			itemScroll.startWithValue(clamped);
	}

	private int getMaxScroll() {
		int visibleHeight = windowHeight - 84;
		int totalRows = 2;
		for (int i = 0; i < displayedItems.size(); i++) {
			if (displayedItems.get(i).isEmpty())
				continue;
			totalRows++;
			if (categories.size() > i && categories.get(i).hidden)
				continue;
			totalRows += (int) Math.ceil(displayedItems.get(i).size() / (float) cols);
		}
		return Math.max(0, (totalRows * rowHeight - visibleHeight + 50) / rowHeight);
	}

	private boolean isConfirmHovered(double mouseX, double mouseY) {
		return mouseX >= getGuiLeft() + 143 && mouseX < getGuiLeft() + 221
			&& mouseY >= getGuiTop() + windowHeight - 39 && mouseY < getGuiTop() + windowHeight - 21;
	}

	private Component getTroubleshootingMessage() {
		if (lastSeenStacks == null)
			return CreateLang.translate("gui.stock_keeper.checking_stocks").component();
		if (lastSeenStacks.isEmpty())
			return CreateLang.translate("gui.stock_keeper.inventories_empty").component();
		return CreateLang.translate("gui.stock_keeper.no_search_results").component();
	}

	private void syncRecipeViewers() {
		if (searchBox == null || !Mods.JEI.isLoaded())
			return;
		if (AllConfigs.client().syncRecipeViewerSearch.get() == SearchSyncMode.NONE)
			return;
		try {
			Object runtime = CPJEI.runtime;
			if (runtime == null) {
				Class<?> jeiClass = Class.forName("com.simibubi.create.compat.jei.CreateJEI");
				runtime = jeiClass.getField("runtime").get(null);
			}
			if (runtime == null)
				return;
			Object filter = runtime.getClass().getMethod("getIngredientFilter").invoke(runtime);
			filter.getClass().getMethod("setFilterText", String.class)
				.invoke(filter, Objects.requireNonNullElse(searchBox.getValue(), ""));
		} catch (Throwable t) {
			CreatePhantom.LOGGER.debug("JEI search sync failed", t);
		}
	}

	@Override
	public void removed() {
		sendHiddenCategories();
		ClientScreenStorage.close();
		super.removed();
	}

	@Override
	public List<Rect2i> getExtraAreas() {
		return extraAreas;
	}

	public Optional<Pair<ItemStack, Rect2i>> getHoveredIngredient(int mouseX, int mouseY) {
		Couple<Integer> hovered = getHoveredSlot(mouseX, mouseY);
		if (hovered == noneHovered)
			return Optional.empty();

		int x;
		int y;
		BigItemStack entry;
		if (hovered.getFirst() == -2) {
			int jeiX = getGuiLeft() + (windowWidth - colWidth * recipesToOrder.size()) / 2 + 1;
			x = jeiX + hovered.getSecond() * colWidth;
			y = orderY - 31;
			entry = recipesToOrder.get(hovered.getSecond());
		} else if (hovered.getFirst() == -1) {
			x = itemsX + hovered.getSecond() * colWidth;
			y = orderY;
			entry = itemsToOrder.get(hovered.getSecond());
		} else {
			int categoryY = categories.isEmpty() ? 0 : categories.get(hovered.getFirst()).y;
			x = itemsX + (hovered.getSecond() % cols) * colWidth;
			y = itemsY + categoryY + (categories.isEmpty() ? 4 : rowHeight) + (hovered.getSecond() / cols) * rowHeight;
			entry = displayedItems.get(hovered.getFirst()).get(hovered.getSecond());
		}
		return Optional.of(Pair.of(entry.stack.copy(), new Rect2i(x, y, 18, 18)));
	}
}
