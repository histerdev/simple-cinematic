package dev.backd00r.simplecinematic;

import dev.backd00r.simplecinematic.block.CameraPointBlock;
import dev.backd00r.simplecinematic.block.entity.ModBlockEntities;
import dev.backd00r.simplecinematic.commands.CameraPointsListCommand;
import dev.backd00r.simplecinematic.commands.CinematicCommand;
import dev.backd00r.simplecinematic.networking.ServerNetworking;
import dev.backd00r.simplecinematic.server.ServerCameraPointRegistry;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class Simplecinematic implements ModInitializer {
	public static final String MOD_ID = "simplecinematic";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final Block CAMERA_POINT_BLOCK = new CameraPointBlock(AbstractBlock.Settings.copy(Blocks.BARRIER));

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		CommandRegistrationCallback.EVENT.register(CinematicCommand::register);

		Registry.register(Registries.BLOCK, new Identifier(MOD_ID, "camera_point"), CAMERA_POINT_BLOCK);
		Registry.register(Registries.ITEM, new Identifier(MOD_ID, "camera_point"),
				new BlockItem(CAMERA_POINT_BLOCK, new FabricItemSettings()));
		ServerNetworking.registerReceivers();
		CommandRegistrationCallback.EVENT.register(CameraPointsListCommand::register);

		ModBlockEntities.registerBlockEntities();
		LOGGER.info("Hello Fabric world!");
	}
}