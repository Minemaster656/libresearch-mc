package com.minemaster.libresearch.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/**
 * Датапак-определение бумаги: {@code data/<namespace>/libresearch/paper/<id>.json}.
 *
 * <p>Стол ссылается на определение полем {@code paper} в своём table_type, поэтому
 * датапак/скрипт может заменить бумагу любым предметом (пергамент, свиток) и задать,
 * сколько штук уходит на один добор руки.</p>
 */
public record PaperDefinition(ResourceLocation item, int perDraw) {
    public static final Codec<PaperDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("item").forGetter(PaperDefinition::item),
            Codec.INT.optionalFieldOf("per_draw", 1).forGetter(PaperDefinition::perDraw)
    ).apply(instance, PaperDefinition::new));
}
