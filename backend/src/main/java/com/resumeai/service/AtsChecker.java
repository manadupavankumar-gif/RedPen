package com.resumeai.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule-based resume checks. No AI involved: the same text always gives the same result,
 * and it runs in a few milliseconds.
 */
public final class AtsChecker {

    public record Check(String label, String status, String detail, String fix, int weight) {}

    public record Report(int score, List<Check> checks) {}

    private static final String PASS = "pass";
    private static final String WARN = "warn";
    private static final String FAIL = "fail";

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(\\+?\\d[\\d\\s().-]{8,}\\d)(?!\\d)");
    private static final Pattern LINKS = Pattern.compile(
            "(?i)(linkedin\\.com|github\\.com|gitlab\\.com|bitbucket\\.org|behance\\.net|dribbble\\.com|kaggle\\.com|leetcode\\.com|https?://|www\\.)");
    private static final Pattern BULLET = Pattern.compile(
            "^\\s*(?:[\\u2022\\u25CF\\u25AA\\u25E6\\u2023\\u2219\\uF0B7\\uF0A7\\uF0D8]\\s*|[-*\\u2013\\u00B7]\\s+)(.+)$");
    private static final Pattern METRIC = Pattern.compile(
            "(?i)(\\d+(?:\\.\\d+)?\\s?(?:%|x\\b|k\\b|m\\b|\\+)|[$\\u20B9\\u20AC\\u00A3]\\s?\\d[\\d,.]*"
                    + "|\\b\\d{2,}\\s+(?:users|customers|clients|projects|members|people|records|requests|students|apps|"
                    + "applications|employees|engineers|stakeholders|endpoints|features|tests|reports|leads|hours))");
    private static final Pattern YEAR = Pattern.compile("\\b(?:19|20)\\d{2}\\b");
    private static final Pattern PRONOUN = Pattern.compile("(?<![\\w/])(?:I|me|my|My)(?![\\w/])");

    private static final Set<String> ACTION_VERBS = new HashSet<>(Arrays.asList(
            "achieved", "analyzed", "analysed", "architected", "automated", "built", "collaborated", "configured",
            "consolidated", "coordinated", "created", "cut", "debugged", "delivered", "deployed", "designed",
            "developed", "directed", "drove", "enabled", "engineered", "enhanced", "established", "executed",
            "expanded", "generated", "guided", "identified", "implemented", "improved", "increased", "initiated",
            "integrated", "introduced", "launched", "led", "maintained", "managed", "mentored", "migrated",
            "modernized", "negotiated", "optimized", "orchestrated", "organized", "owned", "partnered", "performed",
            "planned", "presented", "produced", "programmed", "reduced", "refactored", "resolved", "restructured",
            "revamped", "scaled", "secured", "shipped", "simplified", "solved", "spearheaded", "standardized",
            "streamlined", "strengthened", "supported", "tested", "trained", "transformed", "upgraded", "wrote",
            "won", "published", "researched", "won", "conducted", "curated", "facilitated", "won"));

    private static final List<String> WEAK_PHRASES = Arrays.asList(
            "responsible for", "worked on", "helped with", "duties included", "hard-working", "hardworking",
            "team player", "detail-oriented", "detail oriented", "results-oriented", "results oriented",
            "go-getter", "self-motivated", "references available", "references on request");

    private AtsChecker() {}

