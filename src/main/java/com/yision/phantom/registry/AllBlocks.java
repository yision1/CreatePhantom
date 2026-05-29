package com.yision.phantom.registry;

import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.providers.ProviderType;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;
import com.yision.phantom.block.phantomport.PhantomPortBlock;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import static com.simibubi.create.foundation.data.TagGen.pickaxeOnly;
import static com.yision.phantom.CreatePhantom.REGISTRATE;

public final class AllBlocks {
	public static final BlockEntry<PhantomPortBlock> PHANTOMPORT =
		REGISTRATE.block("phantomport", PhantomPortBlock::new)
			.initialProperties(SharedProperties::softMetal)
			.properties(properties -> properties.strength(3.0F).noOcclusion())
			.transform(pickaxeOnly())
			.setData(ProviderType.LANG, NonNullBiConsumer.noop())
			.blockstate((c, p) -> {
				ModelFile offModel = p.models()
					.getExistingFile(p.modLoc("block/phantomport/block"));
				ModelFile onModel = p.models()
					.getExistingFile(p.modLoc("block/phantomport/block_on"));
				p.getVariantBuilder(c.get()).forAllStates(state -> {
					boolean open = state.getValue(PhantomPortBlock.OPEN);
					Direction facing = state.getValue(PhantomPortBlock.FACING);
					int yRot = ((int) facing.toYRot() + 180) % 360;
					return ConfiguredModel.builder()
						.modelFile(open ? onModel : offModel)
						.rotationY(yRot)
						.build();
				});
			})
			.item()
			.model((c, p) -> p.withExistingParent(c.getName(),
				p.modLoc("block/phantomport/item")))
			.build()
			.register();

	private AllBlocks() {}

	public static void register() {}
}
