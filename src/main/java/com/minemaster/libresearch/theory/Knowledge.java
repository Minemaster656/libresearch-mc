package com.minemaster.libresearch.theory;

import com.minemaster.libresearch.core.KnowledgeMath;
import com.mojang.serialization.Codec;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Запас знаний игрока: санти-очки по дисциплинам. 100 = 1 очко.
 *
 * <p>Хранится в attachment игрока, поэтому переживает перезаход и смерть
 * (если не отключено конфигом).</p>
 */
public record Knowledge(Map<String, Long> centi) {
    public static final Knowledge EMPTY = new Knowledge(Map.of());

    public static final Codec<Knowledge> CODEC = Codec.unboundedMap(Codec.STRING, Codec.LONG)
            .xmap(Knowledge::new, Knowledge::centi);

    public long centi(String discipline) {
        return centi.getOrDefault(discipline, 0L);
    }

    public long points(String discipline) {
        return KnowledgeMath.claimablePoints(centi(discipline));
    }

    public boolean isKnown(String discipline) {
        return centi(discipline) > 0;
    }

    public Knowledge withCenti(String discipline, long value) {
        Map<String, Long> copy = new LinkedHashMap<>(centi);
        if (value <= 0) {
            copy.remove(discipline);
        } else {
            copy.put(discipline, value);
        }
        return new Knowledge(Map.copyOf(copy));
    }

    public Knowledge add(String discipline, long amount) {
        if (amount == 0) {
            return this;
        }
        return withCenti(discipline, centi(discipline) + amount);
    }

    public Knowledge subtract(String discipline, long amount) {
        return withCenti(discipline, centi(discipline) - Math.max(0, amount));
    }
}
