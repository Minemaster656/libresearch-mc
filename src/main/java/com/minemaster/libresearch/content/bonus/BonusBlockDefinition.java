package com.minemaster.libresearch.content.bonus;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Бонусный блок у стола: что ищем рядом и какие карты он добавляет в бонусную колоду.
 * Файл {@code data/<namespace>/libresearch/table_bonus/<id>.json}.
 *
 * <p>Один и тот же бонус может использоваться несколькими типами столов.</p>
 */
public record BonusBlockDefinition(String display, Match match, int cost, List<CardEntry> cards) {
    public static final Codec<BonusBlockDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("display", "").forGetter(BonusBlockDefinition::display),
            Match.CODEC.fieldOf("match").forGetter(BonusBlockDefinition::match),
            Codec.INT.optionalFieldOf("cost", 1).forGetter(BonusBlockDefinition::cost),
            CardEntry.CODEC.listOf().optionalFieldOf("cards", List.of()).forGetter(BonusBlockDefinition::cards)
    ).apply(instance, BonusBlockDefinition::new));

    /** Что считается совпадением. Списки складываются по ИЛИ. */
    public record Match(List<ResourceLocation> blocks, List<ResourceLocation> tags,
                        List<ResourceLocation> entities) {
        public static final Codec<Match> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.listOf().optionalFieldOf("blocks", List.of()).forGetter(Match::blocks),
                ResourceLocation.CODEC.listOf().optionalFieldOf("tags", List.of()).forGetter(Match::tags),
                ResourceLocation.CODEC.listOf().optionalFieldOf("entities", List.of()).forGetter(Match::entities)
        ).apply(instance, Match::new));
    }

    /** Запись колоды: карта и число её копий. */
    public record CardEntry(ResourceLocation card, int count) {
        public static final Codec<CardEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("card").forGetter(CardEntry::card),
                Codec.INT.optionalFieldOf("count", 1).forGetter(CardEntry::count)
        ).apply(instance, CardEntry::new));
    }
}
