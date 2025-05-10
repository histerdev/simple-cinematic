package dev.backd00r.simplecinematic.mixin;

import dev.backd00r.simplecinematic.accessor.RollAccessor;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererRollMixin {
    @Inject(method = "renderWorld", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/util/math/MatrixStack;multiply(Lorg/joml/Quaternionf;)V",
            ordinal = 1, // Justo después de pitch/yaw
            shift = At.Shift.AFTER
    ))
    private void applyRollAfterYawPitch(float tickDelta, long limitTime, MatrixStack matrices, CallbackInfo ci) {
        Camera camera = ((GameRenderer)(Object)this).getCamera();
        if (camera instanceof RollAccessor rollAccessor) {
            float roll = rollAccessor.getRoll();
            if (Math.abs(roll) > 0.01f) {
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll));
            }
        }
    }
}