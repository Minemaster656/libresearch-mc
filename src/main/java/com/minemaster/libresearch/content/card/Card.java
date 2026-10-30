package com.minemaster.libresearch.content.card;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Optional;

/**
 * Карта: шаблон раздачи. Файл {@code data/<namespace>/libresearch/card/<id>.json}.
 *
 * <ul>
 *   <li>{@code discipline} — id дисциплины внутри стола, {@code self}/{@code random}
 *       или пусто для универсальной карты;</li>
 *   <li>{@code spawn.requirements} — условия появления в раздаче;</li>
 *   <li>{@code requires} — условия розыгрыша (мягкие: карту можно предложить);</li>
 *   <li>{@code bonus_only} — карта попадает только в бонусную колоду.</li>
 * </ul>
 */
public record Card(String display, int cost, int weight, Optional<String> discipline, boolean bonusOnly,
                   Spawn spawn, List<CardRequirement> requires, List<CardEffect> effects) {
    public static final Codec<Card> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("display", "").forGetter(Card::display),
            Codec.INT.optionalFieldOf("cost", 1).forGetter(Card::cost),
            Codec.INT.optionalFieldOf("weight", 1).forGetter(Card::weight),
            Codec.STRING.optionalFieldOf("discipline").forGetter(Card::discipline),
            Codec.BOOL.optionalFieldOf("bonus_only", false).forGetter(Card::bonusOnly),
            Spawn.CODEC.optionalFieldOf("spawn", Spawn.EMPTY).forGetter(Card::spawn),
            CardRequirement.CODEC.listOf().optionalFieldOf("requires", List.of()).forGetter(Card::requires),
            CardEffect.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(Card::effects)
    ).apply(instance, Card::new));

    public record Spawn(List<CardRequirement> requirements) {
        public static final Spawn EMPTY = new Spawn(List.of());

        public static final Codec<Spawn> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                CardRequirement.CODEC.listOf().optionalFieldOf("requirements", List.of())
                        .forGetter(Spawn::requirements)
        ).apply(instance, Spawn::new));
    }
}
