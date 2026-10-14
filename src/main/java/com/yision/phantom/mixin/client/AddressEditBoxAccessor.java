package com.yision.phantom.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.simibubi.create.content.logistics.AddressEditBox;
import com.simibubi.create.content.trains.schedule.DestinationSuggestions;

@Mixin(value = AddressEditBox.class, remap = false)
public interface AddressEditBoxAccessor {
	@Accessor("destinationSuggestions")
	void createphantom$setSuggestions(DestinationSuggestions suggestions);
}
