package com.yision.phantom.block.phantomport;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.content.logistics.packagePort.PackagePortBlockEntity;
import com.yision.phantom.compat.sable.SableCompat;
import com.yision.phantom.logistics.courier.AirCourierDeliveryService;
import com.yision.phantom.logistics.courier.flight.AirCourierFlightProfile;
import com.yision.phantom.logistics.courier.flight.AirCourierFlightTargets;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

public class PhantomPortBlockEntity extends PackagePortBlockEntity {
	private final PhantomPortInventory portInventory;
	private final PhantomPortDispatchAccess dispatchAccess;
	private final PhantomPortAutomation automation;
	private final PhantomPortReturnQueue returnQueue;
	private final Set<UUID> landingCouriers = new HashSet<>();
	private @Nullable UUID portId;
	private @Nullable PhantomPortReference stationaryReference;

	public PhantomPortBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		portInventory = new PhantomPortInventory(this);
		PhantomPortBeltAccess beltAccess = new PhantomPortBeltAccess(this);
		dispatchAccess = new PhantomPortDispatchAccess(this, portInventory, beltAccess);
		automation = new PhantomPortAutomation(this, portInventory, beltAccess);
		returnQueue = new PhantomPortReturnQueue(this, portInventory, beltAccess);
		inventory.whenContentsChanged(this::onPackageContentsChanged);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		updateLandingOpenState();
		returnQueue.tick();
		if (portId != null && stationaryReference == null)
			courierReference(serverLevel);
		if (serverLevel.getGameTime() % 20 == 0) {
			PhantomPortTargetRegistry.update(serverLevel, worldPosition, addressFilter);
		}
	}

	@Override
	public void onLoad() {
		super.onLoad();
		if (level instanceof ServerLevel serverLevel)
			PhantomPortTargetRegistry.update(serverLevel, worldPosition, addressFilter);
	}

	public PhantomPortReference courierReference(ServerLevel serverLevel) {
		GlobalPos location = GlobalPos.of(serverLevel.dimension(), worldPosition);
		if (stationaryReference != null && stationaryReference.location().equals(location))
			return stationaryReference;
		stationaryReference = null;
		PhantomPortLocations locations = PhantomPortLocations.get(serverLevel.getServer());
		if (portId == null || isDuplicatePort(serverLevel, location, locations.find(portId))) {
			portId = UUID.randomUUID();
			setChanged();
		}
		Optional<UUID> subLevelId = SableCompat.subLevelId(serverLevel, worldPosition);
		PhantomPortReference reference = new PhantomPortReference(portId, location, subLevelId,
			AirCourierFlightTargets.landingTarget(AirCourierFlightProfile.DEFAULT, serverLevel, worldPosition));
		if (subLevelId.isEmpty()) {
			if (SableCompat.isSubLevelPlot(serverLevel, worldPosition))
				return reference;
			stationaryReference = reference;
		}
		locations.update(reference);
		return reference;
	}

	private boolean isDuplicatePort(ServerLevel level, GlobalPos location, @Nullable PhantomPortReference previous) {
		if (previous == null || previous.location().equals(location))
			return false;
		PhantomPortBlockEntity existing = AirCourierDeliveryService.resolveTargetPhantomPort(
			level.getServer().getLevel(previous.dimension()), previous.pos());
		return existing != null && existing.matchesCourierPort(previous.id());
	}

	public boolean matchesCourierPort(UUID id) {
		return id.equals(portId);
	}

	@Override
	public void lazyTick() {
		super.lazyTick();
		if (level == null || level.isClientSide()) {
			return;
		}
		automation.tick();
	}

	public @Nullable IItemHandler getItemHandler(@Nullable Direction side) {
		return dispatchAccess.getItemHandler(side);
	}

	public ItemStackHandler getCarrierInventory() {
		return portInventory.carrierInventory();
	}

	@Override
	public void clearContent() {
		super.clearContent();
		portInventory.clearContent();
		returnQueue.clear();
	}

	@Override
	public int getComparatorOutput() {
		return ItemHandlerHelper.calcRedstoneFromInventory(portInventory.combinedHandler());
	}

	private void onPackageContentsChanged(int slot) {
		dispatchAccess.clearPendingDispatches();
		signalObserverActivity();
	}

	void onCarrierContentsChanged() {
		dispatchAccess.clearPendingDispatches();
		notifyUpdate();
		signalObserverActivity();
	}

	private void signalObserverActivity() {
		if (level == null || level.isClientSide()) {
			return;
		}
		BlockState state = level.getBlockState(worldPosition);
		if (!(state.getBlock() instanceof PhantomPortBlock) || !state.hasProperty(PhantomPortBlock.ACTIVITY)) {
			return;
		}
		level.setBlockAndUpdate(worldPosition, state.cycle(PhantomPortBlock.ACTIVITY));
	}

	public boolean canReceiveCourier(ItemStack box) {
		return portInventory.canReceiveCourier(box);
	}

	void cancelPendingReturns() {
		if (level instanceof ServerLevel && returnQueue.clear())
			setChanged();
	}

	public boolean receiveCarrier() {
		return portInventory.receiveCarrier();
	}

	public boolean receivePackageAndScheduleCarrierReturnToPlayer(ItemStack box, UUID playerId) {
		return returnQueue.receivePackageAndScheduleCarrierReturnToPlayer(box, playerId);
	}

	public boolean receivePackageAndHandleCarrier(ItemStack box,
		@Nullable ResourceKey<Level> returnDimension, @Nullable BlockPos returnPos,
		@Nullable PhantomPortReference returnPort) {
		return returnQueue.receivePackageAndHandleCarrier(box, returnDimension, returnPos, returnPort);
	}

	@Override
	protected void onOpenChange(boolean open) {
		if (level == null) {
			return;
		}
		level.playSound(null, worldPosition, open ? SoundEvents.BARREL_OPEN : SoundEvents.BARREL_CLOSE,
			SoundSource.BLOCKS);
	}

	public void setCourierLandingOpen(UUID courierId, boolean open) {
		boolean changed = open ? landingCouriers.add(courierId) : landingCouriers.remove(courierId);
		if (changed) {
			updateLandingOpenState();
		}
	}

	private void updateLandingOpenState() {
		if (level == null) {
			return;
		}
		boolean open = !landingCouriers.isEmpty();
		BlockState state = getBlockState();
		if (state.getValue(PhantomPortBlock.OPEN) != open) {
			level.setBlockAndUpdate(worldPosition, state.setValue(PhantomPortBlock.OPEN, open));
		}
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
		return PhantomPortMenu.create(containerId, playerInventory, this);
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		portInventory.write(tag, registries);
		if (!clientPacket) {
			if (portId != null)
				tag.putUUID("CourierPortId", portId);
			returnQueue.write(tag);
		}
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		portInventory.read(tag, registries);
		if (!clientPacket) {
			portId = tag.hasUUID("CourierPortId") ? tag.getUUID("CourierPortId") : null;
			stationaryReference = null;
			returnQueue.read(tag);
		}
	}

	@Override
	public void destroy() {
		if (portId != null && level instanceof ServerLevel serverLevel) {
			PhantomPortLocations.get(serverLevel.getServer()).remove(portId,
				GlobalPos.of(serverLevel.dimension(), worldPosition));
		}
		returnQueue.clear();
		portInventory.dropAllCarriers();
		super.destroy();
	}
}
