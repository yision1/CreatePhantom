package com.yision.phantom.ponder;

import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags;
import com.tterrag.registrate.util.entry.ItemProviderEntry;
import com.tterrag.registrate.util.entry.RegistryEntry;
import com.yision.phantom.CreatePhantom;
import com.yision.phantom.registry.AllBlocks;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public class CreatePhantomPonderPlugin implements PonderPlugin {
	@Override
	public String getModId() {
		return CreatePhantom.MODID;
	}

	@Override
	public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
		PonderSceneRegistrationHelper<ItemProviderEntry<?, ?>> entries = helper.withKeyFunction(RegistryEntry::getId);
		entries.forComponents(AllBlocks.PHANTOMPORT)
			.addStoryBoard("phantom_port", PhantomPortScenes::playerDelivery, AllCreatePonderTags.HIGH_LOGISTICS)
			.addStoryBoard("phantom_port", PhantomPortScenes::stationDelivery, AllCreatePonderTags.HIGH_LOGISTICS)
			.addStoryBoard("phantom_port_automation", PhantomPortScenes::automaticPackageTransfer,
				AllCreatePonderTags.HIGH_LOGISTICS)
			.addStoryBoard("phantom_port_address_matching", PhantomPortScenes::addressSuffixMatching,
				AllCreatePonderTags.HIGH_LOGISTICS)
			.addStoryBoard("phantom_port", PhantomPortScenes::manualFireworkLaunch,
				AllCreatePonderTags.HIGH_LOGISTICS);
	}

	@Override
	public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
		PonderTagRegistrationHelper<ItemProviderEntry<?, ?>> entries = helper.withKeyFunction(RegistryEntry::getId);
		entries.addToTag(AllCreatePonderTags.HIGH_LOGISTICS)
			.add(AllBlocks.PHANTOMPORT);
	}
}
