package com.yision.phantom.config;

import net.createmod.catnip.config.ConfigBase;

public class CPServer extends ConfigBase {

	public final ConfigGroup logistics = group(0, "logistics", Comments.logistics);
	public final ConfigBool allowCrossDimensionDelivery =
		b(true, "allowCrossDimensionDelivery");

	@Override
	public String getName() {
		return "server";
	}

	private static class Comments {
		static String logistics = "Settings for phantom logistics.";
	}
}
