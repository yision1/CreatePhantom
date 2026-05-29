package com.yision.phantom.config;

import net.createmod.catnip.config.ConfigBase;

public class CPClient extends ConfigBase {

	public final ConfigGroup hud = group(0, "hud", Comments.hud);
	public final ConfigEnum<AirCourierHudPlacement> courierHudPlacement =
		e(AirCourierHudPlacement.TOP_RIGHT, "courierHudPlacement");
	public final ConfigFloat courierHudScale =
		f(0.65f, 0.5f, 1.5f, "courierHudScale", Comments.courierHudScale);

	@Override
	public String getName() {
		return "client";
	}

	public enum AirCourierHudPlacement {
		TOP_RIGHT,
		TOP_LEFT,
		HIDDEN
	}

	private static class Comments {
		static String hud = "Settings for the in-flight courier HUD.";
		static String courierHudScale = "Visual scale for the in-flight courier HUD.";
	}
}
