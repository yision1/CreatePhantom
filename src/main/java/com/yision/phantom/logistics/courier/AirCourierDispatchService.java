package com.yision.phantom.logistics.courier;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.yision.phantom.block.phantomport.PhantomPortTargetRegistry;
import com.yision.phantom.block.phantomport.PhantomPortTargetRegistry.TargetLocation;
import com.yision.phantom.logistics.address.PhantomAddressRules;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class AirCourierDispatchService {
	private AirCourierDispatchService() {
	}

	public static @Nullable AirCourierTarget resolvePackageTarget(ServerLevel level, ItemStack box,
		Vec3 origin, @Nullable ResourceKey<Level> sourceDimension, @Nullable BlockPos sourcePos) {
		if (!PackageItem.isPackage(box))
			return null;
		String address = PackageItem.getAddress(box);
		String playerName = PhantomAddressRules.playerName(address);
		if (playerName != null) {
			for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
				if (player.isAlive() && playerName.equalsIgnoreCase(player.getGameProfile().getName())
					&& AirCourierDimensionRules.canTarget(level, player.serverLevel().dimension())) {
					return new AirCourierTarget.PlayerTarget(player.getUUID(), player.serverLevel().dimension());
				}
			}
			return null;
		}

		TargetLocation location = PhantomPortTargetRegistry.findMatchingAnyDimension(level, address, origin,
			sourceDimension, sourcePos, target -> AirCourierDimensionRules.canTarget(level, target.dimension())
				&& isMatchingLoadedPort(level, target, box));
		if (location == null) {
			return null;
		}
		return new AirCourierTarget.PhantomPortTarget(location.dimension(), location.pos());
	}

	private static boolean isMatchingLoadedPort(ServerLevel level, TargetLocation target, ItemStack box) {
		var port = AirCourierDeliveryService.resolveTargetPhantomPort(
			level.getServer().getLevel(target.dimension()), target.pos());
		return port != null && PhantomAddressRules.matchesPackage(box, port.getFilterString())
			&& port.canReceiveCourier(box);
	}
}
