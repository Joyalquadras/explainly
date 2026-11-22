package com.explainly.illustration;

import com.explainly.illustration.DiagramSpec.Edge;
import com.explainly.illustration.DiagramSpec.Node;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Deterministic SVG renderer for {@link DiagramSpec}. All text is XML-escaped and no scripts, links or
 * external references are emitted, so the output is safe to embed.
 */
@Component
public class SvgDiagramRenderer {

    private static final String FONT = "Inter, ui-sans-serif, system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif";
    private static final String[][] PALETTE = {
            {"#EEF2FF", "#6366F1"}, {"#ECFEFF", "#0891B2"}, {"#FFFBEB", "#D97706"},
            {"#ECFDF5", "#059669"}, {"#FFF1F2", "#E11D48"}, {"#F5F3FF", "#7C3AED"}};
    private static final String TEXT = "#111827";
    private static final String MUTED = "#4B5563";
    private static final String LINE = "#94A3B8";

    private record Box(double x, double y, double w, double h) {
        double cx() { return x + w / 2; }
        double cy() { return y + h / 2; }
    }

    public String render(DiagramSpec spec) {
        return switch (spec.type()) {
            case "comparison" -> comparison(spec);
            case "steps" -> steps(spec);
            case "mindmap" -> mindmap(spec);
            case "cycle" -> cycle(spec);
            default -> layered(spec);
        };
    }

    // ---------------------------------------------------------------- layered (flowchart/process/architecture)

    private String layered(DiagramSpec spec) {
        List<Node> nodes = spec.nodes();
        Map<String, Integer> layer = assignLayers(spec);
        int maxLayer = layer.values().stream().max(Integer::compare).orElse(0);
        List<List<Node>> rows = new ArrayList<>();
        for (int i = 0; i <= maxLayer; i++) rows.add(new ArrayList<>());
        for (Node n : nodes) rows.get(layer.get(n.id())).add(n);
        rows.removeIf(List::isEmpty);

        double nodeW = 190, gapX = 36, gapY = 64, pad = 28;
        int widest = rows.stream().mapToInt(List::size).max().orElse(1);
        double width = Math.max(420, widest * nodeW + (widest - 1) * gapX + pad * 2);
        Map<String, Box> boxes = new HashMap<>();
        double y = pad;
        for (List<Node> row : rows) {
            double rowW = row.size() * nodeW + (row.size() - 1) * gapX;
            double x = (width - rowW) / 2;
            double rowH = row.stream().mapToDouble(n -> nodeHeight(n, nodeW)).max().orElse(60);
            for (Node n : row) {
                double h = nodeHeight(n, nodeW);
                boxes.put(n.id(), new Box(x, y + (rowH - h) / 2, nodeW, h));
                x += nodeW + gapX;
            }
            y += rowH + gapY;
        }
        double height = y - gapY + pad;

        Svg svg = new Svg(width, height, spec.title());
        if (spec.type().equals("architecture") && rows.size() > 1) {
            // subtle layer bands make tiers readable
            for (List<Node> row : rows) {
                Box first = boxes.get(row.getFirst().id());
                double top = row.stream().mapToDouble(n -> boxes.get(n.id()).y()).min().orElse(first.y()) - 12;
                double bottom = row.stream().mapToDouble(n -> boxes.get(n.id()).y() + boxes.get(n.id()).h()).max().orElse(first.y()) + 12;
                svg.raw("<rect x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"%.1f\" rx=\"14\" fill=\"#F8FAFC\" stroke=\"#E2E8F0\"/>"
                        .formatted(pad / 2, top, width - pad, bottom - top));
            }
        }
        for (Edge e : spec.edges()) {
            Box a = boxes.get(e.from()), b = boxes.get(e.to());
            boolean forward = layer.get(e.to()) > layer.get(e.from());
            svg.edge(a, b, e.label(), !forward);
        }
        int i = 0;
        for (Node n : nodes) svg.node(boxes.get(n.id()), n, PALETTE[(spec.type().equals("architecture") ? layer.get(n.id()) : i++) % PALETTE.length], 12);
        return svg.build();
    }

