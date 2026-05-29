package com.yision.phantom.logistics.courier.hud;

import java.util.List;
import net.createmod.catnip.codecs.stream.CatnipStreamCodecBuilders;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

public record AirCourierHudEntry(
	AirCourierHudStatus status,
	int etaSeconds,
	List<ItemStack> displayStacks
) {
	public static final StreamCodec<RegistryFriendlyByteBuf, AirCourierHudEntry> STREAM_CODEC =
		StreamCodec.composite(
			ByteBufCodecs.VAR_INT, entry -> entry.status().ordinal(),
			ByteBufCodecs.INT, AirCourierHudEntry::etaSeconds,
			CatnipStreamCodecBuilders.list(ItemStack.OPTIONAL_STREAM_CODEC), AirCourierHudEntry::displayStacks,
			(statusId, etaSeconds, displayStacks) ->
				new AirCourierHudEntry(AirCourierHudStatus.byId(statusId), etaSeconds, displayStacks));

	public AirCourierHudEntry {
		displayStacks = AirCourierPackagePreview.copyDisplayStacks(displayStacks);
	}
}
