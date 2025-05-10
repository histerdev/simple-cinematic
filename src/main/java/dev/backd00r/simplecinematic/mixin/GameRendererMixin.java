package dev.backd00r.simplecinematic.mixin;

import dev.backd00r.simplecinematic.manager.CameraManager;
import dev.backd00r.simplecinematic.manager.CameraPathManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GameRenderer.class, priority = 1100)
public class GameRendererMixin {

    @Shadow @Final private Camera camera;

    @Inject(
            method = "renderWorld",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/Camera;update(Lnet/minecraft/world/BlockView;Lnet/minecraft/entity/Entity;ZZF)V",
                    shift = At.Shift.AFTER
            )
    )
    private void onRenderWorld(float tickDelta, long limitTime, MatrixStack matrices, CallbackInfo ci) {
        try {
            if (camera != null) {
                CameraManager.updateCameraPosition(camera, tickDelta);
            }
            CameraPathManager.updatePathPlayback();
        } catch (Exception e) {
            System.out.println("Caught exception in CarrozaTest's onRenderWorld: " + e.getMessage());
            e.printStackTrace();
        }
    }

}