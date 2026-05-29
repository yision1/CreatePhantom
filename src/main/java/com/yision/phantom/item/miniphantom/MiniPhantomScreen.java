package com.yision.phantom.item.miniphantom;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.trains.station.NoShadowFontWrapper;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.utility.CreateLang;
import com.yision.phantom.CreatePhantom;
import com.yision.phantom.client.gui.address.AddressSuggestionEditBox;
import com.yision.phantom.item.storagecard.StorageChannelExtensionCardItem;
import com.yision.phantom.network.phantom.MiniPhantomConfirmPacket;
import java.util.List;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class MiniPhantomScreen extends AbstractSimiContainerScreen<MiniPhantomMenu> {
	private static final ResourceLocation MINI_PHANTOM_GUI =
		CreatePhantom.asResource("textures/gui/mini_phantom_gui.png");
	private static final int TEXTURE_SIZE = 256;
	private static final int GUI_U = 16;
	private static final int GUI_V = 16;
	private static final int GUI_WIDTH = 232;
	private static final int GUI_HEIGHT = 120;

	private AddressSuggestionEditBox addressBox;
	private IconButton confirmButton;
	private final Inventory playerInventory;
	private List<Rect2i> extraAreas = List.of();
	private ItemStack lastClipboardStack = ItemStack.EMPTY;

	public MiniPhantomScreen(MiniPhantomMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.playerInventory = inventory;
	}

	@Override
	protected void init() {
		setWindowSize(GUI_WIDTH, GUI_HEIGHT + AllGuiTextures.PLAYER_INVENTORY.getHeight());
		super.init();
		clearWidgets();

		int x = getGuiLeft();
		int y = getGuiTop();
		extraAreas = List.of(new Rect2i(x + GUI_WIDTH, y + GUI_HEIGHT - 50, 70, 60));

		String previousAddress = addressBox == null ? menu.initialAddress : addressBox.getValue();
		addressBox = new AddressSuggestionEditBox(this, new NoShadowFontWrapper(font), x + 55, y + 68, 110, 10, false,
			null, clipboardAddresses(), false);
		addressBox.setValue(previousAddress);
		addressBox.setTextColor(0x3D3C48);
		addRenderableWidget(addressBox);

		confirmButton = new IconButton(x + GUI_WIDTH - 30, y + GUI_HEIGHT - 25, AllIcons.I_CONFIRM);
		confirmButton.withCallback(() -> CatnipServices.NETWORK.sendToServer(new MiniPhantomConfirmPacket(addressBox.getValue())));
		addRenderableWidget(confirmButton);

		lastClipboardStack = menu.getClipboardStack().copy();
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		addressBox.tick();
		rebuildAddressBoxIfClipboardChanged();
	}

	private void rebuildAddressBoxIfClipboardChanged() {
		ItemStack currentClipboard = menu.getClipboardStack();
		if (ItemStack.matches(lastClipboardStack, currentClipboard)) {
			return;
		}
		lastClipboardStack = currentClipboard.copy();

		String previousValue = addressBox.getValue();
		removeWidget(addressBox);

		int x = getGuiLeft();
		int y = getGuiTop();
		addressBox = new AddressSuggestionEditBox(this, new NoShadowFontWrapper(font), x + 55, y + 68, 110, 10, false,
			null, clipboardAddresses(), false);
		addressBox.setValue(previousValue);
		addressBox.setTextColor(0x3D3C48);
		addRenderableWidget(addressBox);
	}

	private List<String> clipboardAddresses() {
		ItemStack clipboard = menu.getClipboardStack();
		if (clipboard.isEmpty() || !AllBlocks.CLIPBOARD.isIn(clipboard)) {
			return List.of();
		}
		return StorageChannelExtensionCardItem.extractAddresses(clipboard.getComponents());
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (addressBox.isFocused()) {
			if (addressBox.mouseClicked(mouseX, mouseY, button))
				return true;
			addressBox.setFocused(false);
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (addressBox.mouseScrolled(mouseX, mouseY, scrollX, scrollY))
			return true;
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (addressBox.isFocused() && addressBox.keyPressed(keyCode, scanCode, modifiers))
			return true;
		return addressBox.isFocused() && keyCode != 256 || super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char codePoint, int modifiers) {
		if (addressBox.isFocused() && addressBox.charTyped(codePoint, modifiers))
			return true;
		return super.charTyped(codePoint, modifiers);
	}

	@Override
	protected boolean isHovering(int x, int y, int width, int height, double mouseX, double mouseY) {
		if (addressBox.isFocused())
			return false;
		return super.isHovering(x, y, width, height, mouseX, mouseY);
	}

	@Override
	protected void renderBg(@NotNull GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
		int x = getGuiLeft();
		int y = getGuiTop();

		graphics.blit(MINI_PHANTOM_GUI, x + 3, y, GUI_U, GUI_V, GUI_WIDTH, GUI_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);
		renderPlayerInventory(graphics, x + 25, y + 124);

		Component title = CreateLang.text(menu.openedStack.getHoverName().getString()).component();
		graphics.drawString(font, title, x + 117 - font.width(title) / 2, y + 4, 0x3D3C48, false);
		GuiGameElement.of(new ItemStack(menu.openedStack.getItem()))
			.<GuiGameElement.GuiRenderBuilder>at(x + 245, y + 80, 0)
			.scale(3)
			.render(graphics);

		if (addressBox.getValue().isBlank() && !addressBox.isFocused())
			graphics.drawString(font, CreateLang.translate("gui.stock_keeper.package_address")
				.style(ChatFormatting.ITALIC).component(), addressBox.getX(), addressBox.getY(), 0x8A8794, false);
	}

	@Override
	public List<Rect2i> getExtraAreas() {
		return extraAreas;
	}
}
