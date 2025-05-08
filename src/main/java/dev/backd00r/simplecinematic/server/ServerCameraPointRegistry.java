package dev.backd00r.simplecinematic.server;

import com.google.gson.*;
import dev.backd00r.simplecinematic.Simplecinematic;
import dev.backd00r.simplecinematic.block.entity.CameraPointBlockEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ServerCameraPointRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerCameraPointRegistry.class);

    // Cambiar la clave del mapa de UUID a RegistryKey<World>
    private static final Map<RegistryKey<World>, Map<Integer, Map<Integer, WeakReference<CameraPointBlockEntity>>>> registry = new ConcurrentHashMap<>();

    public static void register(CameraPointBlockEntity be) {
        World world = be.getWorld();
        if (world == null || world.isClient || be.getPos() == null) return;

        // Usar getRegistryKey() en lugar de getUuid()
        RegistryKey<World> worldKey = world.getRegistryKey();
        int channel = be.getChannel();
        int position = be.getPosition();
        BlockPos pos = be.getPos();

        registry.computeIfAbsent(worldKey, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(channel, k -> new ConcurrentHashMap<>())
                .put(position, new WeakReference<>(be));

        // Log opcional (usando el valor de la RegistryKey)
        // LOGGER.debug("Registered CameraPoint: World={}, Pos={}, Channel={}, Position={}", worldKey.getValue(), pos, channel, position);
    }

    public static void unregister(CameraPointBlockEntity be) {
        World world = be.getWorld();
        if (world == null || world.isClient || be.getPos() == null) return;

        // Usar getRegistryKey() en lugar de getUuid()
        RegistryKey<World> worldKey = world.getRegistryKey();
        int channel = be.getChannel();
        int position = be.getPosition();
        BlockPos pos = be.getPos();

        Map<Integer, Map<Integer, WeakReference<CameraPointBlockEntity>>> worldRegistry = registry.get(worldKey);
        if (worldRegistry != null) {
            Map<Integer, WeakReference<CameraPointBlockEntity>> channelRegistry = worldRegistry.get(channel);
            if (channelRegistry != null) {
                WeakReference<CameraPointBlockEntity> existingRef = channelRegistry.get(position);
                if (existingRef != null && existingRef.get() == be) {
                    channelRegistry.remove(position);
                    // Log opcional
                    // LOGGER.debug("Unregistered CameraPoint: World={}, Pos={}, Channel={}, Position={}", worldKey.getValue(), pos, channel, position);
                    if (channelRegistry.isEmpty()) {
                        worldRegistry.remove(channel);
                        // LOGGER.debug("Removed empty channel registry for channel {}", channel);
                        if (worldRegistry.isEmpty()) {
                            registry.remove(worldKey); // Usar worldKey para eliminar
                            // LOGGER.debug("Removed empty world registry for world {}", worldKey.getValue());
                        }
                    }
                } else if (existingRef != null && existingRef.get() != null) {
                    LOGGER.warn("Unregister mismatch: Found different BE at World={}, Pos={}, Ch={}, Pos={}", worldKey.getValue(), pos, channel, position);
                }
            }
        }
    }

    public static List<CameraPointBlockEntity> getPointsForChannel(ServerWorld world, int channel) {
        // Usar getRegistryKey() en lugar de getUuid()
        RegistryKey<World> worldKey = world.getRegistryKey();
        Map<Integer, Map<Integer, WeakReference<CameraPointBlockEntity>>> worldRegistry = registry.get(worldKey);

        if (worldRegistry == null || !worldRegistry.containsKey(channel)) {
            return Collections.emptyList();
        }

        return worldRegistry.get(channel).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getValue().get())
                .filter(Objects::nonNull)
                .filter(be -> be.getWorld() != null && !be.isRemoved())
                .collect(Collectors.toList());
    }

    public static void cleanUp() {
        registry.values().forEach(worldMap ->
                worldMap.values().forEach(channelMap ->
                        channelMap.entrySet().removeIf(entry -> entry.getValue().get() == null)
                )
        );
        // LOGGER.debug("Cleaned up registry.");
    }
}