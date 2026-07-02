package io.callistotech.enterprise.classification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Classifies documents by IRS form type via a Groq LLM call (same {@link ChatClient}
 * used by {@code SummaryService}).
 *
 * <p>Prompt is loaded from {@value #PROMPT_RESOURCE} — no hardcoded label list. The
 * model applies its own IRS knowledge to each document's full raw OCR text (the
 * content payload captured by {@code AzureDocIntelExtractor} — never the KV pairs)
 * to determine the form type(s) present. A single document's content can contain
 * more than one distinct IRS form (e.g. a bundled upload), so each document maps
 * to a list of form types.
 *
 * <p>Fails safe — any parse or network error returns an empty classification for
 * every requested document; callers fall back to "Unknown" form type.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentClassifierService {

    static final String PROMPT_RESOURCE = "prompts/document-classifier-v1.txt";
    private static final String DOCUMENTS_TOKEN = "{{DOCUMENTS}}";

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    /**
     * Classifies each of {@code documents} by IRS form type(s).
     *
     * @param documents one or more documents, each carrying its raw OCR content payload
     * @return one {@link DocumentClassification} per input document, in the same order;
     *         {@code formTypes} is empty for a document if classification failed or found
     *         nothing
     */
    public List<DocumentClassification> classify(List<DocumentToClassify> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }

        Map<String, List<String>> byDocId;
        try {
            String prompt = buildPrompt(documents);
            String content = chatClient.prompt().user(prompt).call().content();
            byDocId = parseResponse(content);
        } catch (Exception e) {
            log.warn("Document classification failed — falling back to Unknown for {} doc(s): {}",
                    documents.size(), e.getMessage());
            byDocId = Map.of();
        }

        Map<String, List<String>> finalByDocId = byDocId;
        return documents.stream()
                .map(doc -> new DocumentClassification(
                        doc.documentId(),
                        finalByDocId.getOrDefault(doc.documentId(), List.of())))
                .toList();
    }

    // ── Prompt ────────────────────────────────────────────────────────────────

    private String buildPrompt(List<DocumentToClassify> documents) throws IOException {
        String template = loadTemplate();
        return template.replace(DOCUMENTS_TOKEN, buildDocumentsBlock(documents));
    }

    private String buildDocumentsBlock(List<DocumentToClassify> documents) {
        StringBuilder sb = new StringBuilder();
        for (DocumentToClassify doc : documents) {
            sb.append("---\n");
            sb.append("Document ID: ").append(doc.documentId()).append("\n");
            sb.append("Content:\n").append(doc.content()).append("\n\n");
        }
        return sb.toString();
    }

    private String loadTemplate() throws IOException {
        ClassPathResource resource = new ClassPathResource(PROMPT_RESOURCE);
        return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    // ── Response parsing ──────────────────────────────────────────────────────

    /**
     * Parses the classifier's JSON response into documentId → form type list.
     * Each value is expected to be an array (multi-form support), but a bare
     * string is also accepted defensively and wrapped as a singleton list.
     *
     * <p>Package-private (not {@code private}) for direct unit testing — pure
     * function, no LLM call involved.
     */
    Map<String, List<String>> parseResponse(String content) {
        if (content == null || content.isBlank()) return Map.of();
        String trimmed = content.strip();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline != -1 && lastFence > firstNewline) {
                trimmed = trimmed.substring(firstNewline + 1, lastFence).strip();
            }
        }
        try {
            JsonNode node = objectMapper.readTree(trimmed);
            Map<String, List<String>> result = new LinkedHashMap<>();
            node.fields().forEachRemaining(e -> {
                JsonNode value = e.getValue();
                if (value.isArray()) {
                    List<String> types = new ArrayList<>();
                    value.forEach(t -> {
                        if (t.isTextual() && !t.asText().isBlank()) types.add(t.asText());
                    });
                    if (!types.isEmpty()) result.put(e.getKey(), types);
                } else if (value.isTextual() && !value.asText().isBlank()) {
                    result.put(e.getKey(), List.of(value.asText()));
                }
            });
            return result;
        } catch (Exception ex) {
            log.warn("Classifier response was not valid JSON: {}", trimmed);
            return Map.of();
        }
    }

    /**
     * One document's content payload submitted for classification.
     *
     * @param documentId caller-supplied identifier, echoed back in {@link DocumentClassification}
     * @param content    full raw OCR text for the document (Azure DI content payload)
     */
    public record DocumentToClassify(String documentId, String content) {}
}
