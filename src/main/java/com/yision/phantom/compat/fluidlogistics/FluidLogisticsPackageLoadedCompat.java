package com.yision.phantom.compat.fluidlogistics;

import com.simibubi.create.content.logistics.BigItemStack;
import com.yision.fluidlogistics.api.packager.PackageResources;

import net.minecraft.world.item.ItemStack;

public final class FluidLogisticsPackageLoadedCompat {

	private FluidLogisticsPackageLoadedCompat() {}

	public static BigItemStack getDisplayEntry(ItemStack stack) {
		if (PackageResources.isBootstrapped()) {
			var resource = PackageResources.readResource(stack);
			if (resource.isPresent()) {
				var contents = resource.orElseThrow();
				return new BigItemStack(contents.key(), contents.amount());
			}
		}
		return new BigItemStack(stack, stack.getCount());
	}

	public static boolean blocksManualOpen(ItemStack packageStack) {
		return PackageResources.isBootstrapped()
			&& PackageResources.blocksManualOpen(packageStack);
	}
}
