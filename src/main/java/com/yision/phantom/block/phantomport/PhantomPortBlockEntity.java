package com.yision.phantom.block.phantomport;

import com.simibubi.create.content.logistics.packagePort.PackagePortBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class PhantomPortBlockEntity extends PackagePortBlockEntity {

	private final PhantomPortInventory portInventory;
	private final PhantomPortBeltAccess beltAccess;
	private final PhantomPortDispatchAccess dispatchAccess;
	private final PhantomPortAutomation automation;
	private final PhantomPortReturnQueue returnQueue;
	private final Set<UUID> landingCouriers = new HashSet<>();

	public PhantomPortBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		portInventory = new PhantomPortInventory(this);
		beltAccess = new PhantomPortBeltAccess(this);
		dispatchAccess = new PhantomPortDispatchAccess(this, portInventory, beltAccess);
		automation = new PhantomPortAutomation(this, portInventory, beltAccess);
		returnQueue = new PhantomPortReturnQueue(this, portInventory, beltAccess);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		updateLandingOpenState();
		returnQueue.tick();
		if (serverLevel.getGameTime() % 20 == 0) {
			PhantomPortTargetRegistry.update(serverLevel, worldPosition, addressFilter);
		}
	}

	@Override
	public void lazyTick() {
		super.lazyTick();
		if (level == null || level.isClientSide()) {
			return;
		}
		automation.tick();
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
	}

	public @Nullable IItemHandler getItemHandler(@Nullable Direction side) {
		return dispatchAccess.getItemHandler(side);
	}

	public ItemStackHandler getCarrierInventory() {
		return portInventory.carrierInventory();
	}

	void markPortContentsChanged() {
		dispatchAccess.clearPendingHudEntries();
		setChanged();
		if (level != null) {
			level.blockEntityChanged(worldPosition);
		}
	}

	public boolean canReceiveCourier(ItemStack box) {
		return portInventory.canReceiveCourier(box);
	}

	public boolean receiveCourier(ItemStack box) {
		return portInventory.receiveCourier(box);
	}

	public boolean canReceivePackage(ItemStack box) {
		return portInventory.canReceivePackage(box);
	}

	public boolean receivePackage(ItemStack box) {
		return portInventory.receivePackage(box);
	}

	public boolean canReceiveCarrier() {
		return portInventory.canReceiveCarrier();
	}

	public boolean receiveCarrier() {
		return portInventory.receiveCarrier();
	}

	public boolean receivePackageAndScheduleCarrierReturnToPlayer(ItemStack box, UUID playerId, int delayTicks) {
		return returnQueue.receivePackageAndScheduleCarrierReturnToPlayer(box, playerId, delayTicks);
	}

	public boolean receivePackageAndScheduleCarrierReturnToPlayer(ItemStack box, UUID playerId) {
		return returnQueue.receivePackageAndScheduleCarrierReturnToPlayer(
			box, playerId, PhantomPortReturnQueue.RETURN_LAUNCH_DELAY_TICKS);
	}

	public boolean tryQueueReturnCarrier(@Nullable ResourceKey<net.minecraft.world.level.Level> returnDimension,
										 @Nullable BlockPos returnPos) {
		return returnQueue.tryQueueReturnCarrier(returnDimension, returnPos);
	}

	public enum CourierReceiveResult {
		REJECTED,
		CARRIER_STORED,
		RETURN_QUEUED,
		CARRIER_DROPPED
	}

	public CourierReceiveResult receivePackageAndHandleCarrier(ItemStack box,
		@Nullable ResourceKey<net.minecraft.world.level.Level> returnDimension, @Nullable BlockPos returnPos) {
		return returnQueue.receivePackageAndHandleCarrier(box, returnDimension, returnPos);
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
	public ItemInteractionResult use(Player player) {
		return super.use(player);
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
		return PhantomPortMenu.create(containerId, playerInventory, this);
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		portInventory.write(tag, registries);
		returnQueue.write(tag);
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		portInventory.read(tag, registries);
		returnQueue.read(tag);
	}

	@Override
	public void destroy() {
		portInventory.dropAllCarriers();
		super.destroy();
	}
}
