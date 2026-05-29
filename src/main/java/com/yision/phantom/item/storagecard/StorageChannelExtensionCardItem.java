package com.yision.phantom.item.storagecard;

import com.simibubi.create.content.equipment.clipboard.ClipboardBlockEntity;
import com.simibubi.create.content.equipment.clipboard.ClipboardEntry;
import com.simibubi.create.content.logistics.filter.FilterItem;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import com.yision.phantom.registry.AllDataComponents;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.createmod.catnip.lang.FontHelper;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StorageChannelExtensionCardItem extends Item {
	public StorageChannelExtensionCardItem(Properties properties) {
		super(properties.stacksTo(1));
	}

	@Override
	public boolean isFoil(@NotNull ItemStack stack) {
		return isLinked(stack);
	}

	public static boolean isLinked(ItemStack stack) {
		return stack.has(AllDataComponents.STORAGE_CHANNEL_EXTENSION_CARD_FREQ);
	}

	@Nullable
	public static UUID networkFromStack(ItemStack stack) {
		CompoundTag tag = stack.getOrDefault(AllDataComponents.STORAGE_CHANNEL_EXTENSION_CARD_FREQ, CustomData.EMPTY).copyTag();
		if (!tag.hasUUID("Freq"))
			return null;
		return tag.getUUID("Freq");
	}

	@Override
	public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext tooltipContext,
		@NotNull List<Component> tooltipComponents, @NotNull TooltipFlag tooltipFlag) {
		super.appendHoverText(stack, tooltipContext, tooltipComponents, tooltipFlag);

		List<String> addresses = loadAddressesFromStack(stack);
		if (!addresses.isEmpty()) {
			tooltipComponents.add(Component.translatable(
				"item.createphantom.storage_channel_extension_card.address_count",
				addresses.size()).withStyle(FontHelper.Palette.STANDARD_CREATE.highlight()));
		}

		CompoundTag tag = stack.getOrDefault(AllDataComponents.STORAGE_CHANNEL_EXTENSION_CARD_FREQ, CustomData.EMPTY).copyTag();
		if (!tag.hasUUID("Freq"))
			return;

		CreateLang.translate("logistically_linked.tooltip")
			.style(ChatFormatting.GOLD)
			.addTo(tooltipComponents);

		CreateLang.translate("logistically_linked.tooltip_clear")
			.style(ChatFormatting.GRAY)
			.addTo(tooltipComponents);
	}

	@Override
	public @NotNull InteractionResult useOn(UseOnContext context) {
		ItemStack stack = context.getItemInHand();
		BlockPos pos = context.getClickedPos();
		Level level = context.getLevel();
		Player player = context.getPlayer();

		if (player == null)
			return InteractionResult.FAIL;

		if (level.getBlockEntity(pos) instanceof ClipboardBlockEntity clipboard) {
			if (level.isClientSide)
				return InteractionResult.SUCCESS;
			int written = saveAddressesFromClipboard(stack, clipboard.components());
			player.displayClientMessage(
				Component.translatable("item.createphantom.storage_channel_extension_card.address_count", written),
				true);
			return InteractionResult.SUCCESS;
		}

		LogisticallyLinkedBehaviour link = BlockEntityBehaviour.get(level, pos, LogisticallyLinkedBehaviour.TYPE);
		if (link == null)
			return InteractionResult.PASS;

		if (level.isClientSide)
			return InteractionResult.SUCCESS;

		if (!link.mayInteractMessage(player))
			return InteractionResult.SUCCESS;

		UUID oldNetwork = networkFromStack(stack);
		UUID newNetwork = link.freqId;

		saveCategoriesIfAvailable(stack, level, pos, oldNetwork, newNetwork);
		assignFrequency(stack, player, newNetwork);
		return InteractionResult.SUCCESS;
	}

	public static void assignFrequency(ItemStack stack, Player player, UUID frequency) {
		CompoundTag tag = stack.getOrDefault(AllDataComponents.STORAGE_CHANNEL_EXTENSION_CARD_FREQ, CustomData.EMPTY).copyTag();
		tag.putUUID("Freq", frequency);
		player.displayClientMessage(CreateLang.translateDirect("logistically_linked.tuned"), true);
		stack.set(AllDataComponents.STORAGE_CHANNEL_EXTENSION_CARD_FREQ, CustomData.of(tag));
	}

	private static void saveCategoriesIfAvailable(
		ItemStack stack, Level level, BlockPos pos, UUID oldNetwork, UUID newNetwork
	) {
		if (level.getBlockEntity(pos) instanceof StockTickerBlockEntity stbe) {
			CompoundTag tag = new CompoundTag();
			stbe.saveAdditional(tag, level.registryAccess());
			List<ItemStack> categories =
				NBTHelper.readItemList(tag.getList("Categories", Tag.TAG_COMPOUND), level.registryAccess());
			saveCategoriesToStack(stack, categories);
			return;
		}

		if (oldNetwork != null && !oldNetwork.equals(newNetwork))
			stack.remove(AllDataComponents.STORAGE_CHANNEL_EXTENSION_CARD_CATEGORIES);
	}

	public static void saveCategoriesToStack(ItemStack stack, List<ItemStack> categories) {
		if (categories != null)
			stack.set(AllDataComponents.STORAGE_CHANNEL_EXTENSION_CARD_CATEGORIES, categories);
	}

	public static List<ItemStack> loadCategoriesFromStack(ItemStack stack) {
		List<ItemStack> readCategories =
			new java.util.ArrayList<>(stack.getOrDefault(AllDataComponents.STORAGE_CHANNEL_EXTENSION_CARD_CATEGORIES, List.of()));
		readCategories.removeIf(itemStack -> !itemStack.isEmpty() && !(itemStack.getItem() instanceof FilterItem));
		return readCategories;
	}

	public static int saveAddressesFromClipboard(ItemStack stack, DataComponentMap components) {
		List<String> addresses = extractAddresses(components);
		stack.set(AllDataComponents.STORAGE_CHANNEL_EXTENSION_CARD_ADDRESSES, addresses);
		return addresses.size();
	}

	public static List<String> loadAddressesFromStack(ItemStack stack) {
		return new ArrayList<>(
			stack.getOrDefault(AllDataComponents.STORAGE_CHANNEL_EXTENSION_CARD_ADDRESSES, List.of()));
	}

	public static List<String> extractAddresses(DataComponentMap components) {
		List<List<ClipboardEntry>> pages = ClipboardEntry.readAll(components);
		Set<String> added = new LinkedHashSet<>();

		for (List<ClipboardEntry> page : pages) {
			for (ClipboardEntry entry : page) {
				if (entry.checked)
					continue;
				String text = entry.text.getString();
				if (!text.startsWith("#") || text.length() == 1)
					continue;
				String address = text.substring(1).trim();
				if (!address.isBlank())
					added.add(address);
			}
		}

		return List.copyOf(added);
	}
}
