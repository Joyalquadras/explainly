package com.explainly.notes;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Renders the markdown subset used in notes (headings, lists, code, bold/italic, paragraphs) to PDF. */
@Component
public class MarkdownPdfRenderer {

    private static final Pattern INLINE = Pattern.compile("\\*\\*(.+?)\\*\\*|`([^`]+)`|\\*(.+?)\\*");
    private static final Color ACCENT = new Color(79, 70, 229);
    private static final Color MUTED = new Color(75, 85, 99);

    public byte[] render(String title, String subtitle, String markdown) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 56, 56, 56, 56);
        PdfWriter.getInstance(doc, out);
        doc.addTitle(title);
        doc.addCreator("Explainly");
        doc.open();
        Paragraph t = new Paragraph(title, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, ACCENT));
        t.setSpacingAfter(4);
        doc.add(t);
        if (subtitle != null) {
            Paragraph s = new Paragraph(subtitle, FontFactory.getFont(FontFactory.HELVETICA, 10, MUTED));
            s.setSpacingAfter(14);
            doc.add(s);
        }
        String[] lines = (markdown == null ? "" : markdown).replace("\r", "").split("\n");
        boolean inCode = false;
        StringBuilder code = new StringBuilder();
        com.lowagie.text.List list = null;
        for (String line : lines) {
            if (line.trim().startsWith("```")) {
                if (inCode) {
                    Paragraph p = new Paragraph(code.toString(), FontFactory.getFont(FontFactory.COURIER, 9, Color.DARK_GRAY));
                    p.setIndentationLeft(12);
                    p.setSpacingBefore(4);
                    p.setSpacingAfter(8);
                    doc.add(p);
                    code.setLength(0);
                }
                inCode = !inCode;
                continue;
            }
            if (inCode) {
                code.append(line).append('\n');
                continue;
            }
            String trimmed = line.trim();
            boolean bullet = trimmed.matches("^([-*+]|\\d+[.)]|- \\[[ xX]\\])\\s+.*");
            if (!bullet && list != null) {
                doc.add(list);
                list = null;
            }
            if (trimmed.isEmpty()) continue;
            if (trimmed.startsWith("#")) {
                int level = (int) trimmed.chars().takeWhile(c -> c == '#').count();
                String text = trimmed.substring(level).trim();
                float size = level == 1 ? 17 : level == 2 ? 14 : 12;
                Paragraph h = new Paragraph(strip(text), FontFactory.getFont(FontFactory.HELVETICA_BOLD, size, level <= 2 ? ACCENT : Color.BLACK));
                h.setSpacingBefore(level == 1 ? 12 : 9);
                h.setSpacingAfter(4);
                doc.add(h);
            } else if (bullet) {
                if (list == null) {
                    list = new com.lowagie.text.List(false, 12);
                    list.setListSymbol(new Chunk("•  ", FontFactory.getFont(FontFactory.HELVETICA, 11, ACCENT)));
                    list.setIndentationLeft(10);
                }
                String text = trimmed.replaceFirst("^([-*+]|\\d+[.)])\\s+", "").replaceFirst("^\\[[ xX]\\]\\s+", "");
                ListItem item = new ListItem();
                inline(text, item);
                item.setSpacingAfter(2);
                list.add(item);
            } else {
                Paragraph p = new Paragraph();
                inline(trimmed.startsWith(">") ? trimmed.substring(1).trim() : trimmed, p);
                p.setSpacingAfter(6);
                p.setLeading(15);
                doc.add(p);
            }
        }
        if (list != null) doc.add(list);
        if (inCode && !code.isEmpty()) doc.add(new Paragraph(code.toString(), FontFactory.getFont(FontFactory.COURIER, 9)));
        doc.close();
        return out.toByteArray();
    }

    private static void inline(String text, Phrase target) {
        Font regular = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.BLACK);
        Matcher m = INLINE.matcher(text);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) target.add(new Chunk(text.substring(last, m.start()), regular));
            if (m.group(1) != null) target.add(new Chunk(m.group(1), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11)));
            else if (m.group(2) != null) target.add(new Chunk(m.group(2), FontFactory.getFont(FontFactory.COURIER, 10, Color.DARK_GRAY)));
            else target.add(new Chunk(m.group(3), FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 11)));
            last = m.end();
        }
        if (last < text.length()) target.add(new Chunk(text.substring(last), regular));
    }

    private static String strip(String s) {
        return s.replaceAll("\\*\\*(.+?)\\*\\*", "$1").replaceAll("`([^`]+)`", "$1");
    }
}
