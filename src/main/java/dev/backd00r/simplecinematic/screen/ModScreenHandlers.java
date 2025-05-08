package dev.backd00r.simplecinematic.screen;

import dev.backd00r.simplecinematic.Simplecinematic;
import dev.backd00r.simplecinematic.screen.CameraPointScreenHandler;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class ModScreenHandlers {
    public static ScreenHandlerType<CameraPointScreenHandler> CAMERA_POINT_SCREEN_HANDLER;

    public static void registerScreenHandlers() {
        // Solo registramos si aún no se ha registrado
        if (CAMERA_POINT_SCREEN_HANDLER == null) {
            CAMERA_POINT_SCREEN_HANDLER = Registry.register(
                    Registries.SCREEN_HANDLER,
                    new Identifier(Simplecinematic.MOD_ID, "camera_point"),
                    new ExtendedScreenHandlerType<>((syncId, inventory, buf) -> {
                        BlockPos pos = buf.readBlockPos();
                        int channel = buf.readInt();
                        int position = buf.readInt();
                        float pitch = buf.readFloat();
                        float yaw = buf.readFloat();
                        double duration = buf.readDouble();
                        double stayDuration = buf.readDouble(); // Leer stayDuration del buffer
                        boolean shouldRotateToNext = buf.readBoolean();
                        // Crear un nuevo PacketByteBuf para pasar estos datos al constructor del cliente
                        PacketByteBuf newBuf = PacketByteBufs.create();
                        newBuf.writeBlockPos(pos);
                        newBuf.writeInt(channel);
                        newBuf.writeInt(position);
                        newBuf.writeFloat(pitch);  // Comprobar el orden: los valores están en el orden correcto
                        newBuf.writeFloat(yaw);    // Comprobar el orden: los valores están en el orden correcto
                        newBuf.writeDouble(duration);
                        newBuf.writeDouble(stayDuration); // Incluir stayDuration en el nuevo buffer
                        newBuf.writeBoolean(shouldRotateToNext); // Incluir stayDuration en el nuevo buffer

                        return new CameraPointScreenHandler(syncId, inventory, newBuf);
                    })
            );
        }
    }
}