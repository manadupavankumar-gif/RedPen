package com.resumeai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.resumeai.model.Analysis;
import com.resumeai.repository.AnalysisRepository;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ResumeService {

    /** Bump when prompts or scoring rules change, so old cached results are not reused. */
    private static final String PROMPT_VERSION = "v3";
    private static final String[] SECTIONS = {"keywordMatch", "formatting", "impact", "skills", "experience"};
    private static final int MAX_JD_CHARS = 5_000;

    public record Target(String level, String role, String jobDescription) {}

    private final TextExtractor extractor;
    private final GroqService groq;
    private final AnalysisRepository repo;
    private final ObjectMapper mapper;
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final SecureRandom random = new SecureRandom();

    @Value("${app.limits.daily-analyses:20}")
    private int dailyLimit;

    public ResumeService(TextExtractor extractor, GroqService groq, AnalysisRepository repo, ObjectMapper mapper) {
        this.extractor = extractor;
        this.groq = groq;
        this.repo = repo;
        this.mapper = mapper;
    }

    @PreDestroy
    void shutdown() {
        pool.shutdown();
    }

    // ================= score one resume =================

    public ObjectNode analyze(Long userId, MultipartFile file, String pastedText, Target rawTarget) {
        Target t = cleanTarget(rawTarget);
        TextExtractor.Extracted resume = readResume(file, pastedText);
        String hash = hash(resume.text(), t);

        Optional<Analysis> hit = repo.findFirstByUserIdAndInputHashOrderByCreatedAtDesc(userId, hash);
        if (hit.isPresent()) return toResult(hit.get(), true); // same input again: instant, no AI call

        enforceLimit(userId, 1);
        return compute(userId, resume, fileName(file), t, hash);
    }

    // ================= compare two versions =================

    public ObjectNode compare(Long userId, MultipartFile fileA, MultipartFile fileB, Target rawTarget) {
        Target t = cleanTarget(rawTarget);
        if (fileA == null || fileA.isEmpty() || fileB == null || fileB.isEmpty()) {
            throw bad("Upload both resume versions to compare them.");
        }
        TextExtractor.Extracted a = readResume(fileA, null);
        TextExtractor.Extracted b = readResume(fileB, null);
        enforceLimit(userId, 2);

        CompletableFuture<ObjectNode> fa = CompletableFuture.supplyAsync(() -> runOrCached(userId, a, fileName(fileA), t), pool);
        CompletableFuture<ObjectNode> fb = CompletableFuture.supplyAsync(() -> runOrCached(userId, b, fileName(fileB), t), pool);
        try {
            ObjectNode out = mapper.createObjectNode();
            out.set("a", fa.join());
            out.set("b", fb.join());
            return out;
        } catch (CompletionException e) {
            if (e.getCause() instanceof ResponseStatusException rse) throw rse;
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not compare the resumes. Please try again.");
        }
    }

    private ObjectNode runOrCached(Long userId, TextExtractor.Extracted resume, String name, Target t) {
        String hash = hash(resume.text(), t);
        Optional<Analysis> hit = repo.findFirstByUserIdAndInputHashOrderByCreatedAtDesc(userId, hash);
        if (hit.isPresent()) return toResult(hit.get(), true);
        return compute(userId, resume, name, t, hash);
    }

    // ================= the actual scoring =================

    private ObjectNode compute(Long userId, TextExtractor.Extracted resume, String name, Target t, String hash) {
        Levels.Info lv = Levels.get(t.level());
        AtsChecker.Report ats = AtsChecker.run(resume.text(), resume.pages(), lv.early(), lv.onePage());
        JsonNode raw = groq.chatJson(Prompts.ANALYSIS_SYSTEM,
                Prompts.analysisUser(lv, t.role(), t.jobDescription(), resume.text()), 0.0);
        ObjectNode result = buildResult(raw, ats, lv, t.role());

        Analysis a = new Analysis();
        a.setUserId(userId);
        a.setFileName(name);
        a.setJobLabel(label(t));
        a.setScore(result.path("score").asInt());
        a.setLevel(lv.key());
        a.setRole(t.role().isEmpty() ? null : t.role());
        a.setJobDescription(t.jobDescription().isEmpty() ? null : t.jobDescription());
        a.setResumeText(resume.text());
        a.setInputHash(hash);
        try {
            a.setResultJson(mapper.writeValueAsString(result));
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save the result.");
        }
        repo.save(a);
        return withMeta(result, a, false, true);
    }

    /** Never trust the model's shape: rebuild a clean, bounded result and add the rule-based checks. */
    private ObjectNode buildResult(JsonNode raw, AtsChecker.Report ats, Levels.Info lv, String role) {
        int ai = ScoreMath.clamp(raw.path("score").asInt(0));
        int score = ScoreMath.combine(ai, ats.score());

        ObjectNode out = mapper.createObjectNode();
        out.put("score", score);
        out.put("aiScore", ai);
        out.put("atsScore", ats.score());
        out.put("verdict", ScoreMath.verdict(score));
        out.put("summary", raw.path("summary").asText("").trim());
        out.put("level", lv.key());
        out.put("levelLabel", lv.shortLabel());
        out.put("role", role);

        ObjectNode sections = out.putObject("sections");
        for (String key : SECTIONS) {
            int v = ScoreMath.clamp(raw.path("sections").path(key).asInt(ai));
            sections.put(key, "formatting".equals(key) ? ats.score() : v); // formatting comes from the rule checks
        }
        out.set("matchedKeywords", textArray(raw.path("matchedKeywords"), 10));
        out.set("missingKeywords", textArray(raw.path("missingKeywords"), 10));
        out.set("strengths", textArray(raw.path("strengths"), 4));
        out.set("weakBullets", textArray(raw.path("weakBullets"), 4));

        ArrayNode suggestions = textArray(raw.path("suggestions"), 5);
        if (score < 75) {
            // make sure a weak resume always gets 5 fixes: top up from the failed rule checks
            for (String status : new String[]{"fail", "warn"}) {
                for (AtsChecker.Check c : ats.checks()) {
                    if (suggestions.size() < 5 && status.equals(c.status()) && !c.fix().isBlank()) {
                        suggestions.add(c.fix());
                    }
                }
            }
        }
        out.set("suggestions", suggestions);

        ArrayNode checks = out.putArray("atsChecks");
        for (AtsChecker.Check c : ats.checks()) {
            ObjectNode n = checks.addObject();
            n.put("label", c.label());
            n.put("status", c.status());
            n.put("detail", c.detail());
            if (!"pass".equals(c.status())) n.put("fix", c.fix());
        }
        return out;
    }

    // ================= history, sharing, usage =================

    public List<Map<String, Object>> history(Long userId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Analysis a : repo.findByUserIdOrderByCreatedAtDesc(userId)) {
            Levels.Info lv = Levels.find(a.getLevel());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("fileName", a.getFileName());
            m.put("jobLabel", a.getJobLabel());
            m.put("levelLabel", lv == null ? "" : lv.shortLabel());
            m.put("score", a.getScore());
            m.put("verdict", ScoreMath.verdict(a.getScore()));
            m.put("createdAt", a.getCreatedAt().toString());
            m.put("shared", a.getShareToken() != null);
            out.add(m);
        }
        return out;
    }

    public ObjectNode get(Long userId, Long id) {
        return toResult(find(userId, id), false);
    }

    public void delete(Long userId, Long id) {
        repo.delete(find(userId, id));
    }

    public Map<String, Object> usage(Long userId) {
        long used = repo.countByUserIdAndCreatedAtAfter(userId, startOfDayUtc());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("used", used);
        m.put("limit", dailyLimit);
        m.put("remaining", Math.max(0, dailyLimit - used));
        return m;
    }

    public String share(Long userId, Long id) {
        Analysis a = find(userId, id);
        if (a.getShareToken() == null) {
            byte[] raw = new byte[18];
            random.nextBytes(raw);
            a.setShareToken(Base64.getUrlEncoder().withoutPadding().encodeToString(raw));
            repo.save(a);
        }
        return a.getShareToken();
    }

    public void unshare(Long userId, Long id) {
        Analysis a = find(userId, id);
        a.setShareToken(null);
        repo.save(a);
    }

    /** Public read-only view: no file name, no resume text, no job description. */
    public ObjectNode publicReport(String token) {
        if (token == null || token.length() > 64) throw notFound();
        Analysis a = repo.findByShareToken(token).orElseThrow(ResumeService::notFound);
        ObjectNode r = readJson(a);
        r.put("jobLabel", a.getJobLabel());
        r.put("createdAt", a.getCreatedAt().toString());
        return r;
    }

    // ================= helpers =================

    private Analysis find(Long userId, Long id) {
        return repo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Analysis not found."));
    }

    private ObjectNode toResult(Analysis a, boolean cached) {
        return withMeta(readJson(a), a, cached, true);
    }

    private ObjectNode readJson(Analysis a) {
        try {
            return (ObjectNode) mapper.readTree(a.getResultJson());
        } catch (JsonProcessingException | ClassCastException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Stored result is unreadable.");
        }
    }

    private ObjectNode withMeta(ObjectNode result, Analysis a, boolean cached, boolean owner) {
        result.put("id", a.getId());
        result.put("fileName", a.getFileName());
        result.put("jobLabel", a.getJobLabel());
        result.put("createdAt", a.getCreatedAt().toString());
        result.put("cached", cached);
        result.put("hasResumeText", a.getResumeText() != null && !a.getResumeText().isBlank());
        if (owner && a.getShareToken() != null) result.put("shareToken", a.getShareToken());
        return result;
    }

    private TextExtractor.Extracted readResume(MultipartFile file, String pasted) {
        TextExtractor.Extracted e;
        if (file != null && !file.isEmpty()) {
            e = extractor.extract(file);
        } else if (pasted != null && pasted.strip().length() >= 80) {
            e = extractor.fromText(pasted);
        } else {
            throw bad("Upload your resume file, or paste the resume text (at least a few lines).");
        }
        if (e.text().length() < 80) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "No readable text found. If this is a scanned image PDF, export a text-based PDF or upload a DOCX.");
        }
        return e;
    }

    private Target cleanTarget(Target raw) {
        String role = raw.role() == null ? "" : raw.role().strip();
        if (role.length() > 120) role = role.substring(0, 120);
        String jd = raw.jobDescription() == null ? "" : raw.jobDescription().strip();
        if (jd.length() > MAX_JD_CHARS) jd = jd.substring(0, MAX_JD_CHARS);
        if (jd.length() < 20) {
            if (role.isEmpty()) throw bad("Pick a target role or paste a job description so your resume has something to be scored against.");
            jd = ""; // role + level is enough
        }
        return new Target(Levels.get(raw.level()).key(), role, jd);
    }

    private void enforceLimit(Long userId, int needed) {
        long used = repo.countByUserIdAndCreatedAtAfter(userId, startOfDayUtc());
        if (used + needed > dailyLimit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Daily limit reached (" + dailyLimit + " scores per day). It resets at midnight UTC.");
        }
    }

    private static Instant startOfDayUtc() {
        return LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private ArrayNode textArray(JsonNode node, int max) {
        ArrayNode arr = mapper.createArrayNode();
        if (node != null && node.isArray()) {
            for (JsonNode n : node) {
                String s = n.asText("").trim();
                if (!s.isEmpty() && arr.size() < max) arr.add(s);
            }
        }
        return arr;
    }

    private static String label(Target t) {
        String base = !t.role().isEmpty() ? t.role()
                : t.jobDescription().lines().map(String::strip).filter(l -> !l.isEmpty()).findFirst().orElse("Job description");
        return base.length() > 90 ? base.substring(0, 90) + "…" : base;
    }

    private static String fileName(MultipartFile f) {
        return f == null || f.getOriginalFilename() == null || f.getOriginalFilename().isBlank()
                ? "Pasted resume" : f.getOriginalFilename();
    }

    private static String hash(String resumeText, Target t) {
        String all = PROMPT_VERSION + "\u0001" + t.level() + "\u0001" + t.role() + "\u0001"
                + t.jobDescription() + "\u0001" + resumeText;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(all.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "This shared report does not exist or was turned off.");
    }
}
