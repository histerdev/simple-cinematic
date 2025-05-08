package dev.backd00r.simplecinematic.datagen;

import dev.backd00r.simplecinematic.Simplecinematic;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricModelProvider;


import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.data.client.*;
import net.minecraft.data.family.BlockFamilies;
import net.minecraft.item.ArmorItem;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;

import java.util.Optional;

public class ModModelProvider extends FabricModelProvider {
    public ModModelProvider(FabricDataOutput output) {
        super(output);
    }

    @Override
    public void generateBlockStateModels(BlockStateModelGenerator blockStateModelGenerator) {
        blockStateModelGenerator.registerSimpleCubeAll(Simplecinematic.CAMERA_POINT_BLOCK);



        //   pinkGarnetTexturePool.stairs(ModBlocks.PINK_GARNET_STAIRS);

    }



    @Override
    public void generateItemModels(ItemModelGenerator itemModelGenerator) {


        // itemModelGenerator.register(ModItems.DATA_TABLET, Models.GENERATED);
    }
}