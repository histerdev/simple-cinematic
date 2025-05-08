package dev.backd00r.simplecinematic.handler;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.render.Camera;

/**
 * Clase que maneja la personalización de la cámara en tercera persona.
 */
public class ThirdPersonCameraHandler {

    // Offset adicional para la cámara en tercera persona
    private static Vec3d thirdPersonOffset = new Vec3d(0, 0, 0);

    // Rotación adicional para la cámara en tercera persona (yaw, pitch)
    private static Vec2f cameraRotation = new Vec2f(0, 0);

    // Booleano para verificar si el offset personalizado está activo
    private static boolean customThirdPersonEnabled = false;

    // Booleano para verificar si la rotación personalizada está activa
    private static boolean customRotationEnabled = false;

    /**
     * Establece un offset personalizado para la cámara en tercera persona
     * @param x Offset en el eje X
     * @param y Offset en el eje Y
     * @param z Offset en el eje Z
     */
    public static void setThirdPersonOffset(double x, double y, double z) {
        thirdPersonOffset = new Vec3d(x, y, z);
        customThirdPersonEnabled = true;
    }

    /**
     * Establece una rotación personalizada para la cámara en tercera persona
     * @param yaw Ángulo horizontal (grados)
     * @param pitch Ángulo vertical (grados)
     */
    public static void setThirdPersonRotation(float yaw, float pitch) {
        cameraRotation = new Vec2f(yaw, pitch);
        customRotationEnabled = true;
    }

    /**
     * Resetea el offset de la cámara en tercera persona a sus valores predeterminados
     */
    public static void resetThirdPersonOffset() {
        thirdPersonOffset = new Vec3d(0, 0, 0);
        customThirdPersonEnabled = false;
    }

    /**
     * Resetea la rotación de la cámara en tercera persona a sus valores predeterminados
     */
    public static void resetThirdPersonRotation() {
        cameraRotation = new Vec2f(0, 0);
        customRotationEnabled = false;
    }

    /**
     * Resetea todos los ajustes personalizados de la cámara
     */
    public static void resetAll() {
        resetThirdPersonOffset();
        resetThirdPersonRotation();
    }

    /**
     * Comprueba si el offset personalizado de la cámara está habilitado
     * @return true si está habilitado, false en caso contrario
     */
    public static boolean isCustomThirdPersonEnabled() {
        return customThirdPersonEnabled;
    }

    /**
     * Comprueba si la rotación personalizada de la cámara está habilitada
     * @return true si está habilitada, false en caso contrario
     */
    public static boolean isCustomRotationEnabled() {
        return customRotationEnabled;
    }

    /**
     * Obtiene el offset actual de la cámara en tercera persona
     * @return El vector de offset
     */
    public static Vec3d getThirdPersonOffset() {
        return thirdPersonOffset;
    }

    /**
     * Obtiene la rotación actual de la cámara en tercera persona
     * @return El vector de rotación (yaw, pitch)
     */
    public static Vec2f getCameraRotation() {
        return cameraRotation;
    }

    /**
     * Calcula el offset correcto para la cámara en tercera persona basado
     * en la dirección actual del jugador y la cámara
     *
     * @param original La posición original calculada por el juego
     * @param player El jugador en foco
     * @param yaw La rotación horizontal (yaw) de la cámara
     * @param pitch La rotación vertical (pitch) de la cámara
     * @return La posición modificada con el offset
     */
    public static Vec3d calculateThirdPersonOffset(Vec3d original, Entity player, float yaw, float pitch) {
        if (!customThirdPersonEnabled) {
            return original;
        }

        // Convertimos el yaw a radianes
        float yawRadians = (float) Math.toRadians(yaw);
        float pitchRadians = (float) Math.toRadians(pitch);

        // Matriz de rotación para aplicar correctamente los offsets
        // Primero creamos un vector de dirección basado en los offsets

        // Aplicamos el offset lateral (X) correctamente
        double offsetX = thirdPersonOffset.x * MathHelper.cos(yawRadians) -
                thirdPersonOffset.z * MathHelper.sin(yawRadians);

        // El offset vertical (Y) debe tener en cuenta el pitch
        double offsetY = thirdPersonOffset.y -
                thirdPersonOffset.z * MathHelper.sin(pitchRadians);

        // Aplicamos el offset de profundidad (Z) correctamente
        double offsetZ = thirdPersonOffset.z * MathHelper.cos(pitchRadians) * MathHelper.cos(yawRadians) +
                thirdPersonOffset.x * MathHelper.sin(yawRadians);

        // Aplicamos el offset a la posición original
        return original.add(offsetX, offsetY, offsetZ);
    }

    /**
     * Calcula la rotación modificada de la cámara
     *
     * @param originalYaw El yaw original de la cámara
     * @param originalPitch El pitch original de la cámara
     * @return Un array con [yaw, pitch] modificados
     */
    public static float[] calculateModifiedRotation(float originalYaw, float originalPitch) {
        if (!customRotationEnabled) {
            return new float[]{originalYaw, originalPitch};
        }

        // Aplicamos los offsets de rotación
        float modifiedYaw = originalYaw + (float)cameraRotation.x;
        float modifiedPitch = originalPitch + (float)cameraRotation.y;

        // Limitamos el pitch entre -90 y 90 grados
        modifiedPitch = MathHelper.clamp(modifiedPitch, -90.0F, 90.0F);

        return new float[]{modifiedYaw, modifiedPitch};
    }
}