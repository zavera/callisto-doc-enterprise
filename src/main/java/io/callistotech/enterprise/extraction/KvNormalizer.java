package io.callistotech.enterprise.extraction;

import io.callistotech.enterprise.fieldmap.FieldMap;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Normalises raw Azure DI key-value pairs to canonical field names and parsed BigDecimal values.
 *
 * Resolution is 3-stage, matching the Python KvNormalizer:
 *   Stage 1 — wsNormalise: collapse all whitespace to a single space, lowercase → lookup in wsMap
 *   Stage 2 — simplify: strip non-alphanumeric-non-space chars, collapse spaces → lookup in simplifiedMap
 *   Stage 3 — snake_case fallback: replace spaces with underscores, lowercase
 *
 * Both wsMap and simplifiedMap are pre-computed from the FieldMap entries in the constructor.
 * Pure function — no I/O, no side effects, safe to unit test directly.
 */
public class KvNormalizer {

    private final Map<String, String> wsMap;
    private final Map<String, String> simplifiedMap;

    public KvNormalizer(FieldMap fieldMap) {
        this.wsMap = new HashMap<>();
        this.simplifiedMap = new HashMap<>();

        for (Map.Entry<String, String> entry : fieldMap.entries().entrySet()) {
            String rawKey = entry.getKey();
            String canonicalName = entry.getValue();

            wsMap.put(wsNormalise(rawKey), canonicalName);
            simplifiedMap.put(simplify(rawKey), canonicalName);
        }
    }

    /**
     * Attempts to resolve a raw Azure DI key to a canonical field name.
     *
     * @param rawKey raw key from Azure DI output
     * @return canonical field name, or snake_case fallback if not in any map
     */
    public String resolveFieldName(String rawKey) {
        if (rawKey == null) return "unknown";

        // Stage 1: whitespace-normalised lookup
        String ws = wsNormalise(rawKey);
        String result = wsMap.get(ws);
        if (result != null) return result;

        // Stage 2: simplified (strip punctuation) lookup
        String simp = simplify(rawKey);
        result = simplifiedMap.get(simp);
        if (result != null) return result;

        // Stage 3: snake_case fallback
        return toSnakeCase(rawKey);
    }

    /**
     * Parses a raw Azure DI value string to BigDecimal.
     *
     * Handles:
     * - Currency symbols ($, €, £, ¥) — stripped
     * - Parenthetical negatives: (1,234.56) → -1234.56
     * - Trailing alphabetic characters: "1234ABC" → 1234
     * - Percentage signs: stripped
     * - Comma thousands separators: stripped
     * - :selected: / :unselected: → empty Optional
     *
     * @param rawValue raw value string from Azure DI
     * @return parsed BigDecimal, or empty if the value is absent, non-numeric, or a checkbox state
     */
    public Optional<BigDecimal> parseValue(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Optional.empty();
        }

        String trimmed = rawValue.strip();

        // Checkbox state — not a numeric value
        if (trimmed.equalsIgnoreCase(":selected:") || trimmed.equalsIgnoreCase(":unselected:")) {
            return Optional.empty();
        }

        // Strip currency symbols and percentage
        String cleaned = trimmed.replaceAll("[\\$€£¥%]", "");

        // Handle parenthetical negatives: (1,234.56) → -1234.56
        boolean negative = cleaned.startsWith("(") && cleaned.endsWith(")");
        if (negative) {
            cleaned = "-" + cleaned.substring(1, cleaned.length() - 1);
        }

        // Strip comma thousands separators
        cleaned = cleaned.replace(",", "");

        // Strip trailing alphabetic characters (e.g. "1234D", "5678 USD")
        cleaned = cleaned.replaceAll("[a-zA-Z\\s]+$", "").strip();

        if (cleaned.isEmpty() || cleaned.equals("-")) {
            return Optional.empty();
        }

        try {
            return Optional.of(new BigDecimal(cleaned));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    // --- private normalisation helpers ---

    /** Replace all whitespace sequences with a single space, then lowercase. */
    static String wsNormalise(String s) {
        return s.replaceAll("\\s+", " ").toLowerCase();
    }

    /** Strip non-alphanumeric non-space characters, collapse spaces, lowercase. */
    static String simplify(String s) {
        return s.replaceAll("[^a-zA-Z0-9 ]", "").replaceAll("\\s+", " ").toLowerCase().strip();
    }

    /** Replace spaces with underscores, lowercase. */
    static String toSnakeCase(String s) {
        return s.replaceAll("\\s+", "_").toLowerCase();
    }
}
