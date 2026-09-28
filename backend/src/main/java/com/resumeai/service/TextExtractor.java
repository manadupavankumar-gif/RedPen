package com.resumeai.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Reads plain text out of PDF, DOCX and TXT resumes. */
@Component
public class TextExtractor {

    private static final int MAX_CHARS = 12_000; // keeps the AI call small = fast

    /** @param pages page count when known (PDF only), otherwise 0 */
    public record Extracted(String text, int pages) {}

    public Extracted extract(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        try {
            byte[] bytes = file.getBytes();
            if (name.endsWith(".pdf")) {
                try (PDDocument doc = Loader.loadPDF(bytes)) {
                    String text = new PDFTextStripper().getText(doc);
                    return new Extracted(tidy(text), doc.getNumberOfPages());
                }
            }
            if (name.endsWith(".docx")) {
                try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes));
                     XWPFWordExtractor ex = new XWPFWordExtractor(doc)) {
                    return new Extracted(tidy(ex.getText()), 0);
                }
            }
            if (name.endsWith(".txt")) {
                return new Extracted(tidy(new String(bytes, StandardCharsets.UTF_8)), 0);
            }
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Unsupported file type. Upload a PDF, DOCX or TXT resume.");
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Could not read this file. Make sure it is not password-protected or corrupted.");
        }
    }

    public Extracted fromText(String pasted) {
        return new Extracted(tidy(pasted), 0);
    }

    private String tidy(String text) {
        if (text == null) return "";
        String t = text.replace("\r", "")
                .replaceAll("[ \\t\\u00A0]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return t.length() > MAX_CHARS ? t.substring(0, MAX_CHARS) : t;
    }
}
