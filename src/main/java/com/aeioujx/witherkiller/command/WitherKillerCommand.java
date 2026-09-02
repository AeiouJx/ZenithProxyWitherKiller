package com.aeioujx.witherkiller.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;
import com.aeioujx.witherkiller.WitherKillerConfig;
import com.aeioujx.witherkiller.WitherKillerPlugin;
import com.aeioujx.witherkiller.module.WitherKillerModule;

import java.util.ArrayList;
import java.util.Locale;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class WitherKillerCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("witherKiller")
            .category(CommandCategory.MODULE)
            .description("""
                Automatically holds soul sand placement toward a fixed target and enables KillAura to kill nearby withers.
                """)
            .usageLines(
                "on/off",
                "captureTarget",
                "target <x> <y> <z>",
                "range <blocks>",
                "requiredWithers <count>",
                "interval <ticks>",
                "spawnWait <ticks>",
                "fightTimeout <ticks>",
                "protect add <blockName>",
                "protect remove <blockName>",
                "protect list",
                "protect reset"
            )
            .aliases("wk")
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("witherKiller")
            .then(argument("toggle", toggle()).executes(c -> {
                if (getToggle(c, "toggle") && !WitherKillerPlugin.PLUGIN_CONFIG.witherKiller.targetConfigured) {
                    c.getSource().getEmbed()
                        .title("Error")
                        .errorColor()
                        .description("Capture or set a target before enabling wither killer.");
                    return ERROR;
                }
                WitherKillerPlugin.PLUGIN_CONFIG.witherKiller.enabled = getToggle(c, "toggle");
                com.zenith.Globals.MODULE.get(WitherKillerModule.class).syncEnabledFromConfig();
                c.getSource().getEmbed()
                    .title("Wither Killer " + toggleStrCaps(WitherKillerPlugin.PLUGIN_CONFIG.witherKiller.enabled))
                    .primaryColor();
                return OK;
            }))
            .then(literal("captureTarget").executes(c -> {
                boolean captured = com.zenith.Globals.MODULE.get(WitherKillerModule.class).captureTarget();
                if (!captured) {
                    c.getSource().getEmbed()
                        .title("Error")
                        .errorColor()
                        .description("Current crosshair is not hitting a block within reach.");
                    return ERROR;
                }
                c.getSource().getEmbed()
                    .title("Target Captured")
                    .description("Saved the current crosshair hit point as the placement target.")
                    .primaryColor();
                return OK;
            }))
            .then(literal("target")
                .then(argument("x", integer(-30000000, 30000000))
                    .then(argument("y", integer(-2048, 2048))
                        .then(argument("z", integer(-30000000, 30000000)).executes(c -> {
                            WitherKillerPlugin.PLUGIN_CONFIG.witherKiller.targetX = getInteger(c, "x");
                            WitherKillerPlugin.PLUGIN_CONFIG.witherKiller.targetY = getInteger(c, "y");
                            WitherKillerPlugin.PLUGIN_CONFIG.witherKiller.targetZ = getInteger(c, "z");
                            WitherKillerPlugin.PLUGIN_CONFIG.witherKiller.targetConfigured = true;
                            c.getSource().getEmbed()
                                .title("Target Set")
                                .primaryColor();
                            return OK;
                        })))))
            .then(literal("range").then(argument("blocks", integer(1, 128)).executes(c -> {
                WitherKillerPlugin.PLUGIN_CONFIG.witherKiller.witherDetectionRange = getInteger(c, "blocks");
                c.getSource().getEmbed()
                    .title("Wither Detection Range Set")
                    .primaryColor();
                return OK;
            })))
            .then(literal("requiredWithers").then(argument("count", integer(1, 24)).executes(c -> {
                WitherKillerPlugin.PLUGIN_CONFIG.witherKiller.requiredWithersBeforeFight = getInteger(c, "count");
                c.getSource().getEmbed()
                    .title("Required Withers Set")
                    .primaryColor();
                return OK;
            })))
            .then(literal("interval").then(argument("ticks", integer(1, 20)).executes(c -> {
                WitherKillerPlugin.PLUGIN_CONFIG.witherKiller.placementIntervalTicks = getInteger(c, "ticks");
                c.getSource().getEmbed()
                    .title("Placement Interval Set")
                    .primaryColor();
                return OK;
            })))
            .then(literal("spawnWait").then(argument("ticks", integer(0, 200)).executes(c -> {
                WitherKillerPlugin.PLUGIN_CONFIG.witherKiller.witherSpawnWaitTicks = getInteger(c, "ticks");
                c.getSource().getEmbed()
                    .title("Spawn Wait Set")
                    .primaryColor();
                return OK;
            })))
            .then(literal("fightTimeout").then(argument("ticks", integer(0, 1200)).executes(c -> {
                WitherKillerPlugin.PLUGIN_CONFIG.witherKiller.fightStartTimeoutTicks = getInteger(c, "ticks");
                c.getSource().getEmbed()
                    .title("Fight Timeout Set")
                    .primaryColor();
                return OK;
            })))
            .then(literal("protect")
                .then(literal("add").then(argument("blockName", word()).executes(c -> {
                    String blockName = getString(c, "blockName").trim().toUpperCase(Locale.ROOT);
                    if (WitherKillerModule.resolveBlockByName(blockName) == null) {
                        c.getSource().getEmbed()
                            .title("Unknown Block")
                            .errorColor()
                            .description("Unknown block name: " + blockName);
                        return ERROR;
                    }
                    var blocks = WitherKillerPlugin.PLUGIN_CONFIG.witherKiller.protectedBlocks;
                    if (!blocks.contains(blockName)) {
                        blocks.add(blockName);
                    }
                    c.getSource().getEmbed()
                        .title("Protected Block Added")
                        .description(blockName)
                        .primaryColor();
                    return OK;
                })))
                .then(literal("remove").then(argument("blockName", word()).executes(c -> {
                    String blockName = getString(c, "blockName").trim().toUpperCase(Locale.ROOT);
                    boolean removed = WitherKillerPlugin.PLUGIN_CONFIG.witherKiller.protectedBlocks.remove(blockName);
                    if (!removed) {
                        c.getSource().getEmbed()
                            .title("Protected Block Not Found")
                            .errorColor()
                            .description(blockName);
                        return ERROR;
                    }
                    c.getSource().getEmbed()
                        .title("Protected Block Removed")
                        .description(blockName)
                        .primaryColor();
                    return OK;
                })))
                .then(literal("list").executes(c -> {
                    var blocks = WitherKillerPlugin.PLUGIN_CONFIG.witherKiller.protectedBlocks;
                    c.getSource().getEmbed()
                        .title("Protected Blocks")
                        .description(blocks.isEmpty() ? "(empty)" : String.join(", ", blocks))
                        .primaryColor();
                    return OK;
                }))
                .then(literal("reset").executes(c -> {
                    WitherKillerPlugin.PLUGIN_CONFIG.witherKiller.protectedBlocks = new ArrayList<>(
                        WitherKillerConfig.WitherConfig.DEFAULT_PROTECTED_BLOCKS
                    );
                    c.getSource().getEmbed()
                        .title("Protected Blocks Reset")
                        .description(String.join(", ", WitherKillerPlugin.PLUGIN_CONFIG.witherKiller.protectedBlocks))
                        .primaryColor();
                    return OK;
                })));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        var config = WitherKillerPlugin.PLUGIN_CONFIG.witherKiller;
        embed
            .primaryColor()
            .addField("Enabled", toggleStr(config.enabled))
            .addField("Target Configured", toggleStr(config.targetConfigured))
            .addField("Target X", config.targetX)
            .addField("Target Y", config.targetY)
            .addField("Target Z", config.targetZ)
            .addField("Range", config.witherDetectionRange + " blocks")
            .addField("Required Withers", config.requiredWithersBeforeFight)
            .addField("Placement Interval", config.placementIntervalTicks + " ticks")
            .addField("Spawn Wait", config.witherSpawnWaitTicks + " ticks")
            .addField("Fight Timeout", config.fightStartTimeoutTicks + " ticks")
            .addField("Protected Blocks", config.protectedBlocks == null || config.protectedBlocks.isEmpty()
                ? "(empty)"
                : String.join(", ", config.protectedBlocks));
    }
}
