package com.yision.phantom.item.miniphantom;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record MiniPhantomReturnTarget(ResourceKey<Level> dimension, BlockPos pos) {
	public static final Codec<MiniPhantomReturnTarget> CODEC = RecordCodecBuilder.create(instance ->
		instance.group(
			ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(MiniPhantomReturnTarget::dimension),
			BlockPos.CODEC.fieldOf("pos").forGetter(MiniPhantomReturnTarget::pos)
		).apply(instance, MiniPhantomReturnTarget::new)
	);

	public static final StreamCodec<RegistryFriendlyByteBuf, MiniPhantomReturnTarget> STREAM_CODEC =
		StreamCodec.composite(
			ResourceKey.streamCodec(Registries.DIMENSION), MiniPhantomReturnTarget::dimension,
			BlockPos.STREAM_CODEC, MiniPhantomReturnTarget::pos,
			MiniPhantomReturnTarget::new
		);
}
