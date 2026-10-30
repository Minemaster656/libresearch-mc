package com.minemaster.libresearch.data;

import com.minemaster.libresearch.LibResearch;
import com.minemaster.libresearch.theory.Knowledge;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Данные, привязанные к игроку, а не к блоку. */
public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, LibResearch.MOD_ID);

    /** Запас знаний: дисциплина → санти-очки. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Knowledge>> KNOWLEDGE =
            ATTACHMENT_TYPES.register("knowledge", () -> AttachmentType.builder(() -> Knowledge.EMPTY)
                    .serialize(Knowledge.CODEC)
                    .copyOnDeath()
                    .build());

    private ModAttachments() {
    }
}
