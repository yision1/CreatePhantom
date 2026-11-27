package com.yision.phantom.logistics.address;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.content.logistics.box.PackageItem;

import net.minecraft.world.item.ItemStack;

public final class PhantomAddressRules {
	private static final String COMMENT_MARKER = "//";

	private PhantomAddressRules() {
	}

	public static String canonical(String address) {
		if (address == null) {
			return "";
		}
		String trimmed = address.trim();
		int commentIndex = trimmed.indexOf(COMMENT_MARKER);
		if (commentIndex >= 0) {
			trimmed = trimmed.substring(0, commentIndex).trim();
		}
		return trimmed;
	}

	public static boolean isBlank(String address) {
		return canonical(address).isBlank();
	}

	public static @Nullable String playerName(String address) {
		return explicitPlayerName(canonical(address));
	}

	private static @Nullable String explicitPlayerName(String address) {
		if (address.startsWith("@"))
			return address.substring(1).trim();
		int start = address.indexOf('<');
		int end = address.indexOf('>', start + 1);
		return start >= 0 && end > start + 1 ? address.substring(start + 1, end).trim() : null;
	}

	public static boolean matchesPackage(ItemStack box, String address) {
		if (!PackageItem.isPackage(box))
			return false;
		String packageAddress = canonical(PackageItem.getAddress(box));
		return explicitPlayerName(packageAddress) == null
			&& PackageItem.matchAddress(packageAddress, canonical(address));
	}
}
