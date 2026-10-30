package com.minemaster.libresearch.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeightedPoolTest {
    private static WeightedPool.Rng fixed(int value) {
        return bound -> {
            assertTrue(value >= 0 && value < bound, "roll " + value + " вне 0.." + bound);
            return value;
        };
    }

    @Test
    void weightZeroIsNeverPicked() {
        List<WeightedPool.Option<String>> options = List.of(
                new WeightedPool.Option<>("never", 0),
                new WeightedPool.Option<>("always", 2));

        for (int roll = 0; roll < 2; roll++) {
            assertEquals(Optional.of("always"), WeightedPool.pick(options, fixed(roll)));
        }
    }

    @Test
    void biggerWeightGetsBiggerShare() {
        List<WeightedPool.Option<String>> options = List.of(
                new WeightedPool.Option<>("a", 1),
                new WeightedPool.Option<>("b", 3));

        assertEquals(Optional.of("a"), WeightedPool.pick(options, fixed(0)));
        assertEquals(Optional.of("b"), WeightedPool.pick(options, fixed(1)));
        assertEquals(Optional.of("b"), WeightedPool.pick(options, fixed(2)));
        assertEquals(Optional.of("b"), WeightedPool.pick(options, fixed(3)));
    }

    @Test
    void emptyPoolReturnsNothing() {
        assertEquals(Optional.empty(), WeightedPool.pick(List.of(), fixed(0)));
        assertEquals(Optional.empty(), WeightedPool.pick(
                List.of(new WeightedPool.Option<>("x", 0)), fixed(0)));
    }
}
