package dev.backd00r.simplecinematic.manager;

import dev.backd00r.simplecinematic.accessor.CameraMixinAccessor;
import dev.backd00r.simplecinematic.mixin.CameraAccessorMixin;
import dev.backd00r.simplecinematic.util.EasingType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

@Environment(EnvType.CLIENT)
public class CameraManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(CameraManager.class);

    public static boolean moving = false;

    private static Vec3d startPosition;
    private static Vec3d targetPosition;
    public static Vec3d cameraPosition;

    private static float startYaw;
    private static float startPitch;
    private static float startRoll; // <--- NUEVO

    private static float startShake;
    private static float targetShakeLerp;
    private static float currentShake;
    private static float finalSegmentShake = 0f;
    private static float targetYawLerp;
    private static float targetPitchLerp;
    private static float targetRollLerp; // <--- NUEVO

    private static float currentYaw;
    private static float currentPitch;
    private static float currentRoll; // <--- NUEVO

    private static float finalSegmentYaw = 0f;
    private static float finalSegmentPitch = 0f;
    private static float finalSegmentRoll = 0f; // <--- NUEVO

    private static double totalTicks = 0.0;
    private static double elapsedTicks = 0.0;
    private static double tickProgress = 0.0;
    private static EasingType easingType = EasingType.LINEAR;
    private static boolean finishedMovement = false;
    private static double endDelaySeconds = 0.0;
    private static double endDelayTicks = 0.0;
    private static double endDelayElapsedTicks = 0.0;

    public static Perspective originalPerspective = null;
    private static boolean perspectiveChangeRequested = false;
    private static long perspectiveChangeTime = 0;
    private static final long PERSPECTIVE_CHANGE_DELAY_MS = 150;

    private static boolean followPlayer = false;
    private static boolean isPartOfPath = false;
    private static Vec3d lastTargetPosition = null;
    private static double shakeTime = 0.0;

    // Call this from your Fabric tick event (one per game tick)
    public static void onTick() {
        if (moving && !finishedMovement) {
            elapsedTicks += 1.0;
            tickProgress = elapsedTicks;
        }
    }

    public static void startCameraMovement(
            BlockPos targetBlockPos,
            double durationInSeconds,
            float intendedFinalYaw,
            float intendedFinalPitch,
            float intendedFinalRoll, // <--- NUEVO
            float intendedFinalShake,
            boolean followPlayerTarget,
            EasingType easing,
            double endDelayInSeconds,
            Direction blockFacing,
            boolean isFirstPointInPath,
            boolean maintainRotationDuringMove
    ) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.options == null || client.gameRenderer == null || client.cameraEntity == null) {
            LOGGER.error("CameraManager: Cannot start movement, client state is invalid."); resetCamera(); return;
        }

        ensureThirdPersonView();
        isPartOfPath = CameraPathManager.isPlayingPath();
        if (isPartOfPath && client.gameRenderer != null) {
            client.execute(() -> {
                if (client.gameRenderer != null) client.gameRenderer.setRenderHand(false);
            });
        }

        Camera currentCamera = client.gameRenderer.getCamera();
        if (isFirstPointInPath || lastTargetPosition == null) {
            startPosition = currentCamera.getPos();
            startYaw = currentCamera.getYaw();
            startPitch = currentCamera.getPitch();
            startRoll = 0f; // <--- NUEVO
            startShake = 0f;
            LOGGER.info("[DIAGNOSTIC] Start Segment (isFirst): StartPos from Camera ({:.2f},{:.2f},{:.2f})", startPosition.x, startPosition.y, startPosition.z);
        } else {
            startPosition = lastTargetPosition;
            startYaw = finalSegmentYaw;
            startPitch = finalSegmentPitch;
            startRoll = finalSegmentRoll; // <--- NUEVO
            startShake = finalSegmentShake;
            LOGGER.info("[DIAGNOSTIC] Start Segment (Continuation): StartPos from LastTarget ({:.2f},{:.2f},{:.2f})", startPosition.x, startPosition.y, startPosition.z);
        }

        targetPosition = Vec3d.ofCenter(targetBlockPos);

        finalSegmentYaw = intendedFinalYaw;
        finalSegmentPitch = MathHelper.clamp(intendedFinalPitch, -90.0f, 90.0f);
        finalSegmentRoll = MathHelper.clamp(intendedFinalRoll, -180.0f, 180.0f); // <--- NUEVO, típico rango roll
        finalSegmentShake = intendedFinalShake;

        if (maintainRotationDuringMove) {
            targetYawLerp = startYaw;
            targetPitchLerp = startPitch;
            targetRollLerp = startRoll; // <--- NUEVO
            targetShakeLerp = startShake;
        } else {
            targetYawLerp = normalizeYawForLerp(finalSegmentYaw, startYaw);
            targetPitchLerp = finalSegmentPitch;
            targetRollLerp = finalSegmentRoll; // <--- NUEVO
            targetShakeLerp = finalSegmentShake;
        }

        cameraPosition = startPosition;
        currentYaw = startYaw;
        currentPitch = startPitch;
        currentRoll = startRoll; // <--- NUEVO
        currentShake = startShake;

        totalTicks = Math.max(durationInSeconds * 20.0, 1.0);
        LOGGER.info("[DIAGNOSTIC] startCameraMovement: Received durationInSeconds={} -> Calculated totalTicks={}", durationInSeconds, totalTicks);

        elapsedTicks = 0.0;
        tickProgress = 0.0;
        finishedMovement = false;
        endDelaySeconds = endDelayInSeconds;
        endDelayTicks = Math.max(endDelaySeconds * 20.0, 0.0);
        endDelayElapsedTicks = 0.0;
        shakeTime = 0.0;

        followPlayer = followPlayerTarget;
        easingType = (easing != null) ? easing : EasingType.LINEAR;
        lastTargetPosition = targetPosition;

        moving = true;

        LOGGER.info(String.format(Locale.US,
                "CameraManager: Segment START -> TargetPos: %s, Dur: %.1ft(%.1fs), Delay: %.1ft(%.1fs). StartRot: %.1f/%.1f/%.1f, LerpTarget: %.1f/%.1f/%.1f, FinalRot: %.1f/%.1f/%.1f, Maintain: %b",
                targetPosition, totalTicks, durationInSeconds, endDelayTicks, endDelaySeconds,
                startYaw, startPitch, startRoll, targetYawLerp, targetPitchLerp, targetRollLerp, finalSegmentYaw, finalSegmentPitch, finalSegmentRoll, maintainRotationDuringMove));
    }

    public static void updateCameraPosition(Camera camera, float tickDelta) {
        if (!(camera instanceof CameraMixinAccessor accessor)) {
            return; // Si la cámara no implementa la interfaz, no hacemos nada
        }

        if (!moving) {
            if (startPosition != null || targetPosition != null || cameraPosition != null) {
                startPosition = null; targetPosition = null; cameraPosition = null;
                elapsedTicks = 0.0; totalTicks = 0.0; finishedMovement = false;
                endDelayTicks = 0; endDelayElapsedTicks = 0.0;
            }
            return;
        }

        if (startPosition == null || targetPosition == null || cameraPosition == null || totalTicks <= 0) {
            LOGGER.error("CameraManager: Invalid state during update (null pos or totalTicks={}) while 'moving'. Resetting.", totalTicks);
            resetCamera();
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            LOGGER.error("CameraManager: Client or world is null during update. Resetting.");
            resetCamera();
            return;
        }

        if (!finishedMovement) {
            // Progreso visual interpolado (progreso real solo en onTick)
            double visualProgress = Math.min((tickProgress + tickDelta) / totalTicks, 1.0);
            double progress = invertEasing(visualProgress, easingType);
            progress = MathHelper.clamp(progress, 0.0, 1.0);

            Vec3d previousPos = cameraPosition;
            cameraPosition = startPosition.lerp(targetPosition, progress);
            currentYaw = MathHelper.lerpAngleDegrees((float) progress, startYaw, targetYawLerp);
            currentPitch = MathHelper.lerp((float) progress, startPitch, targetPitchLerp);
            currentRoll = MathHelper.lerp(tickDelta, currentRoll, finalSegmentRoll);
            currentShake = targetShakeLerp; // shake SIEMPRE el del destino

            if (moving && !finishedMovement) {
                long gameTime = client.world.getTime();
                boolean shouldLog = (elapsedTicks <= 5 || gameTime % 5 == 0 || totalTicks <= 40);
                if (shouldLog) {
                    LOGGER.info("[DIAGNOSTIC] updateCam Tick {}: Elapsed={}/{} (+{}), Progress={}, Pos=({}, {}, {})->({}, {}, {})",
                            gameTime, elapsedTicks, totalTicks, 0,
                            progress,
                            previousPos != null ? previousPos.x : Double.NaN, previousPos != null ? previousPos.y : Double.NaN, previousPos != null ? previousPos.z : Double.NaN,
                            cameraPosition.x, cameraPosition.y, cameraPosition.z
                    );
                }
            }

            if (progress >= 1.0) {
                cameraPosition = targetPosition;
                currentYaw = targetYawLerp;
                currentPitch = targetPitchLerp;
                currentRoll = targetRollLerp; // <--- NUEVO
                currentShake = targetShakeLerp;

                finishedMovement = true;
                LOGGER.debug("[CAMMAN] Interpolation END. Pos: {}, Rot(Lerp): {}/{} (Roll: {}). Starting Delay: {}t", cameraPosition, currentYaw, currentPitch, currentRoll, endDelayTicks);

                if (endDelayTicks <= 0) {
                    currentYaw = finalSegmentYaw;
                    currentPitch = finalSegmentPitch;
                    currentRoll = finalSegmentRoll; // <--- NUEVO
                    applyToCamera(camera);
                    LOGGER.debug("[CAMMAN] Segment FINISHED (No Delay). Final State - Pos: {}, Rot: {}/{} (Roll: {})", cameraPosition, currentYaw, currentPitch, currentRoll);
                    moving = false;
                    return;
                }
            }
        } else {
            currentShake = finalSegmentShake;
            if (endDelayTicks > 0) {
                endDelayElapsedTicks += 1.0;
                cameraPosition = targetPosition;
                currentYaw = finalSegmentYaw;
                currentPitch = finalSegmentPitch;
                currentRoll = finalSegmentRoll; // <--- NUEVO
                if (endDelayElapsedTicks >= endDelayTicks) {
                    applyToCamera(camera);
                    LOGGER.debug("[CAMMAN] Segment FINISHED (After Delay {}t). Final State - Pos: {}, Rot: {}/{} (Roll: {})", endDelayElapsedTicks, cameraPosition, currentYaw, currentPitch, currentRoll);
                    moving = false;
                    endDelayTicks = 0;
                    endDelayElapsedTicks = 0.0;
                    return;
                } else {
                    if (endDelayElapsedTicks == 1 || endDelayElapsedTicks % 20 == 0) {
                        LOGGER.debug("[DIAGNOSTIC] In end delay: {}/{} ticks elapsed.", endDelayElapsedTicks, endDelayTicks);
                    }
                }
            } else {
                LOGGER.warn("CameraManager in unexpected state: finishedMovement=true but endDelay=0. Forcing moving=false.");
                moving = false;
                return;
            }
        }

        if (moving) {
            applyToCamera(camera);
        }
    }

    private static double invertEasing(double eased, EasingType type) {
        if (type == null) type = EasingType.LINEAR;
        switch (type) {
            case EASE_IN:      // y = x^2  ->  x = sqrt(y)
                return Math.sqrt(eased);
            case EASE_OUT:     // y = 1-(1-x)^2 -> x = 1 - Math.sqrt(1-y)
                return 1.0 - Math.sqrt(1.0 - eased);
            case EASE_IN_OUT:  // y = 2x^2 si x<0.5, y = 1-2(1-x)^2 si x>=0.5  -> invertido por tramos
                if (eased < 0.5) {
                    return Math.sqrt(eased / 2.0);
                } else {
                    return 1.0 - Math.sqrt((1.0 - eased) / 2.0);
                }
            default: // LINEAR y = x -> x = y
                return eased;
        }
    }

    private static void applyToCamera(Camera camera) {
        if (!moving) return;
        if (cameraPosition == null) {
            LOGGER.error("CameraManager ERROR: cameraPosition is null! Resetting.");
            resetCamera();
            return;
        }
        if (!(camera instanceof CameraMixinAccessor accessorroll)) {
            return; // Si la cámara no implementa la interfaz, no hacemos nada
        }
        if (!(camera instanceof CameraAccessorMixin accessor)) {
            LOGGER.error("CameraManager FATAL: Camera is not CameraAccessorMixin! Resetting.");
            resetCamera();
            return;
        }

        shakeTime += 1.0;

        Vec3d shakenPosition = cameraPosition;
        float shakenYaw = currentYaw;
        float shakenPitch = currentPitch;
        float shakenRoll = currentRoll;

        float intensity = MathHelper.clamp(currentShake, 0.0f, 10.0f);
        float freqBase = 0.10f;
        float freq = freqBase + intensity * 0.11f;
        float posAmp = intensity * 0.05f;
        float rotAmp = intensity * 0.5f;

        if (intensity > 0f) {
            double shakeX = Math.sin(shakeTime * freq) * posAmp;
            double shakeY = Math.cos(shakeTime * freq * 0.7) * posAmp * 0.7;
            double shakeZ = Math.cos(shakeTime * freq * 1.2) * posAmp * 0.5;
            shakenPosition = cameraPosition.add(shakeX, shakeY, shakeZ);

            float yawShakeAmount = (float)(Math.sin(shakeTime * freq * 1.4) * rotAmp);
            float pitchShakeAmount = (float)(Math.cos(shakeTime * freq * 1.8) * rotAmp * 0.7f);
            float rollShakeAmount = (float)(Math.sin(shakeTime * freq * 2.2) * rotAmp * 0.5f);
            shakenYaw += yawShakeAmount;
            shakenPitch += pitchShakeAmount;
            shakenRoll += rollShakeAmount;
        }

        try {
            accessor.invokeSetPos(shakenPosition);
            accessor.invokeSetRotation(shakenYaw, shakenPitch);
            // Usa la interfaz RollAccessor para manipular el roll agregado por el mixin
            accessorroll.setRoll(currentRoll);

        } catch (Exception e) {
            LOGGER.error("Exception invoking CameraAccessorMixin! Resetting.", e);
            resetCamera();
        }
    }

    public static void resetCamera() {
        startShake = 0f; targetShakeLerp = 0f; currentShake = 0f; finalSegmentShake = 0f;
        tickProgress = 0.0;
        shakeTime = 0.0;

        boolean wasMovingBeforeReset = moving;
        LOGGER.info("CameraManager: Resetting internal state (was moving: {}).", wasMovingBeforeReset);
        moving = false;
        startPosition = null; targetPosition = null; cameraPosition = null;
        startYaw = 0f; startPitch = 0f; startRoll = 0f;
        targetYawLerp = 0f; targetPitchLerp = 0f; targetRollLerp = 0f;
        currentYaw = 0f; currentPitch = 0f; currentRoll = 0f;
        finalSegmentYaw = 0f; finalSegmentPitch = 0f; finalSegmentRoll = 0f;
        elapsedTicks = 0.0; totalTicks = 0.0; finishedMovement = false;
        endDelaySeconds = 0.0; endDelayTicks = 0.0; endDelayElapsedTicks = 0.0;
        perspectiveChangeRequested = false; perspectiveChangeTime = 0;
        isPartOfPath = false; followPlayer = false; lastTargetPosition = null;
        MinecraftClient client = MinecraftClient.getInstance();
        if (wasMovingBeforeReset && client != null && client.gameRenderer != null) {
            client.execute(() -> {
                if (client.gameRenderer != null) {
                    LOGGER.debug("CameraManager: Restoring render hand state to true.");
                    client.gameRenderer.setRenderHand(true);
                } else { LOGGER.warn("Cannot restore render hand, gameRenderer became null."); }
            });
        } else if (wasMovingBeforeReset) { LOGGER.warn("Could not restore render hand state (client or gameRenderer was null)."); }
        LOGGER.debug("CameraManager: Internal state reset complete.");
    }

    public static void requestPerspectiveChange() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options == null) return;
        if (originalPerspective == null) {
            originalPerspective = client.options.getPerspective();
            LOGGER.info("CameraManager: Saved original perspective ({})", originalPerspective);
        }
        if (client.options.getPerspective() != Perspective.THIRD_PERSON_BACK) {
            client.execute(() -> {
                if (client.options != null) {
                    client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                    LOGGER.info("CameraManager: Set perspective to THIRD_PERSON_BACK.");
                    perspectiveChangeRequested = true;
                    perspectiveChangeTime = System.currentTimeMillis();
                }
            });
        } else {
            perspectiveChangeRequested = false;
            LOGGER.debug("CameraManager: Perspective already THIRD_PERSON_BACK.");
        }
    }

    public static float getCurrentShake() { return currentShake; }

    public static boolean isPerspectiveChangeComplete() {
        if (!perspectiveChangeRequested) return true;
        if (System.currentTimeMillis() - perspectiveChangeTime > PERSPECTIVE_CHANGE_DELAY_MS) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.options != null && client.options.getPerspective() == Perspective.THIRD_PERSON_BACK) {
                perspectiveChangeRequested = false;
                return true;
            } else {
                LOGGER.warn("CameraManager: Perspective wasn't THIRD_PERSON_BACK after delay. Forcing again.");
                if (client != null && client.options != null) {
                    client.execute(() -> {
                        if (client.options != null) client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                        perspectiveChangeTime = System.currentTimeMillis();
                    });
                }
                return false;
            }
        }
        return false;
    }

    public static void ensureThirdPersonView() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.options != null && client.options.getPerspective() != Perspective.THIRD_PERSON_BACK) {
            requestPerspectiveChange();
        }
    }

    private static float normalizeYawForLerp(float targetYaw, float currentYaw) { return targetYaw; }

    private static double applyEasing(double progress, EasingType type) {
        if (type == null) type = EasingType.LINEAR;
        switch (type) {
            case EASE_IN: return progress * progress;
            case EASE_OUT: return 1 - (1 - progress) * (1 - progress);
            case EASE_IN_OUT: return progress < 0.5 ? 2 * progress * progress : 1 - Math.pow(-2 * progress + 2, 2) / 2;
            default: return progress;
        }
    }

    private static float getYawFromFacing(Direction facing) {
        return switch (facing) {
            case NORTH -> 180.0f; case SOUTH -> 0.0f; case WEST -> 90.0f; case EAST -> -90.0f; default -> 0.0f;
        };
    }

    public static boolean isCameraMoving() { return moving; }
    public static Vec3d getCameraPosition() { return cameraPosition; }
    public static Vec3d getStartPosition() { return startPosition; }
    public static Vec3d getTargetPosition() { return targetPosition; }
    public static double getElapsedTicks() { return elapsedTicks; }
    public static double getTotalTicks() { return totalTicks; }
    public static EasingType getEasingType() { return easingType; }
    public static float getYaw() { return currentYaw; }
    public static float getPitch() { return currentPitch; }
    public static float getRoll() { return currentRoll; } // <--- NUEVO
}