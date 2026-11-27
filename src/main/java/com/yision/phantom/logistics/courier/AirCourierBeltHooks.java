package com.yision.phantom.logistics.courier;

import java.util.UUID;

import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.yision.phantom.block.phantomport.PhantomPortReference;
import com.yision.phantom.compat.sable.SableCompat;
import com.yision.phantom.item.miniphantom.MiniPhantomDispatch;
import com.yision.phantom.item.miniphantom.MiniPhantomItem;
import com.yision.phantom.logistics.courier.flight.AirCourierFlightMath;
import com.yision.phantom.logistics.courier.hud.AirCourierHudSync;
import com.yision.phantom.registry.AllDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class AirCourierBeltHooks {
	private AirCourierBeltHooks() {
	}

	public static void alignCourierLaunchStack(BeltBlockEntity belt, TransportedItemStack stack) {
		if (!AirCourierHelper.isCourierLaunchStack(stack.stack)) {
			return;
		}
		MiniPhantomItem.setHeadingAngle(stack.stack,
			AirCourierHelper.getHeadingAngle(AirCourierHelper.resolveBeltHeading(belt)));
		stack.angle = 180;
		stack.sideOffset = stack.prevSideOffset = stack.getTargetSideOffset();
	}

	public static boolean tryLaunchCourierAtEnd(BeltBlockEntity belt, TransportedItemStack stack, float nextOffset) {
		if (!(belt.getLevel() instanceof ServerLevel) || !AirCourierHelper.isCourierLaunchStack(stack.stack))
			return false;
		float speed = belt.getDirectionAwareBeltMovementSpeed();
		boolean atEnd = speed > 0 && nextOffset >= belt.beltLength - 0.5f
			|| speed < 0 && nextOffset <= 0.5f;
		if (!atEnd || !AirCourierLaunchRules.canLaunchFrom(belt, stack.insertedAt, speed > 0))
			return false;

		float previousOffset = stack.beltPosition;
		stack.beltPosition = nextOffset;
		boolean launched = MiniPhantomItem.hasCargo(stack.stack)
			? launchPackageCourier(belt, stack)
			: launchReturningCarrier(belt, stack);
		if (!launched)
			stack.beltPosition = previousOffset;
		return launched;
	}

	public static void onCourierEjected(BeltBlockEntity belt, TransportedItemStack stack) {
		if (!(belt.getLevel() instanceof ServerLevel level) || !MiniPhantomItem.hasCargo(stack.stack))
			return;
		MiniPhantomDispatch dispatch = stack.stack.get(AllDataComponents.MINI_PHANTOM_DISPATCH.get());
		if (dispatch == null || dispatch.player().isEmpty())
			return;
		AirCourierDeliveryService.notifyFailure(level.getServer(), MiniPhantomItem.copyCargoPackage(stack.stack),
			dispatch.player().get(), null, dispatch.id());
	}

	private static boolean launchPackageCourier(BeltBlockEntity belt, TransportedItemStack stack) {
		if (!(belt.getLevel() instanceof ServerLevel level))
			return false;

		var box = MiniPhantomItem.copyCargoPackage(stack.stack);
		var dispatch = stack.stack.get(AllDataComponents.MINI_PHANTOM_DISPATCH.get());
		PhantomPortReference sourcePort = null;
		PhantomPortReference targetPort = null;
		ResourceKey<Level> sourceDimension;
		BlockPos sourcePos;
		AirCourierTarget target;
		if (dispatch != null) {
			sourcePort = dispatch.source();
			targetPort = dispatch.port().orElse(null);
			sourceDimension = sourcePort.dimension();
			sourcePos = sourcePort.pos();
			if (targetPort != null) {
				target = new AirCourierTarget.PhantomPortTarget(targetPort.dimension(), targetPort.pos());
			} else if (dispatch.player().isPresent()) {
				target = new AirCourierTarget.PlayerTarget(dispatch.player().get(), dispatch.dimension());
			} else {
				return false;
			}
		} else {
			var source = MiniPhantomItem.getReturnTarget(stack.stack);
			sourceDimension = source.map(value -> value.dimension()).orElse(level.dimension());
			sourcePos = source.map(value -> value.pos())
				.orElseGet(() -> AirCourierHelper.findSourcePhantomPortPos(belt));
			target = AirCourierDispatchService.resolvePackageTarget(level, box,
				belt.getBlockPos().getCenter(), sourceDimension, sourcePos);
			if (target == null)
				return false;
		}

		LaunchGeometry launch = getLaunchGeometry(level, belt, stack);
		UUID id = dispatch != null ? dispatch.id() : UUID.randomUUID();
		UUID hudEntry = MiniPhantomItem.getHudEntryId(stack.stack);
		AirCourierTask task = switch (target) {
			case AirCourierTarget.PhantomPortTarget port -> AirCourierTask.forPackageToAirport(
				id, box, level, port.dimension(), port.pos(), launch.spawnPos(), launch.direction(), launch.motion(),
				sourceDimension, sourcePos, null, hudEntry, null);
			case AirCourierTarget.PlayerTarget player -> AirCourierTask.forPackageToPlayer(
				id, box, level, player.playerId(), player.dimension(), launch.spawnPos(), launch.direction(),
				launch.motion(), sourceDimension, sourcePos, null, hudEntry, null);
		};
		task.setPortReferences(sourcePort, targetPort);
		if (!AirCourierTaskManager.addTask(level.getServer(), task))
			return false;
		AirCourierHudSync.onCourierTaskStarted(level.getServer(), task);
		return true;
	}

	private static boolean launchReturningCarrier(BeltBlockEntity belt, TransportedItemStack stack) {
		if (!(belt.getLevel() instanceof ServerLevel level))
			return false;

		LaunchGeometry launch = getLaunchGeometry(level, belt, stack);
		var portTarget = MiniPhantomItem.getReturnTarget(stack.stack);
		AirCourierTask task;
		if (portTarget.isPresent()) {
			var target = portTarget.get();
			task = AirCourierTask.forCarrierReturn(UUID.randomUUID(), level, target.dimension(), target.pos(),
				launch.spawnPos(), launch.direction(), launch.motion());
			task.setPortReferences(null, stack.stack.get(AllDataComponents.MINI_PHANTOM_RETURN_PORT.get()));
		} else {
			var playerTarget = MiniPhantomItem.getPlayerReturnTarget(stack.stack);
			if (playerTarget.isEmpty())
				return false;
			UUID playerId = playerTarget.get();
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
			ResourceKey<Level> dimension = player != null ? player.serverLevel().dimension() : level.dimension();
			task = AirCourierTask.forCarrierReturnToPlayer(UUID.randomUUID(), level, playerId, dimension,
				launch.spawnPos(), launch.direction(), launch.motion());
		}
		return AirCourierTaskManager.addTask(level.getServer(), task);
	}

	private static Vec3 getSpawnPos(BeltBlockEntity belt, TransportedItemStack stack, Vec3 launchMotion) {
		Vec3 outPos = BeltHelper.getVectorForOffset(belt, stack.beltPosition);
		return outPos.add(launchMotion.normalize().scale(0.001)).add(0, 6 / 16f, 0);
	}

	private static LaunchGeometry getLaunchGeometry(ServerLevel level, BeltBlockEntity belt,
		TransportedItemStack stack) {
		Vec3 localDirection = Vec3.atLowerCornerOf(belt.getMovementFacing().getNormal());
		float movementSpeed = Math.max(Math.abs(belt.getBeltMovementSpeed()), 1 / 8f);
		Vec3 localMotion = AirCourierFlightMath.launchMotion(localDirection, movementSpeed);
		Vec3 localSpawnPos = getSpawnPos(belt, stack, localMotion);
		return new LaunchGeometry(
			SableCompat.projectOutOfSubLevel(level, localSpawnPos),
			SableCompat.projectVector(level, localSpawnPos, localDirection),
			SableCompat.projectVector(level, localSpawnPos, localMotion));
	}

	private record LaunchGeometry(Vec3 spawnPos, Vec3 direction, Vec3 motion) {
	}
}
