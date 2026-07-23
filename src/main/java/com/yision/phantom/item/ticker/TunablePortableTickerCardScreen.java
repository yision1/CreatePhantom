package com.yision.phantom.item.ticker;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.yision.phantom.item.storagecard.StorageChannelExtensionCardItem;
import com.yision.phantom.item.ticker.TunablePortableTickerItem;
import com.yision.phantom.network.ticker.TunablePortableTickerCardEditPacket;
import com.yision.phantom.network.ticker.TunablePortableTickerCardRefundPacket;
import com.yision.phantom.network.ticker.TunablePortableTickerCardSlotPacket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public class TunablePortableTickerCardScreen extends AbstractSimiContainerScreen<TunablePortableTickerCardMenu> {
	private static final int CARD_HEADER = 20;
	private static final int CARD_WIDTH = 160;
	private static final int SLICES = 4;

	private List<Rect2i> extraAreas = Collections.emptyList();
	private final LerpedFloat scroll = LerpedFloat.linear()
		.startWithValue(0);
	private final List<ItemStack> cards;

	private IconButton confirmButton;
	private ItemStack editingItem;
	private int editingIndex;
	private IconButton editorConfirm;
	private EditBox editorEditBox;

	private final Component clickToEdit = Component.translatable("create.gui.schedule.lmb_edit")
		.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);

	public TunablePortableTickerCardScreen(TunablePortableTickerCardMenu menu, Inventory inventory,
		Component title) {
		super(menu, inventory, title);
		cards = new ArrayList<>(menu.getInitialCards());
	}

	@Override
	protected void init() {
		AllGuiTextures bg = AllGuiTextures.STOCK_KEEPER_CATEGORY;
		setWindowSize(bg.getWidth(), bg.getHeight() * SLICES + AllGuiTextures.STOCK_KEEPER_CATEGORY_HEADER.getHeight()
			+ AllGuiTextures.STOCK_KEEPER_CATEGORY_FOOTER.getHeight());
		super.init();
		clearWidgets();

		confirmButton = new IconButton(leftPos + bg.getWidth() - 25, topPos + imageHeight - 25, AllIcons.I_CONFIRM);
		confirmButton.withCallback(() -> minecraft.player.closeContainer());
		addRenderableWidget(confirmButton);

		stopEditing(false);
		extraAreas = ImmutableList.of(new Rect2i(leftPos + bg.getWidth(), topPos + imageHeight - 40, 48, 40));
	}

	protected void startEditing(int index) {
		if (index == -1 && cards.size() >= TunablePortableTickerItem.MAX_CHANNELS)
			return;

		confirmButton.visible = false;
		editorConfirm = new IconButton(leftPos + 36 + 131, topPos + 59, AllIcons.I_CONFIRM);
		menu.slotsActive = true;

		editorEditBox = new EditBox(font, leftPos + 47, topPos + 28, 124, 10, Component.empty());
		editorEditBox.setTextColor(0xffeeeeee);
		editorEditBox.setBordered(false);
		editorEditBox.setFocused(false);
		editorEditBox.mouseClicked(0, 0, 0);
		editorEditBox.setMaxLength(28);

		editingIndex = index;
		editingItem = index == -1 ? ItemStack.EMPTY : cards.get(index)
			.copy();
		editorEditBox.setValue(editingItem.has(DataComponents.CUSTOM_NAME)
			? editingItem.getHoverName()
				.getString()
			: "");

		menu.proxyInventory.setStackInSlot(0, editingItem.copy());
		CatnipServices.NETWORK.sendToServer(new TunablePortableTickerCardSlotPacket(index, true, ""));

		addRenderableWidget(editorConfirm);
		addRenderableWidget(editorEditBox);
	}

	protected void stopEditing(boolean reinit) {
		confirmButton.visible = true;
		if (editingItem == null) {
			menu.slotsActive = false;
			return;
		}

		playUiSound(SoundEvents.UI_BUTTON_CLICK.value(), 1, 1);
		removeWidget(editorConfirm);
		removeWidget(editorEditBox);

		ItemStack stackInSlot = menu.proxyInventory.getStackInSlot(0)
			.copy();
		if (stackInSlot.isEmpty() && editingIndex != -1)
			cards.remove(editingIndex);

		if (!stackInSlot.isEmpty()) {
			stackInSlot.setCount(1);
			String value = editorEditBox.getValue()
				.trim();
			stackInSlot.set(DataComponents.CUSTOM_NAME, value.isBlank() ? null : Component.literal(value));
			if (editingIndex == -1)
				cards.add(stackInSlot);
			else
				cards.set(editingIndex, stackInSlot);
		}

		CatnipServices.NETWORK.sendToServer(
			new TunablePortableTickerCardSlotPacket(editingIndex, false, editorEditBox.getValue()));
		editingItem = null;
		editorConfirm = null;
		editorEditBox = null;
		menu.slotsActive = false;
		if (reinit)
			init();
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		scroll.tickChaser();
		if (editorEditBox == null || editorEditBox.isFocused())
			return;
		ItemStack stackInSlot = menu.proxyInventory.getStackInSlot(0);
		if (editorEditBox.getValue()
			.isBlank() && stackInSlot.has(DataComponents.CUSTOM_NAME))
			editorEditBox.setValue(stackInSlot.getHoverName()
				.getString());
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
		partialTicks = AnimationTickHolder.getPartialTicksUI();
		if (menu.slotsActive)
			super.render(graphics, mouseX, mouseY, partialTicks);
		else {
			renderBackground(graphics, mouseX, mouseY, partialTicks);
			renderBg(graphics, partialTicks, mouseX, mouseY);
			for (Renderable widget : renderables)
				widget.render(graphics, mouseX, mouseY, partialTicks);
			renderForeground(graphics, mouseX, mouseY, partialTicks);
		}
		renderEditingTooltips(graphics, mouseX, mouseY);
	}

	protected void renderCards(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
		PoseStack pose = graphics.pose();
		int yOffset = 25;
		float scrollOffset = -scroll.getValue(partialTicks);

		graphics.enableScissor(leftPos + 3, topPos + 16, leftPos + 187,
			topPos + 19 + AllGuiTextures.STOCK_KEEPER_CATEGORY.getHeight() * SLICES);

		for (int i = 0; i <= cards.size(); i++) {
			pose.pushPose();
			pose.translate(0, scrollOffset, 0);

			if (i == cards.size()) {
				if (cards.size() < TunablePortableTickerItem.MAX_CHANNELS)
					AllGuiTextures.STOCK_KEEPER_CATEGORY_NEW.render(graphics, leftPos + 7, topPos + yOffset);
				pose.popPose();
				break;
			}

			ItemStack card = cards.get(i);
			yOffset += renderCardEntry(graphics, i, card, yOffset);
			pose.popPose();
		}

		graphics.disableScissor();
	}

	public int renderCardEntry(GuiGraphics graphics, int index, ItemStack card, int yOffset) {
		PoseStack pose = graphics.pose();
		pose.pushPose();
		pose.translate(leftPos + 7, topPos + yOffset, 0);

		AllGuiTextures.STOCK_KEEPER_CATEGORY_ENTRY.render(graphics, 0, 0);
		if (index > 0)
			AllGuiTextures.STOCK_KEEPER_CATEGORY_UP.render(graphics, CARD_WIDTH + 12, CARD_HEADER - 18);
		if (index < cards.size() - 1)
			AllGuiTextures.STOCK_KEEPER_CATEGORY_DOWN.render(graphics, CARD_WIDTH + 12, CARD_HEADER - 9);

		graphics.renderItem(noGlintDisplayCopy(card), 14, 1);
		String name = trimmedCardName(card);
		graphics.drawString(font, name, 35, 5, 0x656565, false);

		pose.popPose();
		return CARD_HEADER;
	}

	private ItemStack noGlintDisplayCopy(ItemStack card) {
		ItemStack displayStack = card.copy();
		displayStack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
		return displayStack;
	}

	private String trimmedCardName(ItemStack card) {
		String name = displayName(card).getString();
		return name.length() > 20 ? name.substring(0, 20).stripTrailing() + "..." : name.stripTrailing();
	}

	private Component displayName(ItemStack card) {
		if (card.isEmpty() || !card.has(DataComponents.CUSTOM_NAME))
			return Component.translatable("gui.createphantom.tunable_portable_ticker.unnamed_card");
		return card.getHoverName();
	}

	public boolean action(@Nullable GuiGraphics graphics, double mouseX, double mouseY, int click) {
		if (mouseX < leftPos || mouseX >= leftPos + imageWidth || mouseY < topPos + 15 || mouseY >= topPos + 99)
			return false;
		if (editingItem != null)
			return false;

		int mx = (int) mouseX;
		int my = (int) mouseY;
		int x = mx - leftPos - 20;
		int y = my - topPos - 24;
		if (x < 0 || x >= 196)
			return false;
		if (y < 0 || y >= 143)
			return false;
		y += scroll.getValue(0);

		for (int i = 0; i < cards.size(); i++) {
			ItemStack card = cards.get(i);
			if (y >= CARD_HEADER) {
				y -= CARD_HEADER;
				continue;
			}

			int fieldSize = 140;
			if (x > 0 && x <= fieldSize && y > 0 && y <= 16) {
				renderActionTooltip(graphics, List.of(displayName(card), clickToEdit), mx, my);
				if (click == 0)
					startEditing(i);
				return true;
			}

			if (x > fieldSize && x <= fieldSize + 16 && y > 0 && y <= 16) {
				renderActionTooltip(graphics,
					ImmutableList.of(Component.translatable("gui.createphantom.tunable_portable_ticker.delete_card")),
					mx, my);
				if (click == 0) {
					CatnipServices.NETWORK.sendToServer(new TunablePortableTickerCardRefundPacket(i));
					cards.remove(i);
					init();
				}
				return true;
			}

			if (x > 158 && x < 170) {
				if (y > 2 && y <= 10 && i > 0) {
					renderActionTooltip(graphics,
						ImmutableList.of(Component.translatable("create.gui.schedule.move_up"),
							Component.translatable("create.gui.stock_ticker.shift_moves_top")
								.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)),
						mx, my);
					if (click == 0) {
						int destination = hasShiftDown() ? 0 : i - 1;
						CatnipServices.NETWORK.sendToServer(
							new TunablePortableTickerCardEditPacket(i, destination));
						cards.remove(card);
						cards.add(destination, card);
						init();
					}
					return true;
				}
				if (y > 10 && y <= 22 && i < cards.size() - 1) {
					renderActionTooltip(graphics,
						ImmutableList.of(Component.translatable("create.gui.schedule.move_down"),
							Component.translatable("create.gui.stock_ticker.shift_moves_bottom")
								.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)),
						mx, my);
					if (click == 0) {
						int destination = hasShiftDown() ? cards.size() - 1 : i + 1;
						CatnipServices.NETWORK.sendToServer(
							new TunablePortableTickerCardEditPacket(i, destination));
						cards.remove(card);
						cards.add(destination, card);
						init();
					}
					return true;
				}
			}

			x -= 18;
			y -= 28;
			if (x < 0 || y < 0 || x > 160)
				return false;
		}

		if (cards.size() >= TunablePortableTickerItem.MAX_CHANNELS)
			return false;

		if (x > 0 && x <= 16 && y > 0 && y <= 16) {
			renderActionTooltip(graphics,
				ImmutableList.of(Component.translatable("gui.createphantom.tunable_portable_ticker.new_card")),
				mx, my);
			if (click == 0) {
				playUiSound(SoundEvents.UI_BUTTON_CLICK.value(), 1f, 1f);
				startEditing(-1);
			}
			return true;
		}

		return false;
	}

	private void renderActionTooltip(@Nullable GuiGraphics graphics, List<Component> tooltip, int mx, int my) {
		if (graphics != null)
			graphics.renderTooltip(font, tooltip, Optional.empty(), mx, my);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (editorConfirm != null && editorConfirm.isMouseOver(mouseX, mouseY)) {
			stopEditing(true);
			return true;
		}
		if (action(null, mouseX, mouseY, button)) {
			playUiSound(SoundEvents.UI_BUTTON_CLICK.value(), 1f, 1f);
			return true;
		}

		boolean wasNotFocused = editorEditBox != null && !editorEditBox.isFocused();
		boolean mouseClicked = super.mouseClicked(mouseX, mouseY, button);

		if (editorEditBox != null && editorEditBox.isMouseOver(mouseX, mouseY) && wasNotFocused) {
			editorEditBox.moveCursorToEnd(false);
			editorEditBox.setHighlightPos(0);
		}

		return mouseClicked;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (editingItem == null)
			return super.keyPressed(keyCode, scanCode, modifiers);

		InputConstants.Key mouseKey = InputConstants.getKey(keyCode, scanCode);
		boolean hitEscape = keyCode == GLFW.GLFW_KEY_ESCAPE;
		boolean hitEnter = getFocused() instanceof EditBox && (keyCode == 257 || keyCode == 335);
		boolean hitE = getFocused() == null && minecraft.options.keyInventory.isActiveAndMatches(mouseKey);
		if (hitE || hitEnter || hitEscape) {
			stopEditing(true);
			return true;
		}

		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (editingItem != null)
			return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);

		float chaseTarget = scroll.getChaseTarget();
		float max = 40 - (3 + AllGuiTextures.STOCK_KEEPER_CATEGORY.getHeight() * SLICES);
		max += cards.size() * CARD_HEADER + (cards.size() < TunablePortableTickerItem.MAX_CHANNELS ? 24 : 0);
		if (max > 0) {
			chaseTarget -= (float) (scrollY * 12);
			chaseTarget = Mth.clamp(chaseTarget, 0, max);
			scroll.chase((int) chaseTarget, 0.7f, Chaser.EXP);
		} else
			scroll.chase(0, 0.7f, Chaser.EXP);

		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
		super.renderForeground(graphics, mouseX, mouseY, partialTicks);

		GuiGameElement.of(menu.openedStack)
			.<GuiGameElement.GuiRenderBuilder>at(leftPos + AllGuiTextures.STOCK_KEEPER_CATEGORY.getWidth() + 12,
				topPos + imageHeight - 39, -190)
			.scale(3)
			.render(graphics);

		action(graphics, mouseX, mouseY, -1);
		if (editingItem == null)
			return;

	}

	private void renderEditingTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
		if (editingItem == null)
			return;

		if (menu.proxyInventory.getStackInSlot(0)
			.isEmpty() && isMouseIn(mouseX, mouseY, leftPos + 16, topPos + 24, 16, 16)) {
			graphics.renderComponentTooltip(font, List.of(
				Component.translatable(editingIndex == -1
						? "gui.createphantom.tunable_portable_ticker.new_card"
						: "gui.createphantom.tunable_portable_ticker.card_slot")
					.withStyle(style -> style.withColor(ScrollInput.HEADER_RGB.getRGB())),
				Component.translatable("gui.createphantom.tunable_portable_ticker.card_slot_tip")
					.withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
			return;
		}

		if (editorEditBox != null && editorEditBox.isMouseOver(mouseX, mouseY) && !editorEditBox.isFocused()) {
			graphics.renderComponentTooltip(font, List.of(
				Component.translatable("gui.createphantom.tunable_portable_ticker.card_name")
					.withStyle(style -> style.withColor(ScrollInput.HEADER_RGB.getRGB())),
				clickToEdit), mouseX, mouseY);
		}
	}

	private boolean isMouseIn(int mouseX, int mouseY, int x, int y, int width, int height) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	@Override
	protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		int y = topPos;
		AllGuiTextures.STOCK_KEEPER_CATEGORY_HEADER.render(graphics, leftPos, y);
		y += AllGuiTextures.STOCK_KEEPER_CATEGORY_HEADER.getHeight();
		for (int i = 0; i < SLICES; i++) {
			AllGuiTextures.STOCK_KEEPER_CATEGORY.render(graphics, leftPos, y);
			y += AllGuiTextures.STOCK_KEEPER_CATEGORY.getHeight();
		}
		AllGuiTextures.STOCK_KEEPER_CATEGORY_FOOTER.render(graphics, leftPos, y);
		AllGuiTextures.STOCK_KEEPER_CATEGORY_SAYS.render(graphics, leftPos + imageWidth - 6, y + 7);

		FormattedCharSequence titleText = Component.translatable("gui.createphantom.tunable_portable_ticker.cards")
			.getVisualOrderText();
		int center = leftPos + AllGuiTextures.STOCK_KEEPER_CATEGORY.getWidth() / 2;
		graphics.drawString(font, titleText, center - font.width(titleText) / 2, topPos + 4, 0x3D3C48, false);

		if (editingItem == null) {
			renderCards(graphics, mouseX, mouseY, partialTick);
			return;
		}

		graphics.fillGradient(0, 0, width, height, -1072689136, -804253680);

		y = topPos - 5;
		AllGuiTextures.STOCK_KEEPER_CATEGORY_HEADER.render(graphics, leftPos, y);
		y += AllGuiTextures.STOCK_KEEPER_CATEGORY_HEADER.getHeight();
		AllGuiTextures.STOCK_KEEPER_CATEGORY_EDIT.render(graphics, leftPos, y);
		y += AllGuiTextures.STOCK_KEEPER_CATEGORY_EDIT.getHeight();
		AllGuiTextures.STOCK_KEEPER_CATEGORY_FOOTER.render(graphics, leftPos, y);

		renderPlayerInventory(graphics, leftPos + 10, topPos + 88);

		FormattedCharSequence editorTitle = Component.translatable("gui.createphantom.tunable_portable_ticker.card_editor")
			.getVisualOrderText();
		graphics.drawString(font, editorTitle, center - font.width(editorTitle) / 2, topPos - 1, 0x3D3C48, false);
	}

	@Override
	public void removed() {
		super.removed();
	}

	@Override
	protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
		List<Component> tooltip = super.getTooltipFromContainerItem(stack);
		if (!(hoveredSlot instanceof SlotItemHandler))
			return tooltip;

		if (!tooltip.isEmpty())
			tooltip.set(0, Component.translatable("gui.createphantom.tunable_portable_ticker.card_slot")
				.withStyle(style -> style.withColor(ScrollInput.HEADER_RGB.getRGB())));

		if (!stack.isEmpty() && stack.getItem() instanceof StorageChannelExtensionCardItem) {
			if (!StorageChannelExtensionCardItem.isLinked(stack))
				tooltip.add(Component.translatable("gui.createphantom.tunable_portable_ticker.unlinked_card_tip")
					.withStyle(ChatFormatting.GRAY));
		}
		return tooltip;
	}

	@Override
	public List<Rect2i> getExtraAreas() {
		return extraAreas;
	}

	public Font getFont() {
		return font;
	}
}
