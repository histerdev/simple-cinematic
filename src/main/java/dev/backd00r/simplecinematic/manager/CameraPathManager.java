package dev.backd00r.simplecinematic.manager;

import dev.backd00r.simplecinematic.client.camera.CinematicCameraState;
import dev.backd00r.simplecinematic.networking.ModPackets;
import dev.backd00r.simplecinematic.util.EasingType;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Environment(EnvType.CLIENT)
public class CameraPathManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(CameraPathManager.class);

    private static final Map<Integer, Map<Integer, CameraPointData>> channelPoints = new HashMap<>();

    private static int currentChannelPlaying = -1;
    private static int currentPointIndex = -1;
    private static List<CameraPointData> currentPathPoints = new ArrayList<>();
    private static boolean isPlayingPath = false;
    private static boolean transitioningToNextPoint = false;
    private static boolean isPreviewing = false;
    private static final AtomicBoolean clearingInProgress = new AtomicBoolean(false);

    private static boolean waitingForPerspectiveChange = false;
    private static boolean waitingForInitialChunks = false;

    private static final int CHUNK_REQUEST_RADIUS = 6;
    private static final int MAX_CHUNKS_PER_REQUEST = 64;
    private static final double CHUNK_LOAD_AHEAD_SECONDS = 5.0;
    private static final int INITIAL_CHUNK_CHECK_RADIUS = CHUNK_REQUEST_RADIUS + 1;

    private static final long MAX_CHUNK_WAIT_SECONDS = 8;
    private static long chunkWaitStartTime = 0;
    private static boolean chunkWaitMessageSent = false;

    // Chunks
    private static Set<ChunkPos> requiredChunks = new HashSet<>();
    private static Set<ChunkPos> requestedOrLoadedChunks = new HashSet<>();

    // Mantén el centro de chunks en la cámara virtual incluso durante la precarga (SOLUCIÓN CLAVE)
    private static boolean forceCameraChunkCenter = false;

    private static final double INITIAL_SEGMENT_FIXED_SECONDS = 0.75;

    public static void registerCameraPoint(BlockPos pos, int channel, int position,
                                           float yaw, float pitch, double duration,
                                           double stayDuration, Direction blockFacing, boolean rotateToNext) {
        if (!MinecraftClient.getInstance().isOnThread()) {
            MinecraftClient.getInstance().execute(() -> registerCameraPoint(pos, channel, position, yaw, pitch, duration, stayDuration, blockFacing, rotateToNext));
            return;
        }
        registerOrUpdateCameraPointInternal(pos, channel, position, yaw, pitch, duration, stayDuration, blockFacing, rotateToNext, "Command/Network");
    }

    public static void registerOrUpdateCameraPointClient(BlockPos pos, int channel, int position,
                                                         float yaw, float pitch, double duration,
                                                         double stayDuration, Direction blockFacing, boolean rotateToNext) {
        if (!MinecraftClient.getInstance().isOnThread()) {
            MinecraftClient.getInstance().execute(() -> registerOrUpdateCameraPointClient(pos, channel, position, yaw, pitch, duration, stayDuration, blockFacing, rotateToNext));
            return;
        }
        registerOrUpdateCameraPointInternal(pos, channel, position, yaw, pitch, duration, stayDuration, blockFacing, rotateToNext, "Client GUI Update/NBT");
    }

    private static synchronized void registerOrUpdateCameraPointInternal(BlockPos pos, int channel, int position,
                                                                         float yaw, float pitch, double duration,
                                                                         double stayDuration, Direction blockFacing,
                                                                         boolean rotateToNext, String source) {
        Map<Integer, CameraPointData> positionMap = channelPoints.computeIfAbsent(channel, k -> new TreeMap<>());
        CameraPointData pointData = new CameraPointData(pos.toImmutable(), channel, position, yaw, pitch, duration, stayDuration, blockFacing, rotateToNext);
        positionMap.put(position, pointData);
    }

    // Mantén el centro de chunks durante precarga y reproducción
    public static void onClientTick(MinecraftClient client) {
        if (isPlayingPath || waitingForInitialChunks) {
            CinematicCameraState.setCinematicActive(true);
            CinematicCameraState.setCameraPos(CameraManager.getCameraPosition());
            BlockPos cam = CinematicCameraState.getCameraBlockPos();
            if (cam != null && client.world != null) {
                client.world.getChunkManager().setChunkMapCenter(cam.getX() >> 4, cam.getZ() >> 4);
            }
        } else {
            CinematicCameraState.setCinematicActive(false);
        }
        updatePathPlayback();
    }

    public static synchronized void clearAllPaths() {
        isPlayingPath = false;
        waitingForInitialChunks = false;
        requestedOrLoadedChunks.clear();
        requiredChunks.clear();
        CinematicCameraState.setCinematicActive(false);
        CinematicCameraState.setCameraPos(null);
    }
    public static synchronized void clearChannelPath(int channel) {
        LOGGER.info("CameraPathManager: Clearing path data for channel {}", channel);
        channelPoints.remove(channel);
        if (currentChannelPlaying == channel && isPlayingPath) {
            LOGGER.warn("CameraPathManager: Stopping playback for channel {} because its data was cleared.", channel);
            clearAllPaths();
        }
    }

    // INICIO ROBUSTO DE CINEMÁTICA
    public static void playPath(int channel) {
        playPathFromPosition(channel, 1);
    }

    public static synchronized void playPathFromPosition(int channel, int startPosition) {
        clearAllPaths(); // Limpia antes de empezar
        LOGGER.info("CameraPathManager: Attempting to play path for channel {} starting from position {}", channel, startPosition);

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            LOGGER.error("Cannot play path, client or world is null.");
            clearAllPaths();
            return;
        }

        if (!channelPoints.containsKey(channel) || channelPoints.get(channel).isEmpty()) {
            LOGGER.error("CameraPathManager: No points defined for channel {}", channel);
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§cNo camera points defined for channel " + channel), false);
            }
            clearAllPaths();
            return;
        }

        Map<Integer, CameraPointData> positionMap = channelPoints.get(channel);

        List<CameraPointData> sortedPoints = new ArrayList<>();
        for (CameraPointData point : positionMap.values()) {
            if (point.position >= startPosition) {
                sortedPoints.add(point);
            }
        }

        if (sortedPoints.isEmpty()) {
            LOGGER.error("CameraPathManager: No points found at or after start position {} for channel {}", startPosition, channel);
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§cNo points at or after position " + startPosition + " for channel " + channel), false);
            }
            clearAllPaths();
            return;
        }

        currentChannelPlaying = channel;
        currentPointIndex = -1;
        currentPathPoints = sortedPoints;
        isPlayingPath = false;
        transitioningToNextPoint = false;
        isPreviewing = false;
        waitingForInitialChunks = false;
        waitingForPerspectiveChange = false;
        chunkWaitStartTime = 0;
        chunkWaitMessageSent = false;
        forceCameraChunkCenter = true;

        requiredChunks.clear();
        requestedOrLoadedChunks.clear();

        // Calcula chunks requeridos y pone CINEMATIC STATE en el 1er punto
        LOGGER.info("Calculating required chunks for the path ({} points)...", currentPathPoints.size());
        requiredChunks = calculateRequiredChunksForPath(currentPathPoints);
        LOGGER.info("Total unique chunks required: {}", requiredChunks.size());
        CinematicCameraState.setCameraPos(Vec3d.ofCenter(currentPathPoints.get(0).pos));
        CinematicCameraState.setCinematicActive(true);

        requestNeededChunks(true);
        waitingForInitialChunks = true;
        chunkWaitStartTime = System.nanoTime();
        LOGGER.info("Initial chunks requested. Waiting for them to load (Max Wait: {}s)...", MAX_CHUNK_WAIT_SECONDS);

        LOGGER.info("CameraPathManager: Requesting perspective change...");
        CameraManager.requestPerspectiveChange();
        waitingForPerspectiveChange = true;

        LOGGER.info("CameraPathManager: Path playback initiated for channel {} with {} points. Waiting for perspective and initial chunks...", channel, currentPathPoints.size());
    }

    // Mantener el centro de chunks SIEMPRE durante precarga/movimiento
    public static void updatePathPlayback() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            LOGGER.warn("Client/World null. Stopping."); clearAllPaths(); return;
        }

        boolean canStartMoving = true;

        // 1. Esperar Perspectiva
        if (waitingForPerspectiveChange) {
            if (CameraManager.isPerspectiveChangeComplete()) {
                waitingForPerspectiveChange = false;
            } else {
                canStartMoving = false;
            }
        }

        // 2. Esperar Chunks Iniciales (CON BLOQUEO Y TIMEOUT)
        if (waitingForInitialChunks) {
            if (checkInitialChunksLoaded()) {
                waitingForInitialChunks = false;
                chunkWaitMessageSent = false;
                isPlayingPath = true;
                forceCameraChunkCenter = false; // Ya no es necesario forzar, la cinemática se encarga
                LOGGER.info("Initial chunks loaded.");
            } else {
                canStartMoving = false;
                if (!chunkWaitMessageSent && chunkWaitStartTime > 0 && client.player != null) {
                    client.player.sendMessage(Text.literal("§eCargando área de cinemática..."), false);
                    chunkWaitMessageSent = true;
                }
                long elapsedNanos = System.nanoTime() - chunkWaitStartTime;
                long elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(elapsedNanos);
                if (elapsedSeconds >= MAX_CHUNK_WAIT_SECONDS) {
                    LOGGER.error("Timeout waiting for initial chunks ({}s elapsed). Aborting path.", elapsedSeconds);
                    if (client.player != null) {
                        client.player.sendMessage(Text.literal("§cError: No se pudo cargar el área a tiempo."), false);
                    }
                    clearAllPaths();
                    return;
                }
            }
        }

        // 3. Iniciar Primer Movimiento (Solo si todo está listo)
        if (canStartMoving && !waitingForPerspectiveChange && !waitingForInitialChunks && currentPointIndex == -1 && !CameraManager.isCameraMoving() && !transitioningToNextPoint) {
            LOGGER.info("Perspective and initial chunks ready. Starting movement to first point (Index 0).");
            currentPointIndex = 0;
            transitioningToNextPoint = true;
            moveToCurrentPoint();
            transitioningToNextPoint = false;
        }

        // 4. Solicitar Chunks Continuamente (Si ya estamos en movimiento y no esperando iniciales)
        if (isPlayingPath && !waitingForInitialChunks && CameraManager.isCameraMoving()) {
            if (client.world.getTime() % 20 == 0) {
                requestNeededChunks(false);
            }
        }

        // 5. Avanzar al Siguiente Punto (Si no esperamos nada y la cámara no se mueve)
        if (!waitingForPerspectiveChange && !waitingForInitialChunks && !transitioningToNextPoint && !CameraManager.isCameraMoving()) {
            if (currentPointIndex >= 0) {
                LOGGER.debug("CameraManager finished segment for index {}.", currentPointIndex);
                currentPointIndex++;

                if (currentPointIndex >= currentPathPoints.size()) {
                    clearAllPaths();
                } else {
                    LOGGER.info("CameraPathManager: Proceeding to point index {}", currentPointIndex);
                    transitioningToNextPoint = true;
                    moveToCurrentPoint();
                    transitioningToNextPoint = false;
                }
            }
        }
    }

    private static void moveToCurrentPoint() {
        if (!isPlayingPath || currentPointIndex < 0 || currentPointIndex >= currentPathPoints.size()) {
            LOGGER.error("Invalid state for moveToCurrentPoint. Stopping."); clearAllPaths(); return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.gameRenderer == null || client.gameRenderer.getCamera() == null) {
            LOGGER.error("Client state invalid for move. Stopping."); clearAllPaths(); return;
        }

        CameraPointData currentPoint = currentPathPoints.get(currentPointIndex);
        CameraPointData nextPoint = (currentPointIndex + 1 < currentPathPoints.size()) ? currentPathPoints.get(currentPointIndex + 1) : null;
        double segmentDuration;

        if (currentPointIndex == 0) {
            segmentDuration = INITIAL_SEGMENT_FIXED_SECONDS;
            Vec3d playerPos = client.gameRenderer.getCamera().getPos();
            Vec3d pointAPos = Vec3d.ofCenter(currentPoint.pos);
            double distance = playerPos.distanceTo(pointAPos);
            LOGGER.info("[DIAGNOSTIC] Using FIXED duration for initial segment (Player -> Point A): Distance={}.1f, FixedDuration={}.2fs", distance, segmentDuration);
        } else {
            segmentDuration = Math.max(currentPoint.duration, 0.1);
            LOGGER.info("[DIAGNOSTIC] Using point duration for segment {} -> {}: {}.2fs", currentPointIndex - 1, currentPointIndex, segmentDuration);
        }

        double pointStayDuration = Math.max(currentPoint.stayDuration, 0.0);
        float finalTargetYaw, finalTargetPitch;
        boolean useManualRotation = currentPoint.yaw != 0 || currentPoint.pitch != 0;
        boolean maintainRotationDuringMove;

        if (useManualRotation) {
            finalTargetYaw = currentPoint.yaw; finalTargetPitch = currentPoint.pitch; maintainRotationDuringMove = false;
        } else {
            boolean shouldLookAtNext = currentPoint.rotateToNext && nextPoint != null;
            if (shouldLookAtNext) {
                maintainRotationDuringMove = false;
                Vec3d currentVec = Vec3d.ofCenter(currentPoint.pos); Vec3d nextVec = Vec3d.ofCenter(nextPoint.pos);
                Vec3d directionToNext = nextVec.subtract(currentVec).normalize();
                if (directionToNext.lengthSquared() < 1.0E-6) {
                    finalTargetYaw = CameraManager.getYaw(); finalTargetPitch = CameraManager.getPitch();
                } else {
                    finalTargetYaw = (float) (MathHelper.atan2(directionToNext.z, directionToNext.x) * (180.0 / Math.PI)) - 90.0f;
                    finalTargetPitch = (float) (-Math.asin(directionToNext.y) * (180.0 / Math.PI));
                    finalTargetPitch = MathHelper.clamp(finalTargetPitch, -90.0f, 90.0f);
                }
            } else {
                maintainRotationDuringMove = true; finalTargetYaw = getYawFromDirection(currentPoint.blockDirection); finalTargetPitch = 0;
            }
        }

        LOGGER.info("[DIAGNOSTIC] Calling startCameraMovement for segment -> index {}. FINAL DURATION PASSED: {} seconds", currentPointIndex, segmentDuration);

        CameraManager.startCameraMovement(
                currentPoint.pos, segmentDuration, finalTargetYaw, finalTargetPitch, false, EasingType.EASE_IN_OUT,
                pointStayDuration, currentPoint.blockDirection, currentPointIndex == 0, maintainRotationDuringMove
        );
    }

    public static Set<ChunkPos> calculateRequiredChunksForPath(List<CameraPointData> path) {
        Set<ChunkPos> chunks = new java.util.HashSet<>();
        int radius = 6; // o el radio que uses
        for (CameraPointData point : path) {
            ChunkPos pos = new ChunkPos(point.pos);
            for (int dx = -radius; dx <= radius; dx++)
                for (int dz = -radius; dz <= radius; dz++)
                    chunks.add(new ChunkPos(pos.x + dx, pos.z + dz));
        }
        return chunks;
    }
    private static void addChunksAlongLine(Set<ChunkPos> targetSet, Vec3d start, Vec3d end, int radius) {
        double dist = start.distanceTo(end);
        if (dist < 1.0) { addChunksAroundPoint(targetSet, end, radius); return; }
        int steps = (int) Math.ceil(dist / 16.0);
        addChunksAroundPoint(targetSet, start, radius);
        for (int i = 1; i <= steps; i++) {
            Vec3d samplePos = start.lerp(end, (double) i / steps);
            addChunksAroundPoint(targetSet, samplePos, radius);
        }
    }

    private static void addChunksAroundPoint(Set<ChunkPos> targetSet, Vec3d center, int radius) {
        if (center == null) return;
        ChunkPos centerChunk = new ChunkPos(BlockPos.ofFloored(center));
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                targetSet.add(new ChunkPos(centerChunk.x + dx, centerChunk.z + dz));
            }
        }
    }

    public static void requestNeededChunks(boolean initialRequest) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null || client.world == null || !ClientPlayNetworking.canSend(ModPackets.REQUEST_CINEMATIC_CHUNKS)) return;
        Set<ChunkPos> chunksToRequestNow = new HashSet<>();
        Vec3d referencePos = null;
        int radiusToCheck = CHUNK_REQUEST_RADIUS;

        // For initial request, use the first point in the path or the camera's current position
        if (initialRequest) {
            if (currentPathPoints.isEmpty()) return;
            Camera camera = client.gameRenderer != null ? client.gameRenderer.getCamera() : null;
            referencePos = camera != null ? camera.getPos() : Vec3d.ofCenter(currentPathPoints.get(0).pos);
            radiusToCheck = INITIAL_CHUNK_CHECK_RADIUS;
        } else if (CameraManager.isCameraMoving()) {
            // Estimate the future camera position for non-initial requests
            referencePos = estimateFutureCameraPosition(CHUNK_LOAD_AHEAD_SECONDS);
            if (referencePos == null) referencePos = CameraManager.getCameraPosition();
        } else return;

        if (referencePos == null) return;

        ChunkPos centerChunk = new ChunkPos(BlockPos.ofFloored(referencePos));
        for (int dx = -radiusToCheck; dx <= radiusToCheck; dx++) {
            for (int dz = -radiusToCheck; dz <= radiusToCheck; dz++) {
                ChunkPos cp = new ChunkPos(centerChunk.x + dx, centerChunk.z + dz);
                if (requiredChunks.contains(cp) && !requestedOrLoadedChunks.contains(cp)) {
                    // Check if the chunk is already loaded
                    if (client.world.getChunkManager().getChunk(cp.x, cp.z) == null) {
                        chunksToRequestNow.add(cp);
                    } else {
                        requestedOrLoadedChunks.add(cp);
                    }
                }
            }
        }

        if (chunksToRequestNow.isEmpty()) return;

        // Send requests for chunks to be loaded
        List<ChunkPos> requestList = new ArrayList<>(chunksToRequestNow);
        LOGGER.info("Requesting {} new chunks (Initial: {})...", requestList.size(), initialRequest);
        for (int i = 0; i < requestList.size(); i += MAX_CHUNKS_PER_REQUEST) {
            List<ChunkPos> subList = requestList.subList(i, Math.min(i + MAX_CHUNKS_PER_REQUEST, requestList.size()));
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeInt(subList.size());
            for (ChunkPos cp : subList) buf.writeLong(cp.toLong());
            ClientPlayNetworking.send(ModPackets.REQUEST_CINEMATIC_CHUNKS, buf);
        }
        requestedOrLoadedChunks.addAll(chunksToRequestNow);
    }

    private static Vec3d estimateFutureCameraPosition(double secondsAhead) {
        if (!CameraManager.isCameraMoving()) return CameraManager.getCameraPosition();
        Vec3d start = CameraManager.getStartPosition();
        Vec3d target = CameraManager.getTargetPosition();
        double elapsed = CameraManager.getElapsedTicks();
        double total = CameraManager.getTotalTicks();
        if (start == null || target == null || total <= 0) return CameraManager.getCameraPosition();
        double ticksAhead = secondsAhead * 20.0;
        double futureElapsed = Math.min(elapsed + ticksAhead, total);
        double futureProgress = futureElapsed / total;
        return start.lerp(target, futureProgress);
    }

    // PUBLICO PARA SINCRONIZACION
    public static boolean checkInitialChunksLoaded() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        if (world == null || currentPathPoints.isEmpty()) return true;
        Camera camera = client.gameRenderer != null ? client.gameRenderer.getCamera() : null;
        Vec3d checkCenter = camera != null ? camera.getPos() : Vec3d.ofCenter(currentPathPoints.get(0).pos);
        ChunkPos centerChunk = new ChunkPos(BlockPos.ofFloored(checkCenter));
        int total = 0, loaded = 0;
        for (int dx = -INITIAL_CHUNK_CHECK_RADIUS; dx <= INITIAL_CHUNK_CHECK_RADIUS; dx++) {
            for (int dz = -INITIAL_CHUNK_CHECK_RADIUS; dz <= INITIAL_CHUNK_CHECK_RADIUS; dz++) {
                ChunkPos cpToCheck = new ChunkPos(centerChunk.x + dx, centerChunk.z + dz);
                if (requiredChunks.contains(cpToCheck)) {
                    total++;
                    if (world.getChunk(cpToCheck.x, cpToCheck.z, ChunkStatus.FULL, false) != null) {
                        loaded++;
                        requestedOrLoadedChunks.add(cpToCheck);
                    }
                }
            }
        }
        LOGGER.info("[CINEMATICA] Chunks cargados: {}/{}", loaded, total);
        return loaded == total;
    }

    private static void releaseChunks() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.getNetworkHandler() == null || !ClientPlayNetworking.canSend(ModPackets.RELEASE_CINEMATIC_CHUNKS) || requestedOrLoadedChunks.isEmpty()) {
            if (!requestedOrLoadedChunks.isEmpty()) {
                LOGGER.warn("Cannot send RELEASE_CINEMATIC_CHUNKS. Clearing local list anyway.");
                requestedOrLoadedChunks.clear();
            }
            return;
        }
        LOGGER.info("Releasing {} tracked cinematic chunks.", requestedOrLoadedChunks.size());
        List<ChunkPos> releaseList = new ArrayList<>(requestedOrLoadedChunks);
        for (int i = 0; i < releaseList.size(); i += MAX_CHUNKS_PER_REQUEST) {
            List<ChunkPos> subList = releaseList.subList(i, Math.min(i + MAX_CHUNKS_PER_REQUEST, releaseList.size()));
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeInt(subList.size());
            for (ChunkPos cp : subList) buf.writeLong(cp.toLong());
            ClientPlayNetworking.send(ModPackets.RELEASE_CINEMATIC_CHUNKS, buf);
        }
        requestedOrLoadedChunks.clear();
        LOGGER.debug("Local list of requested/loaded chunks cleared.");
    }

    private static float getYawFromDirection(Direction direction) {
        return switch (direction) {
            case NORTH -> 180.0f; case SOUTH -> 0.0f; case WEST -> 90.0f; case EAST -> -90.0f; default -> 0.0f;
        };
    }

    public static boolean isPlayingPath() { return isPlayingPath; }
    public static int getCurrentPointIndex() { return currentPointIndex; }
    public static synchronized Map<Integer, Map<Integer, CameraPointData>> getAllChannelPoints() {
        Map<Integer, Map<Integer, CameraPointData>> copy = new HashMap<>();
        for (Map.Entry<Integer, Map<Integer, CameraPointData>> entry : channelPoints.entrySet())
            copy.put(entry.getKey(), new TreeMap<>(entry.getValue()));
        return copy;
    }

    public static void playPathForPreview(int channel, int startPosition) {
        LOGGER.info("Starting path preview for channel {} from pos {}", channel, startPosition);
        isPreviewing = true;
        playPathFromPosition(channel, startPosition);
    }

    public static void stopPreview() {
        LOGGER.info("Stopping path preview.");
        if (isPreviewing) {
            isPreviewing = false;
            clearAllPaths();
        }
    }
    public static boolean isPreviewing() { return isPreviewing; }

    public static class CameraPointData {
        public final BlockPos pos;
        public final int channel;
        public final int position;
        public final float yaw;
        public final float pitch;
        public final double duration;
        public final double stayDuration;
        public final Direction blockDirection;
        public final boolean rotateToNext;

        public CameraPointData(BlockPos pos, int channel, int position, float yaw, float pitch,
                               double duration, double stayDuration, Direction blockDirection, boolean rotateToNext) {
            this.pos = pos;
            this.channel = channel;
            this.position = position;
            this.yaw = yaw;
            this.pitch = pitch;
            this.duration = duration;
            this.stayDuration = stayDuration;
            this.blockDirection = blockDirection;
            this.rotateToNext = rotateToNext;
        }

        @Override
        public String toString() {
            return String.format(Locale.US, "CameraPointData{pos=%s, ch=%d, pos=%d, yaw=%.1f, pitch=%.1f, dur=%.1f, stay=%.1f, dir=%s, rotNext=%b}",
                    pos, channel, position, yaw, pitch, duration, stayDuration, blockDirection, rotateToNext);
        }
    }
}