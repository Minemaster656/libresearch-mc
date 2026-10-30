package com.minemaster.libresearch.content.card;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Set;

/**
 * Эффект разыгранной карты. Все случайные значения фиксируются при создании
 * инстанса карты; здесь хранится только шаблон.
 */
public sealed interface CardEffect {
    Set<String> TYPES = Set.of(
            "progress", "progress_drain", "inspiration", "bonus_draws", "full_value_slots",
            "block_discipline", "give_item", "consume_xp", "paper", "ink_charges");

    /** Зарезервированные цели эффектов. Всё остальное трактуется как id дисциплины. */
    Set<String> TARGETS = Set.of("self", "random", "highest", "lowest", "all_equalize", "drain");

    String type();

    MapCodec<? extends CardEffect> codec();

    Codec<CardEffect> CODEC = Codec.STRING.dispatch("type", CardEffect::type, CardEffect::codecFor);

    static MapCodec<? extends CardEffect> codecFor(String type) {
        return switch (type) {
            case "progress" -> Progress.MAP_CODEC;
            case "progress_drain" -> ProgressDrain.MAP_CODEC;
            case "inspiration" -> Inspiration.MAP_CODEC;
            case "bonus_draws" -> BonusDraws.MAP_CODEC;
            case "full_value_slots" -> FullValueSlots.MAP_CODEC;
            case "block_discipline" -> BlockDiscipline.MAP_CODEC;
            case "give_item" -> GiveItem.MAP_CODEC;
            case "consume_xp" -> ConsumeXp.MAP_CODEC;
            case "paper" -> Paper.MAP_CODEC;
            case "ink_charges" -> InkCharges.MAP_CODEC;
            default -> throw new IllegalArgumentException("Неизвестный тип эффекта: " + type);
        };
    }

    /** Прибавка прогресса. {@code target} — self/random/highest/lowest/id дисциплины. */
    record Progress(String target, IntRange amount) implements CardEffect {
        public static final MapCodec<Progress> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.optionalFieldOf("target", "self").forGetter(Progress::target),
                IntRange.CODEC.fieldOf("amount").forGetter(Progress::amount)
        ).apply(instance, Progress::new));

        @Override
        public String type() {
            return "progress";
        }

        @Override
        public MapCodec<? extends CardEffect> codec() {
            return MAP_CODEC;
        }
    }

    /** Снятие прогресса с перечисленных дисциплин (по кругу, без ухода в минус). */
    record ProgressDrain(List<String> from, IntRange amount) implements CardEffect {
        public static final MapCodec<ProgressDrain> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.listOf().optionalFieldOf("from", List.of()).forGetter(ProgressDrain::from),
                IntRange.CODEC.fieldOf("amount").forGetter(ProgressDrain::amount)
        ).apply(instance, ProgressDrain::new));

        @Override
        public String type() {
            return "progress_drain";
        }

        @Override
        public MapCodec<? extends CardEffect> codec() {
            return MAP_CODEC;
        }
    }

    /** Возврат вдохновения (обрезается стартовым запасом). */
    record Inspiration(IntRange amount) implements CardEffect {
        public static final MapCodec<Inspiration> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                IntRange.CODEC.fieldOf("amount").forGetter(Inspiration::amount)
        ).apply(instance, Inspiration::new));

        @Override
        public String type() {
            return "inspiration";
        }

        @Override
        public MapCodec<? extends CardEffect> codec() {
            return MAP_CODEC;
        }
    }

    /** Жетоны добора: следующая рука берёт 3 карты вместо 2. */
    record BonusDraws(int amount) implements CardEffect {
        public static final MapCodec<BonusDraws> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.INT.optionalFieldOf("amount", 1).forGetter(BonusDraws::amount)
        ).apply(instance, BonusDraws::new));

        @Override
        public String type() {
            return "bonus_draws";
        }

        @Override
        public MapCodec<? extends CardEffect> codec() {
            return MAP_CODEC;
        }
    }

    /** Сколько дисциплин получают полный зачёт при завершении. */
    record FullValueSlots(int amount) implements CardEffect {
        public static final MapCodec<FullValueSlots> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.INT.optionalFieldOf("amount", 1).forGetter(FullValueSlots::amount)
        ).apply(instance, FullValueSlots::new));

        @Override
        public String type() {
            return "full_value_slots";
        }

        @Override
        public MapCodec<? extends CardEffect> codec() {
            return MAP_CODEC;
        }
    }

    /** Блокировка дисциплины до конца теории. */
    record BlockDiscipline(String target) implements CardEffect {
        public static final MapCodec<BlockDiscipline> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.optionalFieldOf("target", "random").forGetter(BlockDiscipline::target)
        ).apply(instance, BlockDiscipline::new));

        @Override
        public String type() {
            return "block_discipline";
        }

        @Override
        public MapCodec<? extends CardEffect> codec() {
            return MAP_CODEC;
        }
    }

    /** Выдача предмета игроку. */
    record GiveItem(ResourceLocation item, IntRange count) implements CardEffect {
        public static final MapCodec<GiveItem> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("item").forGetter(GiveItem::item),
                IntRange.CODEC.optionalFieldOf("count", new IntRange(1, 1)).forGetter(GiveItem::count)
        ).apply(instance, GiveItem::new));

        @Override
        public String type() {
            return "give_item";
        }

        @Override
        public MapCodec<? extends CardEffect> codec() {
            return MAP_CODEC;
        }
    }

    /** Списание опыта. {@code all} — забрать все уровни (как «тёмный шёпот»). */
    record ConsumeXp(IntRange amount, boolean all) implements CardEffect {
        public static final MapCodec<ConsumeXp> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                IntRange.CODEC.optionalFieldOf("amount", IntRange.ZERO).forGetter(ConsumeXp::amount),
                Codec.BOOL.optionalFieldOf("all", false).forGetter(ConsumeXp::all)
        ).apply(instance, ConsumeXp::new));

        @Override
        public String type() {
            return "consume_xp";
        }

        @Override
        public MapCodec<? extends CardEffect> codec() {
            return MAP_CODEC;
        }
    }

    /** Дополнительный расход бумаги. */
    record Paper(int amount) implements CardEffect {
        public static final MapCodec<Paper> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.INT.optionalFieldOf("amount", 1).forGetter(Paper::amount)
        ).apply(instance, Paper::new));

        @Override
        public String type() {
            return "paper";
        }

        @Override
        public MapCodec<? extends CardEffect> codec() {
            return MAP_CODEC;
        }
    }

    /** Дополнительный расход зарядов чернильницы. */
    record InkCharges(int amount) implements CardEffect {
        public static final MapCodec<InkCharges> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.INT.optionalFieldOf("amount", 1).forGetter(InkCharges::amount)
        ).apply(instance, InkCharges::new));

        @Override
        public String type() {
            return "ink_charges";
        }

        @Override
        public MapCodec<? extends CardEffect> codec() {
            return MAP_CODEC;
        }
    }
}
