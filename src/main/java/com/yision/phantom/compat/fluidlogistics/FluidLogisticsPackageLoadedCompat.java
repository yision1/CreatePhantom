package com.yision.phantom.compat.fluidlogistics;

import com.yision.fluidlogistics.api.packager.PackageResources;

import net.minecraft.world.item.ItemStack;

public final class FluidLogisticsPackageLoadedCompat {

	private FluidLogisticsPackageLoadedCompat() {}

	public static boolean blocksManualOpen(ItemStack packageStack) {
		return PackageResources.isBootstrapped()
			&& PackageResources.blocksManualOpen(packageStack);
	}
}
