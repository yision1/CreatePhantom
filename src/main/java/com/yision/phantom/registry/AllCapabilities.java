package com.yision.phantom.registry;

import com.yision.phantom.block.phantomport.PhantomPortBlockEntity;
import com.yision.phantom.registry.AllBlockEntityTypes;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public final class AllCapabilities {

	private AllCapabilities() {}

	public static void register(RegisterCapabilitiesEvent event) {
		event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, AllBlockEntityTypes.PHANTOMPORT.get(),
			PhantomPortBlockEntity::getItemHandler);
	}
}