    /**
     * @param pages    page count if known, otherwise 0 or less
     * @param early    fresher or intern (work experience is not expected)
     * @param onePage  true when a single page is the norm for this level
     */
    public static Report run(String rawText, int pages, boolean early, boolean onePage) {
        String text = rawText == null ? "" : rawText;
        String lower = text.toLowerCase(Locale.ROOT);
        String[] lines = text.split("\\R");
        List<Check> checks = new ArrayList<>();

        // ---- contact ----
        boolean hasEmail = EMAIL.matcher(text).find();
        checks.add(new Check("Email address", hasEmail ? PASS : FAIL,
                hasEmail ? "Found" : "Not found",
                hasEmail ? "" : "Add a professional email address at the top of your resume.", 10));

        boolean hasPhone = hasPhone(text);
        checks.add(new Check("Phone number", hasPhone ? PASS : FAIL,
                hasPhone ? "Found" : "Not found",
                hasPhone ? "" : "Add a phone number so recruiters can reach you.", 8));

        boolean hasLink = LINKS.matcher(text).find();
        checks.add(new Check("LinkedIn, GitHub or portfolio link", hasLink ? PASS : WARN,
                hasLink ? "Found" : "No profile link found",
                hasLink ? "" : (early
                        ? "Add your GitHub and LinkedIn links so recruiters can see your projects."
                        : "Add your LinkedIn profile link (and GitHub or portfolio if relevant)."), 4));

        // ---- length ----
        int words = text.isBlank() ? 0 : text.trim().split("\\s+").length;
        int maxPages = onePage ? 1 : 2;
        int maxWords = onePage ? 750 : 1200;
        String lenStatus = PASS;
        if (pages > 0) {
            if (pages > maxPages + 1) lenStatus = FAIL;
            else if (pages > maxPages) lenStatus = WARN;
        }
        if (words < 200) lenStatus = worst(lenStatus, FAIL);
        else if (words < 300) lenStatus = worst(lenStatus, WARN);
        else if (words > maxWords) lenStatus = worst(lenStatus, WARN);
        String lenDetail = (pages > 0 ? pages + (pages == 1 ? " page, " : " pages, ") : "") + words + " words";
        checks.add(new Check("Length", lenStatus, lenDetail,
                PASS.equals(lenStatus) ? "" : "Aim for " + (onePage ? "one page" : "one to two pages")
                        + " with roughly 300 to " + maxWords + " words.", 8));

        // ---- sections ----
        boolean hasExp = hasHeading(lines, "experience", "employment", "work history", "internship");
        boolean hasProjects = hasHeading(lines, "projects");
        if (early) {
            boolean ok = hasExp || hasProjects;
            checks.add(new Check("Projects or internships", ok ? PASS : FAIL,
                    ok ? "Section found" : "No Projects or Internship section found",
                    ok ? "" : "Add a Projects section: what you built, the tools you used and the result.", 10));
        } else {
            checks.add(new Check("Work experience section", hasExp ? PASS : FAIL,
                    hasExp ? "Section found" : "No Experience section found",
                    hasExp ? "" : "Add a clearly labelled Experience section with your roles and dates.", 10));
        }

        boolean hasEdu = hasHeading(lines, "education", "academic", "qualification");
        checks.add(new Check("Education section", hasEdu ? PASS : (early ? FAIL : WARN),
                hasEdu ? "Section found" : "No Education section found",
                hasEdu ? "" : "Add an Education section with your degree, college and year.", 8));

        boolean hasSkills = hasHeading(lines, "skills", "technologies", "competencies", "tech stack");
        checks.add(new Check("Skills section", hasSkills ? PASS : FAIL,
                hasSkills ? "Section found" : "No Skills section found",
                hasSkills ? "" : "Add a Skills section that lists your tools and technologies; ATS filters scan it first.", 10));

        // ---- writing quality ----
        List<String> bullets = new ArrayList<>();
        for (String line : lines) {
            Matcher m = BULLET.matcher(line);
            if (m.matches()) bullets.add(m.group(1).trim());
        }
        String bulletStatus = bullets.size() >= 5 ? PASS : (bullets.isEmpty() ? FAIL : WARN);
        checks.add(new Check("Bullet points", bulletStatus, bullets.size() + " found",
                PASS.equals(bulletStatus) ? "" : "Describe your work in short bullet points instead of long paragraphs.", 6));

        int metrics = countMatches(METRIC, text);
        int metricGood = early ? 2 : 4;
        String metricStatus = metrics >= metricGood ? PASS : (metrics >= 1 ? WARN : FAIL);
        checks.add(new Check("Numbers and results", metricStatus, metrics + " measurable figures",
                PASS.equals(metricStatus) ? "" : "Add numbers to your results (percentages, users, time saved, marks, project size).", 10));

        List<String> candidates = new ArrayList<>(bullets);
        if (candidates.size() < 3) {
            for (String line : lines) {
                String t = line.trim();
                if (t.split("\\s+").length >= 6) candidates.add(t);
            }
        }
        int verbHits = 0;
        for (String c : candidates) {
            String first = firstWord(c);
            if (!first.isEmpty() && ACTION_VERBS.contains(first)) verbHits++;
        }
        int verbGood = early ? 3 : 4;
        String verbStatus = verbHits >= verbGood ? PASS : (verbHits >= 1 ? WARN : FAIL);
        checks.add(new Check("Strong action verbs", verbStatus, verbHits + " lines start with an action verb",
                PASS.equals(verbStatus) ? "" : "Start each bullet with a strong verb such as Built, Led, Reduced, Automated or Designed.", 6));

        Set<String> weakFound = new LinkedHashSet<>();
        int weakCount = 0;
        for (String p : WEAK_PHRASES) {
            int c = countOccurrences(lower, p);
            if (c > 0) {
                weakFound.add(p);
                weakCount += c;
            }
        }
        String weakStatus = weakCount <= 1 ? PASS : (weakCount <= 4 ? WARN : FAIL);
        checks.add(new Check("Filler phrases", weakStatus,
                weakCount == 0 ? "None found" : weakCount + " found: " + String.join(", ", weakFound),
                PASS.equals(weakStatus) ? "" : "Replace phrases like \"responsible for\" or \"team player\" with what you actually achieved.", 5));

        int years = countMatches(YEAR, text);
        String dateStatus = years >= 2 ? PASS : (years == 1 ? WARN : FAIL);
        checks.add(new Check("Dates", dateStatus, years + " years mentioned",
                PASS.equals(dateStatus) ? "" : "Add dates (month and year) to your education, roles and projects.", 4));

        int pronouns = countMatches(PRONOUN, text);
        String proStatus = pronouns > 3 ? WARN : PASS;
        checks.add(new Check("First-person words", proStatus, pronouns + " uses of I / me / my",
                PASS.equals(proStatus) ? "" : "Remove \"I\" and \"my\"; resumes read better as short action statements.", 3));

        int odd = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean privateUse = c >= '\uE000' && c <= '\uF8FF' && c != '\uF0B7' && c != '\uF0A7' && c != '\uF0D8';
            if (c == '\uFFFD' || privateUse) odd++;
        }
        String oddStatus = odd > 5 ? WARN : PASS;
        checks.add(new Check("Readable characters", oddStatus,
                odd > 5 ? odd + " unreadable symbols (icons or special fonts)" : "Text reads cleanly",
                PASS.equals(oddStatus) ? "" : "Avoid icons, graphics and special fonts; ATS software cannot read them. Use plain text.", 3));

