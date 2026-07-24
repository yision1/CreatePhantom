package com.yision.phantom.logistics.courier.flight;

import com.yision.phantom.block.phantomport.PhantomPortBlockEntity;
import com.yision.phantom.compat.sable.SableCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class AirCourierFlightTargets {

	private AirCourierFlightTargets() {}

	public static Vec3 cruiseTarget(AirCourierFlightProfile profile,
		@Nullable PhantomPortBlockEntity phantomPort, @Nullable ServerPlayer player) {
		if (phantomPort != null) {
			return cruiseTarget(profile, phantomPort.getLevel(), phantomPort.getBlockPos());
		}
		if (player != null) {
			return playerDeliveryTarget(profile, player).add(0, profile.playerCruiseHeight() - profile.playerTargetHeight(), 0);
		}
		return Vec3.ZERO;
	}

	public static Vec3 cruiseTarget(AirCourierFlightProfile profile, BlockPos phantomPortPos) {
		return cruiseTarget(profile, null, phantomPortPos);
	}

	public static Vec3 cruiseTarget(AirCourierFlightProfile profile,
		@Nullable Level level, BlockPos phantomPortPos) {
		return SableCompat.projectOutOfSubLevel(level, Vec3.atCenterOf(phantomPortPos))
			.add(0, profile.phantomPortCruiseHeight(), 0);
	}

	public static Vec3 landingTarget(AirCourierFlightProfile profile,
		@Nullable PhantomPortBlockEntity phantomPort, @Nullable ServerPlayer player) {
		if (phantomPort != null) {
			return landingTarget(profile, phantomPort.getLevel(), phantomPort.getBlockPos());
		}
		if (player != null) {
			return playerDeliveryTarget(profile, player);
		}
		return Vec3.ZERO;
	}

	public static Vec3 landingTarget(AirCourierFlightProfile profile, BlockPos phantomPortPos) {
		return landingTarget(profile, null, phantomPortPos);
	}

	public static Vec3 landingTarget(AirCourierFlightProfile profile,
		@Nullable Level level, BlockPos phantomPortPos) {
		return SableCompat.projectOutOfSubLevel(level, Vec3.atCenterOf(phantomPortPos))
			.add(0, profile.phantomPortLandingHeight(), 0);
	}

	public static double completionDistance(AirCourierFlightProfile profile,
		@Nullable PhantomPortBlockEntity phantomPort, @Nullable ServerPlayer player) {
		if (phantomPort != null) {
			return profile.phantomPortCompletionDistance();
		}
		return profile.playerCompletionDistance();
	}

	public static Vec3 playerDeliveryTarget(AirCourierFlightProfile profile, ServerPlayer player) {
		Vec3 horizontalLook = player.getLookAngle().multiply(1, 0, 1);
		if (horizontalLook.lengthSqr() > 1.0E-6) {
			horizontalLook = horizontalLook.normalize();
		} else {
			float bodyYaw = player.yBodyRot;
			float yawRad = bodyYaw * Mth.DEG_TO_RAD;
			horizontalLook = new Vec3(-Mth.sin(yawRad), 0, Mth.cos(yawRad));
		}
		return player.position()
			.add(0, profile.playerTargetHeight(), 0)
			.add(horizontalLook.scale(profile.playerForwardOffset()));
	}

	public static Vec3 approachGate(AirCourierFlightProfile profile, Vec3 currentPos,
		Vec3 currentMotion, Vec3 landingTarget, boolean playerTarget) {
		double dx = currentPos.x - landingTarget.x;
		double dz = currentPos.z - landingTarget.z;
		double horizontalDist = Math.sqrt(dx * dx + dz * dz);
		Vec3 approachDir;
		if (horizontalDist < 1.0E-4) {
			Vec3 hMotion = new Vec3(currentMotion.x, 0, currentMotion.z);
			if (hMotion.lengthSqr() > 1.0E-6) {
				approachDir = hMotion.normalize();
			} else {
				approachDir = new Vec3(0, 0, 1);
			}
		} else {
			approachDir = new Vec3(dx / horizontalDist, 0, dz / horizontalDist);
		}

		double approachDistance = Mth.clamp(horizontalDist * 0.22, 6.0, 18.0);
		double approachHeight = playerTarget ? profile.playerApproachHeight() : profile.phantomPortApproachHeight();

		return landingTarget.add(approachDir.scale(approachDistance)).add(0, approachHeight, 0);
	}
}
