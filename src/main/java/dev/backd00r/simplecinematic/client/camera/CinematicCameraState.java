package dev.backd00r.simplecinematic.client.camera;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Clase para manejar el estado de la cámara cinemática.
 * Incluye la posición virtual de la cámara y si el modo cinemático está activo.
 */
public class CinematicCameraState {
    private static boolean cinematicActive = false;
    private static Vec3d cameraPos = null;

    public static boolean isCinematicActive() {
        return cinematicActive;
    }
    public static void setCinematicActive(boolean active) {
        cinematicActive = active;
    }
    public static void setCameraPos(Vec3d pos) {
        cameraPos = pos;
    }
    public static Vec3d getCameraPos() {
        return cameraPos;
    }
    public static BlockPos getCameraBlockPos() {
        return cameraPos != null ? BlockPos.ofFloored(cameraPos) : null;
    }
}