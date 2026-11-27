package com.yision.phantom.item.miniphantom;

import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yision.phantom.block.phantomport.PhantomPortReference;

import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record MiniPhantomDispatch(UUID id, PhantomPortReference source, ResourceKey<Level> dimension,
	Optional<PhantomPortReference> port, Optional<UUID> player) {
	public static final Codec<MiniPhantomDispatch> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		UUIDUtil.CODEC.fieldOf("id").forGetter(MiniPhantomDispatch::id),
		PhantomPortReference.CODEC.fieldOf("source").forGetter(MiniPhantomDispatch::source),
		ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(MiniPhantomDispatch::dimension),
		PhantomPortReference.CODEC.optionalFieldOf("port").forGetter(MiniPhantomDispatch::port),
		UUIDUtil.CODEC.optionalFieldOf("player").forGetter(MiniPhantomDispatch::player)
	).apply(instance, MiniPhantomDispatch::new));
}
