package dev.backd00r.simplecinematic.networking;

import dev.backd00r.simplecinematic.Simplecinematic;
import net.minecraft.util.Identifier;

public class ModPackets {
    public static final Identifier UPDATE_CAMERA_POINT = new Identifier(Simplecinematic.MOD_ID, "update_camera_point");
    public static final Identifier PLAY_CINEMATIC_PRELOAD = new Identifier(Simplecinematic.MOD_ID, "play_cinematic_preload");
    public static final Identifier CLIENT_CHUNKS_READY = new Identifier(Simplecinematic.MOD_ID, "client_cinematic_chunks_ready");
    public static final Identifier PLAY_CINEMATIC_START = new Identifier(Simplecinematic.MOD_ID, "play_cinematic_start");
    public static final Identifier REQUEST_CINEMATIC_CHUNKS = new Identifier(Simplecinematic.MOD_ID, "request_cinematic_chunks");
    public static final Identifier RELEASE_CINEMATIC_CHUNKS = new Identifier(Simplecinematic.MOD_ID, "release_cinematic_chunks");
}