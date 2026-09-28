package com.resumeai.service;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Turns plain text (cover letter, summary) into a Word document. */
@Component
public class DocxExporter {

    public byte[] build(String title, String text) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (title != null && !title.isBlank()) {
                XWPFParagraph p = doc.createParagraph();
                p.setSpacingAfter(240);
                XWPFRun r = p.createRun();
                r.setBold(true);
                r.setFontSize(16);
                r.setText(title.strip());
            }
            for (String para : text.strip().split("\\R\\R+")) {
                XWPFParagraph p = doc.createParagraph();
                p.setSpacingAfter(200);
                XWPFRun r = p.createRun();
                r.setFontSize(11);
                r.setFontFamily("Calibri");
                String[] lines = para.split("\\R");
                for (int i = 0; i < lines.length; i++) {
                    if (i > 0) r.addBreak();
                    r.setText(lines[i]);
                }
            }
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not create the Word file.");
        }
    }
}
