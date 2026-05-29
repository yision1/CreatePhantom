package com.yision.phantom.mixin;

import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import com.simibubi.create.content.kinetics.belt.transport.BeltInventory;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.yision.phantom.logistics.courier.AirCourierDispatchService;
import com.yision.phantom.entity.courier.AirCourierEntity;
import com.yision.phantom.logistics.courier.AirCourierHelper;
import com.yision.phantom.logistics.courier.AirCourierLaunchRules;
import com.yision.phantom.logistics.courier.AirCourierTask;
import com.yision.phantom.logistics.courier.AirCourierTaskManager;
import com.yision.phantom.logistics.courier.hud.AirCourierHudSync;
import com.yision.phantom.logistics.courier.AirCourierTarget;
import com.yision.phantom.item.miniphantom.MiniPhantomItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(BeltInventory.class)
public abstract class BeltInventoryMixin {
	@Shadow
	@Final
	BeltBlockEntity belt;

	@Shadow
	boolean beltMovementPositive;

	@Inject(method = "eject", at = @At("HEAD"), cancellable = true)
	private void createphantom$launchCourier(TransportedItemStack stack, CallbackInfo ci) {
		if (!AirCourierHelper.isCourierLaunchStack(stack.stack)) {
			return;
		}
		if (!AirCourierLaunchRules.canLaunchFrom(belt, stack.insertedAt, beltMovementPositive)) {
			return;
		}

		if (MiniPhantomItem.hasCargo(stack.stack)) {
			launchPackageCourier(stack, ci);
			return;
		}
		if (MiniPhantomItem.getReturnTarget(stack.stack).isPresent()) {
			launchReturningCarrierToPhantomPort(stack, ci);
			return;
		}
		if (MiniPhantomItem.getPlayerReturnTarget(stack.stack).isPresent()) {
			launchReturningCarrierToPlayer(stack, ci);
		}
	}

	private void launchPackageCourier(TransportedItemStack stack, CallbackInfo ci) {
		if (!(belt.getLevel() instanceof ServerLevel serverLevel)) {
			ci.cancel();
			return;
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
			return;
		}

		Vec3 outPos = BeltHelper.getVectorForOffset(belt, stack.beltPosition);
		Vec3 launchDirection = AirCourierHelper.getCourierLaunchDirection(belt, stack);
		Vec3 launchMotion = AirCourierHelper.getCourierLaunchMotion(belt, stack);
		Vec3 spawnPos = outPos.add(launchMotion.normalize().scale(0.001)).add(0, 6 / 16f, 0);

		UUID taskId = UUID.randomUUID();
		UUID hudEntryId = MiniPhantomItem.getHudEntryId(stack.stack);

		AirCourierTask task = switch (target) {
			case AirCourierTarget.PhantomPortTarget phantomPort -> AirCourierTask.forPackageToAirport(
				taskId, box, serverLevel, phantomPort.dimension(), phantomPort.pos(),
				spawnPos, launchDirection, launchMotion,
				sourcePhantomPortDimension, sourcePhantomPortPos, null, hudEntryId, null);
			case AirCourierTarget.PlayerTarget player -> {
				ServerPlayer targetPlayer = serverLevel.getServer().getPlayerList().getPlayer(player.playerId());
				yield targetPlayer != null
					? AirCourierTask.forPackageToPlayer(taskId, box, serverLevel, player.playerId(),
						player.dimension(), spawnPos, launchDirection, launchMotion,
						sourcePhantomPortDimension, sourcePhantomPortPos, null, hudEntryId, null)
					: null;
			}
		};
		if (task == null) {
			return;
		}

		AirCourierTaskManager.addTask(serverLevel.getServer(), task);
		AirCourierHudSync.onCourierTaskStarted(serverLevel.getServer(), task);

		ci.cancel();
	}

	private void launchReturningCarrierToPhantomPort(TransportedItemStack stack, CallbackInfo ci) {
		var returnTarget = MiniPhantomItem.getReturnTarget(stack.stack);
		if (returnTarget.isEmpty()) {
			return;
		}
		if (!(belt.getLevel() instanceof ServerLevel serverLevel)) {
			ci.cancel();
			return;
		}

		Vec3 outPos = BeltHelper.getVectorForOffset(belt, stack.beltPosition);
		Vec3 launchDirection = AirCourierHelper.getCourierLaunchDirection(belt, stack);
		Vec3 launchMotion = AirCourierHelper.getCourierLaunchMotion(belt, stack);
		Vec3 spawnPos = outPos.add(launchMotion.normalize().scale(0.001)).add(0, 6 / 16f, 0);

		var target = returnTarget.get();
		AirCourierTask task = AirCourierTask.forCarrierReturn(
			UUID.randomUUID(), serverLevel, target.dimension(), target.pos(),
			spawnPos, launchDirection, launchMotion);

		AirCourierTaskManager.addTask(serverLevel.getServer(), task);

		ci.cancel();
	}

	private void launchReturningCarrierToPlayer(TransportedItemStack stack, CallbackInfo ci) {
		var returnTarget = MiniPhantomItem.getPlayerReturnTarget(stack.stack);
		if (returnTarget.isEmpty()) {
			return;
		}
		if (!(belt.getLevel() instanceof ServerLevel serverLevel)) {
			ci.cancel();
			return;
		}
		ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(returnTarget.get());
		if (player == null || !player.isAlive()) {
			return;
		}

		Vec3 outPos = BeltHelper.getVectorForOffset(belt, stack.beltPosition);
		Vec3 launchDirection = AirCourierHelper.getCourierLaunchDirection(belt, stack);
		Vec3 launchMotion = AirCourierHelper.getCourierLaunchMotion(belt, stack);
		Vec3 spawnPos = outPos.add(launchMotion.normalize().scale(0.001)).add(0, 6 / 16f, 0);

		AirCourierTask task = AirCourierTask.forCarrierReturnToPlayer(
			UUID.randomUUID(), serverLevel, player.getUUID(), player.serverLevel().dimension(),
			spawnPos, launchDirection, launchMotion);

		AirCourierTaskManager.addTask(serverLevel.getServer(), task);

		ci.cancel();
	}
}
