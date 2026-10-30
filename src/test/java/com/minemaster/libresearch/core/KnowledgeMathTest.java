package com.minemaster.libresearch.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeMathTest {
    @Test
    void conversionOfOneDisciplineKeepsRemainder() {
        // Сценарий заказчика: 110% + 5% → 1 очко и 15% остатка.
        List<KnowledgeMath.Conversion> result = KnowledgeMath.convert(
                List.of(new KnowledgeMath.Entry("copper", 115)), 6);

        assertEquals(1, result.size());
        assertEquals(115L, result.get(0).centi());
        assertFalse(result.get(0).penalized());
        assertEquals(1L, KnowledgeMath.claimablePoints(result.get(0).centi()));
        assertEquals(15L, result.get(0).centi() - KnowledgeMath.claimablePoints(result.get(0).centi()) * 100);
    }

    @Test
    void noPenaltyWhenSlotsCoverAllDisciplines() {
        List<KnowledgeMath.Conversion> result = KnowledgeMath.convert(
                List.of(new KnowledgeMath.Entry("copper", 110), new KnowledgeMath.Entry("iron", 5)), 2);

        assertEquals(110L, result.get(0).centi());
        assertEquals(5L, result.get(1).centi());
        assertFalse(result.get(1).penalized());
    }

    @Test
    void lowestRanksLoseOneThird() {
        List<KnowledgeMath.Conversion> result = KnowledgeMath.convert(
                List.of(new KnowledgeMath.Entry("copper", 110), new KnowledgeMath.Entry("iron", 90)), 1);

        assertEquals(110L, result.get(0).centi());
        assertEquals(60L, result.get(1).centi());
        assertTrue(result.get(1).penalized());
    }

    @Test
    void penalizedProgressNeverDropsBelowOneCenti() {
        List<KnowledgeMath.Conversion> result = KnowledgeMath.convert(
                List.of(new KnowledgeMath.Entry("a", 1), new KnowledgeMath.Entry("b", 1)), 0);

        assertEquals(1L, result.get(0).centi());
        assertEquals(1L, result.get(1).centi());
    }

    @Test
    void emptyDisciplineGrantsNothing() {
        List<KnowledgeMath.Conversion> result = KnowledgeMath.convert(
                List.of(new KnowledgeMath.Entry("a", 10), new KnowledgeMath.Entry("b", 0)), 0);

        assertEquals(0L, result.get(1).centi());
        assertFalse(result.get(1).penalized());
    }

    @Test
    void tiesKeepOriginalOrder() {
        List<KnowledgeMath.Conversion> result = KnowledgeMath.convert(
                List.of(new KnowledgeMath.Entry("first", 50), new KnowledgeMath.Entry("second", 50)), 1);

        assertEquals("first", result.get(0).discipline());
        assertFalse(result.get(0).penalized());
        assertEquals("second", result.get(1).discipline());
        assertTrue(result.get(1).penalized());
        assertEquals(33L, result.get(1).centi());
    }

    @Test
    void claimIsLimitedByGrantedAmountAndByStock() {
        assertEquals(1L, KnowledgeMath.claimablePoints(115L, 115L));
        assertEquals(0L, KnowledgeMath.claimablePoints(15L, 115L));
        assertEquals(1L, KnowledgeMath.claimablePoints(115L, 199L));

        // После забора очков остаток остаётся, но новая теория уже разрешена.
        assertEquals(0L, KnowledgeMath.claimablePoints(15L, 15L));
    }

    @Test
    void pendingRewardBlocksNewTheoryOnlyWhileWholePointExists() {
        assertTrue(KnowledgeMath.hasPendingReward(115L));
        assertTrue(KnowledgeMath.hasPendingReward(100L));
        assertFalse(KnowledgeMath.hasPendingReward(15L));
        assertFalse(KnowledgeMath.hasPendingReward(0L));
    }

    @Test
    void inspirationGrowsWithResearchAndStopsAtCap() {
        assertEquals(5, KnowledgeMath.startInspiration(0, 0));
        assertEquals(10, KnowledgeMath.startInspiration(10, 0));
        assertEquals(6, KnowledgeMath.startInspiration(0, 10));
        assertEquals(15, KnowledgeMath.startInspiration(40, 40));
    }

    @Test
    void chosenBonusesNeverConsumeTheLastInspiration() {
        assertEquals(4, KnowledgeMath.startInspiration(0, 0, 1));
        assertEquals(1, KnowledgeMath.startInspiration(0, 0, 4));
        assertEquals(1, KnowledgeMath.startInspiration(0, 0, 10));
        assertEquals(1, KnowledgeMath.startInspiration(10, 0, 20));
    }
}
