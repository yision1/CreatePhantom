package com.yision.phantom.compat.fluidlogistics;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;

import net.minecraft.world.item.ItemStack;

import net.neoforged.fml.ModList;

public final class FluidLogisticsPackageCompat {
	private FluidLogisticsPackageCompat() {
	}

	public static List<BigItemStack> getDisplayEntries(ItemStack stack) {
		return ModList.get().isLoaded("fluidlogistics")
			? FluidLogisticsPackageLoadedCompat.getDisplayEntries(stack)
			: getItemDisplayEntries(stack);
	}

	static List<BigItemStack> getItemDisplayEntries(ItemStack stack) {
		List<BigItemStack> entries = new ArrayList<>();
		var contents = PackageItem.getContents(stack);
		for (int slot = 0; slot < contents.getSlots(); slot++) {
			ItemStack item = contents.getStackInSlot(slot);
			if (!item.isEmpty())
				entries.add(new BigItemStack(item.copy(), item.getCount()));
		}
		return entries;
	}

	public static boolean blocksManualOpen(ItemStack packageStack) {
		return !packageStack.isEmpty()
			&& ModList.get().isLoaded("fluidlogistics")
			&& FluidLogisticsPackageLoadedCompat.blocksManualOpen(packageStack);
	}
}
