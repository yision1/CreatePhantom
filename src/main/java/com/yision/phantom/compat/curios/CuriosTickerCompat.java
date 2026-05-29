package com.yision.phantom.compat.curios;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

public final class CuriosTickerCompat {

	private CuriosTickerCompat() {}

	private static boolean cachedLoaded;

	public static boolean isLoaded() {
		if (cachedLoaded) return true;
		if (!ModList.get().isLoaded("curios")) return false;
		cachedLoaded = true;
		return true;
	}

	public static int findBodySlot(Player player) {
		return isLoaded() ? CuriosTickerLoadedCompat.findBodySlot(player) : -1;
	}

	public static ItemStack resolveBody(Player player, int slot) {
		return isLoaded() ? CuriosTickerLoadedCompat.resolveBody(player, slot) : ItemStack.EMPTY;
	}
}
