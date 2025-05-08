package dev.backd00r.simplecinematic.server;

import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CameraPointPersistentRegistry {
    private static final Map<RegistryKey<World>, Map<Integer, Map<Integer, CameraPointData>>> persistentData = new ConcurrentHashMap<>();

    // Guardar/Actualizar datos
    public static void registerOrUpdate(CameraPointData data, RegistryKey<World> worldKey) {
        persistentData
                .computeIfAbsent(worldKey, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(data.channel, k -> new ConcurrentHashMap<>())
                .put(data.position, data);
    }

    // Borrar un punto
    public static void unregister(RegistryKey<World> worldKey, int channel, int position) {
        Map<Integer, Map<Integer, CameraPointData>> worldMap = persistentData.get(worldKey);
        if (worldMap != null) {
            Map<Integer, CameraPointData> channelMap = worldMap.get(channel);
            if (channelMap != null) {
                channelMap.remove(position);
                if (channelMap.isEmpty()) worldMap.remove(channel);
            }
            if (worldMap.isEmpty()) persistentData.remove(worldKey);
        }
    }

    // Leer puntos de un canal
    public static List<CameraPointData> getPointsForChannel(RegistryKey<World> worldKey, int channel) {
        Map<Integer, Map<Integer, CameraPointData>> worldMap = persistentData.get(worldKey);
        if (worldMap == null) return Collections.emptyList();
        Map<Integer, CameraPointData> channelMap = worldMap.get(channel);
        if (channelMap == null) return Collections.emptyList();
        return channelMap.values().stream().sorted(Comparator.comparingInt(p -> p.position)).toList();
    }
}