package dev.backd00r.simplecinematic.block;

import dev.backd00r.simplecinematic.Simplecinematic;
import dev.backd00r.simplecinematic.block.entity.CameraPointBlockEntity;
import dev.backd00r.simplecinematic.networking.ServerNetworking;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.MinecraftServer; // Importar MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class CameraPointBlock extends BlockWithEntity implements BlockEntityProvider {
    public static final DirectionProperty FACING = Properties.FACING;
    private static final VoxelShape SHAPE = Block.createCuboidShape(0, 0, 0, 16, 16, 16);

    public CameraPointBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        PlayerEntity player = ctx.getPlayer();
        Direction direction = Direction.NORTH;

        if (player != null) {
            float yaw = player.getYaw();
            if (yaw < 0) yaw += 360;

            if (yaw >= 315 || yaw < 45) {
                direction = Direction.SOUTH;
            } else if (yaw >= 45 && yaw < 135) {
                direction = Direction.WEST;
            } else if (yaw >= 135 && yaw < 225) {
                direction = Direction.NORTH;
            } else {
                direction = Direction.EAST;
            }
            direction = direction.getOpposite();
        }
        return getDefaultState().with(FACING, direction);
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        if (context instanceof EntityShapeContext entityContext && entityContext.getEntity() instanceof PlayerEntity player) {
            if (player.getMainHandStack().isOf(this.asItem()) ||
                    player.getOffHandStack().isOf(this.asItem()) ||
                    player.hasPermissionLevel(2)) { // Allow OPs to always see outline
                return SHAPE;
            }
        } else if (context == ShapeContext.absent()) { // Show outline if context is absent (e.g., some debug tools)
            return SHAPE;
        }
        return VoxelShapes.empty();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return VoxelShapes.empty();
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.INVISIBLE;
    }

    @Override
    public float getAmbientOcclusionLightLevel(BlockState state, BlockView world, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public boolean isTransparent(BlockState state, BlockView world, BlockPos pos) {
        return true;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new CameraPointBlockEntity(pos, state);
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            if (!world.isClient) {
                // BlockEntity markRemoved handles unregistration now
            }
            super.onStateReplaced(state, world, pos, newState, moved);
        } else {
            super.onStateReplaced(state, world, pos, newState, moved);
        }
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!world.isClient) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof CameraPointBlockEntity cameraPointBlockEntity) {
                player.openHandledScreen(cameraPointBlockEntity);
                return ActionResult.CONSUME;
            } else {
                Simplecinematic.LOGGER.error("Incorrect BlockEntity at {} on server during onUse!", pos);
                return ActionResult.FAIL;
            }
        }
        return ActionResult.SUCCESS;
    }
}