    private Map<String, Integer> assignLayers(DiagramSpec spec) {
        Map<String, Integer> layer = new LinkedHashMap<>();
        boolean explicit = spec.type().equals("architecture") && spec.nodes().stream().allMatch(n -> n.layer() != null);
        if (explicit) {
            spec.nodes().forEach(n -> layer.put(n.id(), n.layer()));
            return compact(layer);
        }
        // Longest-path layering over a DAG obtained by dropping edges that close cycles (in declaration order).
        Map<String, List<String>> out = new HashMap<>();
        Map<String, Integer> indeg = new HashMap<>();
        spec.nodes().forEach(n -> { out.put(n.id(), new ArrayList<>()); indeg.put(n.id(), 0); layer.put(n.id(), 0); });
        for (Edge e : spec.edges()) {
            if (!reachable(out, e.to(), e.from())) {
                out.get(e.from()).add(e.to());
                indeg.merge(e.to(), 1, Integer::sum);
            }
        }
        Deque<String> queue = new ArrayDeque<>();
        spec.nodes().forEach(n -> { if (indeg.get(n.id()) == 0) queue.add(n.id()); });
        while (!queue.isEmpty()) {
            String id = queue.poll();
            for (String t : out.get(id)) {
                layer.put(t, Math.max(layer.get(t), layer.get(id) + 1));
                if (indeg.merge(t, -1, Integer::sum) == 0) queue.add(t);
            }
        }
        // Edge-less "process" diagrams read as a sequence.
        if (spec.edges().isEmpty()) {
            int i = 0;
            for (Node n : spec.nodes()) layer.put(n.id(), i++);
        }
        // Keep rows readable: wrap very wide layers onto the next row.
        return compact(layer);
    }

    private static boolean reachable(Map<String, List<String>> out, String from, String target) {
        Deque<String> stack = new ArrayDeque<>(List.of(from));
        Set<String> seen = new HashSet<>();
        while (!stack.isEmpty()) {
            String c = stack.pop();
            if (c.equals(target)) return true;
            if (seen.add(c)) stack.addAll(out.getOrDefault(c, List.of()));
        }
        return false;
    }

    private static Map<String, Integer> compact(Map<String, Integer> layer) {
        List<Integer> distinct = layer.values().stream().distinct().sorted().toList();
        Map<String, Integer> result = new LinkedHashMap<>();
        layer.forEach((k, v) -> result.put(k, distinct.indexOf(v)));
        return result;
    }

    // ---------------------------------------------------------------- steps

    private String steps(DiagramSpec spec) {
        double pad = 28, cardW = 440, gap = 34, circle = 34;
        double width = pad * 2 + circle + 16 + cardW;
        List<Box> cards = new ArrayList<>();
        double y = pad;
        for (Node n : spec.nodes()) {
            double h = nodeHeight(n, cardW);
            cards.add(new Box(pad + circle + 16, y, cardW, h));
            y += h + gap;
        }
        Svg svg = new Svg(width, y - gap + pad, spec.title());
        for (int i = 0; i + 1 < cards.size(); i++) {
            Box a = cards.get(i), b = cards.get(i + 1);
            double cx = pad + circle / 2;
            svg.raw("<line x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\" stroke=\"%s\" stroke-width=\"2\" marker-end=\"url(#arrow)\"/>"
                    .formatted(cx, a.cy() + circle / 2, cx, b.cy() - circle / 2 - 4, LINE));
        }
        for (int i = 0; i < cards.size(); i++) {
            Box c = cards.get(i);
            String[] colors = PALETTE[i % PALETTE.length];
            double cx = pad + circle / 2;
            svg.raw("<circle cx=\"%.1f\" cy=\"%.1f\" r=\"%.1f\" fill=\"%s\"/>".formatted(cx, c.cy(), circle / 2, colors[1]));
            svg.raw("<text x=\"%.1f\" y=\"%.1f\" text-anchor=\"middle\" dominant-baseline=\"central\" font-size=\"15\" font-weight=\"700\" fill=\"#FFFFFF\">%d</text>"
                    .formatted(cx, c.cy(), i + 1));
            svg.node(c, spec.nodes().get(i), colors, 12, true);
        }
        return svg.build();
    }

