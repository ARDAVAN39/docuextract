package io.github.ardavan39.docuextract.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.github.ardavan39.docuextract.document.Document;
import io.github.ardavan39.docuextract.document.DocumentExceptions.DocumentBusyException;
import io.github.ardavan39.docuextract.document.DocumentExceptions.DocumentNotFoundException;
import io.github.ardavan39.docuextract.document.DocumentExceptions.DocumentNotReadyException;
import io.github.ardavan39.docuextract.document.DocumentExceptions.UnsupportedDocumentTypeException;
import io.github.ardavan39.docuextract.document.DocumentService;
import io.github.ardavan39.docuextract.document.DocumentStatus;
import io.github.ardavan39.docuextract.document.DocumentType;

@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

    private static final String BASE = "/api/v1/documents";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentService service;

    @Test
    void uploadIsAcceptedAndPointsToTheNewDocument() throws Exception {
        Document document = document();
        byte[] content = {'%', 'P', 'D', 'F', '-'};
        when(service.upload("invoice.pdf", content)).thenReturn(document);

        mockMvc.perform(multipart(BASE).file(new MockMultipartFile("file", "invoice.pdf", "application/pdf", content)))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", BASE + "/" + document.getId()))
                .andExpect(jsonPath("$.id").value(document.getId().toString()))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.documentType").value("PDF"));
    }

    @Test
    void uploadWithoutAFilePartIsABadRequest() throws Exception {
        mockMvc.perform(multipart(BASE))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unsupportedFilesGetA415ProblemDetail() throws Exception {
        when(service.upload(any(), any())).thenThrow(new UnsupportedDocumentTypeException());

        mockMvc.perform(multipart(BASE).file(new MockMultipartFile("file", "notes.txt", "text/plain", new byte[] {1})))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.detail").value("Unsupported file type. Supported types: PDF, PNG, JPEG, TIFF"));
    }

    @Test
    void returnsADocumentWithItsExtractedFields() throws Exception {
        Document document = document();
        document.markCompleted("Invoice 42", Map.of("documentType", "invoice", "totalAmount", 120.5));
        when(service.get(document.getId())).thenReturn(document);

        mockMvc.perform(get(BASE + "/{id}", document.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.extractedFields.documentType").value("invoice"))
                .andExpect(jsonPath("$.extractedFields.totalAmount").value(120.5))
                // the full text has its own endpoint and is not repeated here
                .andExpect(jsonPath("$.extractedText").doesNotExist());
    }

    @Test
    void unknownDocumentsAreNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.get(id)).thenThrow(new DocumentNotFoundException(id));

        mockMvc.perform(get(BASE + "/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Document " + id + " does not exist"));
    }

    @Test
    void anIdThatIsNotAUuidIsABadRequest() throws Exception {
        mockMvc.perform(get(BASE + "/not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void textIsServedAsPlainText() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.getText(id)).thenReturn("Invoice 42");

        mockMvc.perform(get(BASE + "/{id}/text", id))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("Invoice 42"));
    }

    @Test
    void textOfAnUnfinishedDocumentIsAConflict() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.getText(id)).thenThrow(new DocumentNotReadyException(id, DocumentStatus.PROCESSING));

        mockMvc.perform(get(BASE + "/{id}/text", id))
                .andExpect(status().isConflict());
    }

    @Test
    void listReturnsAPageNewestFirstAndCapsThePageSize() throws Exception {
        Document document = document();
        when(service.list(eq(DocumentStatus.QUEUED), any(Pageable.class)))
                .thenAnswer(call -> new PageImpl<>(List.of(document), call.getArgument(1), 1));

        mockMvc.perform(get(BASE).param("status", "QUEUED").param("size", "5000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(document.getId().toString()))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.size").value(100));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(service).list(eq(DocumentStatus.QUEUED), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
    }

    @Test
    void listWithoutAStatusReturnsEverything() throws Exception {
        when(service.list(isNull(), any(Pageable.class)))
                .thenAnswer(call -> new PageImpl<>(List.of(), call.getArgument(1), 0));

        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void anUnknownStatusFilterIsABadRequest() throws Exception {
        mockMvc.perform(get(BASE).param("status", "DONE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reprocessIsAccepted() throws Exception {
        Document document = document();
        when(service.reprocess(document.getId())).thenReturn(document);

        mockMvc.perform(post(BASE + "/{id}/reprocess", document.getId()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void reprocessOfADocumentInThePipelineIsAConflict() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.reprocess(id)).thenThrow(new DocumentBusyException(id, DocumentStatus.PROCESSING));

        mockMvc.perform(post(BASE + "/{id}/reprocess", id))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete(BASE + "/{id}", id))
                .andExpect(status().isNoContent());

        verify(service).delete(id);
    }

    @Test
    void deletingAnUnknownDocumentIsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new DocumentNotFoundException(id)).when(service).delete(id);

        mockMvc.perform(delete(BASE + "/{id}", id))
                .andExpect(status().isNotFound());
    }

    private static Document document() {
        UUID id = UUID.randomUUID();
        return Document.queued(id, "invoice.pdf", DocumentType.PDF, 1024, id.toString());
    }
}
