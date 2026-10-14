package com.yision.phantom.compat.fluidlogistics;

import com.simibubi.create.content.logistics.BigItemStack;

import net.minecraft.world.item.ItemStack;

import net.neoforged.fml.ModList;

public final class FluidLogisticsPackageCompat {

	private FluidLogisticsPackageCompat() {
	}

	public static BigItemStack getDisplayEntry(ItemStack stack) {
		return ModList.get().isLoaded("fluidlogistics")
			? FluidLogisticsPackageLoadedCompat.getDisplayEntry(stack)
			: new BigItemStack(stack, stack.getCount());
	}

	public static boolean blocksManualOpen(ItemStack packageStack) {
		return !packageStack.isEmpty()
			&& ModList.get().isLoaded("fluidlogistics")
			&& FluidLogisticsPackageLoadedCompat.blocksManualOpen(packageStack);
	}
}
