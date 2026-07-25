package com.yision.phantom.item.miniphantom;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
import com.yision.phantom.compat.fluidlogistics.FluidLogisticsPackageCompat;
import com.yision.phantom.entity.courier.AirCourierEntity;
import com.yision.phantom.item.miniphantom.MiniPhantomMenu;
import com.yision.phantom.item.miniphantom.MiniPhantomReturnTarget;
import com.yision.phantom.registry.AllAttachmentTypes;
import com.yision.phantom.registry.AllDataComponents;
import com.yision.phantom.registry.AllItems;
import com.yision.phantom.client.render.MiniPhantomItemRenderer;
import java.util.function.Consumer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;

public class MiniPhantomItem extends Item {
	public MiniPhantomItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
		ItemStack stack = player.getItemInHand(usedHand);
		if (FluidLogisticsPackageCompat.blocksManualOpen(copyCargoPackage(stack))) {
			return InteractionResultHolder.pass(stack);
		}
		if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
			openMenu(serverPlayer, stack, usedHand);
		}
		return InteractionResultHolder.success(stack);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		ItemStack stack = context.getItemInHand();
		if (!hasCargo(stack)) {
			return InteractionResult.PASS;
		}
		if (context.getClickedFace() != Direction.UP) {
			return InteractionResult.PASS;
		}

		Level level = context.getLevel();
		Player player = context.getPlayer();
		if (player == null) {
			return InteractionResult.PASS;
		}

		Vec3 spawnPos = Vec3.atBottomCenterOf(context.getClickedPos().above()).add(0, 0.01, 0);
		Vec3 facingDirection = player.getLookAngle().multiply(1, 0, 1);
		if (facingDirection.lengthSqr() < 1.0E-6) {
			facingDirection = Vec3.directionFromRotation(0, player.getYRot()).multiply(-1, 0, -1);
		}
		facingDirection = facingDirection.normalize();
		AirCourierEntity courier = AirCourierEntity.createWaiting(level, copyCargoPackage(stack), facingDirection);
		courier.setPos(spawnPos);

		UUID hudEntryId = getHudEntryId(stack);
		if (hudEntryId != null) {
			courier.setHudEntryId(hudEntryId);
		}

		if (!level.noCollision(courier, courier.getBoundingBox())) {
			return InteractionResult.FAIL;
		}

		if (!level.isClientSide()) {
			level.addFreshEntity(courier);
			if (!player.getAbilities().instabuild) {
				stack.shrink(1);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents,
		TooltipFlag tooltipFlag) {
		ItemStack cargoPackage = copyCargoPackage(stack);
		if (!cargoPackage.isEmpty()) {
			cargoPackage.getItem().appendHoverText(cargoPackage, context, tooltipComponents, tooltipFlag);
		}
	}

	protected static void openMenu(ServerPlayer serverPlayer, ItemStack stack, InteractionHand usedHand) {
		ItemStack openedSnapshot = stack.copy();
		serverPlayer.openMenu(
			new SimpleMenuProvider((id, inv, p) -> MiniPhantomMenu.create(id, inv, stack, usedHand),
				Component.translatable("item.createphantom.mini_phantom")),
			buffer -> {
				ItemStack.STREAM_CODEC.encode(buffer, openedSnapshot);
				buffer.writeEnum(usedHand);
				ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer,
					serverPlayer.getData(AllAttachmentTypes.MINI_PHANTOM_CLIPBOARD).getStackInSlot(0));
				buffer.writeUtf(serverPlayer.getData(AllAttachmentTypes.MINI_PHANTOM_CLIPBOARD).getAddress());
			});
	}

	public static ItemStack createLoaded(ItemStack packageStack) {
		ItemStack phantom = AllItems.MINI_PHANTOM.asStack();
		loadCargo(phantom, packageStack);
		return phantom;
	}

	public static ItemStack createLoadedWithHeading(ItemStack packageStack, int headingAngle) {
		ItemStack phantom = createLoaded(packageStack);
		setHeadingAngle(phantom, headingAngle);
		return phantom;
	}

	public static boolean loadCargo(ItemStack phantom, ItemStack packageStack) {
		MiniPhantomCargo cargo = new MiniPhantomCargo(packageStack);
		if (!cargo.isValid()) {
			phantom.remove(AllDataComponents.MINI_PHANTOM_CARGO);
			return false;
		}

		phantom.set(AllDataComponents.MINI_PHANTOM_CARGO, cargo);
		return true;
	}

	public static ItemStack copyCargoPackage(ItemStack phantom) {
		MiniPhantomCargo cargo = phantom.get(AllDataComponents.MINI_PHANTOM_CARGO);
		if (cargo == null || !cargo.isValid()) {
			return ItemStack.EMPTY;
		}
		return cargo.packageCopy();
	}

	public static boolean hasCargo(ItemStack phantom) {
		MiniPhantomCargo cargo = phantom.get(AllDataComponents.MINI_PHANTOM_CARGO);
		return cargo != null && cargo.isValid();
	}

	public static void clearCargo(ItemStack phantom) {
		phantom.remove(AllDataComponents.MINI_PHANTOM_CARGO);
	}

	public static void setHeadingAngle(ItemStack stack, int headingAngle) {
		stack.set(AllDataComponents.MINI_PHANTOM_HEADING, Math.floorMod(headingAngle, 360));
	}

	public static boolean hasHeadingAngle(ItemStack stack) {
		return stack.has(AllDataComponents.MINI_PHANTOM_HEADING);
	}

	public static int getHeadingAngle(ItemStack stack) {
		Integer headingAngle = stack.get(AllDataComponents.MINI_PHANTOM_HEADING);
		return headingAngle == null ? 0 : Math.floorMod(headingAngle, 360);
	}

	public static void setHudEntryId(ItemStack stack, UUID hudEntryId) {
		stack.set(AllDataComponents.MINI_PHANTOM_HUD_ID, hudEntryId);
	}

	@Nullable
	public static UUID getHudEntryId(ItemStack stack) {
		return stack.get(AllDataComponents.MINI_PHANTOM_HUD_ID);
	}

	public static boolean hasHudEntryId(ItemStack stack) {
		return stack.has(AllDataComponents.MINI_PHANTOM_HUD_ID);
	}

	public static void clearHudEntryId(ItemStack stack) {
		stack.remove(AllDataComponents.MINI_PHANTOM_HUD_ID);
	}

	public static ItemStack returningTo(ResourceKey<Level> dimension, BlockPos pos) {
		ItemStack stack = AllItems.MINI_PHANTOM.asStack();
		setReturnTarget(stack, dimension, pos);
		return stack;
	}

	public static ItemStack returningToPlayer(UUID playerId) {
		ItemStack stack = AllItems.MINI_PHANTOM.asStack();
		setPlayerReturnTarget(stack, playerId);
		return stack;
	}

	public static void setReturnTarget(ItemStack stack, ResourceKey<Level> dimension, BlockPos pos) {
		stack.set(AllDataComponents.MINI_PHANTOM_RETURN_TARGET, new MiniPhantomReturnTarget(dimension, pos.immutable()));
		stack.remove(AllDataComponents.MINI_PHANTOM_PLAYER_RETURN_TARGET);
	}

	public static Optional<MiniPhantomReturnTarget> getReturnTarget(ItemStack stack) {
		return Optional.ofNullable(stack.get(AllDataComponents.MINI_PHANTOM_RETURN_TARGET));
	}

	public static void setPlayerReturnTarget(ItemStack stack, UUID playerId) {
		stack.set(AllDataComponents.MINI_PHANTOM_PLAYER_RETURN_TARGET, playerId);
		stack.remove(AllDataComponents.MINI_PHANTOM_RETURN_TARGET);
	}

	public static Optional<UUID> getPlayerReturnTarget(ItemStack stack) {
		return Optional.ofNullable(stack.get(AllDataComponents.MINI_PHANTOM_PLAYER_RETURN_TARGET));
	}

	@SuppressWarnings("removal")
	@Override
	@OnlyIn(Dist.CLIENT)
	public void initializeClient(Consumer<IClientItemExtensions> consumer) {
		consumer.accept(SimpleCustomRenderer.create(this, new MiniPhantomItemRenderer()));
	}
}
