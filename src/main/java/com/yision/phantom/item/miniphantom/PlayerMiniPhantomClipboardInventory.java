package com.yision.phantom.item.miniphantom;

import com.simibubi.create.AllBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

public class PlayerMiniPhantomClipboardInventory extends ItemStackHandler {
	private static final String ADDRESS_KEY = "Address";

	private String address = "";

	public PlayerMiniPhantomClipboardInventory() {
		super(1);
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address == null ? "" : address.trim();
	}

	@Override
	public int getSlotLimit(int slot) {
		return 1;
	}

	@Override
	public boolean isItemValid(int slot, ItemStack stack) {
		return AllBlocks.CLIPBOARD.isIn(stack);
	}

	@Override
	public CompoundTag serializeNBT(HolderLookup.Provider provider) {
		CompoundTag tag = super.serializeNBT(provider);
		if (!address.isBlank()) {
			tag.putString(ADDRESS_KEY, address);
		}
		return tag;
	}

	@Override
	public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
		super.deserializeNBT(provider, tag);
		setAddress(tag.getString(ADDRESS_KEY));
	}
}
