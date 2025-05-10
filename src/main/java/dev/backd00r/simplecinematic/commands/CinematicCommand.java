package dev.backd00r.simplecinematic.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import dev.backd00r.simplecinematic.Simplecinematic;
import dev.backd00r.simplecinematic.block.entity.CameraPointBlockEntity;
import dev.backd00r.simplecinematic.networking.ServerNetworking;
import dev.backd00r.simplecinematic.server.CameraPointData;
import dev.backd00r.simplecinematic.server.CameraPointPersistentRegistry;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.*;
import java.util.stream.Collectors;

public class CinematicCommand {

    private static final int DEFAULT_START_POSITION = 1;

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess access, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(
                CommandManager.literal("cinematic")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.literal("play")
                                .then(CommandManager.argument("channel", IntegerArgumentType.integer(1))
                                        .executes(context -> executePlay(context, IntegerArgumentType.getInteger(context, "channel"), getDefaultTargets(context), DEFAULT_START_POSITION))
                                        .then(CommandManager.argument("targets", EntityArgumentType.players())
                                                .executes(context -> executePlay(context, IntegerArgumentType.getInteger(context, "channel"), EntityArgumentType.getPlayers(context, "targets"), DEFAULT_START_POSITION))
                                                .then(CommandManager.argument("start_position", IntegerArgumentType.integer(1))
                                                        .executes(context -> executePlay(context, IntegerArgumentType.getInteger(context, "channel"), EntityArgumentType.getPlayers(context, "targets"), IntegerArgumentType.getInteger(context, "start_position")))
                                                )
                                        )
                                        .then(CommandManager.argument("start_position", IntegerArgumentType.integer(1))
                                                .executes(context -> executePlay(context, IntegerArgumentType.getInteger(context, "channel"), getDefaultTargets(context), IntegerArgumentType.getInteger(context, "start_position")))
                                        )
                                )
                        )
        );
    }

    private static Collection<ServerPlayerEntity> getDefaultTargets(CommandContext<ServerCommandSource> context) {
        try {
            return Collections.singletonList(context.getSource().getPlayerOrThrow());
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("Este comando debe ser ejecutado por un jugador si no se especifican objetivos."));
            return Collections.emptyList();
        }
    }

    private static int executePlay(CommandContext<ServerCommandSource> context, int channel, Collection<ServerPlayerEntity> targets, int startPosition) {
        ServerCommandSource source = context.getSource();
        ServerWorld world = source.getWorld();
        if (targets == null || targets.isEmpty()) return 0;

        // Nuevo: usa el registro persistente
        List<CameraPointData> pointsFromRegistry = CameraPointPersistentRegistry.getPointsForChannel(world.getRegistryKey(), channel);
        if (pointsFromRegistry.isEmpty()) {
            source.sendFeedback(() -> Text.literal("§cNo se encontraron puntos de cámara registrados para el canal " + channel + " en este mundo."), false);
            return 0;
        }

        List<CameraPointDataServer> allPoints = pointsFromRegistry.stream()
                .map(data -> new CameraPointDataServer(
                        data.pos, data.channel, data.position,
                        data.yaw, data.pitch, data.roll, data.shake, data.duration, data.stayDuration,
                        data.blockDirection != null ? false : true, // o data.useBlockFacing si existe
                        data.blockDirection,
                        data.rotateToNext
                ))
                .collect(Collectors.toList());

        List<CameraPointDataServer> pointsToSend = allPoints.stream()
                .filter(p -> p.position >= startPosition)
                .collect(Collectors.toList());

        ServerNetworking.prepareCinematicForPlayers(world, channel, startPosition, targets, pointsToSend);

        String targetNames = targets.stream().map(p -> p.getName().getString()).collect(Collectors.joining(", "));
        source.sendFeedback(() -> Text.literal(String.format(
                "Cinemática preparada para canal %d (desde pos %d) para %d jugador(es): %s. Esperando chunks...",
                channel, startPosition, targets.size(), targetNames)), true);

        return targets.size();
    }

    public static class CameraPointDataServer {
        public final BlockPos pos;
        public final int channel;
        public final int position;
        public final float rawYaw;
        public final float rawPitch;
        public final float rawRoll;
        public final float rawShake;
        public final double duration;
        public final double stayDuration;
        public final boolean useBlockFacing;
        public final Direction blockFacing;
        public final boolean rotateToNext;

        public CameraPointDataServer(BlockPos pos, int channel, int position, float rawYaw, float rawPitch, float rawRoll, float rawShake,
                                     double duration, double stayDuration, boolean useBlockFacing, Direction blockFacing, boolean rotateToNext) {
            this.pos = pos;
            this.channel = channel;
            this.position = position;
            this.rawYaw = rawYaw;
            this.rawPitch = rawPitch;
            this.rawRoll = rawRoll;
            this.rawShake = rawShake;
            this.duration = duration;
            this.stayDuration = stayDuration;
            this.useBlockFacing = useBlockFacing;
            this.blockFacing = blockFacing;
            this.rotateToNext = rotateToNext;
        }
    }
}