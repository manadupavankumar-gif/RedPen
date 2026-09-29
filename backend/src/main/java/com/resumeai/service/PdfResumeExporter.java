package com.resumeai.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders a generated resume (see ResumeBuilderService's output shape) as a single-column PDF.
 * Colors and spacing intentionally mirror the on-screen "resume-preview" so the download looks
 * like what the person already approved in the review step.
 */
@Component
public class PdfResumeExporter {

    private static final float MARGIN = 54f;
    private static final float PAGE_W = PDRectangle.LETTER.getWidth();
    private static final float PAGE_H = PDRectangle.LETTER.getHeight();
    private static final float TEXT_W = PAGE_W - 2 * MARGIN;

    // same palette as --brand / --text / --muted / --line in the app's light theme
    private static final int[] NAVY = {18, 32, 90};
    private static final int[] TEXTC = {24, 32, 70};
    private static final int[] MUTED = {91, 102, 133};
    private static final int[] LINEC = {221, 226, 238};

    private final PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private final PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDFont italic = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

    public byte[] build(JsonNode r) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Cursor c = new Cursor(doc);

            JsonNode personal = r.path("personal");
            String name = personal.path("fullName").asText("");
            c.lineText(name, bold, 22, NAVY);
            c.gap(4);
            String contactLine = String.join("    |    ", nonEmpty(
                    personal.path("location").asText(""), personal.path("phone").asText(""),
                    personal.path("email").asText(""), personal.path("linkedin").asText(""),
                    personal.path("portfolio").asText(""), personal.path("github").asText("")));
            if (!contactLine.isBlank()) c.lineText(contactLine, regular, 10, MUTED);
            c.gap(14);

            String summary = r.path("summary").asText("");
            if (!summary.isBlank()) {
                c.heading("Summary");
                c.paragraph(summary, regular, 10.5f, TEXTC);
                c.gap(6);
            }

            List<String> skills = new ArrayList<>();
            r.path("skills").forEach(n -> skills.add(n.asText("")));
            if (!skills.isEmpty()) {
                c.heading("Skills");
                c.paragraph(String.join("   •   ", skills), regular, 10.5f, TEXTC);
                c.gap(6);
            }

            if (r.path("experience").size() > 0) {
                c.heading("Experience");
                for (JsonNode e : r.path("experience")) {
                    String left = e.path("title").asText("");
                    String company = e.path("company").asText("");
                    if (!company.isBlank()) left = left.isBlank() ? company : left + " · " + company;
                    c.rowLine(left, e.path("dateRange").asText(""));
                    String loc = e.path("location").asText("");
                    if (!loc.isBlank()) c.subLine(loc);
                    c.gap(2);
                    for (JsonNode b : e.path("bullets")) c.bullet(b.asText(""));
                    c.gap(10);
                }
            }

            if (r.path("projects").size() > 0) {
                c.heading("Projects");
                for (JsonNode p : r.path("projects")) {
                    c.rowLine(p.path("name").asText(""), p.path("tech").asText(""));
                    String desc = p.path("description").asText("");
                    if (!desc.isBlank()) { c.gap(2); c.paragraph(desc, regular, 10.5f, TEXTC); }
                    String link = p.path("link").asText("");
                    if (!link.isBlank()) c.subLine(link);
                    c.gap(10);
                }
            }

            if (r.path("education").size() > 0) {
                c.heading("Education");
                for (JsonNode ed : r.path("education")) {
                    String left = ed.path("degree").asText("");
                    String field = ed.path("field").asText("");
                    if (!field.isBlank()) left = left.isBlank() ? field : left + " in " + field;
                    String school = ed.path("school").asText("");
                    c.rowLine(left.isBlank() ? school : left, ed.path("dateRange").asText(""));
                    if (!left.isBlank() && !school.isBlank()) c.subLine(school);
                    String grade = ed.path("grade").asText("");
                    if (!grade.isBlank()) c.subLine(grade);
                    c.gap(10);
                }
            }

            if (r.path("certifications").size() > 0) {
                c.heading("Certifications");
                for (JsonNode ce : r.path("certifications")) {
                    String certName = ce.path("name").asText("");
                    String issuer = ce.path("issuer").asText("");
                    String year = ce.path("year").asText("");
                    String line = certName + (issuer.isBlank() ? "" : " — " + issuer) + (year.isBlank() ? "" : " (" + year + ")");
                    c.bullet(line);
                }
            }

            c.close();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not create the PDF file.");
        }
    }

    private static List<String> nonEmpty(String... vals) {
        List<String> out = new ArrayList<>();
        for (String v : vals) if (v != null && !v.isBlank()) out.add(v.strip());
        return out;
    }

    /** Tracks the current page/position, sets colors, and wraps long lines; opens a new page automatically. */
    private class Cursor {
        final PDDocument doc;
        PDPageContentStream cs;
        float y;

        Cursor(PDDocument doc) throws IOException {
            this.doc = doc;
            newPage();
        }

        void newPage() throws IOException {
            if (cs != null) cs.close();
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            cs = new PDPageContentStream(doc, page);
            y = PAGE_H - MARGIN;
        }

        void ensure(float need) throws IOException {
            if (y - need < MARGIN) newPage();
        }

        void gap(float h) throws IOException { ensure(h); y -= h; }

        /** A section title styled like the on-screen preview's h4: small, bold, navy, uppercase, underlined. */
        void heading(String label) throws IOException {
            ensure(26);
            cs.setNonStrokingColor(NAVY[0], NAVY[1], NAVY[2]);
            cs.beginText();
            cs.setFont(bold, 10.5f);
            cs.setCharacterSpacing(0.8f);
            cs.newLineAtOffset(MARGIN, y);
            cs.showText(sanitize(label.toUpperCase()));
            cs.endText();
            cs.setCharacterSpacing(0f);
            y -= 6;
            cs.setLineWidth(0.8f);
            cs.setStrokingColor(LINEC[0], LINEC[1], LINEC[2]);
            cs.moveTo(MARGIN, y);
            cs.lineTo(PAGE_W - MARGIN, y);
            cs.stroke();
            y -= 12;
        }

        /** One line of text at the given size/color, advancing by a normal single-line height. */
        void lineText(String s, PDFont font, float size, int[] color) throws IOException {
            if (s == null || s.isBlank()) return;
            float lh = size * 1.25f;
            ensure(lh);
            write(s, font, size, MARGIN, y, color);
            y -= lh;
        }

        /** Small italic muted line, used for a location, school name, or link under a bold row. */
        void subLine(String s) throws IOException {
            if (s == null || s.isBlank()) return;
            ensure(13);
            write(s, italic, 9.5f, MARGIN, y, MUTED);
            y -= 13;
        }

        /** Bold "title — date" row, spaced apart, like the preview's .rp-row. */
        void rowLine(String left, String right) throws IOException {
            ensure(16);
            write(left, bold, 11, MARGIN, y, TEXTC);
            if (right != null && !right.isBlank()) {
                float w = safeWidth(right, bold, 10.5f);
                write(right, bold, 10.5f, PAGE_W - MARGIN - w, y, TEXTC);
            }
            y -= 15;
        }

        void paragraph(String s, PDFont font, float size, int[] color) throws IOException {
            for (String line : wrap(s, font, size, TEXT_W)) {
                float lh = size * 1.4f;
                ensure(lh);
                write(line, font, size, MARGIN, y, color);
                y -= lh;
            }
        }

        void bullet(String s) throws IOException {
            float size = 10.5f, indent = 13f;
            List<String> lines = wrap(s, regular, size, TEXT_W - indent);
            for (int i = 0; i < lines.size(); i++) {
                float lh = size * 1.4f;
                ensure(lh);
                if (i == 0) write("-", bold, size, MARGIN, y, NAVY);
                write(lines.get(i), regular, size, MARGIN + indent, y, TEXTC);
                y -= lh;
            }
        }

        void write(String s, PDFont font, float size, float x, float yy, int[] color) throws IOException {
            cs.setNonStrokingColor(color[0], color[1], color[2]);
            cs.beginText();
            cs.setFont(font, size);
            cs.newLineAtOffset(x, yy);
            cs.showText(sanitize(s));
            cs.endText();
        }

        void close() throws IOException { if (cs != null) cs.close(); }
    }

    private static float safeWidth(String s, PDFont font, float size) {
        try { return font.getStringWidth(sanitize(s)) / 1000 * size; } catch (IOException e) { return 0; }
    }

    private List<String> wrap(String s, PDFont font, float size, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String para : sanitize(s).split("\\R")) {
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (font.getStringWidth(candidate) / 1000 * size > maxWidth && !line.isEmpty()) {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(candidate);
                }
            }
            lines.add(line.toString());
        }
        return lines;
    }

    /** PDFBox's base-14 fonts only support WinAnsi text; swap the handful of characters people actually type. */
    private static String sanitize(String s) {
        if (s == null) return "";
        return s
                .replace('\u2018', '\'').replace('\u2019', '\'')
                .replace('\u201C', '"').replace('\u201D', '"')
                .replace('\u2013', '-').replace('\u2014', '-')
                .replace('\u2022', '-')
                .replaceAll("[^\\x00-\\xFF]", "?");
    }
}
