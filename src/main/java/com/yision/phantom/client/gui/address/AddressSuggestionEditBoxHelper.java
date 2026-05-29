package com.yision.phantom.client.gui.address;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.trains.schedule.DestinationSuggestions;
import com.yision.phantom.item.storagecard.StorageChannelExtensionCardItem;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.createmod.catnip.data.IntAttached;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class AddressSuggestionEditBoxHelper {

	public static DestinationSuggestions createSuggestions(
		Screen screen,
		EditBox input,
		boolean anchorToBottom,
		String localAddress,
		List<String> cardAddresses
	) {
		return createSuggestions(screen, input, anchorToBottom, localAddress, cardAddresses, true);
	}

	public static DestinationSuggestions createSuggestions(
		Screen screen,
		EditBox input,
		boolean anchorToBottom,
		String localAddress,
		List<String> cardAddresses,
		boolean includeInventoryClipboards
	) {
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		List<IntAttached<String>> options = new ArrayList<>();
		Set<String> alreadyAdded = new HashSet<>();

		DestinationSuggestions suggestions = new DestinationSuggestions(mc, screen, input, mc.font, options,
			anchorToBottom, -72 + input.getY() + (anchorToBottom ? 0 : input.getHeight()));

		if (localAddress != null && alreadyAdded.add(localAddress))
			options.add(IntAttached.with(-1, localAddress));

		for (String address : cardAddresses) {
			String trimmed = address == null ? "" : address.trim();
			if (!trimmed.isBlank() && alreadyAdded.add(trimmed))
				options.add(IntAttached.withZero(trimmed));
		}

		if (includeInventoryClipboards && player != null) {
			for (int i = 0; i < Inventory.INVENTORY_SIZE; i++)
				appendClipboardAddresses(options, alreadyAdded, player.getInventory().getItem(i));
		}

		return suggestions;
	}

	private static void appendClipboardAddresses(List<IntAttached<String>> options, Set<String> alreadyAdded, ItemStack item) {
		if (item == null || !AllBlocks.CLIPBOARD.isIn(item))
			return;
		appendClipboardAddressesFromComponents(options, alreadyAdded, item.getComponents());
	}

	private static void appendClipboardAddressesFromComponents(List<IntAttached<String>> options, Set<String> alreadyAdded,
		DataComponentMap components) {
		for (String address : StorageChannelExtensionCardItem.extractAddresses(components)) {
			if (!alreadyAdded.add(address))
				continue;
			options.add(IntAttached.withZero(address));
		}
	}
}
