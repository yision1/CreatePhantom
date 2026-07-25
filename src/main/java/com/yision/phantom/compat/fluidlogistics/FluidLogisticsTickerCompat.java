package com.yision.phantom.compat.fluidlogistics;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.stockTicker.CraftableBigItemStack;
import java.util.List;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;

public final class FluidLogisticsTickerCompat {

	private FluidLogisticsTickerCompat() {}

	private static boolean cachedLoaded;

	public static boolean isLoaded() {
		if (cachedLoaded) return true;
		if (!ModList.get().isLoaded("fluidlogistics")) return false;
		cachedLoaded = true;
		return true;
	}

	public static boolean isVirtualFluidStack(ItemStack stack) {
		return isLoaded() && FluidLogisticsTickerLoadedCompat.isVirtualFluidStack(stack);
	}

	public static boolean isPackageResourceStack(ItemStack stack) {
		return isLoaded() && FluidLogisticsTickerLoadedCompat.isPackageResourceStack(stack);
	}

	public static boolean containsVirtualFluid(List<BigItemStack> order) {
		if (!isLoaded()) return false;
		for (BigItemStack entry : order) {
			if (isVirtualFluidStack(entry.stack)) return true;
		}
		return false;
	}

	public static ItemStack virtualTank(FluidStack fluid) {
		if (!isLoaded()) return ItemStack.EMPTY;
		return FluidLogisticsTickerLoadedCompat.virtualTank(fluid);
	}

	public static boolean renderAmountInTicker(GuiGraphics graphics, ItemStack stack, int amount) {
		return isLoaded() && FluidLogisticsTickerLoadedCompat.renderAmountInTicker(graphics, stack, amount);
	}

	public static boolean renderHudAmount(GuiGraphics graphics, ItemStack stack, int slotX, int slotY) {
		return isLoaded() && FluidLogisticsTickerLoadedCompat.renderHudAmount(graphics, stack, slotX, slotY);
	}

	public static List<Component> tooltipLines(
		BigItemStack entry, boolean recipeHovered, boolean orderHovered
	) {
		if (!isLoaded()) return List.of();
		return FluidLogisticsTickerLoadedCompat.tooltipLines(entry, recipeHovered, orderHovered);
	}

	public static boolean isVirtualFluidEntry(BigItemStack entry) {
		return isVirtualFluidStack(entry.stack);
	}

	public static boolean isPackageResourceEntry(BigItemStack entry) {
		return isPackageResourceStack(entry.stack);
	}

	public static boolean hasFluidIngredient(IRecipeSlotsView recipeSlots) {
		return isLoaded() && FluidLogisticsTickerLoadedCompat.hasFluidIngredient(recipeSlots);
	}

	public static List<BigItemStack> selectRequirements(
		IRecipeSlotsView recipeSlots,
		InventorySummary summary,
		List<BigItemStack> existingOrders
	) {
		if (!isLoaded()) return List.of();
		return FluidLogisticsTickerLoadedCompat.selectRequirements(recipeSlots, summary, existingOrders);
	}

	public static OutputTarget outputTarget(
		IRecipeSlotsView recipeSlots,
		Player player,
		Recipe<?> recipe
	) {
		if (!isLoaded()) return null;
		return FluidLogisticsTickerLoadedCompat.outputTarget(recipeSlots, player, recipe);
	}

	public static boolean hasCustomRecipeData(CraftableBigItemStack stack) {
		return isLoaded() && FluidLogisticsTickerLoadedCompat.hasCustomRecipeData(stack);
	}

	public static int customOutputCount(CraftableBigItemStack stack) {
		if (!isLoaded()) return 0;
		return FluidLogisticsTickerLoadedCompat.customOutputCount(stack);
	}

	public static int customTransferLimit(CraftableBigItemStack stack) {
		if (!isLoaded()) return 0;
		return FluidLogisticsTickerLoadedCompat.customTransferLimit(stack);
	}

	public static List<BigItemStack> customRequirements(CraftableBigItemStack stack) {
		if (!isLoaded()) return List.of();
		return FluidLogisticsTickerLoadedCompat.customRequirements(stack);
	}

	public static void setCustomRecipeData(
		CraftableBigItemStack stack,
		int outputCount,
		int transferLimit,
		List<BigItemStack> requirements
	) {
		if (!isLoaded()) return;
		FluidLogisticsTickerLoadedCompat.setCustomRecipeData(stack, outputCount, transferLimit, requirements);
	}

	public static int recipeStep(CraftableBigItemStack stack, boolean shift, boolean control) {
		if (!isLoaded()) return 1;
		return FluidLogisticsTickerLoadedCompat.recipeStep(stack, shift, control);
	}

	public static int adjustFluidRequestAmount(
		ItemStack stack, int currentAmount, boolean forward, boolean shift, boolean control,
		int minAmount, int maxAmount, int steps
	) {
		if (!isLoaded()) return currentAmount;
		return FluidLogisticsTickerLoadedCompat.adjustFluidRequestAmount(
			stack, currentAmount, forward, shift, control, minAmount, maxAmount, steps);
	}

	public static int adjustStockTickerFluidRequestAmount(
		ItemStack stack, int currentAmount, boolean forward, boolean shift, boolean control,
		int minAmount, int maxAmount, int steps
	) {
		if (!isLoaded()) return currentAmount;
		return FluidLogisticsTickerLoadedCompat.adjustStockTickerFluidRequestAmount(
			stack, currentAmount, forward, shift, control, minAmount, maxAmount, steps);
	}

	public static int getCustomCraftableSets(
		InventorySummary availableItems,
		List<BigItemStack> existingOrders,
		List<BigItemStack> requirements
	) {
		if (!isLoaded()) return 0;
		return FluidLogisticsTickerLoadedCompat.getCustomCraftableSets(availableItems, existingOrders, requirements);
	}

	public static int getCustomCraftableSets(
		InventorySummary availableItems,
		InventorySummary usedItems,
		List<BigItemStack> requirements
	) {
		if (!isLoaded()) return 0;
		return FluidLogisticsTickerLoadedCompat.getCustomCraftableSets(availableItems, usedItems, requirements);
	}

	public static FluidStack getFluidFromVirtualTank(ItemStack stack) {
		if (!isLoaded()) return FluidStack.EMPTY;
		return FluidLogisticsTickerLoadedCompat.getFluidFromVirtualTank(stack);
	}

	public record OutputTarget(ItemStack displayStack, int outputCount, int transferLimit) {}
}
