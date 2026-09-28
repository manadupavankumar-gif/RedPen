package com.resumeai.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.resumeai.service.PdfResumeExporter;
import com.resumeai.service.ResumeBuilderService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** New, additive feature: build a resume from scratch (separate from the existing scorer). */
@RestController
@RequestMapping("/api/builder")
public class BuilderController {

    public record Personal(String fullName, String email, String phone, String location,
                            String linkedin, String portfolio, String github) {}

    public record ExperienceItem(String company, String title, String location,
                                  String startDate, String endDate, boolean current, List<String> bullets) {}

    public record EducationItem(String school, String degree, String field,
                                 String startDate, String endDate, String grade) {}

    public record ProjectItem(String name, String description, String tech, String link) {}

    public record CertItem(String name, String issuer, String year) {}

    public record BuildRequest(Personal personal, String targetType, String targetTypeOther, String targetRole,
                                String level, String summary, List<ExperienceItem> experience,
                                List<EducationItem> education, List<String> skills,
                                List<ProjectItem> projects, List<CertItem> certifications) {}

    public record ExportRequest(JsonNode resume) {}

    private final ResumeBuilderService builder;
    private final PdfResumeExporter pdf;

    public BuilderController(ResumeBuilderService builder, PdfResumeExporter pdf) {
        this.builder = builder;
        this.pdf = pdf;
    }

    /** Turns the wizard's answers into a polished resume (wording only \u2014 facts pass through untouched). */
    @PostMapping("/generate")
    public ObjectNode generate(@RequestAttribute("userId") Long userId, @RequestBody BuildRequest req) {
        return builder.generate(userId, req);
    }

    /** Renders the (possibly person-edited) generated resume as a downloadable PDF. */
    @PostMapping("/export/pdf")
    public ResponseEntity<byte[]> exportPdf(@RequestAttribute("userId") Long userId, @RequestBody ExportRequest req) {
        byte[] bytes = pdf.build(req.resume());
        String name = req.resume().path("personal").path("fullName").asText("resume");
        String file = name.replaceAll("[^\\w -]+", "").trim().replaceAll("\\s+", "-").toLowerCase();
        if (file.isBlank()) file = "resume";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file + ".pdf\"")
                .body(bytes);
    }
}
