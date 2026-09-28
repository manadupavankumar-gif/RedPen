package com.resumeai.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.resumeai.service.AiToolsService;
import com.resumeai.service.DocxExporter;
import com.resumeai.service.JobFetcher;
import com.resumeai.service.ResumeService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/resume")
public class ResumeController {

    public record UrlRequest(String url) {}
    public record RewriteRequest(String bullet, Long analysisId) {}
    public record GenerateRequest(String kind) {}
    public record ExportRequest(String title, String text) {}

    private static final MediaType DOCX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final ResumeService service;
    private final AiToolsService tools;
    private final JobFetcher jobFetcher;
    private final DocxExporter docx;

    public ResumeController(ResumeService service, AiToolsService tools, JobFetcher jobFetcher, DocxExporter docx) {
        this.service = service;
        this.tools = tools;
        this.jobFetcher = jobFetcher;
        this.docx = docx;
    }

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ObjectNode analyze(@RequestAttribute("userId") Long userId,
                              @RequestParam(value = "file", required = false) MultipartFile file,
                              @RequestParam(value = "resumeText", required = false) String resumeText,
                              @RequestParam(value = "level", required = false) String level,
                              @RequestParam(value = "role", required = false) String role,
                              @RequestParam(value = "jobDescription", required = false) String jobDescription) {
        return service.analyze(userId, file, resumeText, new ResumeService.Target(level, role, jobDescription));
    }

    @PostMapping(value = "/compare", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ObjectNode compare(@RequestAttribute("userId") Long userId,
                              @RequestParam(value = "fileA", required = false) MultipartFile fileA,
                              @RequestParam(value = "fileB", required = false) MultipartFile fileB,
                              @RequestParam(value = "level", required = false) String level,
                              @RequestParam(value = "role", required = false) String role,
                              @RequestParam(value = "jobDescription", required = false) String jobDescription) {
        return service.compare(userId, fileA, fileB, new ResumeService.Target(level, role, jobDescription));
    }

    @GetMapping("/usage")
    public Map<String, Object> usage(@RequestAttribute("userId") Long userId) {
        return service.usage(userId);
    }

    @PostMapping("/job-from-url")
    public Map<String, String> jobFromUrl(@RequestBody UrlRequest r) {
        return Map.of("text", jobFetcher.fetchText(r.url()));
    }

    @PostMapping("/rewrite")
    public ObjectNode rewrite(@RequestAttribute("userId") Long userId, @RequestBody RewriteRequest r) {
        return tools.rewrite(userId, r.bullet(), r.analysisId());
    }

    @PostMapping("/history/{id}/generate")
    public ObjectNode generate(@RequestAttribute("userId") Long userId, @PathVariable Long id,
                               @RequestBody GenerateRequest r) {
        return tools.generate(userId, id, r.kind());
    }

    @PostMapping("/export/docx")
    public ResponseEntity<byte[]> exportDocx(@RequestBody ExportRequest r) {
        if (r.text() == null || r.text().isBlank() || r.text().length() > 20_000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nothing to export.");
        }
        byte[] bytes = docx.build(r.title(), r.text());
        return ResponseEntity.ok()
                .contentType(DOCX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("redpen-document.docx").build().toString())
                .body(bytes);
    }

    @GetMapping("/history")
    public List<Map<String, Object>> history(@RequestAttribute("userId") Long userId) {
        return service.history(userId);
    }

    @GetMapping("/history/{id}")
    public ObjectNode get(@RequestAttribute("userId") Long userId, @PathVariable Long id) {
        return service.get(userId, id);
    }

    @DeleteMapping("/history/{id}")
    public ResponseEntity<Void> delete(@RequestAttribute("userId") Long userId, @PathVariable Long id) {
        service.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/history/{id}/share")
    public Map<String, String> share(@RequestAttribute("userId") Long userId, @PathVariable Long id) {
        return Map.of("token", service.share(userId, id));
    }

    @DeleteMapping("/history/{id}/share")
    public ResponseEntity<Void> unshare(@RequestAttribute("userId") Long userId, @PathVariable Long id) {
        service.unshare(userId, id);
        return ResponseEntity.noContent().build();
    }
}
