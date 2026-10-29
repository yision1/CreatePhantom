package com.yision.phantom.compat.fluidlogistics;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.content.logistics.BigItemStack;
import com.yision.fluidlogistics.api.packager.PackageResources;

import net.minecraft.world.item.ItemStack;

public final class FluidLogisticsPackageLoadedCompat {
	private FluidLogisticsPackageLoadedCompat() {
	}

	public static List<BigItemStack> getDisplayEntries(ItemStack stack) {
		if (!PackageResources.isBootstrapped())
			return FluidLogisticsPackageCompat.getItemDisplayEntries(stack);
		var inspection = PackageResources.inspectPackage(stack);
		List<BigItemStack> entries = new ArrayList<>();
		for (var resource : inspection.resources())
			entries.add(new BigItemStack(resource.key(), resource.amount()));
		for (ItemStack item : inspection.ordinaryItems())
			entries.add(new BigItemStack(item, item.getCount()));
		return entries;
	}

	public static boolean blocksManualOpen(ItemStack packageStack) {
		return PackageResources.isBootstrapped()
			&& PackageResources.blocksManualOpen(packageStack);
	}
}
