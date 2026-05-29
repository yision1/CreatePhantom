package com.yision.phantom.compat.jei;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.stockTicker.CraftableBigItemStack;
import com.simibubi.create.foundation.blockEntity.ItemHandlerContainer;
import com.simibubi.create.foundation.utility.CreateLang;
import com.yision.phantom.item.ticker.TunablePortableTickerMenu;
import com.yision.phantom.item.ticker.TunablePortableTickerScreen;
import com.yision.phantom.compat.fluidlogistics.FluidLogisticsTickerCompat;
import com.yision.phantom.registry.AllMenuTypes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.ParametersAreNonnullByDefault;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import mezz.jei.common.transfer.RecipeTransferErrorInternal;
import mezz.jei.common.transfer.RecipeTransferOperationsResult;
import mezz.jei.common.transfer.RecipeTransferUtil;
import mezz.jei.library.transfer.RecipeTransferErrorMissingSlots;
import mezz.jei.library.transfer.RecipeTransferErrorTooltip;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jetbrains.annotations.Nullable;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class TunablePortableTickerTransferHandler implements IUniversalRecipeTransferHandler<TunablePortableTickerMenu> {
	private static final int CRAFTING_GRID_SLOT_COUNT = 9;

	private final IJeiHelpers helpers;

	public TunablePortableTickerTransferHandler(IJeiHelpers helpers) {
		this.helpers = helpers;
	}

	@Override
	public Class<? extends TunablePortableTickerMenu> getContainerClass() {
		return TunablePortableTickerMenu.class;
	}

	@Override
	public Optional<MenuType<TunablePortableTickerMenu>> getMenuType() {
		return Optional.of(AllMenuTypes.TUNABLE_PORTABLE_TICKER.get());
	}

	@Override
	public @Nullable IRecipeTransferError transferRecipe(TunablePortableTickerMenu container, Object object,
		IRecipeSlotsView recipeSlots, Player player, boolean maxTransfer, boolean doTransfer) {
		MutableObject<IRecipeTransferError> result = new MutableObject<>();
		Level level = player.level();
		if (!(object instanceof RecipeHolder<?> recipe))
			return null;
		if (level.isClientSide())
			CatnipServices.PLATFORM.executeOnClientOnly(
				() -> () -> result.setValue(
					transferRecipeOnClient(container, (RecipeHolder<Recipe<?>>) recipe, recipeSlots, player, maxTransfer,
						doTransfer)));
		return result.getValue();
	}

	private @Nullable IRecipeTransferError transferRecipeOnClient(TunablePortableTickerMenu container,
		RecipeHolder<Recipe<?>> recipeHolder, IRecipeSlotsView recipeSlots, Player player, boolean maxTransfer,
		boolean doTransfer) {
		if (!(container.screenReference instanceof TunablePortableTickerScreen screen))
			return RecipeTransferErrorInternal.INSTANCE;

		if (FluidLogisticsTickerCompat.isLoaded() && FluidLogisticsTickerCompat.hasFluidIngredient(recipeSlots)) {
			var summary = screen.getTransferPlanningSummary();
			List<BigItemStack> existingOrders = screen.itemsToOrder;

			List<BigItemStack> selectedRequirements = FluidLogisticsTickerCompat.selectRequirements(
				recipeSlots, summary, existingOrders);
			if (selectedRequirements.isEmpty())
				return new RecipeTransferErrorTooltip(CreateLang.translate("gui.stock_keeper.not_in_stock").component());

			if (!screen.canFitNewRequirementTypes(selectedRequirements))
				return new RecipeTransferErrorTooltip(CreateLang.translate("gui.stock_keeper.slots_full").component());

			Recipe<?> recipe = recipeHolder.value();
			FluidLogisticsTickerCompat.OutputTarget output = FluidLogisticsTickerCompat.outputTarget(
				recipeSlots, player, recipe);
			if (output == null)
				return new RecipeTransferErrorTooltip(CreateLang.translate("gui.stock_keeper.recipe_result_empty").component());

			int craftableSets = FluidLogisticsTickerCompat.getCustomCraftableSets(summary, existingOrders, selectedRequirements);
			if (craftableSets <= 0)
				return new RecipeTransferErrorTooltip(CreateLang.translate("gui.stock_keeper.not_in_stock").component());

			if (!doTransfer)
				return null;

			CraftableBigItemStack cbis = screen.getRecipeOrderFor(recipe);
			if (cbis == null) {
				cbis = new CraftableBigItemStack(output.displayStack().copy(), recipe);
				FluidLogisticsTickerCompat.setCustomRecipeData(
					cbis, output.outputCount(), output.transferLimit(), selectedRequirements);
				screen.recipesToOrder.add(cbis);
			}

			int setsToAdd = maxTransfer ? craftableSets : 1;
			screen.requestCraftable(cbis, output.outputCount() * setsToAdd);
			screen.searchBox.setValue("");
			screen.refreshSearchNextTick = true;
			return null;
		}
		Recipe<?> recipe = recipeHolder.value();
		if (recipe.getIngredients().size() > 9)
			return RecipeTransferErrorInternal.INSTANCE;
		if (screen.itemsToOrder.size() >= 9)
			return new RecipeTransferErrorTooltip(CreateLang.translate("gui.stock_keeper.slots_full").component());

		List<BigItemStack> availableStacks = screen.getTransferCandidates(recipe.getIngredients());
		if (availableStacks.isEmpty())
			return new RecipeTransferErrorTooltip(CreateLang.translate("gui.stock_keeper.not_in_stock").component());

		RecipeTransferOperationsResult transferOperations = getTransferOperations(recipeSlots, availableStacks);
		if (!transferOperations.missingItems.isEmpty())
			return new RecipeTransferErrorMissingSlots(CreateLang.translate("gui.stock_keeper.not_in_stock").component(),
				transferOperations.missingItems);

		if (!doTransfer)
			return null;

		ItemStack result = recipe.getResultItem(player.level().registryAccess());
		if (result.isEmpty())
			return new RecipeTransferErrorTooltip(CreateLang.translate("gui.stock_keeper.recipe_result_empty").component());

		CraftableBigItemStack cbis = screen.getRecipeOrderFor(recipe);
		if (cbis == null) {
			cbis = new CraftableBigItemStack(result, recipe);
			screen.recipesToOrder.add(cbis);
		}

		screen.searchBox.setValue("");
		screen.refreshSearchNextTick = true;
		screen.requestCraftable(cbis, maxTransfer ? result.getMaxStackSize() : 1);
		return null;
	}

	private RecipeTransferOperationsResult getTransferOperations(IRecipeSlotsView recipeSlots,
		List<BigItemStack> availableStacks) {
		return RecipeTransferUtil.getRecipeTransferOperations(helpers.getStackHelper(),
			createAvailableItemMap(availableStacks), recipeSlots.getSlotViews(RecipeIngredientRole.INPUT),
			createCraftingGridSlots());
	}

	private List<Slot> createCraftingGridSlots() {
		Container phantomCraftingGrid = new ItemHandlerContainer(new ItemStackHandler(CRAFTING_GRID_SLOT_COUNT));
		List<Slot> slots = new ArrayList<>(CRAFTING_GRID_SLOT_COUNT);
		for (int slotIndex = 0; slotIndex < CRAFTING_GRID_SLOT_COUNT; slotIndex++)
			slots.add(new Slot(phantomCraftingGrid, slotIndex, 0, 0));
		return slots;
	}

	private Map<Slot, ItemStack> createAvailableItemMap(List<BigItemStack> availableStacks) {
		Container phantomStock = new ItemHandlerContainer(new ItemStackHandler(availableStacks.size()));
		Map<Slot, ItemStack> availableItemStacks = new HashMap<>();
		for (int slotIndex = 0; slotIndex < availableStacks.size(); slotIndex++) {
			BigItemStack stack = availableStacks.get(slotIndex);
			availableItemStacks.put(new Slot(phantomStock, slotIndex, 0, 0), stack.stack.copyWithCount(stack.count));
		}
		return availableItemStacks;
	}
}
