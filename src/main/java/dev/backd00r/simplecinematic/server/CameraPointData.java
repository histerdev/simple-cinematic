package dev.backd00r.simplecinematic.server;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class CameraPointData {
    public final BlockPos pos;
    public final int channel;
    public final int position;
    public final float yaw, pitch, roll;
    public final float shake;

    public final double duration, stayDuration;
    public final Direction blockDirection;
    public final boolean rotateToNext;

    public CameraPointData(BlockPos pos, int channel, int position,
                           float yaw, float pitch, float roll, float shake,
                           double duration, double stayDuration,
                           Direction blockDirection, boolean rotateToNext) {
        this.pos = pos;
        this.channel = channel;
        this.position = position;
        this.yaw = yaw;
        this.pitch = pitch;
        this.roll  = roll;
        this.shake = shake;

        this.duration = duration;
        this.stayDuration = stayDuration;
        this.blockDirection = blockDirection;
        this.rotateToNext = rotateToNext;
    }
}