    // ---------------------------------------------------------------- mind map

    private String mindmap(DiagramSpec spec) {
        Node centre = spec.nodes().getFirst();
        Map<String, Node> byId = new LinkedHashMap<>();
        spec.nodes().forEach(n -> byId.put(n.id(), n));
        List<String> level1 = new ArrayList<>();
        for (Edge e : spec.edges()) if (e.from().equals(centre.id()) && !level1.contains(e.to())) level1.add(e.to());
        if (level1.isEmpty()) spec.nodes().stream().skip(1).forEach(n -> level1.add(n.id()));
        Map<String, List<String>> children = new LinkedHashMap<>();
        Set<String> placed = new HashSet<>(level1);
        placed.add(centre.id());
        for (Edge e : spec.edges()) {
            if (level1.contains(e.from()) && !placed.contains(e.to())) {
                children.computeIfAbsent(e.from(), k -> new ArrayList<>()).add(e.to());
                placed.add(e.to());
            }
        }
        double r1 = 200, r2 = 345, w1 = 160, w2 = 140;
        Map<String, Box> boxes = new LinkedHashMap<>();
        double cw = 190, ch = nodeHeight(centre, cw);
        boxes.put(centre.id(), new Box(-cw / 2, -ch / 2, cw, ch));
        int n1 = level1.size();
        for (int i = 0; i < n1; i++) {
            double angle = (n1 <= 2 ? 0 : -Math.PI / 2) + 2 * Math.PI * i / n1;
            Node node = byId.get(level1.get(i));
            double h = nodeHeight(node, w1);
            boxes.put(node.id(), new Box(r1 * Math.cos(angle) - w1 / 2, r1 * Math.sin(angle) * 0.8 - h / 2, w1, h));
            List<String> kids = children.getOrDefault(node.id(), List.of());
            double spread = Math.min(Math.PI / 4, (2 * Math.PI / Math.max(1, n1)) * 0.8);
            for (int k = 0; k < kids.size(); k++) {
                double a2 = kids.size() == 1 ? angle : angle - spread / 2 + spread * k / (kids.size() - 1);
                Node kid = byId.get(kids.get(k));
                double kh = nodeHeight(kid, w2);
                boxes.put(kid.id(), new Box(r2 * Math.cos(a2) - w2 / 2, r2 * Math.sin(a2) * 0.85 - kh / 2, w2, kh));
            }
        }
        double minX = boxes.values().stream().mapToDouble(Box::x).min().orElse(0) - 24;
        double minY = boxes.values().stream().mapToDouble(Box::y).min().orElse(0) - 24;
        double maxX = boxes.values().stream().mapToDouble(b -> b.x() + b.w()).max().orElse(0) + 24;
        double maxY = boxes.values().stream().mapToDouble(b -> b.y() + b.h()).max().orElse(0) + 24;
        Map<String, Box> shifted = new LinkedHashMap<>();
        boxes.forEach((k, b) -> shifted.put(k, new Box(b.x() - minX, b.y() - minY, b.w(), b.h())));

        Svg svg = new Svg(maxX - minX, maxY - minY, spec.title());
        Box c = shifted.get(centre.id());
        for (int i = 0; i < n1; i++) {
            Box b = shifted.get(level1.get(i));
            svg.curve(c, b, PALETTE[i % PALETTE.length][1]);
            for (String kid : children.getOrDefault(level1.get(i), List.of())) svg.curve(b, shifted.get(kid), PALETTE[i % PALETTE.length][1]);
        }
        svg.node(c, centre, new String[]{"#312E81", "#312E81"}, 14, false, "#FFFFFF");
        for (int i = 0; i < n1; i++) {
            String[] colors = PALETTE[i % PALETTE.length];
            svg.node(shifted.get(level1.get(i)), byId.get(level1.get(i)), colors, 12);
            for (String kid : children.getOrDefault(level1.get(i), List.of())) {
                svg.node(shifted.get(kid), byId.get(kid), new String[]{"#FFFFFF", colors[1]}, 10);
            }
        }
        return svg.build();
    }

