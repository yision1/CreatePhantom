package com.yision.phantom.registry;

import com.tterrag.registrate.providers.ProviderType;
import com.tterrag.registrate.util.entry.EntityEntry;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;
import com.yision.phantom.entity.courier.AirCourierEntity;
import com.yision.phantom.CreatePhantom;
import net.minecraft.world.entity.MobCategory;

public final class AllEntityTypes {
	public static final EntityEntry<AirCourierEntity> AIR_COURIER = CreatePhantom.REGISTRATE
		.entity("air_courier", AirCourierEntity::createEmpty, MobCategory.MISC)
		.properties(properties -> properties
			.sized(0.6F, 0.6F)
			.eyeHeight(0.25F)
			.clientTrackingRange(96)
			.updateInterval(1))
		.setData(ProviderType.LANG, NonNullBiConsumer.noop())
		.register();

	private AllEntityTypes() {}

	public static void register() {}
}
