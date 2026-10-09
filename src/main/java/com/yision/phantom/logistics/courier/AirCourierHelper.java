package com.yision.phantom.logistics.courier;

import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.yision.phantom.block.phantomport.PhantomPortBlockEntity;
import com.yision.phantom.logistics.address.PhantomAddressRules;
import com.yision.phantom.item.miniphantom.MiniPhantomItem;
import com.yision.phantom.logistics.courier.flight.AirCourierFlightMath;
import com.yision.phantom.registry.AllItems;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class AirCourierHelper {
	private AirCourierHelper() {}

	public static ServerPlayer findTargetPlayer(ServerLevel level, ItemStack box) {
		return findTargetPlayer(level, box, true);
	}

	public static ServerPlayer findTargetPlayerAnyDimension(ServerLevel level, ItemStack box) {
		return findTargetPlayer(level, box, false);
	}

	private static ServerPlayer findTargetPlayer(ServerLevel level, ItemStack box, boolean requireSameDimension) {
		if (!PackageItem.isPackage(box)) {
			return null;
		}
		String address = PhantomAddressRules.canonical(PackageItem.getAddress(box));
		if (address.isBlank()) {
			return null;
		}
		for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
			if (requireSameDimension && !player.serverLevel().dimension().equals(level.dimension())) {
				continue;
			}
			if (!player.isAlive()) {
				continue;
			}
			if (!matchesPlayerAddress(address, player.getGameProfile().getName())) {
				continue;
			}
			return player;
		}
		return null;
	}

	public static boolean deliverPackageOnly(ServerPlayer player, ItemStack box) {
		if (!PackageItem.isPackage(box)) {
			return false;
		}
		player.getInventory().placeItemBackInInventory(box.copy());
		return true;
	}

	public static void deliverCarrier(ServerPlayer player) {
		player.getInventory().placeItemBackInInventory(AllItems.MINI_PHANTOM.asStack());
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

	public static Direction getHeadingDirection(int headingAngle) {
		int normalized = Math.floorMod(headingAngle, 360);
		Direction bestDirection = Direction.SOUTH;
		float bestDifference = Float.MAX_VALUE;
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			float difference = Math.abs(net.minecraft.util.Mth.wrapDegrees(normalized - getHeadingAngle(direction)));
			if (difference < bestDifference) {
				bestDifference = difference;
				bestDirection = direction;
			}
		}
		return bestDirection;
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

	public static Direction resolveCourierHeading(BeltBlockEntity belt, TransportedItemStack stack) {
		if (isCourierLaunchStack(stack.stack) && MiniPhantomItem.hasHeadingAngle(stack.stack)) {
			return getHeadingDirection(MiniPhantomItem.getHeadingAngle(stack.stack));
		}
		return resolveBeltHeading(belt);
	}

	public static Vec3 getCourierLaunchDirection(BeltBlockEntity belt, TransportedItemStack stack) {
		return Vec3.atLowerCornerOf(resolveCourierHeading(belt, stack).getNormal()).normalize();
	}

	public static Vec3 getCourierLaunchMotion(BeltBlockEntity belt, TransportedItemStack stack) {
		float movementSpeed = Math.max(Math.abs(belt.getBeltMovementSpeed()), 1 / 8f);
		Vec3 launchDirection = getCourierLaunchDirection(belt, stack);
		return AirCourierFlightMath.launchMotion(launchDirection, movementSpeed);
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

	private static boolean matchesPlayerAddress(String address, String playerName) {
		for (String candidate : extractAddressCandidates(address)) {
			if (PackageItem.matchAddress(candidate, playerName)) {
				return true;
			}
			if (candidate.equalsIgnoreCase(playerName)) {
				return true;
			}
		}
		return false;
	}

	private static List<String> extractAddressCandidates(String address) {
		Set<String> candidates = new LinkedHashSet<>();
		String trimmed = PhantomAddressRules.canonical(address);
		candidates.add(trimmed);

		int atIndex = trimmed.lastIndexOf('@');
		if (atIndex >= 0 && atIndex + 1 < trimmed.length()) {
			candidates.add(trimmed.substring(atIndex + 1).trim());
		}

		int ltIndex = trimmed.indexOf('<');
		int gtIndex = trimmed.indexOf('>');
		if (ltIndex >= 0 && gtIndex > ltIndex + 1) {
			candidates.add(trimmed.substring(ltIndex + 1, gtIndex).trim());
		}

		for (String separator : new String[] { ",", ";", "|" }) {
			if (trimmed.contains(separator)) {
				for (String part : trimmed.split(java.util.regex.Pattern.quote(separator))) {
					String partTrimmed = part.trim();
					if (!partTrimmed.isEmpty()) {
						candidates.add(partTrimmed);
					}
				}
			}
		}

		return new ArrayList<>(candidates);
	}
}
