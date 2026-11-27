package com.yision.phantom.block.phantomport;

import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public record PhantomPortReference(UUID id, GlobalPos location, Optional<UUID> subLevelId, Vec3 landing) {
	public static final Codec<PhantomPortReference> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		UUIDUtil.CODEC.fieldOf("id").forGetter(PhantomPortReference::id),
		GlobalPos.CODEC.fieldOf("location").forGetter(PhantomPortReference::location),
		UUIDUtil.CODEC.optionalFieldOf("sub_level").forGetter(PhantomPortReference::subLevelId),
		Vec3.CODEC.fieldOf("landing").forGetter(PhantomPortReference::landing)
	).apply(instance, PhantomPortReference::new));

	public ResourceKey<Level> dimension() {
		return location.dimension();
	}

	public BlockPos pos() {
		return location.pos();
	}
}