    // ---------------------------------------------------------------- cycle

    private String cycle(DiagramSpec spec) {
        List<Node> nodes = spec.nodes();
        int n = nodes.size();
        double w = 160, radius = Math.max(150, n * 42);
        List<Box> boxes = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double angle = -Math.PI / 2 + 2 * Math.PI * i / n;
            double h = nodeHeight(nodes.get(i), w);
            boxes.add(new Box(radius * Math.cos(angle) - w / 2, radius * Math.sin(angle) - h / 2, w, h));
        }
        double minX = boxes.stream().mapToDouble(Box::x).min().orElse(0) - 30;
        double minY = boxes.stream().mapToDouble(Box::y).min().orElse(0) - 30;
        double maxX = boxes.stream().mapToDouble(b -> b.x() + b.w()).max().orElse(0) + 30;
        double maxY = boxes.stream().mapToDouble(b -> b.y() + b.h()).max().orElse(0) + 30;
        List<Box> shifted = boxes.stream().map(b -> new Box(b.x() - minX, b.y() - minY, b.w(), b.h())).toList();
        Svg svg = new Svg(maxX - minX, maxY - minY, spec.title());
        for (int i = 0; i < n; i++) svg.edge(shifted.get(i), shifted.get((i + 1) % n), null, true);
        for (int i = 0; i < n; i++) svg.node(shifted.get(i), nodes.get(i), PALETTE[i % PALETTE.length], 12);
        return svg.build();
    }

    // ---------------------------------------------------------------- comparison

    private String comparison(DiagramSpec spec) {
        var cols = spec.columns();
        double pad = 24, colW = 230, gap = 18, header = 46;
        double width = pad * 2 + cols.size() * colW + (cols.size() - 1) * gap;
        List<List<String[]>> wrapped = new ArrayList<>();
        double maxBody = 0;
        for (var col : cols) {
            List<String[]> items = new ArrayList<>();
            double h = 14;
            for (String item : col.items()) {
                String[] lines = wrap(item, charsFor(colW - 40, 13), 4);
                items.add(lines);
                h += lines.length * 18 + 12;
            }
            wrapped.add(items);
            maxBody = Math.max(maxBody, h);
        }
        double height = pad * 2 + header + maxBody;
        Svg svg = new Svg(width, height, spec.title());
        for (int c = 0; c < cols.size(); c++) {
            double x = pad + c * (colW + gap);
            String[] colors = PALETTE[c % PALETTE.length];
            svg.raw("<rect x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"%.1f\" rx=\"14\" fill=\"%s\" stroke=\"%s\" stroke-width=\"1.5\"/>"
                    .formatted(x, pad, colW, header + maxBody, colors[0], colors[1]));
            svg.raw("<path d=\"M%.1f %.1f h%.1f v%.1f h-%.1f z\" fill=\"%s\"/>".formatted(x, pad + 14, colW, header - 14, colW, colors[1]));
            svg.raw("<rect x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"28\" rx=\"14\" fill=\"%s\"/>".formatted(x, pad, colW, colors[1]));
            String[] title = wrap(cols.get(c).title(), charsFor(colW - 24, 15), 1);
            svg.raw("<text x=\"%.1f\" y=\"%.1f\" text-anchor=\"middle\" dominant-baseline=\"central\" font-size=\"15\" font-weight=\"700\" fill=\"#FFFFFF\">%s</text>"
                    .formatted(x + colW / 2, pad + header / 2, esc(title[0])));
            double y = pad + header + 14;
            for (String[] lines : wrapped.get(c)) {
                svg.raw("<circle cx=\"%.1f\" cy=\"%.1f\" r=\"3.5\" fill=\"%s\"/>".formatted(x + 18, y + 8, colors[1]));
                for (int l = 0; l < lines.length; l++) {
                    svg.raw("<text x=\"%.1f\" y=\"%.1f\" font-size=\"13\" fill=\"%s\">%s</text>".formatted(x + 30, y + 13 + l * 18, TEXT, esc(lines[l])));
                }
                y += lines.length * 18 + 12;
            }
        }
        return svg.build();
    }

    // ---------------------------------------------------------------- primitives

    private static double nodeHeight(Node n, double w) {
        int labelLines = wrap(n.label(), charsFor(w - 24, 14), 3).length;
        int detailLines = n.detail() == null ? 0 : wrap(n.detail(), charsFor(w - 24, 12), 3).length;
        return 22 + labelLines * 18 + (detailLines > 0 ? 6 + detailLines * 15 : 0);
    }

    private static int charsFor(double px, double fontSize) {
        return Math.max(8, (int) (px / (fontSize * 0.56)));
    }

    static String[] wrap(String text, int maxChars, int maxLines) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            while (word.length() > maxChars) {
                if (!line.isEmpty()) { lines.add(line.toString()); line.setLength(0); }
                lines.add(word.substring(0, maxChars - 1) + "-");
                word = word.substring(maxChars - 1);
            }
            if (line.length() + word.length() + (line.isEmpty() ? 0 : 1) > maxChars) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) line.append(' ');
            line.append(word);
        }
        if (!line.isEmpty()) lines.add(line.toString());
        if (lines.size() > maxLines) {
            List<String> cut = new ArrayList<>(lines.subList(0, maxLines));
            String last = cut.get(maxLines - 1);
            cut.set(maxLines - 1, (last.length() > maxChars - 1 ? last.substring(0, maxChars - 1) : last) + "…");
            return cut.toArray(String[]::new);
        }
        return lines.toArray(String[]::new);
    }

    static String esc(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> { if (c >= 0x20 || c == '\n' || c == '\t') sb.append(c); }
            }
        }
        return sb.toString();
    }

    /** Minimal SVG builder. */
    private static final class Svg {
        private final StringBuilder edges = new StringBuilder();
        private final StringBuilder body = new StringBuilder();
        private final double width, height;
        private final String title;

        Svg(double width, double height, String title) {
            this.width = Math.ceil(width);
            this.height = Math.ceil(height);
            this.title = title;
        }

        void raw(String s) { body.append(s).append('\n'); }

        void node(Box b, Node n, String[] colors, double radius) { node(b, n, colors, radius, false); }

        void node(Box b, Node n, String[] colors, double radius, boolean leftAlign) { node(b, n, colors, radius, leftAlign, TEXT); }

        void node(Box b, Node n, String[] colors, double radius, boolean leftAlign, String textColor) {
            body.append("<g>");
            body.append("<rect x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"%.1f\" rx=\"%.0f\" fill=\"%s\" stroke=\"%s\" stroke-width=\"1.5\" filter=\"url(#shadow)\"/>"
                    .formatted(b.x(), b.y(), b.w(), b.h(), radius, colors[0], colors[1]));
            String[] label = wrap(n.label(), charsFor(b.w() - 24, 14), 3);
            String[] detail = n.detail() == null ? new String[0] : wrap(n.detail(), charsFor(b.w() - 24, 12), 3);
            double y = b.y() + 11 + 14;
            double x = leftAlign ? b.x() + 14 : b.cx();
            String anchor = leftAlign ? "start" : "middle";
            for (String l : label) {
                body.append("<text x=\"%.1f\" y=\"%.1f\" text-anchor=\"%s\" font-size=\"14\" font-weight=\"600\" fill=\"%s\">%s</text>"
                        .formatted(x, y, anchor, textColor, esc(l)));
                y += 18;
            }
            if (detail.length > 0) y += 4;
            for (String d : detail) {
                body.append("<text x=\"%.1f\" y=\"%.1f\" text-anchor=\"%s\" font-size=\"12\" fill=\"%s\">%s</text>"
                        .formatted(x, y - 2, anchor, textColor.equals(TEXT) ? MUTED : textColor, esc(d)));
                y += 15;
            }
            body.append("</g>\n");
        }

        void edge(Box a, Box b, String label, boolean curved) {
            double[] p1 = boundary(a, b.cx(), b.cy());
            double[] p2 = boundary(b, a.cx(), a.cy());
            double mx = (p1[0] + p2[0]) / 2, my = (p1[1] + p2[1]) / 2;
            if (curved) {
                double dx = p2[0] - p1[0], dy = p2[1] - p1[1];
                double len = Math.max(1, Math.hypot(dx, dy));
                double off = Math.min(70, len * 0.35);
                double cx = mx - dy / len * off, cy = my + dx / len * off;
                edges.append("<path d=\"M%.1f %.1f Q%.1f %.1f %.1f %.1f\" fill=\"none\" stroke=\"%s\" stroke-width=\"2\" marker-end=\"url(#arrow)\"/>\n"
                        .formatted(p1[0], p1[1], cx, cy, p2[0], p2[1], LINE));
                mx = (p1[0] + 2 * cx + p2[0]) / 4;
                my = (p1[1] + 2 * cy + p2[1]) / 4;
            } else {
                edges.append("<line x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\" stroke=\"%s\" stroke-width=\"2\" marker-end=\"url(#arrow)\"/>\n"
                        .formatted(p1[0], p1[1], p2[0], p2[1], LINE));
            }
            if (label != null) {
                double w = label.length() * 6.6 + 14;
                edges.append("<rect x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"20\" rx=\"10\" fill=\"#FFFFFF\" stroke=\"#CBD5E1\"/>\n"
                        .formatted(mx - w / 2, my - 10, w));
                edges.append("<text x=\"%.1f\" y=\"%.1f\" text-anchor=\"middle\" dominant-baseline=\"central\" font-size=\"11.5\" fill=\"%s\">%s</text>\n"
                        .formatted(mx, my, MUTED, esc(label)));
            }
        }

        void curve(Box a, Box b, String color) {
            double cx = (a.cx() + b.cx()) / 2, cy = a.cy();
            edges.append("<path d=\"M%.1f %.1f Q%.1f %.1f %.1f %.1f\" fill=\"none\" stroke=\"%s\" stroke-opacity=\"0.55\" stroke-width=\"2.5\"/>\n"
                    .formatted(a.cx(), a.cy(), cx, cy, b.cx(), b.cy(), color));
        }

        /** Point where the line from the box centre towards (tx, ty) leaves the box, with a small gap. */
        private static double[] boundary(Box b, double tx, double ty) {
            double dx = tx - b.cx(), dy = ty - b.cy();
            if (dx == 0 && dy == 0) return new double[]{b.cx(), b.cy()};
            double sx = dx == 0 ? Double.MAX_VALUE : (b.w() / 2 + 4) / Math.abs(dx);
            double sy = dy == 0 ? Double.MAX_VALUE : (b.h() / 2 + 4) / Math.abs(dy);
            double s = Math.min(sx, sy);
            return new double[]{b.cx() + dx * s, b.cy() + dy * s};
        }

        String build() {
            return """
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %1$.0f %2$.0f" width="%1$.0f" height="%2$.0f" role="img" aria-label="%3$s" font-family="%4$s">
                    <title>%3$s</title>
                    <defs>
                    <marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0 0 L10 5 L0 10 z" fill="%5$s"/></marker>
                    <filter id="shadow" x="-10%%" y="-10%%" width="120%%" height="130%%"><feDropShadow dx="0" dy="1.5" stdDeviation="1.5" flood-color="#0F172A" flood-opacity="0.08"/></filter>
                    </defs>
                    <rect width="100%%" height="100%%" rx="16" fill="#FFFFFF"/>
                    %6$s%7$s</svg>""".formatted(width, height, esc(title), FONT.replace("'", "&#39;"), LINE, edges, body);
        }
    }
}
