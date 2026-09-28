package com.resumeai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.resumeai.model.Analysis;
import com.resumeai.repository.AnalysisRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

/** Extra AI helpers: bullet rewriter, tailored summary, cover letter, interview questions. */
@Service
public class AiToolsService {

    private final GroqService groq;
    private final AnalysisRepository repo;
    private final RateLimiter limiter;
    private final ObjectMapper mapper;

    @Value("${app.limits.ai-tools-per-hour:40}")
    private int perHour;

    public AiToolsService(GroqService groq, AnalysisRepository repo, RateLimiter limiter, ObjectMapper mapper) {
        this.groq = groq;
        this.repo = repo;
        this.limiter = limiter;
        this.mapper = mapper;
    }

    public ObjectNode rewrite(Long userId, String bullet, Long analysisId) {
        throttle(userId);
        String b = bullet == null ? "" : bullet.strip();
        if (b.length() < 10 || b.length() > 600) {
            throw bad("Enter a resume bullet between 10 and 600 characters.");
        }
        String role = "not specified";
        String level = "not specified";
        if (analysisId != null) {
            Analysis a = load(userId, analysisId);
            if (a.getRole() != null && !a.getRole().isBlank()) role = a.getRole();
            Levels.Info lv = Levels.find(a.getLevel());
            if (lv != null) level = lv.label();
        }
        JsonNode raw = groq.chatJson(Prompts.REWRITE_SYSTEM,
                "TARGET ROLE: " + role + "\nEXPERIENCE LEVEL: " + level + "\n\nBULLET:\n" + b, 0.4);
        String improved = raw.path("improved").asText("").strip();
        if (improved.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The AI could not rewrite that. Please try again.");
        }
        ObjectNode out = mapper.createObjectNode();
        out.put("improved", improved);
        out.put("why", raw.path("why").asText("").strip());
        return out;
    }

    public ObjectNode generate(Long userId, Long analysisId, String kind) {
        throttle(userId);
        Analysis a = load(userId, analysisId);
        if (a.getResumeText() == null || a.getResumeText().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This older score has no saved resume text. Score the resume again to use this tool.");
        }
        Levels.Info lv = Levels.get(a.getLevel());
        String jd = a.getJobDescription() == null ? "" : cut(a.getJobDescription(), 3000);
        String user = Prompts.contextBlock(lv, a.getRole(), jd) + "\n\nRESUME:\n" + cut(a.getResumeText(), 8000);

        String system = switch (kind == null ? "" : kind) {
            case "summary" -> Prompts.SUMMARY_SYSTEM;
            case "coverLetter" -> Prompts.COVER_LETTER_SYSTEM;
            case "interview" -> Prompts.INTERVIEW_SYSTEM;
            default -> throw bad("Unknown tool.");
        };

        JsonNode raw = groq.chatJson(system, user, 0.4);
        ObjectNode out = mapper.createObjectNode();
        switch (kind) {
            case "summary" -> {
                out.put("summary", raw.path("summary").asText("").strip());
                out.set("skills", strings(raw.path("skills"), 12));
                if (out.path("summary").asText().isEmpty()) throw failed();
            }
            case "coverLetter" -> {
                out.put("coverLetter", raw.path("coverLetter").asText("").strip());
                if (out.path("coverLetter").asText().isEmpty()) throw failed();
            }
            default -> {
                ArrayNode qs = out.putArray("questions");
                for (JsonNode q : raw.path("questions")) {
                    String question = q.path("question").asText("").strip();
                    if (question.isEmpty() || qs.size() >= 8) continue;
                    ObjectNode n = qs.addObject();
                    n.put("question", question);
                    n.put("why", q.path("why").asText("").strip());
                    n.put("tip", q.path("tip").asText("").strip());
                }
                if (qs.isEmpty()) throw failed();
            }
        }
        return out;
    }

    // ---------- helpers ----------

    private Analysis load(Long userId, Long id) {
        if (id == null) throw bad("Missing analysis id.");
        return repo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Analysis not found."));
    }

    private void throttle(Long userId) {
        if (!limiter.tryAcquire("ai:" + userId, perHour, Duration.ofHours(1))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "You have used a lot of AI tools this hour. Please try again a little later.");
        }
    }

    private ArrayNode strings(JsonNode node, int max) {
        ArrayNode arr = mapper.createArrayNode();
        if (node != null && node.isArray()) {
            for (JsonNode n : node) {
                String s = n.asText("").strip();
                if (!s.isEmpty() && arr.size() < max) arr.add(s);
            }
        }
        return arr;
    }

    private static String cut(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }

    private static ResponseStatusException failed() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The AI returned an empty answer. Please try again.");
    }
}
