package com.yision.phantom.client.gui.hud;

import com.yision.phantom.config.AllConfigs;
import com.yision.phantom.config.CPClient.AirCourierHudPlacement;
import com.yision.phantom.compat.fluidlogistics.FluidLogisticsTickerCompat;
import com.yision.phantom.logistics.courier.hud.AirCourierHudEntry;
import com.yision.phantom.logistics.courier.hud.AirCourierHudPayload;
import com.yision.phantom.logistics.courier.hud.AirCourierPackagePreview;
import com.yision.phantom.CreatePhantom;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

public final class AirCourierHudOverlay {
	private static final ResourceLocation PANEL = CreatePhantom.asResource("textures/gui/package_information.png");
	private static final float DEFAULT_SCALE = 0.65f;
	private static final float MIN_SCALE = 0.5f;
	private static final float MAX_SCALE = 1.5f;
	private static final int PANEL_X = 3;
	private static final int PANEL_U = 16;
	private static final int PANEL_V = 16;
	private static final int PANEL_WIDTH = 232;
	private static final int PANEL_HEIGHT = 120;
	private static final int PANEL_GAP = 4;
	private static final int SLOT_X = 27;
	private static final int SLOT_Y = 28;
	private static final int SLOT_SPACING = 20;
	private static final int ADDRESS_BOX_X = 55;
	private static final int ADDRESS_BOX_Y = 68;
	private static final int ADDRESS_BOX_WIDTH = 110;
	private static final long HIDE_HOLD_MILLIS = 180;

	private static AirCourierHudPayload displayedPayload = AirCourierHudPayload.hidden();
	private static long hideRequestedAtMillis = -1;
	private static List<RenderedEntry> renderedEntries = new ArrayList<>();

	private record RenderedEntry(
		AirCourierHudEntry entry,
		Component statusText,
		Component addressText,
		int statusWidth,
		int addressWidth
	) {}

	private AirCourierHudOverlay() {}

	public static void updateState(AirCourierHudPayload payload) {
		AirCourierHudPayload incoming = payload == null ? AirCourierHudPayload.hidden() : payload;
		if (incoming.visible()) {
			displayedPayload = incoming;
			hideRequestedAtMillis = -1;
			rebuildRenderedEntries();
		} else {
			if (hideRequestedAtMillis < 0) {
				hideRequestedAtMillis = System.currentTimeMillis();
			}
		}
	}

	public static void render(RenderGuiEvent.Post event) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null || minecraft.options.hideGui || minecraft.screen != null) {
			return;
		}

		AirCourierHudPlacement placement = getPlacement();
		if (placement == AirCourierHudPlacement.HIDDEN) {
			return;
		}

		if (!displayedPayload.visible()) {
			return;
		}

		if (hideRequestedAtMillis > 0) {
			long elapsed = System.currentTimeMillis() - hideRequestedAtMillis;
			if (elapsed >= HIDE_HOLD_MILLIS) {
				displayedPayload = AirCourierHudPayload.hidden();
				hideRequestedAtMillis = -1;
				renderedEntries.clear();
				return;
			}
		}

		if (renderedEntries.isEmpty()) {
			return;
		}

		int count = Math.min(renderedEntries.size(), AirCourierHudPayload.MAX_VISIBLE_ENTRIES);
		GuiGraphics graphics = event.getGuiGraphics();

		float configuredScale = getScale();
		float maxStackScale = (minecraft.getWindow().getGuiScaledHeight() - 4f)
			/ (count * PANEL_HEIGHT + (count - 1) * PANEL_GAP);
		float scale = Math.min(configuredScale, maxStackScale);
		scale = Mth.clamp(scale, MIN_SCALE, MAX_SCALE);

		int renderedWidth = Mth.ceil((PANEL_X + PANEL_WIDTH) * scale);
		float x = switch (placement) {
			case TOP_LEFT -> -PANEL_X * scale;
			case TOP_RIGHT -> minecraft.getWindow().getGuiScaledWidth() - renderedWidth + 5;
			case HIDDEN -> 0;
		};

		float panelStride = (PANEL_HEIGHT + PANEL_GAP) * scale;

		for (int i = 0; i < count; i++) {
			RenderedEntry rendered = renderedEntries.get(i);
			float y = i * panelStride;

			graphics.pose().pushPose();
			graphics.pose().translate(x, y, 0);
			graphics.pose().scale(scale, scale, 1);

			renderPanel(graphics, minecraft, rendered);

			graphics.pose().popPose();
		}
	}

	private static void renderPanel(GuiGraphics graphics, Minecraft minecraft, RenderedEntry rendered) {
		graphics.blit(PANEL, PANEL_X, 0, PANEL_U, PANEL_V, PANEL_WIDTH, PANEL_HEIGHT);

		graphics.drawString(minecraft.font, rendered.statusText(),
			117 - rendered.statusWidth() / 2, 4, 0x3D3C48, false);
		graphics.drawString(minecraft.font, rendered.addressText(),
			ADDRESS_BOX_X + (ADDRESS_BOX_WIDTH - rendered.addressWidth()) / 2, ADDRESS_BOX_Y, 0x555555, false);

		List<ItemStack> displayStacks = rendered.entry().displayStacks();
		for (int slot = 0; slot < displayStacks.size() && slot < AirCourierPackagePreview.MAX_DISPLAY_STACKS; slot++) {
			var stack = displayStacks.get(slot);
			if (stack.isEmpty()) {
				continue;
			}

			int slotX = SLOT_X + SLOT_SPACING * slot;
			graphics.renderItem(stack, slotX, SLOT_Y);
			if (!FluidLogisticsTickerCompat.renderHudAmount(graphics, stack, slotX, SLOT_Y)) {
				graphics.renderItemDecorations(minecraft.font, stack, slotX, SLOT_Y);
			}
		}
	}

	private static String formatEta(int seconds) {
		if (seconds < 0) {
			return "--";
		}
		int minutes = seconds / 60;
		int remainderSeconds = seconds % 60;
		return String.format("%d:%02d", minutes, remainderSeconds);
	}

	private static void rebuildRenderedEntries() {
		renderedEntries.clear();
		Minecraft minecraft = Minecraft.getInstance();
		for (AirCourierHudEntry entry : displayedPayload.entries()) {
			Component statusText = entry.status().asComponent();
			Component addressText = Component.translatable("gui.createphantom.phantomport.arrival_time_display",
				formatEta(entry.etaSeconds()));
			int statusWidth = minecraft.font != null ? minecraft.font.width(statusText) : 0;
			int addressWidth = minecraft.font != null ? minecraft.font.width(addressText) : 0;
			renderedEntries.add(new RenderedEntry(entry, statusText, addressText, statusWidth, addressWidth));
		}
	}

	private static AirCourierHudPlacement getPlacement() {
		if (AllConfigs.client() == null) {
			return AirCourierHudPlacement.TOP_RIGHT;
		}
		return AllConfigs.client().courierHudPlacement.get();
	}

	private static float getScale() {
		if (AllConfigs.client() == null) {
			return DEFAULT_SCALE;
		}
		return Mth.clamp(AllConfigs.client().courierHudScale.getF(), MIN_SCALE, MAX_SCALE);
	}
}
