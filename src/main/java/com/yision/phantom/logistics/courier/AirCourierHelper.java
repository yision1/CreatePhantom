package com.yision.phantom.logistics.courier;

import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.yision.phantom.block.phantomport.PhantomPortBlockEntity;
import com.yision.phantom.item.miniphantom.MiniPhantomItem;
import com.yision.phantom.registry.AllItems;

import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class AirCourierHelper {
	private AirCourierHelper() {
	}

	public static boolean isCourierLaunchStack(ItemStack stack) {
		return stack.is(AllItems.MINI_PHANTOM.get())
			&& (MiniPhantomItem.hasCargo(stack)
				|| MiniPhantomItem.getReturnTarget(stack).isPresent()
				|| MiniPhantomItem.getPlayerReturnTarget(stack).isPresent());
	}

	public static int getHeadingAngle(Direction movementDirection) {
		return Math.floorMod(Math.round(AngleHelper.horizontalAngle(movementDirection)), 360);
	}

	public static Direction resolveBeltHeading(BeltBlockEntity belt) {
		Vec3i chainDirection = belt.getBeltChainDirection();
		int x = chainDirection.getX();
		int z = chainDirection.getZ();
		if (x == 0 && z == 0) {
			return belt.getMovementFacing();
		}
		if (Math.abs(x) > Math.abs(z)) {
			return x > 0 ? Direction.EAST : Direction.WEST;
		}
		return z > 0 ? Direction.SOUTH : Direction.NORTH;
	}

	public static BlockPos findSourcePhantomPortPos(BeltBlockEntity belt) {
		if (belt.getLevel() == null) {
			return null;
		}
		BlockPos beltPos = belt.getBlockPos();
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos candidatePos = beltPos.above().relative(direction);
			if (!belt.getLevel().hasChunkAt(candidatePos)) {
				continue;
			}
			BlockEntity blockEntity = belt.getLevel().getBlockEntity(candidatePos);
			if (blockEntity instanceof PhantomPortBlockEntity phantomPortBlockEntity) {
				return phantomPortBlockEntity.getBlockPos();
			}
		}
		return null;
	}
}
