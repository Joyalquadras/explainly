package com.explainly.illustration;

import com.explainly.common.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SvgDiagramRendererTest {

    private final SvgDiagramRenderer renderer = new SvgDiagramRenderer();

    private static DiagramSpec spec(String json) {
        return DiagramSpec.fromJson(Json.tree(json)).orElseThrow();
    }

    @ParameterizedTest
    @ValueSource(strings = {"flowchart", "process", "architecture", "steps", "mindmap", "cycle"})
    void rendersWellFormedSvgForEveryNodeType(String type) throws Exception {
        String svg = renderer.render(spec("""
                {"type":"%s","title":"Request <lifecycle>","nodes":[
                  {"id":"a","label":"Client sends an HTTP request","detail":"browser or app","layer":0},
                  {"id":"b","label":"Controller","layer":1},{"id":"c","label":"Service layer with business rules","layer":2},
                  {"id":"d","label":"Repository","layer":2},{"id":"e","label":"Database","layer":3}],
                 "edges":[{"from":"a","to":"b","label":"GET /orders"},{"from":"b","to":"c"},{"from":"c","to":"d"},
                          {"from":"d","to":"e"},{"from":"e","to":"a","label":"response"}]}""".formatted(type)));
        assertWellFormed(svg);
        assertThat(svg).startsWith("<svg").contains("Controller").contains("Request &lt;lifecycle&gt;");
    }

    @Test
    void rendersComparison() throws Exception {
        String svg = renderer.render(spec("""
                {"type":"comparison","title":"SQL vs NoSQL","columns":[
                  {"title":"SQL","items":["Tables with fixed schema","Strong consistency"]},
                  {"title":"NoSQL","items":["Flexible documents","Scales horizontally"]}]}"""));
        assertWellFormed(svg);
        assertThat(svg).contains("NoSQL").contains("Flexible documents");
    }

    @Test
    void escapesMaliciousLabelsAndEmitsNoScripts() throws Exception {
        String svg = renderer.render(spec("""
                {"type":"flowchart","title":"x\\" onload=\\"alert(1)","nodes":[
                  {"id":"a","label":"<script>alert(1)</script>"},{"id":"b","label":"<img src=x onerror=alert(1)>"}],
                 "edges":[{"from":"a","to":"b","label":"</text><script>"}]}"""));
        assertWellFormed(svg);
        assertThat(svg).doesNotContain("<script").doesNotContain("<img").doesNotContain("onload=\"");
    }

    @Test
    void rejectsInvalidSpecs() {
        assertThat(DiagramSpec.fromJson(Json.tree("{\"type\":\"flowchart\",\"title\":\"x\",\"nodes\":[{\"id\":\"a\",\"label\":\"A\"}]}"))).isEmpty();
        assertThat(DiagramSpec.fromJson(Json.tree("{\"type\":\"comparison\",\"title\":\"x\",\"columns\":[{\"title\":\"A\",\"items\":[\"1\"]}]}"))).isEmpty();
        assertThat(DiagramSpec.fromJson(Json.tree("{\"type\":\"flowchart\",\"nodes\":[]}"))).isEmpty();
    }

    @Test
    void dropsEdgesToUnknownNodesAndUnknownTypesFallBack() {
        DiagramSpec s = spec("""
                {"type":"hologram","title":"t","nodes":[{"id":"a","label":"A"},{"id":"b","label":"B"},{"id":"a","label":"dup"}],
                 "edges":[{"from":"a","to":"zzz"},{"from":"a","to":"a"},{"from":"a","to":"b"}]}""");
        assertThat(s.type()).isEqualTo("process");
        assertThat(s.nodes()).hasSize(2);
        assertThat(s.edges()).hasSize(1);
    }

    @Test
    void wrapsLongWords() {
        assertThat(SvgDiagramRenderer.wrap("supercalifragilisticexpialidocious word", 10, 5)).allMatch(l -> l.length() <= 10);
    }

    private static void assertWellFormed(String svg) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document doc = f.newDocumentBuilder().parse(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)));
        assertThat(doc.getDocumentElement().getTagName()).isEqualTo("svg");
        assertThat(doc.getElementsByTagName("script").getLength()).isZero();
    }
}
