package dev.backd00r.simplecinematic.networking;

// ... otros imports ...
import dev.backd00r.simplecinematic.Simplecinematic;
import dev.backd00r.simplecinematic.block.entity.CameraPointBlockEntity;
import dev.backd00r.simplecinematic.manager.CameraManager; // Asegúrate que esté importado
import dev.backd00r.simplecinematic.manager.CameraPathManager; // Asegúrate que esté importado
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective; // Asegúrate que esté importado
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier; // Asegúrate que esté importado
import net.minecraft.util.math.BlockPos; // Asegúrate que esté importado
import net.minecraft.util.math.Direction; // Asegúrate que esté importado
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
// ... otros imports ...

@Environment(EnvType.CLIENT)
public class ClientPackets {

    // Executor para polling de chunks
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor();

    public static void registerPackets() {

        // PRELOAD: Recibe la ruta, la registra y espera chunks
        ClientPlayNetworking.registerGlobalReceiver(ModPackets.PLAY_CINEMATIC_PRELOAD, (client, handler, buf, responseSender) -> {
            try {
                int channel = buf.readInt();
                int startPosition = buf.readInt();
                int numPoints = buf.readInt();
                List<CameraPathManager.CameraPointData> receivedPoints = new ArrayList<>(numPoints);
                for (int i = 0; i < numPoints; i++) {
                    BlockPos pos = buf.readBlockPos();
                    int pointChannel = buf.readInt();
                    int pointPosition = buf.readInt();
                    float rawYaw = buf.readFloat();
                    float rawPitch = buf.readFloat();
                    double duration = buf.readDouble();
                    double stayDuration = buf.readDouble();
                    boolean useBlockFacing = buf.readBoolean();
                    Direction blockFacing = buf.readEnumConstant(Direction.class);
                    boolean rotateToNext = buf.readBoolean();
                    receivedPoints.add(new CameraPathManager.CameraPointData(
                            pos.toImmutable(), pointChannel, pointPosition, rawYaw, rawPitch, duration, stayDuration, blockFacing, rotateToNext
                    ));
                }
                client.execute(() -> {
                    CameraPathManager.clearChannelPath(channel);
                    for (CameraPathManager.CameraPointData point : receivedPoints) {
                        CameraPathManager.registerCameraPoint(point.pos, point.channel, point.position, point.yaw, point.pitch, point.duration, point.stayDuration, point.blockDirection, point.rotateToNext);
                    }
                    CameraPathManager.requestNeededChunks(true);
                    waitForChunksAndNotifyServer(client, channel);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // START: Recibe la señal de arranque sincronizado
        ClientPlayNetworking.registerGlobalReceiver(ModPackets.PLAY_CINEMATIC_START, (client, handler, buf, responseSender) -> {
            int channel = buf.readInt();
            int startPosition = buf.readInt();
            int numPoints = buf.readInt();
            List<CameraPathManager.CameraPointData> receivedPoints = new ArrayList<>(numPoints);
            for (int i = 0; i < numPoints; i++) {
                BlockPos pos = buf.readBlockPos();
                int pointChannel = buf.readInt();
                int pointPosition = buf.readInt();
                float rawYaw = buf.readFloat();
                float rawPitch = buf.readFloat();
                double duration = buf.readDouble();
                double stayDuration = buf.readDouble();
                boolean useBlockFacing = buf.readBoolean();
                Direction blockFacing = buf.readEnumConstant(Direction.class);
                boolean rotateToNext = buf.readBoolean();
                receivedPoints.add(new CameraPathManager.CameraPointData(
                        pos.toImmutable(), pointChannel, pointPosition, rawYaw, rawPitch, duration, stayDuration, blockFacing, rotateToNext
                ));
            }
            client.execute(() -> {
                CameraPathManager.clearChannelPath(channel);
                for (CameraPathManager.CameraPointData point : receivedPoints) {
                    CameraPathManager.registerCameraPoint(point.pos, point.channel, point.position, point.yaw, point.pitch, point.duration, point.stayDuration, point.blockDirection, point.rotateToNext);
                }
                CameraPathManager.playPathFromPosition(channel, startPosition);
            });
        });


        // --- REMOVED ---
        // ClientPlayNetworking.registerGlobalReceiver(ModPackets.PLAY_CINEMATIC, ... ) // Replaced by PREPARE/START


    }

    private static void waitForChunksAndNotifyServer(MinecraftClient client, int channel) {
        client.execute(() -> {
            if (CameraPathManager.checkInitialChunksLoaded()) {
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeInt(channel);
                ClientPlayNetworking.send(ModPackets.CLIENT_CHUNKS_READY, buf);
            } else {
                SCHEDULER.schedule(() -> waitForChunksAndNotifyServer(client, channel), 200, TimeUnit.MILLISECONDS);
            }
        });
        // --- Sending Packets ---
    }
}