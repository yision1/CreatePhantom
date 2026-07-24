package com.yision.phantom.logistics.courier;

import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.yision.phantom.compat.sable.SableCompat;
import com.yision.phantom.item.miniphantom.MiniPhantomItem;
import com.yision.phantom.logistics.courier.hud.AirCourierHudSync;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class AirCourierBeltHooks {
	private AirCourierBeltHooks() {}

	public static void alignCourierLaunchStack(BeltBlockEntity belt, TransportedItemStack stack) {
		if (!AirCourierHelper.isCourierLaunchStack(stack.stack)) {
			return;
		}
		MiniPhantomItem.setHeadingAngle(stack.stack,
			AirCourierHelper.getHeadingAngle(AirCourierHelper.resolveBeltHeading(belt)));
		stack.angle = 180;
		stack.sideOffset = stack.prevSideOffset = stack.getTargetSideOffset();
	}

	public static boolean tryLaunchCourier(BeltBlockEntity belt, TransportedItemStack stack) {
		if (!AirCourierHelper.isCourierLaunchStack(stack.stack)) {
			return false;
		}
		boolean beltMovementPositive = belt.getDirectionAwareBeltMovementSpeed() > 0;
		if (!AirCourierLaunchRules.canLaunchFrom(belt, stack.insertedAt, beltMovementPositive)) {
			return false;
		}

		if (MiniPhantomItem.hasCargo(stack.stack)) {
			return launchPackageCourier(belt, stack);
		}
		if (MiniPhantomItem.getReturnTarget(stack.stack).isPresent()) {
			return launchReturningCarrierToPhantomPort(belt, stack);
		}
		if (MiniPhantomItem.getPlayerReturnTarget(stack.stack).isPresent()) {
			return launchReturningCarrierToPlayer(belt, stack);
		}
		return false;
	}

	private static boolean launchPackageCourier(BeltBlockEntity belt, TransportedItemStack stack) {
		if (!(belt.getLevel() instanceof ServerLevel serverLevel)) {
			return true;
		}

		var box = MiniPhantomItem.copyCargoPackage(stack.stack);
		var sourceReturnTarget = MiniPhantomItem.getReturnTarget(stack.stack);
		var sourcePhantomPortDimension = sourceReturnTarget.map(target -> target.dimension())
			.orElse(serverLevel.dimension());
		var sourcePhantomPortPos = sourceReturnTarget.map(target -> target.pos())
			.orElseGet(() -> AirCourierHelper.findSourcePhantomPortPos(belt));
		AirCourierTarget target = AirCourierDispatchService.resolvePackageTarget(serverLevel, box,
			Vec3.atCenterOf(belt.getBlockPos()), sourcePhantomPortDimension, sourcePhantomPortPos);
		if (target == null) {
			return false;
		}

		LaunchGeometry launch = getLaunchGeometry(serverLevel, belt, stack);
		UUID taskId = UUID.randomUUID();
		UUID hudEntryId = MiniPhantomItem.getHudEntryId(stack.stack);

		AirCourierTask task = switch (target) {
			case AirCourierTarget.PhantomPortTarget phantomPort -> AirCourierTask.forPackageToAirport(
				taskId, box, serverLevel, phantomPort.dimension(), phantomPort.pos(),
				launch.spawnPos(), launch.direction(), launch.motion(),
				sourcePhantomPortDimension, sourcePhantomPortPos, null, hudEntryId, null);
			case AirCourierTarget.PlayerTarget player -> {
				ServerPlayer targetPlayer = serverLevel.getServer().getPlayerList().getPlayer(player.playerId());
				yield targetPlayer != null
					? AirCourierTask.forPackageToPlayer(taskId, box, serverLevel, player.playerId(),
						player.dimension(), launch.spawnPos(), launch.direction(), launch.motion(),
						sourcePhantomPortDimension, sourcePhantomPortPos, null, hudEntryId, null)
					: null;
			}
		};
		if (task == null) {
			return false;
		}

		AirCourierTaskManager.addTask(serverLevel.getServer(), task);
		AirCourierHudSync.onCourierTaskStarted(serverLevel.getServer(), task);
		return true;
	}

	private static boolean launchReturningCarrierToPhantomPort(BeltBlockEntity belt, TransportedItemStack stack) {
		var returnTarget = MiniPhantomItem.getReturnTarget(stack.stack);
		if (returnTarget.isEmpty()) {
			return false;
		}
		if (!(belt.getLevel() instanceof ServerLevel serverLevel)) {
			return true;
		}

		var target = returnTarget.get();
		if (!AirCourierDispatchService.canReceiveCarrierTarget(
			serverLevel, target.dimension(), target.pos())) {
			return false;
		}
		LaunchGeometry launch = getLaunchGeometry(serverLevel, belt, stack);
		AirCourierTask task = AirCourierTask.forCarrierReturn(
			UUID.randomUUID(), serverLevel, target.dimension(), target.pos(),
			launch.spawnPos(), launch.direction(), launch.motion());

		AirCourierTaskManager.addTask(serverLevel.getServer(), task);
		return true;
	}

	private static boolean launchReturningCarrierToPlayer(BeltBlockEntity belt, TransportedItemStack stack) {
		var returnTarget = MiniPhantomItem.getPlayerReturnTarget(stack.stack);
		if (returnTarget.isEmpty()) {
			return false;
		}
		if (!(belt.getLevel() instanceof ServerLevel serverLevel)) {
			return true;
		}
		ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(returnTarget.get());
		if (player == null || !player.isAlive()) {
			return false;
		}

		LaunchGeometry launch = getLaunchGeometry(serverLevel, belt, stack);
		AirCourierTask task = AirCourierTask.forCarrierReturnToPlayer(
			UUID.randomUUID(), serverLevel, player.getUUID(), player.serverLevel().dimension(),
			launch.spawnPos(), launch.direction(), launch.motion());

		AirCourierTaskManager.addTask(serverLevel.getServer(), task);
		return true;
	}

	private static Vec3 getSpawnPos(BeltBlockEntity belt, TransportedItemStack stack, Vec3 launchMotion) {
		Vec3 outPos = BeltHelper.getVectorForOffset(belt, stack.beltPosition);
		return outPos.add(launchMotion.normalize().scale(0.001)).add(0, 6 / 16f, 0);
	}

	private static LaunchGeometry getLaunchGeometry(ServerLevel level, BeltBlockEntity belt,
		TransportedItemStack stack) {
		Vec3 localDirection = AirCourierHelper.getCourierLaunchDirection(belt, stack);
		Vec3 localMotion = AirCourierHelper.getCourierLaunchMotion(belt, stack);
		Vec3 localSpawnPos = getSpawnPos(belt, stack, localMotion);
		return new LaunchGeometry(
			SableCompat.projectOutOfSubLevel(level, localSpawnPos),
			SableCompat.projectVector(level, localSpawnPos, localDirection),
			SableCompat.projectVector(level, localSpawnPos, localMotion));
	}

	private record LaunchGeometry(Vec3 spawnPos, Vec3 direction, Vec3 motion) {}
}
