package io.github.ardavan39.docuextract.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import io.github.ardavan39.docuextract.TestFixtures;
import io.github.ardavan39.docuextract.document.DocumentExceptions.DocumentBusyException;
import io.github.ardavan39.docuextract.document.DocumentExceptions.DocumentNotFoundException;
import io.github.ardavan39.docuextract.document.DocumentExceptions.DocumentNotReadyException;
import io.github.ardavan39.docuextract.document.DocumentExceptions.InvalidUploadException;
import io.github.ardavan39.docuextract.document.DocumentExceptions.UnsupportedDocumentTypeException;
import io.github.ardavan39.docuextract.storage.FileStorage;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository repository;

    @Mock
    private FileStorage storage;

    @Mock
    private ApplicationEventPublisher events;

    @InjectMocks
    private DocumentService service;

    @Test
    void uploadStoresTheFileAndQueuesTheDocument() {
        when(repository.save(any(Document.class))).then(returnsFirstArg());
        byte[] pdf = TestFixtures.pdfWithText("Invoice 42");

        Document document = service.upload("invoice.pdf", pdf);

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.QUEUED);
        assertThat(document.getDocumentType()).isEqualTo(DocumentType.PDF);
        assertThat(document.getFilename()).isEqualTo("invoice.pdf");
        assertThat(document.getSizeBytes()).isEqualTo(pdf.length);
        verify(storage).store(document.getStorageKey(), pdf);

        ArgumentCaptor<DocumentQueuedEvent> event = ArgumentCaptor.forClass(DocumentQueuedEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().documentId()).isEqualTo(document.getId());
    }

    @Test
    void uploadRejectsAnEmptyFile() {
        assertThatThrownBy(() -> service.upload("empty.pdf", new byte[0]))
                .isInstanceOf(InvalidUploadException.class);
        verifyNoInteractions(storage, repository, events);
    }

    @Test
    void uploadRejectsUnsupportedContentWhateverTheFileIsCalled() {
        byte[] text = "not really a pdf".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.upload("report.pdf", text))
                .isInstanceOf(UnsupportedDocumentTypeException.class);
        verifyNoInteractions(storage, repository, events);
    }

    @Test
    void filenamesAreReducedToTheirLastSegment() {
        assertThat(DocumentService.cleanFilename("../../etc/passwd")).isEqualTo("passwd");
        assertThat(DocumentService.cleanFilename("C:\\Users\\me\\scan.png")).isEqualTo("scan.png");
        assertThat(DocumentService.cleanFilename("  ")).isEqualTo("unnamed");
        assertThat(DocumentService.cleanFilename(null)).isEqualTo("unnamed");
        assertThat(DocumentService.cleanFilename("a".repeat(300))).hasSize(255);
    }

    @Test
    void getFailsForAnUnknownId() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id)).isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void textIsOnlyAvailableOnceProcessingHasFinished() {
        Document document = document();
        when(repository.findById(document.getId())).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> service.getText(document.getId())).isInstanceOf(DocumentNotReadyException.class);

        document.markCompleted("the text", Map.of());
        assertThat(service.getText(document.getId())).isEqualTo("the text");
    }

    @Test
    void reprocessClearsThePreviousResultAndQueuesAgain() {
        Document document = document();
        document.markFailed("model was down");
        when(repository.findById(document.getId())).thenReturn(Optional.of(document));

        Document result = service.reprocess(document.getId());

        assertThat(result.getStatus()).isEqualTo(DocumentStatus.QUEUED);
        assertThat(result.getErrorMessage()).isNull();
        verify(events).publishEvent(new DocumentQueuedEvent(document.getId()));
    }

    @Test
    void reprocessIsRefusedWhileADocumentIsStillInThePipeline() {
        Document document = document();
        when(repository.findById(document.getId())).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> service.reprocess(document.getId())).isInstanceOf(DocumentBusyException.class);
        verifyNoInteractions(events);
    }

    @Test
    void deleteRemovesTheRowAndTheStoredFile() {
        Document document = document();
        when(repository.findById(document.getId())).thenReturn(Optional.of(document));

        service.delete(document.getId());

        verify(repository).delete(document);
        verify(storage).delete(document.getStorageKey());
    }

    private static Document document() {
        UUID id = UUID.randomUUID();
        return Document.queued(id, "invoice.pdf", DocumentType.PDF, 1024, id.toString());
    }
}
