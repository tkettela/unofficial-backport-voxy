package me.cortex.voxy.commonImpl.network;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.Locale;

//In game control of the server side LoD features: /voxylod status|config|generation|reload|cache (operators only)
final class ServerLodCommand {
    private ServerLodCommand() {}

    //The command uses the same names as config/voxy-server.json
    static String toFileName(String field) {
        var out = new StringBuilder();
        for (char c : field.toCharArray()) {
            if (Character.isUpperCase(c)) out.append('_').append(Character.toLowerCase(c));
            else out.append(c);
        }
        return out.toString();
    }

    static String toFieldName(String fileName) {
        for (var field : ServerLodConfig.EDITABLE) {
            if (toFileName(field).equals(fileName.toLowerCase(Locale.ROOT)) || field.equals(fileName)) return field;
        }
        return null;
    }

    private static final SuggestionProvider<CommandSourceStack> KEYS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(ServerLodConfig.EDITABLE.stream().map(ServerLodCommand::toFileName), builder);

    private static final SuggestionProvider<CommandSourceStack> VALUES = (ctx, builder) -> {
        var field = toFieldName(StringArgumentType.getString(ctx, "setting"));
        if (field != null && ServerLodConfig.CONFIG.isBoolean(field)) {
            return SharedSuggestionProvider.suggest(new String[]{"true", "false"}, builder);
        }
        if (field != null) {
            return SharedSuggestionProvider.suggest(new String[]{String.valueOf(ServerLodConfig.CONFIG.get(field))}, builder);
        }
        return builder.buildFuture();
    };

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("voxylod")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status").executes(ServerLodCommand::status))
                .then(Commands.literal("config")
                        .executes(ServerLodCommand::listConfig)
                        .then(Commands.argument("setting", StringArgumentType.word()).suggests(KEYS)
                                .executes(ServerLodCommand::getConfig)
                                .then(Commands.argument("value", StringArgumentType.word()).suggests(VALUES)
                                        .executes(ServerLodCommand::setConfig))))
                .then(Commands.literal("generation")
                        .then(Commands.literal("pause").executes(ctx -> setAndReport(ctx, "autoGenerate", "false")))
                        .then(Commands.literal("resume").executes(ctx -> setAndReport(ctx, "autoGenerate", "true"))))
                .then(Commands.literal("reload").executes(ServerLodCommand::reload))
                .then(Commands.literal("cache").then(Commands.literal("clear").executes(ServerLodCommand::clearCache))));
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        for (var line : ServerLodStreamer.statusLines(ctx.getSource().getServer())) {
            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    private static int listConfig(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("Voxy server settings (/voxylod config <setting> <value>):").withStyle(ChatFormatting.GOLD), false);
        for (var field : ServerLodConfig.EDITABLE) {
            var value = ServerLodConfig.CONFIG.get(field);
            ctx.getSource().sendSuccess(() -> Component.literal(" " + toFileName(field) + " = ").append(Component.literal(String.valueOf(value)).withStyle(ChatFormatting.AQUA)), false);
        }
        return 1;
    }

    private static int getConfig(CommandContext<CommandSourceStack> ctx) {
        var field = toFieldName(StringArgumentType.getString(ctx, "setting"));
        if (field == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown setting, use /voxylod config to list them"));
            return 0;
        }
        var value = ServerLodConfig.CONFIG.get(field);
        ctx.getSource().sendSuccess(() -> Component.literal(toFileName(field) + " = " + value), false);
        return 1;
    }

    private static int setConfig(CommandContext<CommandSourceStack> ctx) {
        var field = toFieldName(StringArgumentType.getString(ctx, "setting"));
        if (field == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown setting, use /voxylod config to list them"));
            return 0;
        }
        return setAndReport(ctx, field, StringArgumentType.getString(ctx, "value"));
    }

    private static int setAndReport(CommandContext<CommandSourceStack> ctx, String field, String value) {
        try {
            var stored = ServerLodConfig.CONFIG.set(field, value);
            ServerLodStreamer.onConfigChanged(ctx.getSource().getServer());
            ctx.getSource().sendSuccess(() -> Component.literal("Voxy: " + toFileName(field) + " = " + stored + " (applied and saved)"), true);
            return 1;
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal("Voxy: " + e.getMessage()));
            return 0;
        }
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        ServerLodConfig.reload();
        ServerLodStreamer.onConfigChanged(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal("Voxy: config/voxy-server.json reloaded and applied"), true);
        return 1;
    }

    private static int clearCache(CommandContext<CommandSourceStack> ctx) {
        ServerLodStreamer.clearTimestampCache();
        ctx.getSource().sendSuccess(() -> Component.literal("Voxy: server LoD cache cleared, chunks will be read from disk again"), true);
        return 1;
    }
}
