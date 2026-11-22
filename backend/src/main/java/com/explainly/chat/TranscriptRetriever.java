package com.explainly.chat;

import com.explainly.ai.TranscriptFormatter;

import java.util.*;
import java.util.stream.Collectors;

/** Lightweight BM25 retrieval over transcript windows; no external vector store required. */
public final class TranscriptRetriever {

    private static final Set<String> STOP = Set.of("the", "a", "an", "and", "or", "of", "to", "in", "is", "it", "that",
            "this", "for", "on", "with", "as", "are", "be", "was", "what", "how", "why", "can", "you", "me", "i", "do",
            "does", "explain", "about", "again", "please", "give", "tell", "we", "so", "if", "my", "your", "they", "there",
            "then", "but", "not", "at", "by", "from", "which", "when", "where", "who", "will", "would", "should", "could");

    private TranscriptRetriever() {}

    public static List<TranscriptFormatter.Block> topWindows(List<TranscriptFormatter.Block> blocks, String query, int k) {
        // Windows of two consecutive blocks (~1 minute) give the model enough context per hit.
        List<TranscriptFormatter.Block> windows = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i += 2) {
            String text = blocks.get(i).text() + (i + 1 < blocks.size() ? " " + blocks.get(i + 1).text() : "");
            windows.add(new TranscriptFormatter.Block(blocks.get(i).startSeconds(), text));
        }
        List<String> terms = tokenize(query);
        if (terms.isEmpty() || windows.size() <= k) return windows.subList(0, Math.min(k, windows.size()));

        List<List<String>> docs = windows.stream().map(w -> tokenize(w.text())).toList();
        double avgLen = docs.stream().mapToInt(List::size).average().orElse(1);
        Map<String, Integer> df = new HashMap<>();
        for (List<String> d : docs) new HashSet<>(d).forEach(t -> df.merge(t, 1, Integer::sum));
        int n = docs.size();
        double k1 = 1.4, b = 0.75;
        double[] scores = new double[n];
        for (int i = 0; i < n; i++) {
            Map<String, Long> tf = docs.get(i).stream().collect(Collectors.groupingBy(t -> t, Collectors.counting()));
            for (String term : terms) {
                long f = tf.getOrDefault(term, 0L);
                if (f == 0) continue;
                double idf = Math.log(1 + (n - df.getOrDefault(term, 0) + 0.5) / (df.getOrDefault(term, 0) + 0.5));
                scores[i] += idf * (f * (k1 + 1)) / (f + k1 * (1 - b + b * docs.get(i).size() / avgLen));
            }
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) order.add(i);
        order.sort((x, y) -> Double.compare(scores[y], scores[x]));
        List<Integer> chosen = order.stream().limit(k).filter(i -> scores[i] > 0).sorted().toList();
        if (chosen.isEmpty()) chosen = List.of(0); // nothing matched: give the opening as minimal context
        return chosen.stream().map(windows::get).toList();
    }

    static List<String> tokenize(String text) {
        List<String> out = new ArrayList<>();
        for (String raw : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}+#]+")) {
            if (raw.length() < 2 || STOP.contains(raw)) continue;
            out.add(stem(raw));
        }
        return out;
    }

    private static String stem(String w) {
        if (w.length() > 5 && w.endsWith("ing")) return w.substring(0, w.length() - 3);
        if (w.length() > 4 && w.endsWith("es")) return w.substring(0, w.length() - 2);
        if (w.length() > 3 && w.endsWith("s") && !w.endsWith("ss")) return w.substring(0, w.length() - 1);
        return w;
    }
}
