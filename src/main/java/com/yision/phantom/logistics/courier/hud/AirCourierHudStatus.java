package com.yision.phantom.logistics.courier.hud;

import net.minecraft.network.chat.Component;

public enum AirCourierHudStatus {
	PREPARING("gui.createphantom.phantomport.order_status.preparing"),
	IN_TRANSIT("gui.createphantom.phantomport.order_status.in_transit"),
	CROSS_DIMENSION("gui.createphantom.phantomport.order_status.cross_dimension"),
	DELIVERED("gui.createphantom.phantomport.order_status.delivered"),
	FAILED("gui.createphantom.phantomport.order_status.failed"),
	RETURNING("gui.createphantom.phantomport.order_status.returning");

	private final String translationKey;

	AirCourierHudStatus(String translationKey) {
		this.translationKey = translationKey;
	}

	public Component asComponent() {
		return Component.translatable(translationKey);
	}

	public static AirCourierHudStatus byId(int id) {
		AirCourierHudStatus[] values = values();
		if (id < 0 || id >= values.length) {
			return PREPARING;
		}
		return values[id];
	}
}
