package com.yision.phantom.logistics.address;

import com.simibubi.create.content.logistics.box.PackageItem;
import net.minecraft.world.item.ItemStack;

public final class PhantomAddressRules {
	private static final String COMMENT_MARKER = "//";

	private PhantomAddressRules() {}

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

	public static boolean matches(String left, String right) {
		return PackageItem.matchAddress(canonical(left), canonical(right));
	}

	public static boolean exact(String left, String right) {
		return canonical(left).equalsIgnoreCase(canonical(right));
	}

	public static boolean matchesPackage(ItemStack box, String address) {
		if (!PackageItem.isPackage(box)) {
			return false;
		}
		return matches(PackageItem.getAddress(box), address);
	}
}
