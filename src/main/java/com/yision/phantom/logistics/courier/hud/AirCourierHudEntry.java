package com.yision.phantom.logistics.courier.hud;

import java.util.List;
import java.util.UUID;

import net.minecraft.world.item.ItemStack;

public record AirCourierHudEntry(
	UUID id,
	AirCourierHudStatus status,
	int etaSeconds,
	List<ItemStack> displayStacks) {
	public AirCourierHudEntry {
		displayStacks = List.copyOf(displayStacks);
	}

	public boolean hasSameContents(AirCourierHudEntry other) {
		if (displayStacks == other.displayStacks)
			return true;
		if (displayStacks.size() != other.displayStacks.size())
			return false;
		for (int i = 0; i < displayStacks.size(); i++) {
			if (!ItemStack.matches(displayStacks.get(i), other.displayStacks.get(i)))
				return false;
		}
		return true;
	}
}
