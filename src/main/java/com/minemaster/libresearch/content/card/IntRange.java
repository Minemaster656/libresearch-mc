package com.minemaster.libresearch.content.card;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Целое число или диапазон включительно: {@code 5} либо {@code {"min": 15, "max": 25}}.
 * Диапазон разыгрывается в момент создания инстанса карты, а не при розыгрыше.
 */
public record IntRange(int min, int max) {
    public static final IntRange ZERO = new IntRange(0, 0);

    public static final Codec<IntRange> CODEC = Codec.either(Codec.INT, Bounds.CODEC).xmap(
            either -> either.map(value -> new IntRange(value, value), bounds -> new IntRange(bounds.min(), bounds.max())),
            range -> range.min == range.max
                    ? Either.left(range.min)
                    : Either.right(new Bounds(range.min, range.max())));

    public boolean isConstant() {
        return min == max;
    }

    /** Приводит диапазон к виду min ≤ max; используется валидацией. */
    public IntRange normalized() {
        return new IntRange(Math.min(min, max), Math.max(min, max));
    }

    public record Bounds(int min, int max) {
        public static final Codec<Bounds> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("min").forGetter(Bounds::min),
                Codec.INT.fieldOf("max").forGetter(Bounds::max)
        ).apply(instance, Bounds::new));
    }
}
