package com.yision.phantom.compat.fluidlogistics;

import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

public final class FluidLogisticsPackageCompat {

	private FluidLogisticsPackageCompat() {}

	public static boolean blocksManualOpen(ItemStack packageStack) {
		return !packageStack.isEmpty()
			&& ModList.get().isLoaded("fluidlogistics")
			&& FluidLogisticsPackageLoadedCompat.blocksManualOpen(packageStack);
	}
}
