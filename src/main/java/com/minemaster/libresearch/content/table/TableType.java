package com.minemaster.libresearch.content.table;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Определение типа стола: какие дисциплины он знает и какие бонусные блоки
 * рядом с ним распознаёт.
 *
 * <p>Файл: {@code data/<namespace>/libresearch/table_type/<id>.json}.
 * Типов столов может быть сколько угодно; блок лишь ссылается на id типа.</p>
 */
public record TableType(String display, List<Discipline> disciplines, List<ResourceLocation> bonusBlocks) {
    public static final Codec<TableType> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("display").forGetter(TableType::display),
            Discipline.CODEC.listOf().optionalFieldOf("disciplines", List.of()).forGetter(TableType::disciplines),
            ResourceLocation.CODEC.listOf().optionalFieldOf("bonus_blocks", List.of()).forGetter(TableType::bonusBlocks)
    ).apply(instance, TableType::new));

    public static final TableType EMPTY = new TableType("libresearch.table.empty", List.of(), List.of());

    /** Уникальные id дисциплин — для проверки ссылок из карт. */
    public Set<String> disciplineIds() {
        Set<String> ids = new LinkedHashSet<>();
        disciplines.forEach(discipline -> ids.add(discipline.id()));
        return ids;
    }

    /**
     * Дисциплина: куда копится прогресс и что выдаётся за очки.
     * {@code color} — цвет полосы в GUI (0xRRGGBB).
     */
    public record Discipline(String id, String display, int color, ResourceLocation icon,
                             ResourceLocation payout, int countPerPoint) {
        public static final Codec<Discipline> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Discipline::id),
                Codec.STRING.fieldOf("display").forGetter(Discipline::display),
                RgbColor.CODEC.optionalFieldOf("color", 0xFFFFFF).forGetter(Discipline::color),
                ResourceLocation.CODEC.fieldOf("icon").forGetter(Discipline::icon),
                ResourceLocation.CODEC.fieldOf("payout").forGetter(Discipline::payout),
                Codec.INT.optionalFieldOf("count_per_point", 1).forGetter(Discipline::countPerPoint)
        ).apply(instance, Discipline::new));
    }

    /** Цвет в JSON — строка {@code "#RRGGBB"}. */
    public static final class RgbColor {
        public static final Codec<Integer> CODEC = Codec.STRING.comapFlatMap(
                RgbColor::parse,
                value -> String.format("#%06X", value & 0xFFFFFF));

        private RgbColor() {
        }

        private static DataResult<Integer> parse(String text) {
            String hex = text.startsWith("#") ? text.substring(1) : text;
            try {
                return DataResult.success(Integer.parseInt(hex, 16) & 0xFFFFFF);
            } catch (NumberFormatException exception) {
                return DataResult.error(() -> "Ожидался цвет вида #RRGGBB, получено: " + text);
            }
        }
    }
}
