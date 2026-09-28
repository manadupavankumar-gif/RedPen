package com.resumeai.service;

import java.util.LinkedHashMap;
import java.util.Map;

/** Experience levels the user can pick. Each one changes what "a good resume" means. */
public final class Levels {

    public record Info(String key, String label, String shortLabel, String guidance, boolean early, boolean onePage) {}

    private static final Map<String, Info> MAP = new LinkedHashMap<>();

    static {
        add(new Info("fresher", "Fresher / entry-level (recent graduate, 0-1 years)", "Fresher",
                "Do NOT penalize missing work experience. Weigh education, academic and personal projects, internships, "
                        + "certifications, technical skills, and links such as GitHub or a portfolio. Expect ONE page.",
                true, true));
        add(new Info("intern", "Internship seeker (current student)", "Internship",
                "Do NOT penalize missing work experience. Weigh coursework, projects, hackathons, clubs, skills and "
                        + "eagerness to learn. Expect ONE page.",
                true, true));
        add(new Info("junior", "Junior (1-3 years of experience)", "1-3 years",
                "Expect some professional experience with concrete contributions and the first measurable results. "
                        + "ONE page is best.",
                false, true));
        add(new Info("mid", "Mid-level (3-6 years of experience)", "3-6 years",
                "Expect ownership, measurable impact, growth in responsibility and depth in the core skills. "
                        + "One to two pages are fine.",
                false, false));
        add(new Info("senior", "Senior (6+ years of experience)", "6+ years",
                "Expect leadership, scope, mentoring, architecture or strategy decisions and quantified business impact. "
                        + "Two pages are fine; focus on the last 10 years.",
                false, false));
        add(new Info("switcher", "Career switcher (moving into a new field)", "Career switch",
                "Value transferable skills, relevant courses, projects and certifications, and a clear summary that "
                        + "explains the switch. Do not penalize unrelated past job titles.",
                false, false));
    }

    private Levels() {}

    private static void add(Info i) {
        MAP.put(i.key(), i);
    }

    /** Unknown or missing keys fall back to "junior". */
    public static Info get(String key) {
        Info i = find(key);
        return i != null ? i : MAP.get("junior");
    }

    /** Returns null when the key is unknown (used for old records). */
    public static Info find(String key) {
        return key == null ? null : MAP.get(key.trim().toLowerCase());
    }
}
