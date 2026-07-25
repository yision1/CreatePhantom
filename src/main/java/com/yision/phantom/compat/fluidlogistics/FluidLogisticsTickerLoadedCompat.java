package com.yision.phantom.compat.fluidlogistics;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.stockTicker.CraftableBigItemStack;
import com.simibubi.create.foundation.utility.CreateLang;

import com.yision.fluidlogistics.api.packager.PackageResourceCrafting;
import com.yision.fluidlogistics.api.packager.PackageResourceCraftingData;
import com.yision.fluidlogistics.api.packager.PackageResourceDisplay;
import com.yision.fluidlogistics.api.packager.PackageResources;
import com.yision.fluidlogistics.api.packager.PackageResourceTypes;
import com.yision.fluidlogistics.api.packager.client.PackageResourceClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.fluids.FluidStack;

public final class FluidLogisticsTickerLoadedCompat {

	private FluidLogisticsTickerLoadedCompat() {}

	public static boolean isVirtualFluidStack(ItemStack stack) {
		return PackageResources.isBootstrapped()
			&& PackageResources.findType(stack)
				.map(type -> PackageResourceTypes.FLUID.equals(type.id()))
				.orElse(false);
	}

	public static boolean isPackageResourceStack(ItemStack stack) {
		return PackageResources.isBootstrapped() && PackageResources.findType(stack).isPresent();
	}

	public static ItemStack virtualTank(FluidStack fluid) {
		return PackageResourceTypes.createFluidKey(fluid);
	}

	public static boolean renderAmountInTicker(GuiGraphics graphics, ItemStack stack, int amount) {
		return PackageResourceClient.tryRenderStockKeeperAmount(graphics, stack, amount);
	}

	public static boolean renderHudAmount(GuiGraphics graphics, ItemStack stack, int slotX, int slotY) {
		if (!PackageResources.isBootstrapped()) return false;
		var resource = PackageResources.readResource(stack);
		if (resource.isEmpty()) return false;

		graphics.pose().pushPose();
		try {
			graphics.pose().translate(slotX, slotY, 200);
			return PackageResourceClient.tryRenderStockKeeperAmount(
				graphics, stack, resource.orElseThrow().amount());
		} finally {
			graphics.pose().popPose();
		}
	}

	public static List<Component> tooltipLines(
		BigItemStack entry, boolean recipeHovered, boolean orderHovered
	) {
		PackageResourceDisplay.TooltipContext context = recipeHovered
			? PackageResourceDisplay.TooltipContext.STOCK_KEEPER_CRAFTABLE
			: orderHovered
				? PackageResourceDisplay.TooltipContext.STOCK_KEEPER_ORDER
				: PackageResourceDisplay.TooltipContext.STOCK_KEEPER_INVENTORY;
		ArrayList<Component> lines = PackageResources.tooltipOf(
				entry.stack, entry.count, Minecraft.getInstance().options.advancedItemTooltips, context)
			.map(ArrayList::new)
			.orElseGet(ArrayList::new);
		if (lines.isEmpty()) return lines;
		if (context == PackageResourceDisplay.TooltipContext.STOCK_KEEPER_CRAFTABLE) {
			lines.set(0, CreateLang.translateDirect("gui.stock_keeper.craft", lines.getFirst().copy()));
		}
		return lines;
	}

	public static boolean hasFluidIngredient(IRecipeSlotsView recipeSlots) {
		return hasFluidIngredients(recipeSlots, RecipeIngredientRole.INPUT)
			|| hasFluidIngredients(recipeSlots, RecipeIngredientRole.OUTPUT);
	}

	private static boolean hasFluidIngredients(IRecipeSlotsView recipeSlots, RecipeIngredientRole role) {
		for (IRecipeSlotView slotView : recipeSlots.getSlotViews(role)) {
			if (slotView.getIngredients(NeoForgeTypes.FLUID_STACK).anyMatch(fluid -> !fluid.isEmpty())) {
				return true;
			}
		}
		return false;
	}

	public static List<BigItemStack> selectRequirements(
		IRecipeSlotsView recipeSlots,
		InventorySummary summary,
		List<BigItemStack> existingOrders
	) {
		List<BigItemStack> selectedRequirements = new ArrayList<>();

		for (IRecipeSlotView slotView : recipeSlots.getSlotViews(RecipeIngredientRole.INPUT)) {
			List<BigItemStack> candidates = getCandidates(slotView);
			if (candidates.isEmpty()) continue;

			if (summary == null) {
				mergeRequirement(selectedRequirements, candidates.getFirst());
				continue;
			}

			BigItemStack chosen = chooseBestCandidate(candidates, summary, selectedRequirements, existingOrders);
			if (chosen == null) return List.of();

			mergeRequirement(selectedRequirements, chosen);
		}

		return selectedRequirements;
	}

