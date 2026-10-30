package com.minemaster.libresearch.core;

import java.util.List;
import java.util.Optional;

/**
 * Взвешенный выбор из конечного набора. Используется для карт: вес 0 — карта
 * не выпадает, вес по умолчанию 1.
 *
 * <p>Случайность передаётся интерфейсом {@link Rng}, чтобы ядро не зависело
 * от Minecraft и тестировалось предсказуемо.</p>
 */
public final class WeightedPool {
    private WeightedPool() {
    }

    public interface Rng {
        /** Число от 0 (включительно) до {@code bound} (исключительно). */
        int nextInt(int bound);
    }

    public record Option<T>(T value, int weight) {
    }

    public static <T> Optional<T> pick(List<Option<T>> options, Rng rng) {
        int total = 0;
        for (Option<T> option : options) {
            if (option.weight() > 0) {
                total += option.weight();
            }
        }
        if (total <= 0) {
            return Optional.empty();
        }

        int roll = rng.nextInt(total);
        for (Option<T> option : options) {
            if (option.weight() <= 0) {
                continue;
            }
            roll -= option.weight();
            if (roll < 0) {
                return Optional.of(option.value());
            }
        }
        return Optional.empty();
    }
}
