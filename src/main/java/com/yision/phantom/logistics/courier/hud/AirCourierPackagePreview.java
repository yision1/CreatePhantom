package com.yision.phantom.logistics.courier.hud;

import java.util.List;

import com.simibubi.create.content.logistics.box.PackageItem;

import net.minecraft.world.item.ItemStack;

public final class AirCourierPackagePreview {
	public static final int MAX_DISPLAY_STACKS = 9;

	private AirCourierPackagePreview() {
	}

	public static List<ItemStack> fromPackage(ItemStack box) {
		if (box == null || box.isEmpty() || !PackageItem.isPackage(box)) {
			return List.of();
		}
		return List.of(box.copyWithCount(1));
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
