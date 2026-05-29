package com.yision.phantom.compat.curios;

import com.yision.phantom.item.ticker.TunablePortableTickerItem;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

public final class CuriosTickerLoadedCompat {

	private CuriosTickerLoadedCompat() {}

	private static Optional<Map<String, ICurioStacksHandler>> resolveCuriosMap(Player player) {
		return CuriosApi.getCuriosInventory(player)
			.map(ICuriosItemHandler::getCurios);
	}

	public static int findBodySlot(Player player) {
		return resolveCuriosMap(player).map(curiosMap -> {
			ICurioStacksHandler bodyHandler = curiosMap.get("body");
			if (bodyHandler == null) return -1;
			int slots = bodyHandler.getSlots();
			for (int i = 0; i < slots; i++) {
				ItemStack stack = bodyHandler.getStacks().getStackInSlot(i);
				if (stack.getItem() instanceof TunablePortableTickerItem)
					return i;
			}
			return -1;
		}).orElse(-1);
	}

	public static ItemStack resolveBody(Player player, int slot) {
		if (slot < 0) return ItemStack.EMPTY;
		return resolveCuriosMap(player).map(curiosMap -> {
			ICurioStacksHandler bodyHandler = curiosMap.get("body");
			if (bodyHandler == null) return ItemStack.EMPTY;
			if (slot >= bodyHandler.getSlots()) return ItemStack.EMPTY;
			return bodyHandler.getStacks().getStackInSlot(slot);
		}).orElse(ItemStack.EMPTY);
	}
}
