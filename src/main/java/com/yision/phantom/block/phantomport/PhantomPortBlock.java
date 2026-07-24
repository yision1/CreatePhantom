package com.yision.phantom.block.phantomport;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import com.yision.phantom.registry.AllBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

public class PhantomPortBlock extends HorizontalDirectionalBlock implements IWrenchable, IBE<PhantomPortBlockEntity> {
	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
	public static final BooleanProperty OPEN = BooleanProperty.create("open");
	public static final BooleanProperty ACTIVITY = BooleanProperty.create("activity");
	public static final MapCodec<PhantomPortBlock> CODEC = simpleCodec(PhantomPortBlock::new);
	private static final VoxelShape SHAPE_NORTH = Shapes.or(
		box(0, 0, 0, 16, 2, 16),
		box(1, 4, 5, 15, 13, 14),
		box(2, 2, 2, 14, 13, 5),
		box(2, 2, 14, 14, 4, 16),
		box(0, 10, 0, 2, 14, 16),
		box(1, 13, 1, 15, 16, 15),
		box(14, 2, 0, 16, 4, 16),
		box(0, 2, 0, 2, 4, 16),
		box(14, 10, 0, 16, 14, 16)
	);
	private static final VoxelShape SHAPE_EAST = rotateY(SHAPE_NORTH);
	private static final VoxelShape SHAPE_SOUTH = rotateY(SHAPE_EAST);
	private static final VoxelShape SHAPE_WEST = rotateY(SHAPE_SOUTH);

	public PhantomPortBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH)
			.setValue(OPEN, false)
			.setValue(ACTIVITY, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(FACING, OPEN, ACTIVITY);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction facing = context.getHorizontalDirection().getOpposite();
		Player player = context.getPlayer();
		if (player != null && player.isShiftKeyDown()) {
			facing = facing.getOpposite();
		}
		return defaultBlockState().setValue(FACING, facing)
			.setValue(OPEN, false);
	}

	@Override
	protected @NotNull MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected @NotNull ItemInteractionResult useItemOn(@NotNull ItemStack stack, @NotNull BlockState state,
		@NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
		@NotNull BlockHitResult hitResult) {
		return onBlockEntityUseItemOn(level, pos, blockEntity -> blockEntity.use(player));
	}

	@Override
	public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos,
		@NotNull CollisionContext context) {
		return getShapeForFacing(state.getValue(FACING));
	}

	@Override
	public @NotNull VoxelShape getCollisionShape(@NotNull BlockState state, @NotNull BlockGetter level,
		@NotNull BlockPos pos, @NotNull CollisionContext context) {
		return getShapeForFacing(state.getValue(FACING));
	}

	@Override
	public boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
		return getBlockEntityOptional(level, pos)
			.map(PhantomPortBlockEntity::getComparatorOutput)
			.orElse(0);
	}

	private static VoxelShape getShapeForFacing(Direction facing) {
		return switch (facing) {
			case EAST -> SHAPE_EAST;
			case SOUTH -> SHAPE_SOUTH;
			case WEST -> SHAPE_WEST;
			default -> SHAPE_NORTH;
		};
	}

	private static VoxelShape rotateY(VoxelShape shape) {
		VoxelShape[] rotated = new VoxelShape[] { Shapes.empty() };
		shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> rotated[0] = Shapes.or(rotated[0],
			box(16 - maxZ * 16, minY * 16, minX * 16, 16 - minZ * 16, maxY * 16, maxX * 16)));
		return rotated[0];
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock())) {
			IBE.onRemove(state, level, pos, newState);
			return;
		}
		super.onRemove(state, level, pos, newState, movedByPiston);
	}

	@Override
	public Class<PhantomPortBlockEntity> getBlockEntityClass() {
		return PhantomPortBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends PhantomPortBlockEntity> getBlockEntityType() {
		return AllBlockEntityTypes.PHANTOMPORT.get();
	}
}
