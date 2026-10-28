package ru.sloboda.registry;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import ru.sloboda.Sloboda;
import ru.sloboda.colony.ColonyData;

/** Данные всех слобод хранятся как вложение (attachment) мира. */
public final class ModAttachments {
    public static final AttachmentType<ColonyData> COLONIES = AttachmentRegistry.create(
            Sloboda.id("colonies"),
            builder -> builder
                    .initializer(ColonyData::new)
                    .persistent(ColonyData.CODEC));

    private ModAttachments() {}

    public static void init() {}
}
