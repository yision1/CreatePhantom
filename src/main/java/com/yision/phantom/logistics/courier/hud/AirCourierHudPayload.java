package com.yision.phantom.logistics.courier.hud;

import java.util.List;

public record AirCourierHudPayload(
	List<AirCourierHudEntry> entries
) {
	public static final int MAX_VISIBLE_ENTRIES = 3;

	public static AirCourierHudPayload hidden() {
		return new AirCourierHudPayload(List.of());
	}

	public boolean visible() {
		return !entries.isEmpty();
	}

	public AirCourierHudPayload {
		entries = entries == null ? List.of() : entries.stream()
			.limit(MAX_VISIBLE_ENTRIES)
			.toList();
	}
}
