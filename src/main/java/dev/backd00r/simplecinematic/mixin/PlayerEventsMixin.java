package dev.backd00r.simplecinematic.mixin;

import dev.backd00r.simplecinematic.manager.CameraManager;
import dev.backd00r.simplecinematic.manager.CameraPathManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEventsMixin {

    @Inject(
            method = "onDeath",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onPlayerDeath(CallbackInfo info) {
        // Asegurarse de que el código se ejecuta solo en el lado lógico del cliente
        // y que el jugador que muere es el jugador del cliente actual.
        // El objeto 'this' en un Mixin de PlayerEntity ES la instancia del jugador.
        PlayerEntity thisPlayer = (PlayerEntity)(Object)this;
        MinecraftClient client = MinecraftClient.getInstance();

        if (client != null && client.player != null && thisPlayer == client.player) {
            // Solo actuar si el jugador que muere es el jugador del cliente

            // Detener cualquier cinemática y resetear el estado de los managers
            // Esto también se encarga de restaurar la perspectiva original si es necesario.
            CameraPathManager.clearAllPaths();

            // --- Código anterior eliminado ---
            // No necesitas establecer la perspectiva ni la posición/rotación manualmente aquí.
            // clearAllPaths() y resetCamera() devuelven el control al estado normal del juego.
            /*
            if (CameraManager.originalPerspective != null) {
                 client.options.setPerspective(CameraManager.originalPerspective);
                 CameraManager.originalPerspective = null; // Limpiar después de restaurar
            }
            // Estas líneas daban error y ya no son necesarias:
            // CameraManager.cameraPosition = new Vec3d(playerEntity.getX(), playerEntity.getY() + playerEntity.getStandingEyeHeight(), playerEntity.getZ());
            // CameraManager.yaw = playerEntity.getYaw();
            // CameraManager.pitch = playerEntity.getPitch();
            // CameraManager.moving = false;
            */
        }
    }
}