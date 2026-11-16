package io.github.ardavan39.docuextract.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.ardavan39.docuextract.TestFixtures;

class OllamaFieldExtractorTest {

    private static final String CHAT_URL = "http://ollama.test/api/chat";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockRestServiceServer server;
    private OllamaFieldExtractor extractor;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ollama.test");
        server = MockRestServiceServer.bindTo(builder).build();
        extractor = new OllamaFieldExtractor(builder.build(), TestFixtures.properties(), objectMapper);
    }

    @Test
    void asksTheConfiguredModelForJsonAndReturnsTheFields() {
        server.expect(requestTo(CHAT_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("test-model"))
                .andExpect(jsonPath("$.format").value("json"))
                .andExpect(jsonPath("$.stream").value(false))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].content").value("Document text:\n\nInvoice 42"))
                .andRespond(withSuccess(
                        answer("{\"documentType\":\"invoice\",\"totalAmount\":120.5,\"currency\":\"EUR\"}"),
                        MediaType.APPLICATION_JSON));

        Map<String, Object> fields = extractor.extract("Invoice 42");

        assertThat(fields)
                .containsEntry("documentType", "invoice")
                .containsEntry("totalAmount", 120.5)
                .containsEntry("currency", "EUR");
        server.verify();
    }

    @Test
    void alwaysReturnsTheSameKeys() {
        server.expect(requestTo(CHAT_URL)).andRespond(withSuccess(
                answer("{\"title\":\"Rental contract\",\"somethingElse\":\"ignored\"}"),
                MediaType.APPLICATION_JSON));

        Map<String, Object> fields = extractor.extract("Rental contract ...");

        assertThat(fields.keySet()).containsExactlyElementsOf(OllamaFieldExtractor.FIELDS);
        assertThat(fields).containsEntry("title", "Rental contract").containsEntry("date", null);
        assertThat(fields).doesNotContainKey("somethingElse");
    }

    @Test
    void cutsLongDocumentsToTheConfiguredLength() {
        String longText = "x".repeat(500);
        // the test settings allow 50 characters of document text
        server.expect(requestTo(CHAT_URL))
                .andExpect(jsonPath("$.messages[1].content").value("Document text:\n\n" + "x".repeat(50)))
                .andRespond(withSuccess(answer("{}"), MediaType.APPLICATION_JSON));

        extractor.extract(longText);

        server.verify();
    }

    @Test
    void failsWhenTheModelDoesNotAnswerWithJson() {
        server.expect(requestTo(CHAT_URL)).andRespond(withSuccess(
                answer("Sure! Here is the data you asked for."), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> extractor.extract("Invoice 42"))
                .isInstanceOf(LlmExtractionException.class)
                .hasMessageContaining("not a JSON object");
    }

    @Test
    void failsWhenTheModelAnswersWithAJsonArray() {
        server.expect(requestTo(CHAT_URL)).andRespond(withSuccess(answer("[1, 2, 3]"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> extractor.extract("Invoice 42")).isInstanceOf(LlmExtractionException.class);
    }

    @Test
    void failsWhenOllamaReturnsAnError() {
        server.expect(requestTo(CHAT_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> extractor.extract("Invoice 42"))
                .isInstanceOf(LlmExtractionException.class)
                .hasMessageContaining("Ollama request failed");
    }

    @Test
    void failsWhenTheAnswerHasNoMessage() {
        server.expect(requestTo(CHAT_URL)).andRespond(withSuccess("{\"done\":true}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> extractor.extract("Invoice 42"))
                .isInstanceOf(LlmExtractionException.class)
                .hasMessageContaining("empty answer");
    }

    /** Wraps model output the way Ollama's /api/chat does. */
    private String answer(String content) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "model", "test-model",
                    "message", Map.of("role", "assistant", "content", content),
                    "done", true));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
