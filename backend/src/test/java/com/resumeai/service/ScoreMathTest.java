package com.resumeai.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScoreMathTest {

    @Test
    void combineWeightsAiEightyAndRulesTwenty() {
        assertEquals(74, ScoreMath.combine(70, 90));
        assertEquals(100, ScoreMath.combine(100, 100));
        assertEquals(0, ScoreMath.combine(0, 0));
    }

    @Test
    void combineKeepsValuesInRange() {
        assertEquals(100, ScoreMath.combine(250, 250));
        assertEquals(0, ScoreMath.combine(-5, -5));
    }

    @Test
    void verdictBands() {
        assertEquals("Excellent match", ScoreMath.verdict(90));
        assertEquals("Good match", ScoreMath.verdict(75));
        assertEquals("Needs improvement", ScoreMath.verdict(55));
        assertEquals("Weak match", ScoreMath.verdict(20));
    }

    @Test
    void unknownLevelFallsBackToJunior() {
        assertEquals("junior", Levels.get("nonsense").key());
        assertEquals("junior", Levels.get(null).key());
        assertTrue(Levels.get("Fresher").early());
        assertFalse(Levels.get("senior").early());
    }
}
