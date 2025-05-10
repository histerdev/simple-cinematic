package dev.backd00r.simplecinematic.networking;

import com.mojang.authlib.GameProfile;
import dev.backd00r.simplecinematic.Simplecinematic;
import dev.backd00r.simplecinematic.block.entity.CameraPointBlockEntity;
import dev.backd00r.simplecinematic.commands.CinematicCommand;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class ServerNetworking {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerNetworking.class);

    private static final Map<UUID, Set<ChunkPos>> playerRequestedCinematicChunks = new HashMap<>();
    // Sincro por canal
    private static final Map<Integer, Set<UUID>> pendingPlayersByChannel = new HashMap<>();
    private static final Map<Integer, List<CinematicCommand.CameraPointDataServer>> pathCacheByChannel = new HashMap<>();
    private static final Map<Integer, Integer> startPositionByChannel = new HashMap<>();
    private static final Map<Integer, List<UUID>> playerListByChannel = new HashMap<>();

    // TicketType para chunks de cinemática (usa comparator manual por ChunkPos no es Comparable en Vanilla)
    private static final net.minecraft.server.world.ChunkTicketType<ChunkPos> CINEMATIC_TICKET =
            net.minecraft.server.world.ChunkTicketType.create(
                    "cinematic",
                    (a, b) -> {
                        if (a.x != b.x) return Integer.compare(a.x, b.x);
                        return Integer.compare(a.z, b.z);
                    },
                    300
            );

    public static void registerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(ModPackets.UPDATE_CAMERA_POINT, ServerNetworking::handleUpdateCameraPoint);
        ServerPlayNetworking.registerGlobalReceiver(ModPackets.REQUEST_CINEMATIC_CHUNKS, ServerNetworking::handleRequestCinematicChunks);
        ServerPlayNetworking.registerGlobalReceiver(ModPackets.RELEASE_CINEMATIC_CHUNKS, ServerNetworking::handleReleaseCinematicChunks);
        ServerPlayNetworking.registerGlobalReceiver(ModPackets.CLIENT_CHUNKS_READY, ServerNetworking::handleClientChunksReady);
        LOGGER.info("Server Packet Receivers Registered.");
    }

    private static void handleUpdateCameraPoint(MinecraftServer server, ServerPlayerEntity player,
                                                ServerPlayNetworkHandler handler, PacketByteBuf buf, PacketSender responseSender) {
        try {
            BlockPos pos = buf.readBlockPos();
            int channel = buf.readInt();
            int position = buf.readInt();
            float yaw = buf.readFloat();
            float pitch = buf.readFloat();
            float roll = buf.readFloat(); // <--- ROLL después de pitch
            float shake = buf.readFloat(); // <-- SHAKE después de roll
            double duration = buf.readDouble();
            double stayDuration = buf.readDouble();
            boolean rotateToNext = buf.readBoolean();

            server.execute(() -> {
                ServerWorld world = player.getServerWorld();
                if (world.isChunkLoaded(pos)) {
                    BlockEntity blockEntity = world.getBlockEntity(pos);
                    if (blockEntity instanceof CameraPointBlockEntity cameraPoint) {
                        cameraPoint.setChannel(channel);
                        cameraPoint.setPosition(position);
                        cameraPoint.setYaw(yaw);
                        cameraPoint.setPitch(pitch);
                        cameraPoint.setRoll(roll);   // <--- Asigna roll
                        cameraPoint.setShake(shake); // <-- Asignar shake
                        cameraPoint.setDuration(duration);
                        cameraPoint.setStayDuration(stayDuration);
                        cameraPoint.setRotateToNext(rotateToNext);
                        LOGGER.debug("Updated CameraPoint at {} from player {}", pos, player.getName().getString());
                    } else {
                        LOGGER.warn("Player {} tried to update a non-CameraPointBlockEntity at {}", player.getName().getString(), pos);
                    }
                } else {
                    LOGGER.warn("Player {} failed to update CameraPoint at {} (Chunk Unloaded)", player.getName().getString(), pos);
                }
            });
        } catch (Exception e) {
            GameProfile profile = player.getGameProfile();
            String playerName = (profile != null) ? profile.getName() : "[Unknown Player]";
            Simplecinematic.LOGGER.error("Failed to handle UPDATE_CAMERA_POINT packet from player {}: {}", playerName, e.getMessage(), e);
        }
    }

    private static void handleRequestCinematicChunks(MinecraftServer server, ServerPlayerEntity player,
                                                     ServerPlayNetworkHandler handler, PacketByteBuf buf, PacketSender responseSender) {
        GameProfile profile = player.getGameProfile();
        String playerName = (profile != null) ? profile.getName() : "[Unknown Player]";
        try {
            int count = buf.readInt();
            if (count <= 0 || count > 1024) {
                LOGGER.warn("Player {} sent invalid chunk request count: {}", playerName, count);
                return;
            }

            List<ChunkPos> requestedPositions = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                requestedPositions.add(new ChunkPos(buf.readLong()));
            }

            UUID playerUuid = player.getUuid();
            synchronized (playerRequestedCinematicChunks) {
                playerRequestedCinematicChunks.computeIfAbsent(playerUuid, k -> new HashSet<>()).addAll(requestedPositions);
            }

            server.execute(() -> {
                ServerWorld world = player.getServerWorld();
                ServerChunkManager serverChunkManager = world.getChunkManager();

                LOGGER.debug("Player {} requested {} cinematic chunks.", playerName, requestedPositions.size());

                for (ChunkPos pos : requestedPositions) {
                    // Añade ticket cinemático
                    world.getChunkManager().addTicket(
                            CINEMATIC_TICKET, pos, 2, pos
                    );

                    // Fuerza carga FULL del chunk (asíncrono)
                    CompletableFuture<Chunk> chunkFuture = world.getChunkManager().getChunkFuture(
                            pos.x, pos.z, ChunkStatus.FULL, true
                    ).thenApply(either -> either.left().orElse(null));

                    chunkFuture.thenAccept(chunk -> {
                        if (chunk instanceof WorldChunk worldChunk && handler.isConnectionOpen()) {
                            server.execute(() -> {
                                try {
                                    handler.sendPacket(new net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket(
                                            worldChunk, world.getLightingProvider(), null, null));
                                    LOGGER.trace("Sent full chunk data for {} to {}", pos, playerName);
                                } catch (Exception ex) {
                                    LOGGER.error("Error sending chunk data packet for {} to {}: {}", pos, playerName, ex.getMessage(), ex);
                                }
                            });
                        } else if (chunk == null) {
                            LOGGER.warn("Chunk {} requested by {} was unloaded or failed to load via getChunkFuture.", pos, playerName);
                            synchronized (playerRequestedCinematicChunks) {
                                Set<ChunkPos> tracked = playerRequestedCinematicChunks.get(playerUuid);
                                if (tracked != null) {
                                    tracked.remove(pos);
                                    if (tracked.isEmpty()) {
                                        playerRequestedCinematicChunks.remove(playerUuid);
                                    }
                                }
                            }
                        }
                    });
                }
            });

        } catch (Exception e) {
            Simplecinematic.LOGGER.error("Failed to handle REQUEST_CINEMATIC_CHUNKS from player {}: {}", playerName, e.getMessage(), e);
        }
    }

    private static void handleReleaseCinematicChunks(MinecraftServer server, ServerPlayerEntity player,
                                                     ServerPlayNetworkHandler handler, PacketByteBuf buf, PacketSender responseSender) {
        GameProfile profile = player.getGameProfile();
        String playerName = (profile != null) ? profile.getName() : "[Unknown Player]";
        try {
            int count = buf.readInt();
            if (count <= 0 || count > 4096) {
                LOGGER.warn("Player {} sent invalid chunk release count: {}", playerName, count);
                return;
            }

            List<ChunkPos> releasedPositions = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                releasedPositions.add(new ChunkPos(buf.readLong()));
            }

            UUID playerUuid = player.getUuid();
            synchronized (playerRequestedCinematicChunks) {
                if (playerRequestedCinematicChunks.containsKey(playerUuid)) {
                    Set<ChunkPos> trackedChunks = playerRequestedCinematicChunks.get(playerUuid);
                    int removedCount = 0;
                    for (ChunkPos pos : releasedPositions) {
                        if (trackedChunks.remove(pos)) {
                            removedCount++;
                        }
                    }
                    LOGGER.debug("Player {} released {} tracked cinematic chunks ({} actual removals).", playerName, releasedPositions.size(), removedCount);
                    if (trackedChunks.isEmpty()) {
                        playerRequestedCinematicChunks.remove(playerUuid);
                        LOGGER.debug("Removed empty chunk tracking entry for player {}.", playerName);
                    }
                } else {
                    LOGGER.debug("Player {} released {} chunks, but no tracking entry found.", playerName, releasedPositions.size());
                }
            }

            server.execute(() -> {
                ServerWorld world = player.getServerWorld();
                // Libera tickets de chunks cinemáticos
                for (ChunkPos pos : releasedPositions) {
                    world.getChunkManager().removeTicket(
                            CINEMATIC_TICKET, pos, 2, pos
                    );
                }
            });

        } catch (Exception e) {
            Simplecinematic.LOGGER.error("Failed to handle RELEASE_CINEMATIC_CHUNKS from player {}: {}", playerName, e.getMessage(), e);
        }
    }

    private static void handleClientChunksReady(MinecraftServer server, ServerPlayerEntity player, ServerPlayNetworkHandler handler, PacketByteBuf buf, PacketSender responseSender) {
        int channel = buf.readInt();
        UUID uuid = player.getUuid();
        server.execute(() -> {
            Set<UUID> pending = pendingPlayersByChannel.get(channel);
            List<UUID> players = playerListByChannel.get(channel);
            if (pending != null && players != null) {
                pending.remove(uuid);
                LOGGER.info("Jugador {} listo para cinemática canal {}. Faltan: {}", player.getName().getString(), channel, pending.size());
                if (pending.isEmpty()) {
                    List<CinematicCommand.CameraPointDataServer> points = pathCacheByChannel.get(channel);
                    int startPos = startPositionByChannel.getOrDefault(channel, 1);
                    for (UUID plUUID : players) {
                        ServerPlayerEntity p = server.getPlayerManager().getPlayer(plUUID);
                        if (p != null) {
                            sendPlayCinematicStartPacket(p, channel, startPos, points);
                        }
                    }
                    // Limpiar estado
                    pendingPlayersByChannel.remove(channel);
                    pathCacheByChannel.remove(channel);
                    startPositionByChannel.remove(channel);
                    playerListByChannel.remove(channel);
                }
            }
        });
    }


    public static void sendPlayCinematicStartPacket(ServerPlayerEntity player, int channel, int startPosition, List<CinematicCommand.CameraPointDataServer> points) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(channel);
        buf.writeInt(startPosition);
        buf.writeInt(points.size());
        for (CinematicCommand.CameraPointDataServer point : points) {
            buf.writeBlockPos(point.pos);
            buf.writeInt(point.channel);
            buf.writeInt(point.position);
            buf.writeFloat(point.rawYaw);
            buf.writeFloat(point.rawPitch);
            buf.writeFloat(point.rawRoll);  // <--- ROLL
            buf.writeFloat(point.rawShake); // <-- SHAKE después de roll
            buf.writeDouble(point.duration);
            buf.writeDouble(point.stayDuration);
            buf.writeBoolean(point.useBlockFacing);
            buf.writeEnumConstant(point.blockFacing);
            buf.writeBoolean(point.rotateToNext);
        }
        ServerPlayNetworking.send(player, ModPackets.PLAY_CINEMATIC_START, buf);
    }
    public static void prepareCinematicForPlayers(ServerWorld world, int channel, int startPosition, Collection<ServerPlayerEntity> targets, List<CinematicCommand.CameraPointDataServer> points) {
        Set<UUID> pending = new HashSet<>();
        List<UUID> players = new ArrayList<>();
        for (ServerPlayerEntity player : targets) {
            pending.add(player.getUuid());
            players.add(player.getUuid());
        }
        pendingPlayersByChannel.put(channel, pending);
        pathCacheByChannel.put(channel, points);
        startPositionByChannel.put(channel, startPosition);
        playerListByChannel.put(channel, players);

        // Enviar a cada jugador el preload (ellos pedirán chunks y luego notificarán cuando estén listos)
        for (ServerPlayerEntity player : targets) {
            PacketByteBuf preload = PacketByteBufs.create();
            preload.writeInt(channel);
            preload.writeInt(startPosition);
            preload.writeInt(points.size());
            for (CinematicCommand.CameraPointDataServer point : points) {
                preload.writeBlockPos(point.pos);
                preload.writeInt(point.channel);
                preload.writeInt(point.position);
                preload.writeFloat(point.rawYaw);
                preload.writeFloat(point.rawPitch);
                preload.writeFloat(point.rawRoll);  // <--- ROLL
                preload.writeFloat(point.rawShake); // <-- SHAKE después de roll
                preload.writeDouble(point.duration);
                preload.writeDouble(point.stayDuration);
                preload.writeBoolean(point.useBlockFacing);
                preload.writeEnumConstant(point.blockFacing);
                preload.writeBoolean(point.rotateToNext);
            }
            ServerPlayNetworking.send(player, ModPackets.PLAY_CINEMATIC_PRELOAD, preload);
        }
    }
}