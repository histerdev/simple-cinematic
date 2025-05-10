package dev.backd00r.simplecinematic.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CameraAccessorMixin {
    @Invoker("setPos")
    void invokeSetPos(Vec3d pos);

    @Invoker("setRotation")
    void invokeSetRotation(float yaw, float pitch);

    @Accessor("pos")
    void setPos(Vec3d pos);

    @Accessor("yaw")
    void setYaw(float yaw);

    @Accessor("pitch")
    void setPitch(float pitch);

}