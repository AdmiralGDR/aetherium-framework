/*
 * Aetherium Framework — PAL command registration SPI (loader-agnostic).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.edge;

import java.util.List;

/**
 * Loader-agnostic command registration — the missing surface a server mod needs to expose {@code /commands}.
 *
 * <p>EN: A server-side Aetherium mod registers a command by name with a {@link CommandSpec} (permission +
 * argument shape) and a {@link CommandHandler}; the loader translates each registration into the platform's
 * command system (Brigadier on NeoForge) <em>without leaking a single Brigadier type across this boundary</em>.
 * Arguments arrive already tokenized; the loader supplies the sender's permission level. This unblocks the
 * whole category of admin features ({@code /faction}, {@code /reload}, …) that had no home before.
 *
 * <p>RU: Серверный мод Aetherium регистрирует команду по имени со {@link CommandSpec} (права + форма
 * аргументов) и {@link CommandHandler}; загрузчик переводит регистрацию в систему команд платформы
 * (Brigadier на NeoForge), не пропуская ни одного типа Brigadier через границу. Аргументы приходят уже
 * разобранными; загрузчик передаёт уровень прав отправителя.
 */
public interface EdgeCommands {

    /**
     * Register a command. Called during the loader's command-registration phase.
     *
     * @param name    the command literal, without a leading slash (e.g. {@code "faction"})
     * @param spec    permission level + argument shape + description
     * @param handler invoked when a player (or the console) runs the command
     */
    void register(String name, CommandSpec spec, CommandHandler handler);

    /** Runs a registered command. */
    @FunctionalInterface
    interface CommandHandler {
        /**
         * @param sender the player who ran it, or {@code null} for the server console / command block
         * @param args   the already-tokenized arguments, in declaration order
         * @return {@link InteractionResult#PASS} on success; {@link InteractionResult#CANCEL} to report failure
         */
        InteractionResult run(PlayerHandle sender, List<String> args);
    }

    /** The declaration of a command: required permission, argument types, and a help description. */
    record CommandSpec(int permissionLevel, List<ArgType> args, String description) {
        public CommandSpec {
            args = List.copyOf(args);
            description = description == null ? "" : description;
        }

        /** Convenience factory: {@code CommandSpec.of(2, "reload config", ArgType.WORD)}. */
        public static CommandSpec of(int permissionLevel, String description, ArgType... args) {
            return new CommandSpec(permissionLevel, List.of(args), description);
        }
    }

    /** The argument kinds the loader knows how to parse into Brigadier arguments. */
    enum ArgType {
        /** A single unquoted word. */
        WORD,
        /** The rest of the input as one greedy string (only valid as the last argument). */
        GREEDY_STRING,
        INT,
        DOUBLE,
        /** A player selector, resolved to that player's name in the tokenized args. */
        PLAYER,
        BOOL
    }

    /** No-op commands used by the no-game bridge (registrations are silently dropped off-platform). */
    EdgeCommands NONE = (name, spec, handler) -> {
        // no command system outside a game
    };
}