        // ---- score ----
        double earned = 0;
        int total = 0;
        for (Check c : checks) {
            total += c.weight();
            if (PASS.equals(c.status())) earned += c.weight();
            else if (WARN.equals(c.status())) earned += c.weight() / 2.0;
        }
        int score = total == 0 ? 0 : (int) Math.round(100.0 * earned / total);
        return new Report(ScoreMath.clamp(score), checks);
    }

    // ---------- helpers ----------

    private static boolean hasPhone(String text) {
        Matcher m = PHONE.matcher(text);
        while (m.find()) {
            int digits = m.group(1).replaceAll("\\D", "").length();
            if (digits >= 10 && digits <= 13) return true;
        }
        return false;
    }

    private static boolean hasHeading(String[] lines, String... keys) {
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty() || t.length() > 45 || t.split("\\s+").length > 5) continue;
            if (BULLET.matcher(t).matches()) continue;
            String l = t.toLowerCase(Locale.ROOT);
            for (String k : keys) {
                if (l.contains(k)) return true;
            }
        }
        return false;
    }

    private static int countMatches(Pattern p, String text) {
        Matcher m = p.matcher(text);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    private static int countOccurrences(String haystack, String needle) {
        int n = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) >= 0) {
            n++;
            idx += needle.length();
        }
        return n;
    }

    private static String firstWord(String s) {
        Matcher m = Pattern.compile("^[^A-Za-z]*([A-Za-z]+)").matcher(s);
        return m.find() ? m.group(1).toLowerCase(Locale.ROOT) : "";
    }

    private static String worst(String a, String b) {
        return rank(a) >= rank(b) ? a : b;
    }

    private static int rank(String s) {
        return FAIL.equals(s) ? 2 : WARN.equals(s) ? 1 : 0;
    }
}