	private static List<BigItemStack> getCandidates(IRecipeSlotView slotView) {
		List<BigItemStack> candidates = new ArrayList<>();

		slotView.getItemStacks().forEach(stack -> {
			if (!stack.isEmpty()) {
				candidates.add(new BigItemStack(stack.copyWithCount(1), stack.getCount()));
			}
		});

		slotView.getIngredients(NeoForgeTypes.FLUID_STACK).forEach(fluid -> {
			if (fluid.isEmpty()) return;
			ItemStack virtualTank = PackageResourceTypes.createFluidKey(fluid);
			candidates.add(new BigItemStack(virtualTank, fluid.getAmount()));
		});

		return candidates;
	}

	private static BigItemStack chooseBestCandidate(
		List<BigItemStack> candidates,
		InventorySummary summary,
		List<BigItemStack> selectedRequirements,
		List<BigItemStack> existingOrders
	) {
		BigItemStack best = null;
		int bestAvailable = -1;
		boolean bestPrefersExisting = false;

		for (BigItemStack candidate : candidates) {
			int alreadySelected = getMatchingCount(selectedRequirements, candidate.stack);
			int available = summary.getCountOf(candidate.stack) - alreadySelected;
			if (available < candidate.count) continue;

			boolean prefersExisting = hasMatchingStack(selectedRequirements, candidate.stack)
				|| hasMatchingStack(existingOrders, candidate.stack);

			if (best != null && prefersExisting == bestPrefersExisting && available <= bestAvailable) continue;
			if (best != null && !prefersExisting && bestPrefersExisting) continue;

			best = new BigItemStack(candidate.stack.copyWithCount(1), candidate.count);
			bestAvailable = available;
			bestPrefersExisting = prefersExisting;
		}

		return best;
	}

	private static void mergeRequirement(List<BigItemStack> requirements, BigItemStack candidate) {
		BigItemStack existing = findMatchingOrder(requirements, candidate.stack);
		if (existing == null) {
			requirements.add(new BigItemStack(candidate.stack.copyWithCount(1), candidate.count));
			return;
		}
		existing.count += candidate.count;
	}

	private static BigItemStack findMatchingOrder(List<BigItemStack> stacks, ItemStack target) {
		for (BigItemStack entry : stacks) {
			if (ItemStack.isSameItemSameComponents(entry.stack, target)) return entry;
		}
		return null;
	}

	public static FluidLogisticsTickerCompat.OutputTarget outputTarget(
		IRecipeSlotsView recipeSlots,
		Player player,
		Recipe<?> recipe
	) {
		for (IRecipeSlotView slotView : recipeSlots.getSlotViews(RecipeIngredientRole.OUTPUT)) {
			Optional<ItemStack> itemOutput = slotView.getItemStacks()
				.filter(stack -> !stack.isEmpty())
				.findFirst();
			if (itemOutput.isPresent()) {
				ItemStack stack = itemOutput.get();
				return new FluidLogisticsTickerCompat.OutputTarget(
					stack.copyWithCount(1), Math.max(1, stack.getCount()), stack.getMaxStackSize());
			}

			Optional<FluidStack> fluidOutput = slotView.getIngredients(NeoForgeTypes.FLUID_STACK)
				.filter(fluid -> !fluid.isEmpty())
				.findFirst();
			if (fluidOutput.isPresent()) {
				ItemStack virtualTank = PackageResourceTypes.createFluidKey(fluidOutput.get());
				return new FluidLogisticsTickerCompat.OutputTarget(
					virtualTank, Math.max(1, fluidOutput.get().getAmount()), PackageResourceTypes.getFluidPerPackage());
			}
		}

		ItemStack result = recipe.getResultItem(player.level().registryAccess());
		if (result.isEmpty()) return null;
		return new FluidLogisticsTickerCompat.OutputTarget(
			result.copyWithCount(1), Math.max(1, result.getCount()), result.getMaxStackSize());
	}

	public static boolean hasCustomRecipeData(CraftableBigItemStack stack) {
		return PackageResourceCrafting.has(stack);
	}

	public static int customOutputCount(CraftableBigItemStack stack) {
		return PackageResourceCrafting.get(stack)
			.map(PackageResourceCraftingData::outputCount)
			.orElse(0);
	}

	public static int customTransferLimit(CraftableBigItemStack stack) {
		return PackageResourceCrafting.get(stack)
			.map(PackageResourceCraftingData::transferLimit)
			.orElse(0);
	}

	public static List<BigItemStack> customRequirements(CraftableBigItemStack stack) {
		return PackageResourceCrafting.get(stack)
			.map(PackageResourceCraftingData::requirements)
			.orElseGet(List::of);
	}

