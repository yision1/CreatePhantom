package com.yision.phantom.block.phantomport;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.logistics.funnel.BeltFunnelBlock;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.yision.phantom.logistics.courier.AirCourierHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

final class PhantomPortBeltAccess {

	private final PhantomPortBlockEntity port;

	PhantomPortBeltAccess(PhantomPortBlockEntity port) {
		this.port = port;
	}

	Direction specialSide() {
		return port.getBlockState().getValue(PhantomPortBlock.FACING);
	}

	boolean canDispatchThrough(Direction side) {
		return side == specialSide() && hasManualDispatchFunnel(side);
	}

	boolean hasManualDispatchFunnel(Direction side) {
		if (port.getLevel() == null || !side.getAxis().isHorizontal()) {
			return false;
		}

		BlockPos funnelPos = funnelPos(side);
		BlockState funnelState = port.getLevel().getBlockState(funnelPos);
		if (AllBlocks.ANDESITE_FUNNEL.has(funnelState)) {
			if (funnelState.getValue(FunnelBlock.FACING) != side) {
				return false;
			}
			if (!funnelState.getValue(FunnelBlock.EXTRACTING)) {
				return false;
			}
			return isBeltOutputCompatible(side);
		}
		if (!AllBlocks.ANDESITE_BELT_FUNNEL.has(funnelState)) {
			return false;
		}
		if (funnelState.getValue(BeltFunnelBlock.HORIZONTAL_FACING) != side) {
			return false;
		}
		if (funnelState.getValue(BeltFunnelBlock.SHAPE) == BeltFunnelBlock.Shape.PULLING) {
			return false;
		}
		return isBeltOutputCompatible(side);
	}

	boolean isBeltOutputCompatible(Direction side) {
		if (port.getLevel() == null || !side.getAxis().isHorizontal()) {
			return false;
		}

		BlockPos beltPos = beltPos(side);
		if (!(port.getLevel().getBlockEntity(beltPos) instanceof BeltBlockEntity)) {
			return false;
		}

		var beltInput = BlockEntityBehaviour.get(port.getLevel(), beltPos,
			com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour.TYPE);
		return beltInput != null && beltInput.canInsertFromSide(side);
	}

	Direction resolveBeltHeading(Direction side) {
		if (port.getLevel() == null) {
			return side;
		}
		BlockPos beltPos = beltPos(side);
		BlockEntityBehaviour beltInput = BlockEntityBehaviour.get(port.getLevel(), beltPos,
			com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour.TYPE);
		if (beltInput == null) {
			return side;
		}
		if (port.getLevel().getBlockEntity(beltPos) instanceof BeltBlockEntity beltBlockEntity) {
			return AirCourierHelper.resolveBeltHeading(beltBlockEntity);
		}
		return side;
	}

	@Nullable IItemHandler launchBeltHandler(Direction side) {
		BlockPos beltPos = beltPos(side);
		return port.getLevel() != null
			? port.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, beltPos, Direction.UP)
			: null;
	}

	boolean tryInsertToLaunchBelt(ItemStack stack) {
		Direction side = specialSide();
		if (!hasManualDispatchFunnel(side) || !isBeltOutputCompatible(side)) {
			return false;
		}
		IItemHandler beltHandler = launchBeltHandler(side);
		if (beltHandler == null) {
			return false;
		}
		return beltHandler.insertItem(0, stack.copy(), true).isEmpty()
			&& beltHandler.insertItem(0, stack.copy(), false).isEmpty();
	}

	BlockPos funnelPos(Direction side) {
		return port.getBlockPos().relative(side);
	}

	BlockPos beltPos(Direction side) {
		return funnelPos(side).below();
	}
}
