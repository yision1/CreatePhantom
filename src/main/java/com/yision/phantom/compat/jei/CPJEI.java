package com.yision.phantom.compat.jei;

import com.yision.phantom.CreatePhantom;
import com.yision.phantom.item.ticker.TunablePortableTickerScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public class CPJEI implements IModPlugin {
	private static final ResourceLocation ID = CreatePhantom.asResource("jei_plugin");

	public static IJeiRuntime runtime;

	@Override
	public ResourceLocation getPluginUid() {
		return ID;
	}

	@Override
	public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
		registration.addUniversalRecipeTransferHandler(new TunablePortableTickerTransferHandler(registration.getJeiHelpers()));
	}

	@Override
	public void registerGuiHandlers(IGuiHandlerRegistration registration) {
		registration.addGuiContainerHandler(TunablePortableTickerScreen.class,
			new CPJEIGuiHandler(registration.getJeiHelpers().getIngredientManager()));
	}

	@Override
	public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
		runtime = jeiRuntime;
	}
}
