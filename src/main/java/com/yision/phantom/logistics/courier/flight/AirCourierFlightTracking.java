package com.yision.phantom.logistics.courier.flight;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class AirCourierFlightTracking {

	public static final int DELIVERY_LIMIT_TICKS = 200;
	public static final int FINAL_APPROACH_TICKS = 10;
	public static final int FINAL_APPROACH_START_TICKS = DELIVERY_LIMIT_TICKS - FINAL_APPROACH_TICKS;
	private static final double REPOSITION_DISTANCE_SQR = 64.0;
	private static final double MOVEMENT_SMOOTHING = 0.25;
	private static final double FINAL_APPROACH_DISTANCE = 6.0;

	private @Nullable Vec3 lastPosition;
	private @Nullable ResourceKey<Level> lastDimension;
	private int lastSampleTick;
	private Vec3 movement = Vec3.ZERO;
	private Vec3 smoothedMovement = Vec3.ZERO;
	private Vec3 inheritedMovement = Vec3.ZERO;
	private boolean hasMoved;
	private @Nullable Vec3 finalApproachOffset;

	public boolean sample(Vec3 position, ResourceKey<Level> dimension, int tick) {
		boolean changedDimension = lastDimension != null && !lastDimension.equals(dimension);
		boolean continuous = lastPosition != null && tick == lastSampleTick + 1 && !changedDimension;
		Vec3 displacement = continuous ? position.subtract(lastPosition) : Vec3.ZERO;
		boolean reposition = changedDimension || displacement.lengthSqr() > REPOSITION_DISTANCE_SQR;
		if (displacement.lengthSqr() > 1.0E-6 || changedDimension) hasMoved = true;
		movement = reposition ? Vec3.ZERO : displacement;
		smoothedMovement = continuous && !reposition
			? smoothedMovement.lerp(movement, MOVEMENT_SMOOTHING) : Vec3.ZERO;
		lastPosition = position;
		lastDimension = dimension;
		lastSampleTick = tick;
		return reposition;
	}

	public Vec3 movement() {
		return movement;
	}

	public Vec3 smoothedMovement() {
		return smoothedMovement;
	}

	public boolean hasMoved() {
		return hasMoved;
	}

	public Vec3 relativeMotion(Vec3 motion) {
		return motion.subtract(inheritedMovement);
	}

	public Vec3 inheritMovement(Vec3 relativeMotion) {
		inheritedMovement = movement;
		return relativeMotion.add(inheritedMovement);
	}

	public boolean usesDeliveryLimit(boolean playerTarget) {
		return playerTarget || hasMoved || isFinalApproach();
	}

	public boolean shouldApproach(int elapsedTicks, boolean playerTarget) {
		return usesDeliveryLimit(playerTarget) && elapsedTicks >= FINAL_APPROACH_START_TICKS;
	}

	public boolean isFinalApproach() {
		return finalApproachOffset != null;
	}

	public void beginFinalApproach(Vec3 position, Vec3 target) {
		if (isFinalApproach()) return;
		Vec3 offset = position.subtract(target);
		finalApproachOffset = offset.lengthSqr() > FINAL_APPROACH_DISTANCE * FINAL_APPROACH_DISTANCE
			? offset.normalize().scale(FINAL_APPROACH_DISTANCE) : offset;
	}

	public Vec3 finalApproachPosition(Vec3 target, int elapsedTicks) {
		double remaining = Math.clamp((double) (DELIVERY_LIMIT_TICKS - elapsedTicks) / FINAL_APPROACH_TICKS, 0, 1);
		return target.add(finalApproachOffset != null ? finalApproachOffset.scale(remaining) : Vec3.ZERO);
	}

	public void clearMotion() {
		lastPosition = null;
		lastDimension = null;
		movement = Vec3.ZERO;
		smoothedMovement = Vec3.ZERO;
		inheritedMovement = Vec3.ZERO;
	}

	public void reset() {
		clearMotion();
		hasMoved = false;
		finalApproachOffset = null;
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("HasMoved", hasMoved);
		tag.put("InheritedMovement", saveVector(inheritedMovement));
		if (finalApproachOffset != null) tag.put("FinalApproachOffset", saveVector(finalApproachOffset));
		return tag;
	}

	public void load(CompoundTag tag) {
		reset();
		hasMoved = tag.getBoolean("HasMoved");
		inheritedMovement = loadVector(tag.getCompound("InheritedMovement"));
		if (tag.contains("FinalApproachOffset")) finalApproachOffset = loadVector(tag.getCompound("FinalApproachOffset"));
	}

	private static CompoundTag saveVector(Vec3 vector) {
		CompoundTag tag = new CompoundTag();
		tag.putDouble("X", vector.x);
		tag.putDouble("Y", vector.y);
		tag.putDouble("Z", vector.z);
		return tag;
	}

	private static Vec3 loadVector(CompoundTag tag) {
		return new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
	}
}
