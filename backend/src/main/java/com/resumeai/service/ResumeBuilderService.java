package com.resumeai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.resumeai.controller.BuilderController.BuildRequest;
import com.resumeai.controller.BuilderController.EducationItem;
import com.resumeai.controller.BuilderController.ExperienceItem;
import com.resumeai.controller.BuilderController.ProjectItem;
import com.resumeai.controller.BuilderController.CertItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds a resume from the wizard's structured input. Facts (names, dates, companies, schools,
 * contact details) always come straight from what the person typed and are never touched by the AI;
 * only free-text wording (summary, bullets, project descriptions, skill list clean-up) is polished.
 */
@Service
public class ResumeBuilderService {

    private final GroqService groq;
    private final RateLimiter limiter;
    private final ObjectMapper mapper;

    @Value("${app.limits.ai-tools-per-hour:40}")
    private int perHour;

    public ResumeBuilderService(GroqService groq, RateLimiter limiter, ObjectMapper mapper) {
        this.groq = groq;
        this.limiter = limiter;
        this.mapper = mapper;
    }

    public ObjectNode generate(Long userId, BuildRequest req) {
        if (!limiter.tryAcquire("builder:" + userId, perHour, Duration.ofHours(1))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "You have used a lot of AI tools this hour. Please try again a little later.");
        }
        if (req.personal() == null || isBlank(req.personal().fullName())) {
            throw bad("Enter your full name.");
        }
        List<ExperienceItem> experience = req.experience() == null ? List.of() : req.experience();
        List<EducationItem> education = req.education() == null ? List.of() : req.education();
        List<String> skills = cleanStrings(req.skills());
        List<ProjectItem> projects = req.projects() == null ? List.of() : req.projects();
        List<CertItem> certifications = req.certifications() == null ? List.of() : req.certifications();

        if (experience.isEmpty() && education.isEmpty() && skills.isEmpty()) {
            throw bad("Add at least some experience, education or skills before generating a resume.");
        }

        String targetLabel = targetLabel(req);
        JsonNode raw = callAi(req, targetLabel, experience, skills, projects);

        ObjectNode out = mapper.createObjectNode();
        ObjectNode personal = out.putObject("personal");
        personal.put("fullName", req.personal().fullName().strip());
        personal.put("email", str(req.personal().email()));
        personal.put("phone", str(req.personal().phone()));
        personal.put("location", str(req.personal().location()));
        personal.put("linkedin", str(req.personal().linkedin()));
        personal.put("portfolio", str(req.personal().portfolio()));
        personal.put("github", str(req.personal().github()));

        out.put("targetLabel", targetLabel);
        out.put("level", str(req.level()));

        String summary = raw.path("summary").asText("").strip();
        out.put("summary", summary.isEmpty() ? str(req.summary()) : summary);

        ArrayNode skillsOut = out.putArray("skills");
        List<String> aiSkills = cleanStrings(strings(raw.path("skills")));
        // only keep AI-returned skills that are actually skills the person entered (case-insensitive) —
        // this is the guardrail that stops the AI from ever adding a skill nobody typed.
        Set<String> allowed = new LinkedHashSet<>();
        for (String s : skills) allowed.add(s.toLowerCase());
        List<String> finalSkills = new ArrayList<>();
        for (String s : aiSkills) if (allowed.remove(s.toLowerCase())) finalSkills.add(s);
        for (String s : skills) if (allowed.contains(s.toLowerCase())) finalSkills.add(s); // anything AI dropped
        if (finalSkills.isEmpty()) finalSkills = skills;
        finalSkills.forEach(skillsOut::add);

        ArrayNode expOut = out.putArray("experience");
        JsonNode aiExp = raw.path("experience");
        for (int i = 0; i < experience.size(); i++) {
            ExperienceItem e = experience.get(i);
            ObjectNode n = expOut.addObject();
            n.put("company", str(e.company()));
            n.put("title", str(e.title()));
            n.put("location", str(e.location()));
            n.put("dateRange", dateRange(e.startDate(), e.endDate(), e.current()));
            List<String> original = cleanStrings(e.bullets());
            List<String> polished = i < aiExp.size() ? cleanStrings(strings(aiExp.get(i).path("bullets"))) : List.of();
            ArrayNode bullets = n.putArray("bullets");
            (polished.isEmpty() ? original : polished).forEach(bullets::add);
        }

