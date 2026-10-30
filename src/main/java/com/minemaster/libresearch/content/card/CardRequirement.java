package com.minemaster.libresearch.content.card;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.Set;

/**
 * Требование карты. {@code requires} проверяются при розыгрыше,
 * {@code spawn.requirements} — при формировании раздачи.
 *
 * <p>Типы регистрируются вручную; новый тип = новый record + ветка в {@link #codecFor}.
 * Неизвестные типы отсеиваются до парсинга (см. {@code CardJson}), чтобы ошибка была
 * читаемой, а не стектрейсом.</p>
 */
public sealed interface CardRequirement {
    Set<String> TYPES = Set.of("item", "any_item", "xp_levels", "table_resources", "progress", "never");

    String type();

    MapCodec<? extends CardRequirement> codec();

    Codec<CardRequirement> CODEC = Codec.STRING.dispatch("type", CardRequirement::type, CardRequirement::codecFor);

    static MapCodec<? extends CardRequirement> codecFor(String type) {
        return switch (type) {
            case "item" -> Item.MAP_CODEC;
            case "any_item" -> AnyItem.MAP_CODEC;
            case "xp_levels" -> XpLevels.MAP_CODEC;
            case "table_resources" -> TableResources.MAP_CODEC;
            case "progress" -> Progress.MAP_CODEC;
            case "never" -> Never.MAP_CODEC;
            default -> throw new IllegalArgumentException("Неизвестный тип требования: " + type);
        };
    }

    /** Предмет в инвентаре игрока; ровно одно из {@code item}/{@code tag}. */
    record Item(Optional<ResourceLocation> item, Optional<ResourceLocation> tag, int count,
                boolean consume, boolean visible) implements CardRequirement {
        public static final MapCodec<Item> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                ResourceLocation.CODEC.optionalFieldOf("item").forGetter(Item::item),
                ResourceLocation.CODEC.optionalFieldOf("tag").forGetter(Item::tag),
                Codec.INT.optionalFieldOf("count", 1).forGetter(Item::count),
                Codec.BOOL.optionalFieldOf("consume", false).forGetter(Item::consume),
                Codec.BOOL.optionalFieldOf("visible", true).forGetter(Item::visible)
        ).apply(instance, Item::new));

        @Override
        public String type() {
            return "item";
        }

        @Override
        public MapCodec<? extends CardRequirement> codec() {
            return MAP_CODEC;
        }
    }

    /** Любой предмет из тега. */
    record AnyItem(ResourceLocation tag, int count, boolean consume) implements CardRequirement {
        public static final MapCodec<AnyItem> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("tag").forGetter(AnyItem::tag),
                Codec.INT.optionalFieldOf("count", 1).forGetter(AnyItem::count),
                Codec.BOOL.optionalFieldOf("consume", false).forGetter(AnyItem::consume)
        ).apply(instance, AnyItem::new));

        @Override
        public String type() {
            return "any_item";
        }

        @Override
        public MapCodec<? extends CardRequirement> codec() {
            return MAP_CODEC;
        }
    }

    /** Уровни опыта игрока. */
    record XpLevels(int min, boolean consume) implements CardRequirement {
        public static final MapCodec<XpLevels> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.INT.optionalFieldOf("min", 1).forGetter(XpLevels::min),
                Codec.BOOL.optionalFieldOf("consume", true).forGetter(XpLevels::consume)
        ).apply(instance, XpLevels::new));

        @Override
        public String type() {
            return "xp_levels";
        }

        @Override
        public MapCodec<? extends CardRequirement> codec() {
            return MAP_CODEC;
        }
    }

    /** Дополнительный расход бумаги и зарядов чернильницы. */
    record TableResources(int paper, int inkCharges) implements CardRequirement {
        public static final MapCodec<TableResources> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.INT.optionalFieldOf("paper", 0).forGetter(TableResources::paper),
                Codec.INT.optionalFieldOf("ink_charges", 0).forGetter(TableResources::inkCharges)
        ).apply(instance, TableResources::new));

        @Override
        public String type() {
            return "table_resources";
        }

        @Override
        public MapCodec<? extends CardRequirement> codec() {
            return MAP_CODEC;
        }
    }

    /** Состояние текущей теории. */
    record Progress(Optional<String> discipline, int minPercent, int minTotal, int minDisciplines)
            implements CardRequirement {
        public static final MapCodec<Progress> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.optionalFieldOf("discipline").forGetter(Progress::discipline),
                Codec.INT.optionalFieldOf("min_percent", 0).forGetter(Progress::minPercent),
                Codec.INT.optionalFieldOf("min_total", 0).forGetter(Progress::minTotal),
                Codec.INT.optionalFieldOf("min_disciplines", 0).forGetter(Progress::minDisciplines)
        ).apply(instance, Progress::new));

        @Override
        public String type() {
            return "progress";
        }

        @Override
        public MapCodec<? extends CardRequirement> codec() {
            return MAP_CODEC;
        }
    }

    /** Карта не выпадает и не может быть разыграна — отладочный «мёртвый» тип. */
    record Never() implements CardRequirement {
        public static final MapCodec<Never> MAP_CODEC = MapCodec.unit(Never::new);

        @Override
        public String type() {
            return "never";
        }

        @Override
        public MapCodec<? extends CardRequirement> codec() {
            return MAP_CODEC;
        }
    }
}
