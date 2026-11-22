package com.explainly.transcript;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses WebVTT, SRT, "timestamp + text" copies of YouTube transcripts, and plain text. */
public final class TranscriptParser {

    private static final Pattern CUE_TIME = Pattern.compile(
            "((?:\\d{1,2}:)?\\d{1,2}:\\d{2}(?:[.,]\\d{1,3})?)\\s*-->\\s*((?:\\d{1,2}:)?\\d{1,2}:\\d{2}(?:[.,]\\d{1,3})?)");
    private static final Pattern INLINE_STAMP = Pattern.compile(
            "^\\s*[\\[(]?((?:\\d{1,2}:)?\\d{1,2}:\\d{2})[\\])]?\\s*[-–:]?\\s*(.*)$");
    private static final Pattern TAGS = Pattern.compile("<[^>]+>");

    private TranscriptParser() {}

    public static List<TranscriptSegment> parse(String raw) {
        if (raw == null) return List.of();
        String text = raw.replace("﻿", "").replace("\r\n", "\n").replace('\r', '\n');
        List<TranscriptSegment> result;
        if (CUE_TIME.matcher(text).find()) {
            result = parseCues(text);
        } else {
            result = parseInlineStamps(text);
            if (result.isEmpty()) result = parsePlain(text);
        }
        return mergeDuplicates(result);
    }

    public static String plainText(List<TranscriptSegment> segments) {
        StringBuilder sb = new StringBuilder();
        for (TranscriptSegment s : segments) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(s.text());
        }
        return sb.toString().trim();
    }

    public static boolean isTimed(List<TranscriptSegment> segments) {
        return !segments.isEmpty() && segments.stream().allMatch(s -> s.start() != null);
    }

    private static List<TranscriptSegment> parseCues(String text) {
        List<TranscriptSegment> out = new ArrayList<>();
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            Matcher m = CUE_TIME.matcher(lines[i]);
            if (!m.find()) continue;
            double start = toSeconds(m.group(1));
            double end = toSeconds(m.group(2));
            StringBuilder cue = new StringBuilder();
            int j = i + 1;
            while (j < lines.length && !lines[j].isBlank() && !CUE_TIME.matcher(lines[j]).find()) {
                String clean = clean(lines[j]);
                if (!clean.isEmpty()) cue.append(cue.isEmpty() ? "" : " ").append(clean);
                j++;
            }
            i = j - 1;
            if (!cue.isEmpty()) out.add(new TranscriptSegment(start, end, cue.toString()));
        }
        return out;
    }

    private static List<TranscriptSegment> parseInlineStamps(String text) {
        List<TranscriptSegment> out = new ArrayList<>();
        Double currentStart = null;
        StringBuilder current = new StringBuilder();
        int stamped = 0;
        int nonEmpty = 0;
        for (String line : text.split("\n")) {
            if (line.isBlank()) continue;
            nonEmpty++;
            Matcher m = INLINE_STAMP.matcher(line);
            if (m.matches()) {
                stamped++;
                if (currentStart != null && !current.isEmpty()) {
                    out.add(new TranscriptSegment(currentStart, null, current.toString().trim()));
                }
                currentStart = toSeconds(m.group(1));
                current = new StringBuilder(clean(m.group(2)));
            } else if (currentStart != null) {
                current.append(current.isEmpty() ? "" : " ").append(clean(line));
            }
        }
        if (currentStart != null && !current.isEmpty()) out.add(new TranscriptSegment(currentStart, null, current.toString().trim()));
        // Only treat as timestamped when timestamps are a substantial share of lines.
        if (stamped < 2 || stamped * 4 < nonEmpty) return List.of();
        List<TranscriptSegment> withEnds = new ArrayList<>();
        for (int i = 0; i < out.size(); i++) {
            Double end = i + 1 < out.size() ? out.get(i + 1).start() : out.get(i).start() + 5;
            withEnds.add(new TranscriptSegment(out.get(i).start(), end, out.get(i).text()));
        }
        return withEnds;
    }

    private static List<TranscriptSegment> parsePlain(String text) {
        List<TranscriptSegment> out = new ArrayList<>();
        for (String para : text.split("\n\\s*\n|\n")) {
            String clean = clean(para);
            if (clean.isEmpty()) continue;
            // Split long paragraphs into ~600 character chunks on sentence boundaries.
            while (clean.length() > 800) {
                int cut = clean.lastIndexOf(". ", 600);
                if (cut < 200) cut = 600;
                out.add(new TranscriptSegment(null, null, clean.substring(0, cut + 1).trim()));
                clean = clean.substring(cut + 1).trim();
            }
            if (!clean.isEmpty()) out.add(new TranscriptSegment(null, null, clean));
        }
        return out;
    }

    private static List<TranscriptSegment> mergeDuplicates(List<TranscriptSegment> in) {
        List<TranscriptSegment> out = new ArrayList<>();
        for (TranscriptSegment s : in) {
            if (!out.isEmpty()) {
                TranscriptSegment prev = out.getLast();
                if (prev.text().equals(s.text())) {
                    out.set(out.size() - 1, new TranscriptSegment(prev.start(), s.end(), prev.text()));
                    continue;
                }
                // Auto-generated VTT often repeats the previous line as a prefix ("rolling" captions).
                if (s.text().startsWith(prev.text() + " ") && prev.start() != null) {
                    out.set(out.size() - 1, new TranscriptSegment(prev.start(), s.end(), s.text()));
                    continue;
                }
            }
            out.add(s);
        }
        return out;
    }

    private static String clean(String s) {
        return TAGS.matcher(s).replaceAll("").replace("&nbsp;", " ").replace("&amp;", "&")
                .replace("&lt;", "<").replace("&gt;", ">").replaceAll("\\s+", " ").trim();
    }

    static double toSeconds(String stamp) {
        String[] parts = stamp.replace(',', '.').split(":");
        double total = 0;
        for (String p : parts) total = total * 60 + Double.parseDouble(p);
        return total;
    }
}
