/*
 * Aetherium Framework — typed injection anchor (no @At strings).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.injector;

import org.objectweb.asm.Opcodes;

/**
 * Where a merged hook group attaches in a method — the strongly-typed replacement for Mixin's
 * {@code @At("HEAD")} / {@code @At("RETURN")} string selectors.
 *
 * <p>EN: A merged DAG hook group (see {@link MergedHookRule}) needs a single, well-defined attachment
 * point so that every hook in the group shares one {@link HookContext} and one cancellation epilogue.
 * {@link #HEAD} attaches before the method's first real instruction; {@link #RETURN} attaches before
 * the first {@code *RETURN}. The {@link BytecodeCursor} navigation each anchor maps to is an enum
 * constant, not a parsed string — the value cannot be typo'd into a silent no-op.
 *
 * <p>RU: Группе слитых DAG-хуков (см. {@link MergedHookRule}) нужна единая чётко определённая точка
 * привязки, чтобы все хуки группы делили один {@link HookContext} и один эпилог отмены. {@link #HEAD}
 * привязывается перед первой реальной инструкцией метода; {@link #RETURN} — перед первым
 * {@code *RETURN}. Навигация {@link BytecodeCursor}, в которую отображается каждый якорь, — константа
 * перечисления, а не разбираемая строка.
 */
public enum InjectionAnchor {

    /** Before the method's first real instruction (the entry of the body). */
    HEAD {
        @Override
        void navigate(BytecodeCursor cursor) {
            cursor.toStart();
        }
    },

    /** Before the first {@code IRETURN}/{@code LRETURN}/.../{@code RETURN} in the method. */
    RETURN {
        @Override
        void navigate(BytecodeCursor cursor) {
            cursor.findReturn();
        }
    };

    /** Position {@code cursor} at this anchor (package-private; used by the transformer). */
    abstract void navigate(BytecodeCursor cursor);

    static {
        // Compile-time anchor: keep a hard reference so a careless refactor of ASM opcodes is caught.
        assert Opcodes.RETURN > 0;
    }
}
