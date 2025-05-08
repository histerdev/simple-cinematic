package dev.backd00r.simplecinematic;

import dev.backd00r.simplecinematic.block.entity.ModBlockEntities;
import dev.backd00r.simplecinematic.client.render.CameraPointBlockEntityRenderer;
import dev.backd00r.simplecinematic.manager.CameraManager;
import dev.backd00r.simplecinematic.manager.CameraPathManager;
import dev.backd00r.simplecinematic.networking.ClientPackets;
import dev.backd00r.simplecinematic.screen.CameraPointScreen;
import dev.backd00r.simplecinematic.screen.ModScreenHandlers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;

public class SimplecinematicClient implements ClientModInitializer {


    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(CameraPathManager::onClientTick);
        BlockEntityRendererFactories.register(ModBlockEntities.CAMERA_POINT_BLOCK_ENTITY, CameraPointBlockEntityRenderer::new);
        ModScreenHandlers.registerScreenHandlers();
        HandledScreens.register(ModScreenHandlers.CAMERA_POINT_SCREEN_HANDLER, CameraPointScreen::new);
        ClientPackets.registerPackets();
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

    }
    private void onClientTick(MinecraftClient client) {
        // Este código se ejecuta una vez por tick de juego en el cliente

        // Solo ejecutar si el mundo y el jugador existen
        if (client.world == null || client.player == null) {
            // Si estamos en una pantalla sin mundo (menú principal),
            // podríamos querer resetear CameraManager por si acaso.
            if (CameraManager.isCameraMoving() || CameraPathManager.isPlayingPath()) {
                Simplecinematic.LOGGER.warn("Client tick without world/player, but camera manager or path was active. Resetting.");
                CameraPathManager.clearAllPaths(); // Esto llama a CameraManager.resetCamera()
            }
            return;
        }

        // Obtener la cámara del GameRenderer
        Camera camera = client.gameRenderer.getCamera();
        if (camera == null) {
            // No debería pasar si hay un mundo, pero por si acaso
            return;
        }

        try {
            // Actualizar la lógica de la cinemática UNA VEZ POR TICK
            // Importante: Pasar tickDelta como 1.0f porque sabemos que es un tick completo
            CameraManager.updateCameraPosition(camera, 1.0f);
            CameraPathManager.updatePathPlayback();

        } catch (Exception e) {
            // Capturar cualquier error inesperado durante la actualización
            Simplecinematic.LOGGER.error("Exception during Carrozatest tick update:", e);
            // Considera resetear el estado si ocurre un error grave
            CameraPathManager.clearAllPaths();
        }
    }
}