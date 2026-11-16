package io.github.ardavan39.docuextract.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.ardavan39.docuextract.extraction.TextExtractionException;
import io.github.ardavan39.docuextract.extraction.TextExtractionService;
import io.github.ardavan39.docuextract.llm.FieldExtractor;
import io.github.ardavan39.docuextract.llm.LlmExtractionException;
import io.github.ardavan39.docuextract.storage.FileStorage;

@ExtendWith(MockitoExtension.class)
class DocumentProcessorTest {

    private static final byte[] CONTENT = {1, 2, 3};

    @Mock
    private DocumentRepository repository;

    @Mock
    private FileStorage storage;

    @Mock
    private TextExtractionService textExtraction;

    @Mock
    private FieldExtractor fieldExtractor;

    @InjectMocks
    private DocumentProcessor processor;

    private Document document;

    @BeforeEach
    void setUp() {
        UUID id = UUID.randomUUID();
        document = Document.queued(id, "invoice.pdf", DocumentType.PDF, CONTENT.length, id.toString());
    }

    @Test
    void completesADocumentWithItsTextAndFields() {
        givenStoredDocument();
        when(textExtraction.extract(CONTENT, DocumentType.PDF)).thenReturn("Invoice 42, total 120.50 EUR");
        when(fieldExtractor.extract("Invoice 42, total 120.50 EUR")).thenReturn(Map.of("totalAmount", 120.5));

        processor.process(document.getId());

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.COMPLETED);
        assertThat(document.getExtractedText()).isEqualTo("Invoice 42, total 120.50 EUR");
        assertThat(document.getExtractedFields()).containsEntry("totalAmount", 120.5);
        assertThat(document.getErrorMessage()).isNull();
    }

    @Test
    void marksTheDocumentFailedWhenTheModelCannotBeReached() {
        givenStoredDocument();
        when(textExtraction.extract(CONTENT, DocumentType.PDF)).thenReturn("some text");
        when(fieldExtractor.extract("some text")).thenThrow(new LlmExtractionException("Ollama request failed"));

        processor.process(document.getId());

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(document.getErrorMessage()).isEqualTo("Ollama request failed");
        assertThat(document.getExtractedFields()).isNull();
    }

    @Test
    void marksTheDocumentFailedWhenTheFileCannotBeRead() {
        givenStoredDocument();
        when(textExtraction.extract(CONTENT, DocumentType.PDF)).thenThrow(new TextExtractionException("Cannot read PDF"));

        processor.process(document.getId());

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(document.getErrorMessage()).isEqualTo("Cannot read PDF");
        verifyNoInteractions(fieldExtractor);
    }

    @Test
    void doesNotCallTheModelForADocumentWithNoText() {
        givenStoredDocument();
        when(textExtraction.extract(CONTENT, DocumentType.PDF)).thenReturn("   ");

        processor.process(document.getId());

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(document.getErrorMessage()).contains("No readable text");
        verifyNoInteractions(fieldExtractor);
    }

    @Test
    void ignoresADocumentThatWasDeletedWhileQueued() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        processor.process(id);

        verify(repository, never()).save(any());
        verifyNoInteractions(storage, textExtraction, fieldExtractor);
    }

    private void givenStoredDocument() {
        when(repository.findById(document.getId())).thenReturn(Optional.of(document));
        when(repository.save(any(Document.class))).then(returnsFirstArg());
        when(storage.load(document.getStorageKey())).thenReturn(CONTENT);
    }
}
