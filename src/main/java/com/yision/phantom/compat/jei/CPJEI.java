package com.yision.phantom.compat.jei;

import com.yision.phantom.CreatePhantom;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public class CPJEI implements IModPlugin {
	private static final ResourceLocation ID = CreatePhantom.asResource("jei_plugin");

	@Override
	public ResourceLocation getPluginUid() {
		return ID;
	}

	@Override
	public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
		registration.addUniversalRecipeTransferHandler(new TunablePortableTickerTransferHandler(registration.getJeiHelpers()));
	}

}
