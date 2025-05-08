package dev.backd00r.simplecinematic.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.backd00r.simplecinematic.server.CameraPointData;
import dev.backd00r.simplecinematic.server.CameraPointPersistentRegistry;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.List;

public class CameraPointsListCommand {

    // Método estándar de Fabric para registrar el comando
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(
                CommandManager.literal("camerapoints")
                        .then(CommandManager.literal("list")
                                .then(CommandManager.argument("channel", IntegerArgumentType.integer(1))
                                        .executes(ctx -> executeList(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "channel")))
                                )
                        )
        );
    }

    private static int executeList(ServerCommandSource source, int channel) {
        ServerWorld world = source.getWorld();
        List<CameraPointData> points = CameraPointPersistentRegistry.getPointsForChannel(world.getRegistryKey(), channel);

        if (points.isEmpty()) {
            source.sendFeedback(() -> Text.literal("§cNo se encontraron puntos de cámara registrados para el canal " + channel + " en este mundo."), false);
            return 0;
        }

        source.sendFeedback(() -> Text.literal("§aPuntos registrados para canal " + channel + ":"), false);
        for (CameraPointData point : points) {
            source.sendFeedback(() -> Text.literal(
                    String.format(" - Pos %d: %s [Yaw: %.1f, Pitch: %.1f, Dur: %.2fs, Stay: %.2fs, Dir: %s, RotNext: %b]",
                            point.position,
                            point.pos,
                            point.yaw,
                            point.pitch,
                            point.duration,
                            point.stayDuration,
                            point.blockDirection,
                            point.rotateToNext
                    )
            ), false);
        }
        return points.size();
    }

    // Esto te permite registrar el comando de forma directa con Fabric v2:

}