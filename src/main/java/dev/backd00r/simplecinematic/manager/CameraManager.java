package dev.backd00r.simplecinematic.manager;

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
    private static float targetYawLerp;
    private static float targetPitchLerp;
    private static float currentYaw;
    private static float currentPitch;
    private static float finalSegmentYaw = 0f;
    private static float finalSegmentPitch = 0f;

    private static double totalTicks = 0.0;
    private static double elapsedTicks = 0.0;
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

    public static void startCameraMovement(
            BlockPos targetBlockPos,
            double durationInSeconds,
            float intendedFinalYaw,
            float intendedFinalPitch,
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
                if(client.gameRenderer != null) client.gameRenderer.setRenderHand(false);
            });
        }

        Camera currentCamera = client.gameRenderer.getCamera();
        if (isFirstPointInPath || lastTargetPosition == null) {
            startPosition = currentCamera.getPos();
            startYaw = currentCamera.getYaw();
            startPitch = currentCamera.getPitch();
            LOGGER.info("[DIAGNOSTIC] Start Segment (isFirst): StartPos from Camera ({:.2f},{:.2f},{:.2f})", startPosition.x, startPosition.y, startPosition.z);
        } else {
            startPosition = lastTargetPosition;
            startYaw = finalSegmentYaw;
            startPitch = finalSegmentPitch;
            LOGGER.info("[DIAGNOSTIC] Start Segment (Continuation): StartPos from LastTarget ({:.2f},{:.2f},{:.2f})", startPosition.x, startPosition.y, startPosition.z);
        }

        targetPosition = Vec3d.ofCenter(targetBlockPos);

        finalSegmentYaw = intendedFinalYaw;
        finalSegmentPitch = MathHelper.clamp(intendedFinalPitch, -90.0f, 90.0f);

        if (maintainRotationDuringMove) {
            targetYawLerp = startYaw;
            targetPitchLerp = startPitch;
        } else {
            targetYawLerp = normalizeYawForLerp(finalSegmentYaw, startYaw);
            targetPitchLerp = finalSegmentPitch;
        }

        cameraPosition = startPosition;
        currentYaw = startYaw;
        currentPitch = startPitch;

        totalTicks = Math.max(durationInSeconds * 20.0, 1.0);
        LOGGER.info("[DIAGNOSTIC] startCameraMovement: Received durationInSeconds={} -> Calculated totalTicks={}", durationInSeconds, totalTicks);

        elapsedTicks = 0.0;
        finishedMovement = false;
        endDelaySeconds = endDelayInSeconds;
        endDelayTicks = Math.max(endDelaySeconds * 20.0, 0.0);
        endDelayElapsedTicks = 0.0;

        followPlayer = followPlayerTarget;
        easingType = (easing != null) ? easing : EasingType.LINEAR;
        lastTargetPosition = targetPosition;

        moving = true;

        LOGGER.info(String.format(Locale.US,
                "CameraManager: Segment START -> TargetPos: %s, Dur: %.1ft(%.1fs), Delay: %.1ft(%.1fs). StartRot: %.1f/%.1f, LerpTarget: %.1f/%.1f, FinalRot: %.1f/%.1f, Maintain: %b",
                targetPosition, totalTicks, durationInSeconds, endDelayTicks, endDelaySeconds,
                startYaw, startPitch, targetYawLerp, targetPitchLerp, finalSegmentYaw, finalSegmentPitch, maintainRotationDuringMove));
    }

    public static void updateCameraPosition(Camera camera, float tickDelta) {
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
            double previousElapsed = elapsedTicks;
            elapsedTicks += 1.0;

            double progress = Math.min(elapsedTicks / totalTicks, 1.0);
            double easedProgress = applyEasing(progress, easingType);

            Vec3d previousPos = cameraPosition;
            cameraPosition = startPosition.lerp(targetPosition, easedProgress);
            currentYaw = MathHelper.lerpAngleDegrees((float) easedProgress, startYaw, targetYawLerp);
            currentPitch = MathHelper.lerp((float) easedProgress, startPitch, targetPitchLerp);

            if (moving && !finishedMovement) {
                long gameTime = client.world.getTime();
                boolean shouldLog = (elapsedTicks <= 5 || gameTime % 5 == 0 || totalTicks <= 40);
                if (shouldLog) {
                    LOGGER.info("[DIAGNOSTIC] updateCam Tick {}: Elapsed={}/{} (+{}), Prog={}, Eased={}, Pos=({}, {}, {})->({}, {}, {})",
                            gameTime, elapsedTicks, totalTicks, elapsedTicks - previousElapsed,
                            progress, easedProgress,
                            previousPos != null ? previousPos.x : Double.NaN, previousPos != null ? previousPos.y : Double.NaN, previousPos != null ? previousPos.z : Double.NaN,
                            cameraPosition.x, cameraPosition.y, cameraPosition.z
                    );
                }
            }

            if (progress >= 1.0) {
                cameraPosition = targetPosition;
                currentYaw = targetYawLerp;
                currentPitch = targetPitchLerp;
                finishedMovement = true;
                LOGGER.debug("[CAMMAN] Interpolation END. Pos: {}, Rot(Lerp): {}/{}. Starting Delay: {}t", cameraPosition, currentYaw, currentPitch, endDelayTicks);

                if (endDelayTicks <= 0) {
                    currentYaw = finalSegmentYaw;
                    currentPitch = finalSegmentPitch;
                    applyToCamera(camera);
                    LOGGER.debug("[CAMMAN] Segment FINISHED (No Delay). Final State - Pos: {}, Rot: {}/{}", cameraPosition, currentYaw, currentPitch);
                    moving = false;
                    return;
                }
            }
        }
        else {
            if (endDelayTicks > 0) {
                endDelayElapsedTicks += 1.0;
                cameraPosition = targetPosition;
                currentYaw = finalSegmentYaw;
                currentPitch = finalSegmentPitch;
                if (endDelayElapsedTicks >= endDelayTicks) {
                    applyToCamera(camera);
                    LOGGER.debug("[CAMMAN] Segment FINISHED (After Delay {}t). Final State - Pos: {}, Rot: {}/{}", endDelayElapsedTicks, cameraPosition, currentYaw, currentPitch);
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

    private static void applyToCamera(Camera camera) {
        if (!moving) return;
        if (cameraPosition == null) { LOGGER.error("CameraManager ERROR: cameraPosition is null! Resetting."); resetCamera(); return; }
        if (!(camera instanceof CameraAccessorMixin accessor)) { LOGGER.error("CameraManager FATAL: Camera is not CameraAccessorMixin! Resetting."); resetCamera(); return; }
        try {
            accessor.invokeSetPos(cameraPosition);
            accessor.invokeSetRotation(currentYaw, currentPitch);
        } catch (Exception e) {
            LOGGER.error("Exception invoking CameraAccessorMixin! Resetting.", e);
            resetCamera();
        }
    }

    public static void resetCamera() {
        boolean wasMovingBeforeReset = moving;
        LOGGER.info("CameraManager: Resetting internal state (was moving: {}).", wasMovingBeforeReset);
        moving = false;
        startPosition = null; targetPosition = null; cameraPosition = null;
        startYaw = 0f; startPitch = 0f; targetYawLerp = 0f; targetPitchLerp = 0f;
        currentYaw = 0f; currentPitch = 0f; finalSegmentYaw = 0f; finalSegmentPitch = 0f;
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
                if(client.options != null) {
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

    public static boolean isPerspectiveChangeComplete() {
        if (!perspectiveChangeRequested) return true;
        if (System.currentTimeMillis() - perspectiveChangeTime > PERSPECTIVE_CHANGE_DELAY_MS) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.options != null && client.options.getPerspective() == Perspective.THIRD_PERSON_BACK) {
                perspectiveChangeRequested = false; return true;
            } else {
                LOGGER.warn("CameraManager: Perspective wasn't THIRD_PERSON_BACK after delay. Forcing again.");
                if (client != null && client.options != null) {
                    client.execute(() -> {
                        if(client.options != null) client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
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
}