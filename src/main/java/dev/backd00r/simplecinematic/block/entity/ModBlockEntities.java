package dev.backd00r.simplecinematic.block.entity;

import dev.backd00r.simplecinematic.Simplecinematic;
import dev.backd00r.simplecinematic.block.entity.CameraPointBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModBlockEntities {
    public static BlockEntityType<CameraPointBlockEntity> CAMERA_POINT_BLOCK_ENTITY;

    public static void registerBlockEntities() {
        CAMERA_POINT_BLOCK_ENTITY = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                new Identifier(Simplecinematic.MOD_ID, "camera_point_block_entity"),
                FabricBlockEntityTypeBuilder.create(
                        CameraPointBlockEntity::new,
                        Simplecinematic.CAMERA_POINT_BLOCK
                ).build()
        );
    }
}