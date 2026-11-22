package com.explainly.ai;

import com.explainly.common.Json;
import com.fasterxml.jackson.databind.JsonNode;

/** Extracts the JSON object from a model response, tolerating code fences or surrounding prose. */
public final class JsonExtractor {
    private JsonExtractor() {}

    public static JsonNode extractObject(String text) {
        if (text == null) throw new IllegalArgumentException("empty response");
        String t = text.trim();
        if (t.startsWith("```")) {
            int firstNewline = t.indexOf('\n');
            int lastFence = t.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) t = t.substring(firstNewline + 1, lastFence).trim();
        }
        int start = t.indexOf('{');
        int end = t.lastIndexOf('}');
        if (start < 0 || end <= start) throw new IllegalArgumentException("no JSON object found");
        try {
            JsonNode node = Json.MAPPER.readTree(t.substring(start, end + 1));
            if (!node.isObject()) throw new IllegalArgumentException("not a JSON object");
            return node;
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid JSON: " + e.getMessage(), e);
        }
    }
}