	public static void setCustomRecipeData(
		CraftableBigItemStack stack,
		int outputCount,
		int transferLimit,
		List<BigItemStack> requirements
	) {
		PackageResourceCrafting.set(
			stack, new PackageResourceCraftingData(outputCount, transferLimit, requirements));
	}

	public static int recipeStep(CraftableBigItemStack stack, boolean shift, boolean control) {
		PackageResourceCraftingData data = PackageResourceCrafting.get(stack).orElse(null);
		if (data == null) return 1;
		int outputCount = Math.max(1, data.outputCount());
		if (shift) return Math.max(outputCount, data.transferLimit());
		if (control) return outputCount * 10;
		return outputCount;
	}

	public static int adjustFluidRequestAmount(
		ItemStack stack, int currentAmount, boolean forward, boolean shift, boolean control,
		int minAmount, int maxAmount, int steps
	) {
		return PackageResources.adjustAmount(stack, new PackageResourceDisplay.Adjustment(
				currentAmount, forward, shift, control, minAmount, maxAmount, steps,
				PackageResourceDisplay.Interaction.STOCK_KEEPER_ORDER))
			.orElse(currentAmount);
	}

	public static int adjustStockTickerFluidRequestAmount(
		ItemStack stack, int currentAmount, boolean forward, boolean shift, boolean control,
		int minAmount, int maxAmount, int steps
	) {
		return PackageResources.adjustAmount(stack, new PackageResourceDisplay.Adjustment(
				currentAmount, forward, shift, control, minAmount, maxAmount, steps,
				PackageResourceDisplay.Interaction.STOCK_KEEPER_INVENTORY))
			.orElse(currentAmount);
	}

	public static FluidStack getFluidFromVirtualTank(ItemStack stack) {
		return PackageResourceTypes.getFluid(stack);
	}

	public static int getCraftableSets(InventorySummary summary, List<BigItemStack> existingOrders,
			List<BigItemStack> requirements) {
		int craftableSets = Integer.MAX_VALUE;
		for (BigItemStack requirement : requirements) {
			int orderedCount = getMatchingCount(existingOrders, requirement.stack);
			int available = summary.getCountOf(requirement.stack) - orderedCount;
			craftableSets = Math.min(craftableSets, available / requirement.count);
		}
		return craftableSets == Integer.MAX_VALUE ? 0 : craftableSets;
	}

	public static boolean canFitNewTypes(List<BigItemStack> existingOrders, List<BigItemStack> requirements) {
		int totalTypes = existingOrders.size();
		List<ItemStack> newTypes = new ArrayList<>();
		for (BigItemStack requirement : requirements) {
			if (hasMatchingStack(existingOrders, requirement.stack)
				|| hasMatchingStack(newTypes, requirement.stack)) continue;
			newTypes.add(requirement.stack);
			totalTypes++;
			if (totalTypes > 9) return false;
		}
		return true;
	}

	public static int getCustomCraftableSets(InventorySummary availableItems, List<BigItemStack> existingOrders,
			List<BigItemStack> requirements) {
		int craftableSets = Integer.MAX_VALUE;
		for (BigItemStack requirement : requirements) {
			int orderedCount = getMatchingCount(existingOrders, requirement.stack);
			int available = availableItems.getCountOf(requirement.stack) - orderedCount;
			craftableSets = Math.min(craftableSets, available / requirement.count);
		}
		return craftableSets == Integer.MAX_VALUE ? 0 : Math.max(0, craftableSets);
	}


	public static int getCustomCraftableSets(InventorySummary availableItems, InventorySummary usedItems,
			List<BigItemStack> requirements) {
		int craftableSets = Integer.MAX_VALUE;
		for (BigItemStack requirement : requirements) {
			int usedCount = usedItems.getCountOf(requirement.stack);
			int available = availableItems.getCountOf(requirement.stack) - usedCount;
			craftableSets = Math.min(craftableSets, available / requirement.count);
		}
		return craftableSets == Integer.MAX_VALUE ? 0 : Math.max(0, craftableSets);
	}
	public static boolean hasMatchingStack(List<?> stacks, ItemStack target) {
		if (stacks == null) return false;
		for (Object entry : stacks) {
			ItemStack stack = entry instanceof BigItemStack bis ? bis.stack : (ItemStack) entry;
			if (ItemStack.isSameItemSameComponents(stack, target)) return true;
		}
		return false;
	}

	public static int getMatchingCount(List<BigItemStack> stacks, ItemStack target) {
		if (stacks == null) return 0;
		int total = 0;
		for (BigItemStack entry : stacks) {
			if (ItemStack.isSameItemSameComponents(entry.stack, target)) total += entry.count;
		}
		return total;
	}
}
