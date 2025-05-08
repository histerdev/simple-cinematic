package dev.backd00r.simplecinematic.mixin;

import dev.backd00r.simplecinematic.handler.ThirdPersonCameraHandler;
import dev.backd00r.simplecinematic.manager.CameraManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow private Entity focusedEntity;
    @Shadow private float pitch;
    @Shadow private float yaw;
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow protected abstract void setPos(double x, double y, double z);
    @Shadow private boolean thirdPerson;

    // Variable para almacenar la posición original antes de aplicar nuestros cambios
    private Vec3d originalPosition;

    // Variables para almacenar los ángulos originales de la cámara
    private float originalYaw;
    private float originalPitch;

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void updateCamera(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        // Si la cámara está siendo controlada por el CameraManager, usa esa lógica
        if (CameraManager.isCameraMoving()) {
            Camera camera = (Camera) (Object) this;
            Vec3d customPos = CameraManager.getCameraPosition();
            float customYaw = CameraManager.getYaw();
            float customPitch = CameraManager.getPitch();
            if (camera instanceof CameraAccessorMixin accessor) {
                accessor.invokeSetPos(customPos);
                accessor.invokeSetRotation(customYaw, customPitch);
                ci.cancel();

            }
        }
    }

    // Capturamos la posición y rotación calculadas por el juego justo antes de que se posicione la cámara
    @Inject(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/Camera;setPos(DDD)V",
            shift = At.Shift.BEFORE))
    private void captureOriginalPosition(BlockView area, Entity entity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (thirdPerson && (ThirdPersonCameraHandler.isCustomThirdPersonEnabled() || ThirdPersonCameraHandler.isCustomRotationEnabled()) && !CameraManager.isCameraMoving()) {
            Camera camera = (Camera) (Object) this;
            // Guardamos la posición original
            this.originalPosition = new Vec3d(
                    entity.getX() - Math.sin(Math.toRadians(this.yaw)) * 4.0,
                    entity.getY() + entity.getStandingEyeHeight() + 1.0,
                    entity.getZ() + Math.cos(Math.toRadians(this.yaw)) * 4.0
            );

            // Guardamos la rotación original
            this.originalYaw = this.yaw;
            this.originalPitch = this.pitch;
        }
    }

    // Aplicamos nuestro offset personalizado y la rotación después de que el juego haya posicionado la cámara
    @Inject(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/Camera;setPos(DDD)V",
            shift = At.Shift.AFTER))
    private void updateThirdPersonCamera(BlockView area, Entity entity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();

        // Solo modificamos si estamos en tercera persona y alguna de las personalizaciones está habilitada
        if (thirdPerson && (ThirdPersonCameraHandler.isCustomThirdPersonEnabled() || ThirdPersonCameraHandler.isCustomRotationEnabled()) && !CameraManager.isCameraMoving()) {
            Camera camera = (Camera) (Object) this;

            // Aplicamos el offset de posición si está habilitado
            if (ThirdPersonCameraHandler.isCustomThirdPersonEnabled()) {
                // Obtenemos la posición actual de la cámara
                Vec3d currentPos = new Vec3d(camera.getPos().x, camera.getPos().y, camera.getPos().z);

                // Calculamos la nueva posición con nuestro offset aplicado correctamente
                Vec3d newPos = ThirdPersonCameraHandler.calculateThirdPersonOffset(currentPos, entity, this.yaw, this.pitch);

                // Actualizamos la posición de la cámara
                this.setPos(newPos.x, newPos.y, newPos.z);
            }

            // Aplicamos la rotación personalizada si está habilitada
            if (ThirdPersonCameraHandler.isCustomRotationEnabled()) {
                // Calculamos la rotación modificada
                float[] modifiedRotation = ThirdPersonCameraHandler.calculateModifiedRotation(this.originalYaw, this.originalPitch);

                // Aplicamos la nueva rotación
                this.setRotation(modifiedRotation[0], modifiedRotation[1]);
            }
        }
    }
}