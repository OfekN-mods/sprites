package com.ofekn.mcsprites;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpritesClient implements ClientModInitializer {
	public static final String MOD_ID = "sprites";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		LOGGER.info("Hello Fabric Client world!");
		ClientCommandRegistrationCallback.EVENT.register(this::registerCommands);
	}

	private void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext ctx) {
		dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("built_item_icons").executes(this::buildItemIcons));
	}

	private int buildItemIcons(CommandContext<FabricClientCommandSource> ctx) {
		ctx.getSource().sendFeedback(Component.literal("Creating..."));
		IconBuilder.SHOULD_BUILD.getAndSet(true);
		return 1;
	}

}