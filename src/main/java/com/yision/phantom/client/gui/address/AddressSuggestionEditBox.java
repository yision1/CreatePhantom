package com.yision.phantom.client.gui.address;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.trains.schedule.DestinationSuggestions;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

public class AddressSuggestionEditBox extends EditBox {

	private DestinationSuggestions destinationSuggestions;
	private Consumer<String> mainResponder;
	private String prevValue = "=)";

	public AddressSuggestionEditBox(Screen screen, Font font, int x, int y, int width, int height,
		boolean anchorToBottom, String localAddress, List<String> cardAddresses) {
		this(screen, font, x, y, width, height, anchorToBottom, localAddress, cardAddresses, true);
	}

	public AddressSuggestionEditBox(Screen screen, Font font, int x, int y, int width, int height,
		boolean anchorToBottom, String localAddress, List<String> cardAddresses, boolean includeInventoryClipboards) {
		super(font, x, y, width, height, Component.empty());
		destinationSuggestions = AddressSuggestionEditBoxHelper.createSuggestions(
			screen, this, anchorToBottom, localAddress, cardAddresses, includeInventoryClipboards);
		destinationSuggestions.setAllowSuggestions(true);
		destinationSuggestions.updateCommandInfo();
		mainResponder = t -> {
			if (!t.equals(prevValue))
				destinationSuggestions.updateCommandInfo();
			prevValue = t;
		};
		setResponder(mainResponder);
		setBordered(false);
		setFocused(false);
		mouseClicked(0, 0, 0);
		setMaxLength(25);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (destinationSuggestions.keyPressed(keyCode, scanCode, modifiers))
			return true;
		if (isFocused() && keyCode == GLFW.GLFW_KEY_ENTER) {
			setFocused(false);
			moveCursorToEnd(false);
			mouseClicked(0, 0, 0);
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (destinationSuggestions.mouseScrolled(Mth.clamp(scrollY, -1.0D, 1.0D)))
			return true;
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && isMouseOver(mouseX, mouseY)) {
			setValue("");
			return true;
		}

		boolean wasFocused = isFocused();
		if (super.mouseClicked(mouseX, mouseY, button)) {
			if (!wasFocused) {
				setHighlightPos(0);
				setCursorPosition(getValue().length());
			}
			return true;
		}
		return destinationSuggestions.mouseClicked((int) mouseX, (int) mouseY, button);
	}

	@Override
	public void setValue(String text) {
		setHighlightPos(0);
		super.setValue(text);
	}

	@Override
	public void setFocused(boolean focused) {
		super.setFocused(focused);
	}

	@Override
	public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.renderWidget(graphics, mouseX, mouseY, partialTick);
		PoseStack matrixStack = graphics.pose();
		matrixStack.pushPose();
		matrixStack.translate(0, 0, 500);
		destinationSuggestions.render(graphics, mouseX, mouseY);
		matrixStack.popPose();
	}

	@Override
	public void setResponder(Consumer<String> responder) {
		super.setResponder(responder == mainResponder ? mainResponder : mainResponder.andThen(responder));
	}

	public void tick() {
		if (!isFocused())
			destinationSuggestions.hide();
		if (isFocused())
			destinationSuggestions.updateCommandInfo();
		destinationSuggestions.tick();
	}
}
