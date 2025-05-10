package dev.backd00r.simplecinematic.block.entity;

import dev.backd00r.simplecinematic.block.CameraPointBlock;
import dev.backd00r.simplecinematic.manager.CameraPathManager;
import dev.backd00r.simplecinematic.screen.CameraPointScreenHandler;
import dev.backd00r.simplecinematic.server.CameraPointData;
import dev.backd00r.simplecinematic.server.CameraPointPersistentRegistry;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CameraPointBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(CameraPointBlockEntity.class);

    private int channel = 1;
    private int position = 1;
    private float yaw = 0.0f;
    private float pitch = 0.0f;
    private float roll = 0.0f;
    private float shake = 0.0f;

    private double duration = 2.0;
    private double stayDuration = 0.0;
    private boolean useBlockFacing = true;
    private boolean rotateToNext = true;

    public CameraPointBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CAMERA_POINT_BLOCK_ENTITY, pos, state);
    }

    public int getChannel() { return channel; }
    public int getPosition() { return position; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public float getRoll() { return roll; }

    public float getShake() { return shake; }

    public double getDuration() { return duration; }
    public double getStayDuration() { return stayDuration; }
    public boolean isUseBlockFacing() { return useBlockFacing; }
    public boolean shouldRotateToNext() { return rotateToNext; }

    public Direction getBlockDirection() {
        BlockState state = this.getCachedState();
        if (state.getBlock() instanceof CameraPointBlock) {
            return state.get(CameraPointBlock.FACING);
        }
        return Direction.NORTH;
    }

    private void updatePersistentRegistry() {
        if (getWorld() != null && !getWorld().isClient) {
            CameraPointData data = new CameraPointData(
                    getPos(),
                    getChannel(),
                    getPosition(),
                    getYaw(),
                    getPitch(),
                    getRoll(),
                    getShake(),
                    getDuration(),
                    getStayDuration(),
                    getBlockDirection(),
                    shouldRotateToNext()
            );
            CameraPointPersistentRegistry.registerOrUpdate(data, getWorld().getRegistryKey());
        }
    }

    @Override
    public void setWorld(World world) {
        super.setWorld(world);
        if (world != null && !world.isClient) {
            updatePersistentRegistry(); // <-- Esto debe llamar CameraPointPersistentRegistry.registerOrUpdate(...)
        }
    }

    @Override
    public void markRemoved() {

        super.markRemoved();
    }

    public void setChannel(int channel) {
        int oldChannel = this.channel;
        int newChannel = Math.max(1, channel);
        if (oldChannel != newChannel) {
            if (world != null && !world.isClient) {
                CameraPointPersistentRegistry.unregister(world.getRegistryKey(), oldChannel, this.position);
            }
            this.channel = newChannel;
            updatePersistentRegistry();
            markDirtyAndUpdate();
        }
    }

    public void setPosition(int position) {
        int oldPosition = this.position;
        int newPosition = Math.max(1, position);
        if (oldPosition != newPosition) {
            if (world != null && !world.isClient) {
                CameraPointPersistentRegistry.unregister(world.getRegistryKey(), this.channel, oldPosition);
            }
            this.position = newPosition;
            updatePersistentRegistry();
            markDirtyAndUpdate();
        }
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
        updatePersistentRegistry();
        markDirtyAndUpdate();
    }

    public void setPitch(float pitch) {
        this.pitch = Math.max(-90.0f, Math.min(90.0f, pitch));
        updatePersistentRegistry();
        markDirtyAndUpdate();
    }
    public void setRoll(float roll) {
        this.roll = roll;
        updatePersistentRegistry();
        markDirtyAndUpdate();
    }
    public void setShake(float shake) {
        this.shake = Math.max(0.0f, Math.min(100.0f, shake));
        updatePersistentRegistry();
        markDirtyAndUpdate();
    }

    public void setDuration(double duration) {
        this.duration = Math.max(0.0, duration);
        updatePersistentRegistry();
        markDirtyAndUpdate();
    }

    public void setStayDuration(double stayDuration) {
        this.stayDuration = Math.max(0.0, stayDuration);
        updatePersistentRegistry();
        markDirtyAndUpdate();
    }

    public void setUseBlockFacing(boolean useBlockFacing) {
        this.useBlockFacing = useBlockFacing;
        updatePersistentRegistry();
        markDirtyAndUpdate();
    }

    public void setRotateToNext(boolean rotateToNext) {
        this.rotateToNext = rotateToNext;
        updatePersistentRegistry();
        markDirtyAndUpdate();
    }

    private void markDirtyAndUpdate() {
        if (world != null) {
            markDirty();
            if (!world.isClient) {
                world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
            }
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putInt("Channel", this.channel);
        nbt.putInt("Position", this.position);
        nbt.putFloat("Yaw", this.yaw);
        nbt.putFloat("Pitch", this.pitch);
        nbt.putFloat("Roll", this.roll);
        nbt.putFloat("Shake", this.shake);
        nbt.putDouble("Duration", this.duration);
        nbt.putDouble("StayDuration", this.stayDuration);
        nbt.putBoolean("UseBlockFacing", this.useBlockFacing);
        nbt.putBoolean("RotateToNext", this.rotateToNext);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        int oldChannel = this.channel;
        int oldPosition = this.position;
        super.readNbt(nbt);
        this.channel = nbt.getInt("Channel");
        this.position = nbt.getInt("Position");
        this.yaw = nbt.getFloat("Yaw");
        this.pitch = nbt.getFloat("Pitch");
        this.pitch = nbt.getFloat("Roll");
        this.shake = nbt.getFloat("Shake");

        this.duration = nbt.getDouble("Duration");
        this.stayDuration = nbt.getDouble("StayDuration");
        this.useBlockFacing = nbt.getBoolean("UseBlockFacing");
        this.rotateToNext = nbt.contains("RotateToNext", NbtElement.BYTE_TYPE) ? nbt.getBoolean("RotateToNext") : true;

        if (world != null && world.isClient) {
            CameraPathManager.registerOrUpdateCameraPointClient(
                    this.pos, this.channel, this.position, this.yaw, this.pitch, this.roll, this.shake,
                    this.duration, this.stayDuration, getBlockDirection(), this.rotateToNext
            );
        }
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        return createNbt();
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.carrozatest.camera_point");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new CameraPointScreenHandler(syncId, playerInventory, this);
    }

    @Override
    public void writeScreenOpeningData(ServerPlayerEntity serverPlayerEntity, PacketByteBuf packetByteBuf) {
        packetByteBuf.writeBlockPos(this.pos);
        packetByteBuf.writeInt(this.channel);
        packetByteBuf.writeInt(this.position);
        packetByteBuf.writeFloat(this.yaw);
        packetByteBuf.writeFloat(this.pitch);
        packetByteBuf.writeFloat(this.roll);
        packetByteBuf.writeFloat(this.shake);
        packetByteBuf.writeDouble(this.duration);
        packetByteBuf.writeDouble(this.stayDuration);
        packetByteBuf.writeBoolean(this.rotateToNext);
    }
}