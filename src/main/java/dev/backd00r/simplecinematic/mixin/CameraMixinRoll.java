package dev.backd00r.simplecinematic.mixin;

import dev.backd00r.simplecinematic.accessor.CameraMixinAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixinRoll implements CameraMixinAccessor {
    @Shadow public abstract Vec3d getPos();
    @Shadow public abstract float getPitch();
    @Shadow public abstract float getYaw();
    @Shadow protected abstract void setPos(Vec3d pos);
    @Shadow private float pitch;
    @Shadow private float yaw;
    @Shadow @Final private Quaternionf rotation;

    @Unique
    private float roll = 0.0f; // Campo para el roll personalizado

    /**
     * Intercepta la actualización de la cámara para aplicar el roll personalizado.
     */
    @Inject(method = "update", at = @At("TAIL"))
    private void applyRoll(
            BlockView area,
            Entity focusedEntity,
            boolean thirdPerson,
            boolean inverseView,
            float tickDelta,
            CallbackInfo ci
    ) {
        if (!thirdPerson && focusedEntity instanceof ClientPlayerEntity player) {
            Vec3d playerPos = new Vec3d(
                    MathHelper.lerp(tickDelta, focusedEntity.prevX, focusedEntity.getX()),
                    MathHelper.lerp(tickDelta, focusedEntity.prevY, focusedEntity.getY()),
                    MathHelper.lerp(tickDelta, focusedEntity.prevZ, focusedEntity.getZ())
            );
            Vec3d cameraRelativePos = this.getPos().subtract(playerPos);

            // Aplica la posición relativa y la rotación con roll
            this.setPos(playerPos.add(cameraRelativePos));
            this.updateRotation(this.getPitch(), this.getYaw(), this.roll);
            MinecraftClient.getInstance().worldRenderer.scheduleTerrainUpdate();
        }
    }

    /**
     * Actualiza la rotación de la cámara, incluyendo el roll.
     * @param pitch Ángulo de pitch en radianes.
     * @param yaw Ángulo de yaw en radianes.
     * @param roll Ángulo de roll en radianes.
     */
    @Unique
    private void updateRotation(float pitch, float yaw, float roll) {
        this.pitch = pitch * MathHelper.DEGREES_PER_RADIAN;
        this.yaw = yaw * MathHelper.DEGREES_PER_RADIAN;
        this.rotation.rotationYXZ(-yaw, -pitch, -roll);

        // Calcula los planos de rotación directamente (no dependen de HORIZONTAL, VERTICAL, DIAGONAL)
        Vector3f horizontal = new Vector3f(1.0f, 0.0f, 0.0f);
        Vector3f vertical = new Vector3f(0.0f, 1.0f, 0.0f);
        Vector3f diagonal = new Vector3f(0.0f, 0.0f, 1.0f);

        horizontal.rotate(this.rotation);
        vertical.rotate(this.rotation);
        diagonal.rotate(this.rotation);
    }

    /**
     * Establece el valor del roll.
     * @param roll Ángulo de roll en grados.
     */


    @Override
    public void setRoll(float roll) {
        this.roll = roll * MathHelper.RADIANS_PER_DEGREE; // Convierte a radianes
    }

    @Override
    public float getRoll() {
        return this.roll * MathHelper.DEGREES_PER_RADIAN; // Convierte a grados
    }
}