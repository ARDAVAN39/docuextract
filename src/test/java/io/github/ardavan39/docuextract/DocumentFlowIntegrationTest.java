package io.github.ardavan39.docuextract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.github.ardavan39.docuextract.document.DocumentStatus;
import io.github.ardavan39.docuextract.llm.FieldExtractor;
import io.github.ardavan39.docuextract.llm.LlmExtractionException;
import io.github.ardavan39.docuextract.web.DocumentResponse;

/**
 * Runs the whole application against a real PostgreSQL: HTTP upload, Flyway schema, background
 * processing, JSONB round trip. Only the model is replaced, because a test should not need a GPU.
 * Skipped automatically on machines without Docker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class DocumentFlowIntegrationTest {

    private static final String BASE = "/api/v1/documents";
    private static final Duration PROCESSING_TIMEOUT = Duration.ofSeconds(20);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final Path STORAGE = createStorageDirectory();

    @DynamicPropertySource
    static void storageDirectory(DynamicPropertyRegistry registry) {
        registry.add("docuextract.storage.directory", STORAGE::toString);
    }

    @MockitoBean
    private FieldExtractor fieldExtractor;

    @Autowired
    private TestRestTemplate rest;

    @Test
    void anUploadedPdfIsProcessedInTheBackgroundAndCanBeReadBack() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("documentType", "invoice");
        fields.put("totalAmount", 120.5);
        fields.put("recipient", null);
        when(fieldExtractor.extract(anyString())).thenReturn(fields);

        ResponseEntity<DocumentResponse> uploaded =
                upload("invoice.pdf", TestFixtures.pdfWithText("Invoice 2026-001 Total 120.50 EUR"));

        assertThat(uploaded.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        UUID id = uploaded.getBody().id();
        assertThat(uploaded.getHeaders().getLocation()).hasPath(BASE + "/" + id);

        DocumentResponse completed = awaitStatus(id, DocumentStatus.COMPLETED);
        assertThat(completed.extractedFields())
                .containsEntry("documentType", "invoice")
                .containsEntry("totalAmount", 120.5)
                .containsEntry("recipient", null);
        assertThat(Files.exists(STORAGE.resolve(id.toString()))).isTrue();

        assertThat(rest.getForObject(BASE + "/{id}/text", String.class, id)).contains("Invoice 2026-001");

        String listed = rest.getForObject(BASE + "?status=COMPLETED", String.class);
        assertThat(listed).contains(id.toString());

        rest.delete(BASE + "/{id}", id);
        assertThat(rest.getForEntity(BASE + "/{id}", String.class, id).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(Files.exists(STORAGE.resolve(id.toString()))).isFalse();
    }

    @Test
    void aFailedDocumentKeepsItsErrorAndCanBeReprocessed() {
        when(fieldExtractor.extract(anyString())).thenThrow(new LlmExtractionException("Ollama request failed"));

        UUID id = upload("contract.pdf", TestFixtures.pdfWithText("Rental contract between A and B, 2026")).getBody().id();

        DocumentResponse failed = awaitStatus(id, DocumentStatus.FAILED);
        assertThat(failed.errorMessage()).isEqualTo("Ollama request failed");
        assertThat(failed.extractedFields()).isNull();

        // the model is back (doReturn, because calling the throwing stub inside when() would throw)
        doReturn(Map.of("documentType", "contract")).when(fieldExtractor).extract(anyString());
        ResponseEntity<DocumentResponse> reprocess =
                rest.postForEntity(BASE + "/{id}/reprocess", null, DocumentResponse.class, id);
        assertThat(reprocess.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        DocumentResponse completed = awaitStatus(id, DocumentStatus.COMPLETED);
        assertThat(completed.errorMessage()).isNull();
        assertThat(completed.extractedFields()).containsEntry("documentType", "contract");
    }

    @Test
    void aFileThatIsNotADocumentIsRejected() {
        ResponseEntity<String> response = rest.exchange(BASE, HttpMethod.POST,
                multipart("notes.pdf", "plain text with a misleading name".getBytes(StandardCharsets.UTF_8)),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    private DocumentResponse awaitStatus(UUID id, DocumentStatus expected) {
        return await().atMost(PROCESSING_TIMEOUT)
                .pollInterval(Duration.ofMillis(200))
                .until(() -> rest.getForObject(BASE + "/{id}", DocumentResponse.class, id),
                        document -> document.status() == expected);
    }

    private ResponseEntity<DocumentResponse> upload(String filename, byte[] content) {
        return rest.exchange(BASE, HttpMethod.POST, multipart(filename, content), DocumentResponse.class);
    }

    private static HttpEntity<MultiValueMap<String, Object>> multipart(String filename, byte[] content) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return new HttpEntity<>(body, headers);
    }

    private static Path createStorageDirectory() {
        try {
            return Files.createTempDirectory("docuextract-it-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
