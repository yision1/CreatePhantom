package com.yision.phantom.entity.courier;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.yision.phantom.logistics.courier.AirCourierDispatchService;
import com.yision.phantom.logistics.courier.AirCourierTarget;
import com.yision.phantom.logistics.courier.flight.AirCourierFlightMath;
import com.yision.phantom.logistics.courier.flight.AirCourierFlightProfile;
import com.yision.phantom.logistics.courier.AirCourierTask;
import com.yision.phantom.logistics.courier.AirCourierTaskManager;
import com.yision.phantom.logistics.courier.hud.AirCourierHudSync;
import com.yision.phantom.item.miniphantom.MiniPhantomItem;
import com.yision.phantom.registry.AllEntityTypes;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class AirCourierEntity extends Entity implements Container {
	private static final EntityDataAccessor<ItemStack> DATA_PACKAGE =
		SynchedEntityData.defineId(AirCourierEntity.class, EntityDataSerializers.ITEM_STACK);
	private static final EntityDataAccessor<Byte> DATA_PHASE =
		SynchedEntityData.defineId(AirCourierEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Vector3f> DATA_LAUNCH_DIRECTION =
		SynchedEntityData.defineId(AirCourierEntity.class, EntityDataSerializers.VECTOR3);
	private static final EntityDataAccessor<Byte> DATA_MISSION =
		SynchedEntityData.defineId(AirCourierEntity.class, EntityDataSerializers.BYTE);

	private static final AirCourierFlightProfile FLIGHT = AirCourierFlightProfile.DEFAULT;
	private static final double CLIENT_FAST_CORRECTION_DISTANCE_SQR = 4.0;
	private static final double CLIENT_HARD_SNAP_DISTANCE_SQR = 64.0;
	private static final double CLIENT_CORRECTION_EPSILON_SQR = 1.0E-4;
	private static final double CLIENT_CORRECTION_BLEND = 0.1;
	private static final int CLIENT_SYNC_TARGET_MAX_AGE = 3;
	private static final float MAX_ROLL_INPUT_DEGREES = 8.0f;
	private static final float MAX_ROLL_CHANGE_DEGREES = 1.25f;
	private static final float ROLL_SMOOTHING = 0.25f;

	@Nullable
	private UUID hudEntryId;
	private Vec3 launchDirection = new Vec3(0, 0, 1);
	@Nullable
	private Vec3 clientSyncedPos;
	private int clientSyncedPosTick;

	@Nullable
	private UUID taskId;

	public float oldDeltaYaw;
	public float newDeltaYaw;

	public AirCourierEntity(EntityType<? extends AirCourierEntity> type, Level level) {
		super(type, level);
		noPhysics = true;
	}

	public static AirCourierEntity createWaiting(Level level, ItemStack box, Vec3 launchDirection) {
		AirCourierEntity courier = new AirCourierEntity(AllEntityTypes.AIR_COURIER.get(), level);
		courier.setPackage(box);
		courier.setPhase(Phase.WAITING);
		courier.setNoGravity(true);
		courier.setDeltaMovement(Vec3.ZERO);
		courier.alignToDirection(launchDirection);
		return courier;
	}

	public static AirCourierEntity createEmpty(EntityType<? extends AirCourierEntity> type, Level level) {
		return new AirCourierEntity(type, level);
	}

	public static @Nullable AirCourierEntity createFromTask(ServerLevel level, AirCourierTask task) {
		AirCourierEntity courier = new AirCourierEntity(AllEntityTypes.AIR_COURIER.get(), level);
		courier.taskId = task.id();
		courier.setPackage(task.box());
		courier.setMission(task.mission());
		courier.setPhase(task.phase());
		courier.setNoGravity(true);
		courier.setPos(task.position());
		courier.setDeltaMovement(task.motion());
		courier.setLaunchDirection(task.launchDirection());

		courier.hudEntryId = task.hudEntryId();
		courier.hasImpulse = true;
		courier.hurtMarked = true;
		courier.snapRotationToMotion();
		return courier;
	}

	@Override
	public void tick() {
		super.tick();
		setNoGravity(true);
		noPhysics = getPhase() != Phase.WAITING;

		if (getPhase() == Phase.WAITING) {
			tickWaiting();
			return;
		}

		if (level().isClientSide()) {
			tickClient();
			return;
		}

		tickVisualProxy();
	}

	private void tickVisualProxy() {
		if (taskId == null) {
			discard();
			return;
		}
		if (getPhase() == Phase.TAKEOFF) {
			lockTakeoffRotation();
		} else {
			updateRotation();
			updateRoll();
		}
	}

	private void tickWaiting() {
		syncLaunchDirectionFromEntityData();
		setDeltaMovement(Vec3.ZERO);
		setXRot(0);
		xRotO = 0;
		oldDeltaYaw = 0;
		newDeltaYaw = 0;
	}

	private void tickClient() {
		syncLaunchDirectionFromEntityData();
		setPos(position().add(getDeltaMovement()));
		applyClientCorrection();

		if (getPhase() == Phase.TAKEOFF) {
			lockTakeoffRotation();
			if (tickCount % 3 == 0) {
				Vec3 trail = getDeltaMovement().scale(-0.2);
				level().addParticle(ParticleTypes.CLOUD, getX(), getY(), getZ(), trail.x, Math.max(trail.y, 0.02), trail.z);
			}
			return;
		}

		updateRotation();
		updateRoll();

		if (getPhase() == Phase.LANDING && tickCount % 3 == 0) {
			Vec3 trail = getDeltaMovement().scale(-0.2);
			level().addParticle(ParticleTypes.CLOUD, getX(), getY(), getZ(), trail.x, Math.max(trail.y, 0.02), trail.z);
		}
	}

	// ── Rotation & Visual ──

	private void updateRotation() {
		Vec3 motion = getDeltaMovement();
		if (motion.lengthSqr() < 1.0E-6) {
			return;
		}
		double horizontalDistance = motion.horizontalDistance();
		setXRot(lerpRotation(xRotO, (float) (Mth.atan2(motion.y, horizontalDistance) * 180.0F / Math.PI)));
		setYRot(lerpRotation(yRotO, (float) (Mth.atan2(motion.x, motion.z) * 180.0F / Math.PI)));
	}

	private void snapClientRotationToServer(float yRot, float xRot) {
		float continuousYRot = unwrapRotation(getYRot(), yRot);
		float continuousXRot = unwrapRotation(getXRot(), xRot);
		setYRot(continuousYRot);
		setXRot(continuousXRot);
		yRotO = continuousYRot;
		xRotO = continuousXRot;
		oldDeltaYaw = 0;
		newDeltaYaw = 0;
	}

	private void updateRoll() {
		oldDeltaYaw = newDeltaYaw;
		float targetRoll = Mth.wrapDegrees(yRotO - getYRot());
		targetRoll = Mth.clamp(targetRoll, -MAX_ROLL_INPUT_DEGREES, MAX_ROLL_INPUT_DEGREES);
		float smoothedRoll = Mth.lerp(ROLL_SMOOTHING, newDeltaYaw, targetRoll);
		float rollStep = Mth.clamp(smoothedRoll - newDeltaYaw, -MAX_ROLL_CHANGE_DEGREES, MAX_ROLL_CHANGE_DEGREES);
		newDeltaYaw += rollStep;
	}

	private void snapRotationToMotion() {
		Vec3 motion = getDeltaMovement();
		if (motion.lengthSqr() < 1.0E-6) {
			return;
		}
		double horizontalDistance = motion.horizontalDistance();
		float xRot = (float) (Mth.atan2(motion.y, horizontalDistance) * 180.0F / Math.PI);
		float yRot = (float) (Mth.atan2(motion.x, motion.z) * 180.0F / Math.PI);
		setXRot(xRot);
		setYRot(yRot);
		xRotO = xRot;
		yRotO = yRot;
		oldDeltaYaw = 0;
		newDeltaYaw = 0;
	}

	private void alignToDirection(Vec3 direction) {
		Vec3 horizontal = AirCourierFlightMath.sanitizeNonNegativeDirection(new Vec3(direction.x, 0, direction.z));
		setLaunchDirection(horizontal);
		float yRot = (float) (Mth.atan2(horizontal.x, horizontal.z) * 180.0F / Math.PI);
		setYRot(yRot);
		yRotO = yRot;
		setXRot(0);
		xRotO = 0;
		oldDeltaYaw = 0;
		newDeltaYaw = 0;
	}

	private void lockTakeoffRotation() {
		Vec3 motion = getDeltaMovement();
		if (motion.lengthSqr() > 1.0E-6) {
			double horizontalDistance = motion.horizontalDistance();
			float xRot = (float) (Mth.atan2(motion.y, horizontalDistance) * 180.0F / Math.PI);
			float yRot = (float) (Mth.atan2(motion.x, motion.z) * 180.0F / Math.PI);
			setXRot(xRot);
			setYRot(yRot);
			xRotO = xRot;
			yRotO = yRot;
		}
		oldDeltaYaw = 0;
		newDeltaYaw = 0;
	}

	private void applyClientCorrection() {
		if (clientSyncedPos == null) {
			return;
		}
		if (tickCount - clientSyncedPosTick > CLIENT_SYNC_TARGET_MAX_AGE) {
			clientSyncedPos = null;
			return;
		}
		double distanceSqr = position().distanceToSqr(clientSyncedPos);
		if (distanceSqr > CLIENT_HARD_SNAP_DISTANCE_SQR) {
			setPos(clientSyncedPos);
			clientSyncedPos = null;
			return;
		}
		if (distanceSqr <= CLIENT_CORRECTION_EPSILON_SQR) {
			clientSyncedPos = null;
			return;
		}
		double blend = distanceSqr > CLIENT_FAST_CORRECTION_DISTANCE_SQR ? 0.35 : CLIENT_CORRECTION_BLEND;
		Vec3 correctedPos = position().lerp(clientSyncedPos, blend);
		setPos(correctedPos);
		if (correctedPos.distanceToSqr(clientSyncedPos) <= CLIENT_CORRECTION_EPSILON_SQR) {
			clientSyncedPos = null;
		}
	}

	private static float lerpRotation(float currentRotation, float targetRotation, float smoothing) {
		return Mth.lerp(smoothing, currentRotation, unwrapRotation(currentRotation, targetRotation));
	}

	private static float lerpRotation(float currentRotation, float targetRotation) {
		return lerpRotation(currentRotation, targetRotation, 0.2F);
	}

	private static float unwrapRotation(float referenceRotation, float targetRotation) {
		return referenceRotation + Mth.wrapDegrees(targetRotation - referenceRotation);
	}

	public ItemStack getPackage() {
		return getEntityData().get(DATA_PACKAGE);
	}

	public void setPackage(ItemStack box) {
		getEntityData().set(DATA_PACKAGE, PackageItem.isPackage(box) ? box.copy() : ItemStack.EMPTY);
	}

	public Phase getPhase() {
		return Phase.byId(getEntityData().get(DATA_PHASE));
	}

	public void setPhase(Phase phase) {
		getEntityData().set(DATA_PHASE, (byte) phase.id);
	}

	public Mission getMission() {
		return Mission.byId(getEntityData().get(DATA_MISSION));
	}

	public void setMission(Mission mission) {
		getEntityData().set(DATA_MISSION, (byte) mission.id);
	}

	private void setLaunchDirection(Vec3 direction) {
		launchDirection = AirCourierFlightMath.sanitizeNonNegativeDirection(direction);
		getEntityData().set(DATA_LAUNCH_DIRECTION,
			new Vector3f((float) launchDirection.x, (float) launchDirection.y, (float) launchDirection.z));
	}

	private void syncLaunchDirectionFromEntityData() {
		Vec3 syncedDirection = AirCourierFlightMath.sanitizeNonNegativeDirection(fromSyncedLaunchDirection(getEntityData().get(DATA_LAUNCH_DIRECTION)));
		if (syncedDirection.distanceToSqr(launchDirection) > 1.0E-6) {
			launchDirection = syncedDirection;
		}
	}

	private static Vec3 fromSyncedLaunchDirection(Vector3f direction) {
		return new Vec3(direction.x(), direction.y(), direction.z());
	}

	@Nullable
	public UUID getHudEntryId() {
		return hudEntryId;
	}

	public void setHudEntryId(UUID hudEntryId) {
		this.hudEntryId = hudEntryId;
	}

	private boolean exposesPackageContents() {
		return getPhase() == Phase.WAITING && PackageItem.isPackage(getPackage());
	}

	@Override
	public int getContainerSize() {
		return PackageItem.SLOTS;
	}

	@Override
	public boolean isEmpty() {
		if (!exposesPackageContents()) {
			return true;
		}
		ItemStackHandler contents = PackageItem.getContents(getPackage());
		for (int slot = 0; slot < contents.getSlots(); slot++) {
			if (!contents.getStackInSlot(slot).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack getItem(int slot) {
		if (!exposesPackageContents() || slot < 0 || slot >= PackageItem.SLOTS) {
			return ItemStack.EMPTY;
		}
		return PackageItem.getContents(getPackage())
			.getStackInSlot(slot)
			.copy();
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		return ItemStack.EMPTY;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public void setItem(int slot, ItemStack stack) {}

	@Override
	public void setChanged() {}

	@Override
	public boolean stillValid(Player player) {
		return isAlive() && exposesPackageContents() && player.distanceToSqr(this) <= 64.0;
	}

	@Override
	public void clearContent() {}

	@Override
	public ItemStack getPickedResult(HitResult target) {
		if (getPhase() != Phase.WAITING) {
			return ItemStack.EMPTY;
		}
		ItemStack picked = MiniPhantomItem.createLoaded(getPackage());
		MiniPhantomItem.setHeadingAngle(picked, Math.round(getYRot()));
		if (hudEntryId != null) {
			MiniPhantomItem.setHudEntryId(picked, hudEntryId);
		}
		return picked;
	}

	@Override
	public boolean hurt(net.minecraft.world.damagesource.DamageSource damageSource, float amount) {
		if (getPhase() != Phase.WAITING) {
			return false;
		}
		if (level().isClientSide()) {
			return true;
		}
		if (!(damageSource.getEntity() instanceof Player player)) {
			return false;
		}

		ItemStack droppedStack = MiniPhantomItem.createLoaded(getPackage());
		MiniPhantomItem.setHeadingAngle(droppedStack, Math.round(getYRot()));
		if (hudEntryId != null) {
			MiniPhantomItem.setHudEntryId(droppedStack, hudEntryId);
		}
		ItemEntity itemEntity = new ItemEntity(level(), getX(), getY(), getZ(), droppedStack);
		Vec3 popMotion = player.getLookAngle().multiply(1, 0, 1);
		if (popMotion.lengthSqr() > 1.0E-6) {
			popMotion = popMotion.normalize().scale(0.12);
		} else {
			popMotion = Vec3.ZERO;
		}
		itemEntity.setDeltaMovement(popMotion.add(0, 0.08, 0));
		level().addFreshEntity(itemEntity);
		level().playSound(null, blockPosition(), SoundEvents.ITEM_FRAME_BREAK, SoundSource.PLAYERS, 0.7f, 1.0f);
		discard();
		return false;
	}

	@Override
	public boolean isPickable() {
		return getPhase() == Phase.WAITING;
	}

	@Override
	public boolean canBeCollidedWith() {
		return getPhase() == Phase.WAITING;
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand) {
		if (getPhase() != Phase.WAITING) {
			return InteractionResult.PASS;
		}

		ItemStack heldItem = player.getItemInHand(hand);
		if (heldItem.is(Items.FIREWORK_ROCKET)) {
			if (level().isClientSide()) {
				return InteractionResult.SUCCESS;
			}
			if (!(level() instanceof ServerLevel serverLevel)) {
				return InteractionResult.CONSUME;
			}

			AirCourierTarget target = AirCourierDispatchService.resolvePackageTarget(serverLevel, getPackage(), position(),
				null, null);
			if (target == null) {
				player.displayClientMessage(
					Component.translatable("gui.createphantom.mini_phantom.invalid_target_phantomport")
						.withStyle(ChatFormatting.RED),
					true);
				return InteractionResult.CONSUME;
			}

			Vec3 launchDir = AirCourierFlightMath.sanitizeNonNegativeDirection(
				new Vec3(launchDirection.x, 0, launchDirection.z));
			Vec3 launchMotion = launchDir.scale(FLIGHT.takeoffSpeed()).add(0, 0.15f, 0);
			Vec3 spawnPos = position().add(0, 0.01, 0);

			java.util.UUID newTaskId = java.util.UUID.randomUUID();
			AirCourierTask task;

			if (target instanceof AirCourierTarget.PhantomPortTarget phantomPortTarget) {
				task = AirCourierTask.forPackageToAirport(newTaskId, getPackage(), serverLevel,
					phantomPortTarget.dimension(), phantomPortTarget.pos(),
					spawnPos, launchDir, launchMotion,
					null, null,
					player instanceof ServerPlayer sp ? sp.getUUID() : null,
					hudEntryId,
					player instanceof ServerPlayer sp2 ? sp2.getUUID() : null);
			} else if (target instanceof AirCourierTarget.PlayerTarget playerTarget) {
				ServerPlayer targetPlayer = serverLevel.getServer().getPlayerList().getPlayer(playerTarget.playerId());
				if (targetPlayer == null) {
					player.displayClientMessage(
						Component.translatable("gui.createphantom.mini_phantom.invalid_target_phantomport")
							.withStyle(ChatFormatting.RED),
						true);
					return InteractionResult.CONSUME;
				}
				task = AirCourierTask.forPackageToPlayer(newTaskId, getPackage(), serverLevel,
					playerTarget.playerId(), playerTarget.dimension(),
					spawnPos, launchDir, launchMotion,
					null, null,
					player instanceof ServerPlayer sp ? sp.getUUID() : null,
					hudEntryId,
					player instanceof ServerPlayer sp2 ? sp2.getUUID() : null);
			} else {
				return InteractionResult.CONSUME;
			}

			AirCourierTaskManager.addTask(serverLevel.getServer(), task);
			AirCourierHudSync.onCourierTaskStarted(serverLevel.getServer(), task);

			level().playSound(null, blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.8f, 1.0f);
			if (!player.getAbilities().instabuild) {
				heldItem.shrink(1);
			}
			discard();
			return InteractionResult.SUCCESS;
		}

		if (!heldItem.isEmpty()) {
			return InteractionResult.PASS;
		}
		if (level().isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		ItemStack pickedUp = MiniPhantomItem.createLoaded(getPackage());
		MiniPhantomItem.setHeadingAngle(pickedUp, Math.round(getYRot()));
		player.setItemInHand(hand, pickedUp);
		level().playSound(null, blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2f,
			0.75f + level().random.nextFloat());
		discard();
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean shouldBeSaved() {
		return getPhase() == Phase.WAITING;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_PACKAGE, ItemStack.EMPTY);
		builder.define(DATA_PHASE, (byte) Phase.WAITING.id);
		builder.define(DATA_LAUNCH_DIRECTION, new Vector3f(0, 0, 1));
		builder.define(DATA_MISSION, (byte) Mission.PACKAGE_TO_PLAYER.id);
	}

	@Override
	protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
		setPackage(ItemStack.parseOptional(level().registryAccess(), tag.getCompound("Package")));
		if (tag.hasUUID("HudEntryId")) {
			hudEntryId = tag.getUUID("HudEntryId");
		}
		if (tag.contains("LaunchDirection")) {
			CompoundTag dirTag = tag.getCompound("LaunchDirection");
			setLaunchDirection(new Vec3(dirTag.getDouble("X"), dirTag.getDouble("Y"), dirTag.getDouble("Z")));
		}
		setPhase(Phase.byId(tag.getByte("Phase")));
		if (tag.contains("Mission")) {
			setMission(Mission.byId(tag.getByte("Mission")));
		}
	}

	@Override
	protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
		tag.put("Package", getPackage().saveOptional(level().registryAccess()));
		if (hudEntryId != null) {
			tag.putUUID("HudEntryId", hudEntryId);
		}
		CompoundTag dirTag = new CompoundTag();
		dirTag.putDouble("X", launchDirection.x);
		dirTag.putDouble("Y", launchDirection.y);
		dirTag.putDouble("Z", launchDirection.z);
		tag.put("LaunchDirection", dirTag);
		tag.putByte("Phase", (byte) getPhase().id);
		tag.putByte("Mission", (byte) getMission().id);
	}

	@Override
	public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
		if (!level().isClientSide()) {
			super.lerpTo(x, y, z, yRot, xRot, steps);
			return;
		}

		Vec3 target = new Vec3(x, y, z);
		if (tickCount < 2 || position().distanceToSqr(target) > CLIENT_HARD_SNAP_DISTANCE_SQR) {
			setPos(target);
			snapClientRotationToServer(yRot, xRot);
			setOldPosAndRot();
			clientSyncedPos = null;
		} else {
			clientSyncedPos = target;
			clientSyncedPosTick = tickCount;
		}
	}

	@Override
	public void lerpMotion(double x, double y, double z) {
		Vec3 serverMotion = new Vec3(x, y, z);
		if (!level().isClientSide()) {
			super.lerpMotion(x, y, z);
			return;
		}
		if (getPhase() == Phase.TAKEOFF) {
			setDeltaMovement(serverMotion);
			snapRotationToMotion();
			return;
		}
		if (getDeltaMovement().lengthSqr() < 1.0E-6) {
			setDeltaMovement(serverMotion);
			return;
		}
		double blend = getPhase() == Phase.LANDING ? 0.25 : 0.18;
		setDeltaMovement(getDeltaMovement().lerp(serverMotion, blend));
	}

	public enum Phase {
		WAITING(0),
		TAKEOFF(1),
		EXITING_DIMENSION(2),
		CRUISE(3),
		LANDING(4);

		private final int id;

		Phase(int id) {
			this.id = id;
		}

		public static Phase byId(byte id) {
			for (Phase phase : values()) {
				if (phase.id == id) {
					return phase;
				}
			}
			return TAKEOFF;
		}
	}

	public enum Mission {
		PACKAGE_TO_PLAYER(0),
		PACKAGE_TO_AIRPORT(1),
		CARRIER_RETURN(2),
		CARRIER_RETURN_TO_PLAYER(3);

		private final int id;

		Mission(int id) {
			this.id = id;
		}

		public static Mission byId(byte id) {
			for (Mission mission : values()) {
				if (mission.id == id) {
					return mission;
				}
			}
			return PACKAGE_TO_PLAYER;
		}
	}
}
