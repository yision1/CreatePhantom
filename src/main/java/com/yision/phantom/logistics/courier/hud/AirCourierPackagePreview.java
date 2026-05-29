package com.yision.phantom.logistics.courier.hud;

import com.simibubi.create.content.logistics.box.PackageItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class AirCourierPackagePreview {
	public static final int MAX_DISPLAY_STACKS = 9;

	private AirCourierPackagePreview() {}

	public static List<ItemStack> fromPackage(ItemStack box) {
		if (box == null || box.isEmpty() || !PackageItem.isPackage(box)) {
			return List.of();
		}

		List<ItemStack> displayStacks = new ArrayList<>();
		ItemStackHandler contents = PackageItem.getContents(box);
		for (int slot = 0; slot < contents.getSlots() && displayStacks.size() < MAX_DISPLAY_STACKS; slot++) {
			ItemStack stack = contents.getStackInSlot(slot);
			if (stack.isEmpty()) {
				continue;
			}
			displayStacks.add(stack.copy());
		}
		return displayStacks;
	}

	public static List<ItemStack> copyDisplayStacks(List<ItemStack> stacks) {
		if (stacks == null || stacks.isEmpty()) {
			return List.of();
		}
		return stacks.stream()
			.filter(stack -> stack != null && !stack.isEmpty())
			.limit(MAX_DISPLAY_STACKS)
			.map(ItemStack::copy)
			.toList();
	}
}
