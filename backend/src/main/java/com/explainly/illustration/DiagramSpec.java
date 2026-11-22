package com.explainly.illustration;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.*;

/** Validated, size-limited diagram description produced by the AI and rendered by {@link SvgDiagramRenderer}. */
public record DiagramSpec(String type, String title, List<Node> nodes, List<Edge> edges, List<Column> columns) {

    public static final Set<String> TYPES = Set.of("flowchart", "process", "architecture", "steps", "mindmap", "comparison", "cycle");

    public record Node(String id, String label, String detail, Integer layer) {}
    public record Edge(String from, String to, String label) {}
    public record Column(String title, List<String> items) {}

    public static Optional<DiagramSpec> fromJson(JsonNode n) {
        if (n == null || !n.isObject()) return Optional.empty();
        String type = n.path("type").asText("").toLowerCase(Locale.ROOT).trim();
        if (!TYPES.contains(type)) type = "process";
        String title = clip(n.path("title").asText(""), 120);
        if (title.isBlank()) return Optional.empty();

        List<Node> nodes = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonNode node : n.path("nodes")) {
            if (nodes.size() >= 14) break;
            String id = clip(node.path("id").asText(""), 40);
            String label = clip(node.path("label").asText(""), 60);
            if (id.isBlank() || label.isBlank() || !ids.add(id)) continue;
            String detail = node.hasNonNull("detail") ? clip(node.path("detail").asText(""), 90) : null;
            Integer layer = node.path("layer").isInt() ? Math.max(0, Math.min(8, node.path("layer").asInt())) : null;
            nodes.add(new Node(id, label, detail == null || detail.isBlank() ? null : detail, layer));
        }
        List<Edge> edges = new ArrayList<>();
        for (JsonNode e : n.path("edges")) {
            if (edges.size() >= 30) break;
            String from = e.path("from").asText(""), to = e.path("to").asText("");
            if (!ids.contains(from) || !ids.contains(to) || from.equals(to)) continue;
            String label = e.hasNonNull("label") ? clip(e.path("label").asText(""), 30) : null;
            edges.add(new Edge(from, to, label == null || label.isBlank() ? null : label));
        }
        List<Column> columns = new ArrayList<>();
        for (JsonNode c : n.path("columns")) {
            if (columns.size() >= 4) break;
            String ct = clip(c.path("title").asText(""), 40);
            List<String> items = new ArrayList<>();
            for (JsonNode item : c.path("items")) {
                if (items.size() >= 7) break;
                String it = clip(item.asText(""), 110);
                if (!it.isBlank()) items.add(it);
            }
            if (!ct.isBlank() && !items.isEmpty()) columns.add(new Column(ct, items));
        }
        if (type.equals("comparison")) {
            if (columns.size() < 2) return Optional.empty();
        } else if (nodes.size() < 2) {
            return Optional.empty();
        }
        return Optional.of(new DiagramSpec(type, title, nodes, edges, columns));
    }

    private static String clip(String s, int max) {
        String t = s == null ? "" : s.replaceAll("[\\p{Cntrl}]", " ").trim();
        return t.length() <= max ? t : t.substring(0, max - 1).trim() + "…";
    }
}
