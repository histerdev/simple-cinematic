package dev.backd00r.simplecinematic.client.render;

import dev.backd00r.simplecinematic.block.entity.CameraPointBlockEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

@Environment(EnvType.CLIENT)
public class CameraPointBlockEntityRenderer implements BlockEntityRenderer<CameraPointBlockEntity> {
    private final BlockRenderManager blockRenderManager;

    public CameraPointBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
        this.blockRenderManager = ctx.getRenderManager();
    }

    @Override
    public void render(CameraPointBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {

        MinecraftClient client = MinecraftClient.getInstance();
        PlayerEntity player = client.player;

        // No renderizar nada si no hay jugador
        if (player == null) {
            return;
        }
        // Comprobamos si el jugador es OP o tiene el item en la mano
        boolean isOp = player.hasPermissionLevel(2);
        boolean hasItemInHand = player.getMainHandStack().isOf(entity.getCachedState().getBlock().asItem()) ||
                player.getOffHandStack().isOf(entity.getCachedState().getBlock().asItem());

        if (isOp || hasItemInHand) {
            BlockState blockState = entity.getCachedState();

            // Guardamos el estado de la matrix
            matrices.push();

            // Asegurarnos de que estamos renderizando con la capa correcta
            VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getSolid());

            // Renderizamos el modelo del bloque directamente
            blockRenderManager.getModelRenderer().render(
                    entity.getWorld(),
                    blockRenderManager.getModel(blockState),
                    blockState,
                    entity.getPos(),
                    matrices,
                    vertexConsumer,
                    true,  // randomizará el modelo si es necesario
                    Random.create(),
                    0,  // Usando 0 como semilla
                    overlay
            );

            // Restauramos el estado de la matrix
            matrices.pop();
        }

    }
}