        ArrayNode eduOut = out.putArray("education");
        for (EducationItem ed : education) {
            ObjectNode n = eduOut.addObject();
            n.put("school", str(ed.school()));
            n.put("degree", str(ed.degree()));
            n.put("field", str(ed.field()));
            n.put("dateRange", dateRange(ed.startDate(), ed.endDate(), false));
            n.put("grade", str(ed.grade()));
        }

        ArrayNode projOut = out.putArray("projects");
        JsonNode aiProj = raw.path("projects");
        for (int i = 0; i < projects.size(); i++) {
            ProjectItem p = projects.get(i);
            ObjectNode n = projOut.addObject();
            n.put("name", str(p.name()));
            n.put("tech", str(p.tech()));
            n.put("link", str(p.link()));
            String polished = i < aiProj.size() ? aiProj.get(i).path("description").asText("").strip() : "";
            n.put("description", polished.isEmpty() ? str(p.description()) : polished);
        }

        ArrayNode certOut = out.putArray("certifications");
        for (CertItem c : certifications) {
            ObjectNode n = certOut.addObject();
            n.put("name", str(c.name()));
            n.put("issuer", str(c.issuer()));
            n.put("year", str(c.year()));
        }

        return out;
    }

    private JsonNode callAi(BuildRequest req, String targetLabel, List<ExperienceItem> experience,
                             List<String> skills, List<ProjectItem> projects) {
        ObjectNode draft = mapper.createObjectNode();
        draft.put("summaryDraft", str(req.summary()));
        ArrayNode skillsNode = draft.putArray("skills");
        skills.forEach(skillsNode::add);
        ArrayNode expNode = draft.putArray("experience");
        for (ExperienceItem e : experience) {
            ObjectNode n = expNode.addObject();
            n.put("title", str(e.title()));
            n.put("company", str(e.company()));
            ArrayNode b = n.putArray("bullets");
            cleanStrings(e.bullets()).forEach(b::add);
        }
        ArrayNode projNode = draft.putArray("projects");
        for (ProjectItem p : projects) {
            ObjectNode n = projNode.addObject();
            n.put("name", str(p.name()));
            n.put("tech", str(p.tech()));
            n.put("description", str(p.description()));
        }
        try {
            return groq.chatJson(Prompts.RESUME_BUILD_SYSTEM,
                    Prompts.builderUser(targetLabel, req.level(), draft.toString()), 0.3);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            // if AI polish fails for any reason, fall back to the person's own wording rather than failing the request
            return mapper.createObjectNode();
        }
    }

    private String targetLabel(BuildRequest req) {
        String type = str(req.targetType());
        if ("Other".equalsIgnoreCase(type) && !isBlank(req.targetTypeOther())) {
            type = req.targetTypeOther().strip();
        }
        String role = str(req.targetRole());
        if (!role.isEmpty() && !type.isEmpty()) return role + " (" + type + ")";
        if (!role.isEmpty()) return role;
        return type;
    }

    private static String dateRange(String start, String end, boolean current) {
        String s = str(start);
        String e = current ? "Present" : str(end);
        if (s.isEmpty() && e.isEmpty()) return "";
        if (s.isEmpty()) return e;
        if (e.isEmpty()) return s;
        return s + " – " + e;
    }

    private static List<String> strings(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode n : node) out.add(n.asText(""));
        }
        return out;
    }

    private static List<String> cleanStrings(List<String> in) {
        List<String> out = new ArrayList<>();
        if (in == null) return out;
        for (String s : in) {
            if (s == null) continue;
            String t = s.strip();
            if (!t.isEmpty() && t.length() <= 500) out.add(t);
        }
        return out;
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
    private static String str(String s) { return s == null ? "" : s.strip(); }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}
