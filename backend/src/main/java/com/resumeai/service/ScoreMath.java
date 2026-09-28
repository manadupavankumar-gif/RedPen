package com.resumeai.service;

/** Small, pure helpers so the scoring rules are easy to test. */
public final class ScoreMath {

    private ScoreMath() {}

    public static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }

    /** Final score = 80% AI job-fit + 20% rule-based resume checks (steadier than AI alone). */
    public static int combine(int aiScore, int atsScore) {
        return clamp((int) Math.round(0.8 * clamp(aiScore) + 0.2 * clamp(atsScore)));
    }

    public static String verdict(int score) {
        if (score >= 85) return "Excellent match";
        if (score >= 70) return "Good match";
        if (score >= 50) return "Needs improvement";
        return "Weak match";
    }
}
