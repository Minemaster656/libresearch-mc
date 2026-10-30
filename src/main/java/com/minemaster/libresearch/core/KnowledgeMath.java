package com.minemaster.libresearch.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Математика теории: перевод процентов в санти-очки, выдача наград и стартовое
 * вдохновение. Здесь нет ни одного класса Minecraft — только целые числа.
 *
 * <p>Единицы: 1 очко = 100 санти-очков; 100% в дисциплине = 1 очко.</p>
 */
public final class KnowledgeMath {
    public static final int CENTI_PER_POINT = 100;

    public static final int BASE_INSPIRATION = 5;
    public static final int MAX_INSPIRATION = 15;
    public static final double SPIKY_INSPIRATION = 0.5;
    public static final double HIDDEN_INSPIRATION = 0.1;

    private KnowledgeMath() {
    }

    /** Прогресс одной дисциплины в процентах. */
    public record Entry(String discipline, int percent) {
    }

    /** Результат конвертации: сколько санти-очков получит дисциплина. */
    public record Conversion(String discipline, int percent, long centi, boolean penalized) {
    }

    /**
     * Конвертация процентов в санти-очки по правилам оригинала:
     * сортировка по убыванию, ранг выше {@code fullValueSlots} получает 2/3
     * (но не меньше 1 санти-очка, если прогресс был).
     *
     * <p>Порядок при равных процентах сохраняется — так результат стабилен между
     * сохранениями мира.</p>
     */
    public static List<Conversion> convert(List<Entry> progress, int fullValueSlots) {
        List<Entry> sorted = new ArrayList<>(progress);
        // List.sort стабильна: равные проценты остаются в исходном порядке.
        sorted.sort(Comparator.comparingInt(Entry::percent).reversed());

        List<Conversion> result = new ArrayList<>(sorted.size());
        for (int rank = 0; rank < sorted.size(); rank++) {
            Entry entry = sorted.get(rank);
            int percent = entry.percent();
            if (percent <= 0) {
                result.add(new Conversion(entry.discipline(), percent, 0L, false));
                continue;
            }

            long centi = percent;
            boolean penalized = rank >= fullValueSlots;
            if (penalized) {
                centi = Math.max(1L, centi * 2L / 3L);
            }
            result.add(new Conversion(entry.discipline(), percent, centi, penalized));
        }
        return result;
    }

    /** Сколько целых очков можно забрать из запаса. */
    public static long claimablePoints(long centi) {
        return Math.max(0L, centi) / CENTI_PER_POINT;
    }

    /** Есть ли у стола целое незабранное очко: пока да — новая теория не начинается. */
    public static boolean hasPendingReward(long grantedCenti) {
        return claimablePoints(grantedCenti) > 0;
    }

    /**
     * Сколько очков реально можно забрать с учётом и награды, и текущего запаса:
     * нельзя получить больше, чем начислено этим столом, и больше, чем есть у игрока.
     */
    public static long claimablePoints(long grantedCenti, long stockCenti) {
        return Math.min(claimablePoints(grantedCenti), claimablePoints(stockCenti));
    }

    /** Стартовое вдохновение до вычета бонусных блоков. */
    public static int startInspiration(int spiky, int hidden) {
        double raw = BASE_INSPIRATION + SPIKY_INSPIRATION * spiky + HIDDEN_INSPIRATION * hidden;
        long rounded = Math.round(raw);
        return (int) Math.min(MAX_INSPIRATION, Math.max(1L, rounded));
    }

    /** Стартовое вдохновение после выбора бонусных блоков (минимум 1 остаётся). */
    public static int startInspiration(int spiky, int hidden, int chosenBonuses) {
        return Math.max(1, startInspiration(spiky, hidden) - Math.max(0, chosenBonuses));
    }
}
