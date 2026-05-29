package com.yision.phantom.registry;

import com.yision.phantom.CreatePhantom;
import com.yision.phantom.item.miniphantom.PlayerMiniPhantomClipboardInventory;
import java.util.function.Supplier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class AllAttachmentTypes {
	private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
		DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, CreatePhantom.MODID);

	public static final Supplier<AttachmentType<PlayerMiniPhantomClipboardInventory>> MINI_PHANTOM_CLIPBOARD =
		ATTACHMENT_TYPES.register("mini_phantom_clipboard",
			() -> AttachmentType.serializable(PlayerMiniPhantomClipboardInventory::new)
				.copyOnDeath()
				.build());

	private AllAttachmentTypes() {}

	public static void register(IEventBus modEventBus) {
		ATTACHMENT_TYPES.register(modEventBus);
	}
}
