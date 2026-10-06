package com.yision.phantom.content.logistics.tunablePortableTicker;

import com.simibubi.create.content.logistics.AddressEditBox;
import com.simibubi.create.content.trains.schedule.DestinationSuggestions;
import com.yision.phantom.client.gui.address.AddressSuggestionEditBoxHelper;
import com.yision.phantom.mixin.client.AddressEditBoxAccessor;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;

public class PortableTickerAddressEditBox extends AddressEditBox {
	public PortableTickerAddressEditBox(Screen screen, Font font, int x, int y, int width, int height,
		boolean anchorToBottom, String playerAddress, List<String> cardAddresses) {
		super(screen, font, x, y, width, height, anchorToBottom);
		DestinationSuggestions suggestions = AddressSuggestionEditBoxHelper.createSuggestions(
			screen, this, anchorToBottom, playerAddress, cardAddresses);
		((AddressEditBoxAccessor) this).createphantom$setSuggestions(suggestions);
		suggestions.setAllowSuggestions(true);
		suggestions.updateCommandInfo();
	}
}
