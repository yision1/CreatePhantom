package com.yision.phantom.compat.jei;

import com.yision.phantom.item.ticker.TunablePortableTickerScreen;
import com.yision.phantom.compat.fluidlogistics.FluidLogisticsTickerCompat;
import java.util.List;
import java.util.Optional;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

public class CPJEIGuiHandler implements IGuiContainerHandler<TunablePortableTickerScreen> {
	private final IIngredientManager ingredientManager;

	public CPJEIGuiHandler(IIngredientManager ingredientManager) {
		this.ingredientManager = ingredientManager;
	}

	@Override
	public List<Rect2i> getGuiExtraAreas(TunablePortableTickerScreen containerScreen) {
		return containerScreen.getExtraAreas();
	}

	@Override
	public Optional<IClickableIngredient<?>> getClickableIngredientUnderMouse(TunablePortableTickerScreen containerScreen,
		double mouseX, double mouseY) {
		return containerScreen.getHoveredIngredient((int) mouseX, (int) mouseY)
			.flatMap(pair -> ingredientManager.createClickableIngredient(clickableIngredient(pair.getFirst()),
				pair.getSecond(), true));
	}

	private Object clickableIngredient(ItemStack hoveredIngredient) {
		if (FluidLogisticsTickerCompat.isVirtualFluidStack(hoveredIngredient))
			return FluidLogisticsTickerCompat.getFluidFromVirtualTank(hoveredIngredient).copy();
		return hoveredIngredient;
	}
}
