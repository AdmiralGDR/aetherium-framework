/*
 * Aetherium Framework — NeoForge command bridge (EdgeCommands → Brigadier).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.loader;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.aetherium.edge.EdgeCommands;
import org.aetherium.edge.InteractionResult;
import org.aetherium.edge.PlayerHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Bridges the loader-agnostic {@link EdgeCommands} SPI onto NeoForge/Brigadier.
 *
 * <p>EN: Aetherium mods enqueue command registrations during init through {@link NeoForgePlatformBridge}'s
 * {@link EdgeCommands}; when NeoForge fires {@code RegisterCommandsEvent}, this bridge translates each one
 * into a Brigadier command tree — permission via {@code source.hasPermission(level)}, one required argument
 * per declared {@link EdgeCommands.ArgType}, and an {@code executes} handler that hands the tokenized args
 * to the mod. <strong>No Brigadier type crosses back into {@code aetherium-edge}</strong>; the mod only ever
 * sees {@code List<String>} + {@link PlayerHandle}. Every registration is wrapped so one malformed command
 * can never abort the rest.
 *
 * <p>RU: Переносит независимый от загрузчика SPI {@link EdgeCommands} на NeoForge/Brigadier. Моды ставят
 * регистрации в очередь при инициализации; на {@code RegisterCommandsEvent} мост строит дерево команд
 * Brigadier — права через {@code hasPermission(level)}, по одному аргументу на {@link EdgeCommands.ArgType},
 * и {@code executes}, передающий разобранные аргументы моду. Ни один тип Brigadier не возвращается в edge.
 */
public final class NeoForgeCommandBridge {

    private static final Logger LOG = LoggerFactory.getLogger("Aetherium");

    /** One queued command registration from a mod. */
    private record Registration(String name, EdgeCommands.CommandSpec spec, EdgeCommands.CommandHandler handler) {
    }

    private static final CopyOnWriteArrayList<Registration> QUEUE = new CopyOnWriteArrayList<>();

    /** Public for {@code NeoForge.EVENT_BUS.register(new NeoForgeCommandBridge())}. */
    public NeoForgeCommandBridge() {
    }

    /** The {@link EdgeCommands} implementation the bridge returns to mods; queues registrations. */
    static EdgeCommands commands() {
        return (name, spec, handler) -> QUEUE.add(new Registration(name, spec, handler));
    }

    /** Game-bus listener: translate every queued command into Brigadier. */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        for (Registration reg : QUEUE) {
            try {
                register(dispatcher, reg);
            } catch (Throwable t) {
                // A malformed command must never break registration of the others.
                LOG.warn("Aetherium command '/{}' failed to register; skipping ({}).", reg.name(), t.toString());
            }
        }
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher, Registration reg) {
        final EdgeCommands.CommandSpec spec = reg.spec();
        final List<EdgeCommands.ArgType> args = spec.args();

        final com.mojang.brigadier.Command<CommandSourceStack> exec = ctx -> run(ctx, reg);

        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(reg.name())
                .requires(src -> src.hasPermission(spec.permissionLevel()));

        if (args.isEmpty()) {
            root.executes(exec);
        } else {
            // Build the required-argument chain inside-out so `executes` sits on the deepest node.
            ArgumentBuilder<CommandSourceStack, ?> node =
                    Commands.argument("arg" + (args.size() - 1), typeFor(args.get(args.size() - 1))).executes(exec);
            for (int i = args.size() - 2; i >= 0; i--) {
                node = Commands.argument("arg" + i, typeFor(args.get(i))).then(node);
            }
            root.then(node);
        }
        dispatcher.register(root);
    }

    /** Invoke the mod handler with the tokenized args; PASS→1, CANCEL→0 (Brigadier success code). */
    private static int run(CommandContext<CommandSourceStack> ctx, Registration reg) throws CommandSyntaxException {
        List<String> tokens = new ArrayList<>();
        List<EdgeCommands.ArgType> args = reg.spec().args();
        for (int i = 0; i < args.size(); i++) {
            tokens.add(readArg(ctx, "arg" + i, args.get(i)));
        }
        ServerPlayer player = ctx.getSource().getPlayer();
        PlayerHandle sender = player == null ? null : new NeoForgePlayerHandle(player);
        InteractionResult result = reg.handler().run(sender, List.copyOf(tokens));
        return result == InteractionResult.CANCEL ? 0 : 1;
    }

    /** The Brigadier argument type for a declared {@link EdgeCommands.ArgType}. */
    private static ArgumentType<?> typeFor(EdgeCommands.ArgType type) {
        return switch (type) {
            case WORD -> StringArgumentType.word();
            case GREEDY_STRING -> StringArgumentType.greedyString();
            case INT -> IntegerArgumentType.integer();
            case DOUBLE -> DoubleArgumentType.doubleArg();
            case BOOL -> BoolArgumentType.bool();
            case PLAYER -> EntityArgument.player();
        };
    }

    /** Read one parsed argument back as a string token for the loader-agnostic handler. */
    private static String readArg(CommandContext<CommandSourceStack> ctx, String name, EdgeCommands.ArgType type)
            throws CommandSyntaxException {
        return switch (type) {
            case WORD, GREEDY_STRING -> StringArgumentType.getString(ctx, name);
            case INT -> String.valueOf(IntegerArgumentType.getInteger(ctx, name));
            case DOUBLE -> String.valueOf(DoubleArgumentType.getDouble(ctx, name));
            case BOOL -> String.valueOf(BoolArgumentType.getBool(ctx, name));
            case PLAYER -> EntityArgument.getPlayer(ctx, name).getName().getString();
        };
    }
}
