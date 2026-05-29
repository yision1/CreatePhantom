package com.yision.phantom.item.miniphantom;

import com.mojang.serialization.Codec;
import com.simibubi.create.content.logistics.box.PackageItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

public record MiniPhantomCargo(ItemStack packageStack) {
	public static final Codec<MiniPhantomCargo> CODEC =
		ItemStack.OPTIONAL_CODEC.xmap(MiniPhantomCargo::new, MiniPhantomCargo::packageCopy);

	public static final StreamCodec<RegistryFriendlyByteBuf, MiniPhantomCargo> STREAM_CODEC =
		ItemStack.OPTIONAL_STREAM_CODEC.map(MiniPhantomCargo::new, MiniPhantomCargo::packageCopy);

	public MiniPhantomCargo {
		packageStack = sanitize(packageStack);
	}

	public boolean isValid() {
		return PackageItem.isPackage(packageStack);
	}

	public ItemStack packageCopy() {
		return packageStack.copy();
	}

	private static ItemStack sanitize(ItemStack stack) {
		if (!PackageItem.isPackage(stack)) {
			return ItemStack.EMPTY;
		}

		ItemStack copy = stack.copy();
		copy.setCount(1);
		return copy;
	}
}
