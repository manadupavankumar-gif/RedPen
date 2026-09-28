package com.resumeai.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AtsCheckerTest {

    private static final String GOOD = String.join("\n",
            "Pavan Kumar",
            "pavan@example.com | +91 98765 43210 | github.com/pavan | linkedin.com/in/pavan",
            "SUMMARY", "Java developer with two internships.",
            "EDUCATION", "B.Tech Computer Science, JNTU, 2021 - 2025, CGPA 8.4",
            "SKILLS", "Java, Spring Boot, MySQL, REST, Git",
            "PROJECTS",
            "\u2022 Built a resume scoring web app with Spring Boot and MySQL used by 120 students",
            "\u2022 Reduced API response time by 40% by caching repeated requests",
            "\u2022 Designed a REST API with 12 endpoints and JWT login",
            "\u2022 Automated deployment using Docker, cutting setup from 2 hours to 10 minutes",
            "INTERNSHIP",
            "\u2022 Developed unit tests raising coverage from 35% to 70% at Acme, 2024",
            "\u2022 Implemented a dashboard for 300 users, 2024");

    private static final String BAD =
            "I am a hard-working team player. I was responsible for many tasks. Worked on things. "
                    + "My duties included helping with work. Looking for a job.";

    private static AtsChecker.Check find(AtsChecker.Report r, String label) {
        return r.checks().stream().filter(c -> c.label().equals(label)).findFirst().orElseThrow();
    }

    @Test
    void goodFresherResumePassesTheBasics() {
        AtsChecker.Report r = AtsChecker.run(GOOD, 1, true, true);
        assertEquals("pass", find(r, "Email address").status());
        assertEquals("pass", find(r, "Phone number").status());
        assertEquals("pass", find(r, "Skills section").status());
        assertEquals("pass", find(r, "Projects or internships").status());
        assertEquals("pass", find(r, "Numbers and results").status());
        assertTrue(r.score() >= 80, "score was " + r.score());
    }

    @Test
    void badResumeScoresLowAndHasFixes() {
        AtsChecker.Report r = AtsChecker.run(BAD, 0, false, false);
        assertTrue(r.score() < 30, "score was " + r.score());
        assertEquals("fail", find(r, "Email address").status());
        assertEquals("fail", find(r, "Filler phrases").status());
        assertFalse(find(r, "Email address").fix().isBlank());
    }

    @Test
    void freshersAreNotPenalisedForMissingWorkExperience() {
        String noExperience = GOOD.replace("INTERNSHIP", "ACHIEVEMENTS");
        AtsChecker.Report early = AtsChecker.run(noExperience, 1, true, true);
        assertEquals("pass", find(early, "Projects or internships").status());

        String noProjectsEither = noExperience.replace("PROJECTS", "ACTIVITIES");
        AtsChecker.Report experienced = AtsChecker.run(noProjectsEither, 1, false, false);
        assertEquals("fail", find(experienced, "Work experience section").status());
    }

    @Test
    void yearRangesAreNotMistakenForPhoneNumbers() {
        AtsChecker.Report r = AtsChecker.run("Name\nname@site.com\nB.Tech 2021 - 2025\nSkills\nJava", 1, true, true);
        assertEquals("fail", find(r, "Phone number").status());
    }

    @Test
    void sameTextAlwaysGivesTheSameScore() {
        assertEquals(AtsChecker.run(GOOD, 1, true, true).score(), AtsChecker.run(GOOD, 1, true, true).score());
    }
}
