package io.github.ardavan39.docuextract.llm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.ardavan39.docuextract.config.AppProperties;
import io.github.ardavan39.docuextract.config.OllamaClientConfig;

/**
 * Asks a model served by Ollama to fill in a fixed set of fields.
 *
 * <p>The model is told to answer in JSON and Ollama is asked to enforce it ({@code format: json}),
 * but the answer is still treated as untrusted input: it must parse as a JSON object, unknown keys
 * are dropped and missing keys are filled with {@code null}, so callers always get the same shape.
 */
@Component
public class OllamaFieldExtractor implements FieldExtractor {

    static final List<String> FIELDS = List.of(
            "documentType", "title", "date", "issuer", "recipient",
            "totalAmount", "currency", "language", "summary");

    private static final String SYSTEM_PROMPT = """
            You extract structured data from business documents such as invoices, receipts, \
            contracts and letters. Answer with one JSON object and nothing else. Use exactly \
            these keys:
            - documentType: one of "invoice", "receipt", "contract", "letter", "report", "other"
            - title: short title of the document
            - date: main date of the document in ISO format (YYYY-MM-DD)
            - issuer: person or organisation that issued the document
            - recipient: person or organisation the document is addressed to
            - totalAmount: total amount as a number, without currency symbol
            - currency: ISO 4217 currency code
            - language: ISO 639-1 code of the document's language
            - summary: one sentence describing the document
            Use null for anything the document does not state. Do not guess.""";

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final RestClient restClient;
    private final AppProperties.Ollama settings;
    private final ObjectMapper objectMapper;

    public OllamaFieldExtractor(@Qualifier(OllamaClientConfig.OLLAMA_REST_CLIENT) RestClient restClient,
            AppProperties properties, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.settings = properties.ollama();
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> extract(String documentText) {
        ChatResponse response;
        try {
            response = restClient.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildRequest(documentText))
                    .retrieve()
                    .body(ChatResponse.class);
        } catch (RestClientException e) {
            throw new LlmExtractionException("Ollama request failed: " + e.getMessage(), e);
        }

        if (response == null || response.message() == null || response.message().content() == null) {
            throw new LlmExtractionException("Ollama returned an empty answer");
        }
        return normalise(parse(response.message().content()));
    }

    private Map<String, Object> buildRequest(String documentText) {
        String text = documentText.length() > settings.maxInputChars()
                ? documentText.substring(0, settings.maxInputChars())
                : documentText;

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", settings.model());
        request.put("stream", false);
        request.put("format", "json");
        request.put("options", Map.of("temperature", 0));
        request.put("messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", "Document text:\n\n" + text)));
        return request;
    }

    private Map<String, Object> parse(String content) {
        try {
            return objectMapper.readValue(content, MAP_TYPE);
        } catch (JsonProcessingException e) {
            throw new LlmExtractionException("Model answer is not a JSON object", e);
        }
    }

    private static Map<String, Object> normalise(Map<String, Object> raw) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (String field : FIELDS) {
            fields.put(field, raw.get(field));
        }
        return fields;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatResponse(Message message) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Message(String role, String content) {
        }
    }
}
