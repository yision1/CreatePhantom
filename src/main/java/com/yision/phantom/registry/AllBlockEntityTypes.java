package com.yision.phantom.registry;

import com.tterrag.registrate.util.entry.BlockEntityEntry;
import com.yision.phantom.CreatePhantom;
import com.yision.phantom.block.phantomport.PhantomPortBlockEntity;

public final class AllBlockEntityTypes {
	public static final BlockEntityEntry<PhantomPortBlockEntity> PHANTOMPORT = CreatePhantom.REGISTRATE
		.blockEntity("phantomport", PhantomPortBlockEntity::new)
		.validBlocks(AllBlocks.PHANTOMPORT)
		.register();

	private AllBlockEntityTypes() {
	}

	public static void register() {
	}
}
