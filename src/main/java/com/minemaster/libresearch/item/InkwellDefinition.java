package com.minemaster.libresearch.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/**
 * Датапак-определение чернильницы: {@code data/<namespace>/libresearch/inkwell/<id>.json}.
 *
 * <p>Позволяет KubeJS/датапакам добавлять свои чернильницы с другой ёмкостью,
 * цветом и расходом на розыгрыш карты.</p>
 */
public record InkwellDefinition(ResourceLocation item, int capacity, int color, int chargesPerDraw) {
    public static final Codec<InkwellDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("item").forGetter(InkwellDefinition::item),
            Codec.INT.optionalFieldOf("capacity", InkwellItem.DEFAULT_CAPACITY).forGetter(InkwellDefinition::capacity),
            com.minemaster.libresearch.content.table.TableType.RgbColor.CODEC
                    .optionalFieldOf("color", 0x1E1B18).forGetter(InkwellDefinition::color),
            Codec.INT.optionalFieldOf("charges_per_draw", 1).forGetter(InkwellDefinition::chargesPerDraw)
    ).apply(instance, InkwellDefinition::new));
}
