package com.yision.phantom.logistics.courier;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.yision.phantom.block.phantomport.PhantomPortBlockEntity;
import com.yision.phantom.logistics.address.PhantomAddressRules;
import com.yision.phantom.block.phantomport.PhantomPortTargetRegistry;
import com.yision.phantom.block.phantomport.PhantomPortTargetRegistry.TargetLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class AirCourierDispatchService {
	private AirCourierDispatchService() {}

	public static @Nullable AirCourierTarget resolvePackageTarget(ServerLevel level, ItemStack box,
		Vec3 origin, @Nullable ResourceKey<Level> sourceDimension, @Nullable BlockPos sourcePos) {
		ServerPlayer player = AirCourierDimensionRules.allowCrossDimensionDelivery()
			? AirCourierHelper.findTargetPlayerAnyDimension(level, box)
			: AirCourierHelper.findTargetPlayer(level, box);
		if (player != null && canReceivePackageTarget(level, new AirCourierTarget.PlayerTarget(player.getUUID(),
			player.serverLevel().dimension()), box)) {
			return new AirCourierTarget.PlayerTarget(player.getUUID(), player.serverLevel().dimension());
		}

		AirCourierTarget.PhantomPortTarget phantomPort = findPhantomPortExcludingSource(level, box, origin, sourceDimension, sourcePos);
		return phantomPort;
	}

	private static @Nullable AirCourierTarget.PhantomPortTarget findPhantomPortExcludingSource(ServerLevel level, ItemStack box,
		Vec3 origin, @Nullable ResourceKey<Level> sourceDimension, @Nullable BlockPos sourcePos) {
		if (!PackageItem.isPackage(box)) {
			return null;
		}
		String address = PhantomAddressRules.canonical(PackageItem.getAddress(box));
		if (address.isBlank()) {
			return null;
		}
		TargetLocation location = PhantomPortTargetRegistry.findMatchingAnyDimension(level, address, origin,
			sourceDimension, sourcePos, target -> AirCourierDimensionRules.canTarget(level, target.dimension())
					&& canReceivePhantomPortTarget(level, target, box));
		if (location == null) {
			return null;
		}
		return new AirCourierTarget.PhantomPortTarget(location.dimension(), location.pos());
	}

	public static boolean canReceivePackageTarget(ServerLevel level, AirCourierTarget target, ItemStack box) {
		if (!AirCourierDimensionRules.canTarget(level, target.dimension())) {
			return false;
		}
		return switch (target) {
			case AirCourierTarget.PlayerTarget playerTarget -> {
				ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerTarget.playerId());
				yield player != null && player.isAlive()
					&& player.serverLevel().dimension().equals(playerTarget.dimension())
					&& AirCourierHelper.canReceiveDelivery(player, box);
			}
			case AirCourierTarget.PhantomPortTarget phantomPortTarget ->
				canReceivePhantomPortTarget(level, new TargetLocation(phantomPortTarget.dimension(), phantomPortTarget.pos(), ""), box);
		};
	}

	private static boolean canReceivePhantomPortTarget(ServerLevel level, TargetLocation target, ItemStack box) {
		ServerLevel targetLevel = level.getServer().getLevel(target.dimension());
		if (targetLevel == null || !targetLevel.isPositionEntityTicking(target.pos())) {
			return false;
		}
		BlockEntity blockEntity = targetLevel.getBlockEntity(target.pos());
		return blockEntity instanceof PhantomPortBlockEntity phantomPort && phantomPort.canReceiveCourier(box);
	}

	public static boolean canReceiveCarrierTarget(ServerLevel level,
		ResourceKey<Level> targetDimension, BlockPos targetPos) {
		ServerLevel targetLevel = level.getServer().getLevel(targetDimension);
		if (targetLevel == null || !targetLevel.isPositionEntityTicking(targetPos)) {
			return false;
		}
		return targetLevel.getBlockEntity(targetPos) instanceof PhantomPortBlockEntity phantomPort
			&& phantomPort.canReceiveCarrier();
	}
}
