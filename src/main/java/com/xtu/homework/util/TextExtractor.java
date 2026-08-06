package com.xtu.homework.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 课程资源文本提取（AI 出题素材）
 * 支持：.txt / .md（UTF-8 文本）、.docx（POI）、.pdf（PDFBox）
 */
public class TextExtractor {

    private TextExtractor() {}

    public static String extract(MultipartFile file) throws IOException {
        return extract(file.getOriginalFilename(), file.getBytes());
    }

    public static String extract(String fileName, byte[] bytes) throws IOException {
        String name = fileName == null ? "" : fileName.toLowerCase();
        if (name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".markdown")) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        if (name.endsWith(".docx")) {
            try (InputStream is = new java.io.ByteArrayInputStream(bytes)) {
                return extractDocx(is);
            }
        }
        if (name.endsWith(".pdf")) {
            try (InputStream is = new java.io.ByteArrayInputStream(bytes)) {
                return extractPdf(is);
            }
        }
        throw new IOException("不支持的文件类型: " + fileName + "（支持 txt/md/docx/pdf）");
    }

    private static String extractDocx(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (XWPFDocument doc = new XWPFDocument(is)) {
            for (XWPFParagraph p : doc.getParagraphs()) {
                String text = p.getText();
                if (text != null && !text.isBlank()) {
                    sb.append(text).append('\n');
                }
            }
            // 表格内容也提取
            doc.getTables().forEach(table -> table.getRows().forEach(row ->
                    row.getTableCells().forEach(cell -> {
                        String text = cell.getText();
                        if (text != null && !text.isBlank()) sb.append(text).append('\n');
                    })));
        }
        return sb.toString();
    }

    private static String extractPdf(InputStream is) throws IOException {
        try (PDDocument doc = PDDocument.load(is)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }
}
