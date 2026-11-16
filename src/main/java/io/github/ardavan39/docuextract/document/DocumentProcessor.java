package io.github.ardavan39.docuextract.document;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import io.github.ardavan39.docuextract.config.AsyncConfig;
import io.github.ardavan39.docuextract.extraction.TextExtractionService;
import io.github.ardavan39.docuextract.llm.FieldExtractor;
import io.github.ardavan39.docuextract.storage.FileStorage;

/**
 * The processing pipeline: load the file, get its text, ask the model for fields, save the result.
 *
 * <p>No database transaction is held open while OCR or the model is running. Each status change is
 * saved on its own, so a client polling the API sees PROCESSING while the slow part is under way.
 */
@Component
public class DocumentProcessor {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessor.class);

    private final DocumentRepository repository;
    private final FileStorage storage;
    private final TextExtractionService textExtraction;
    private final FieldExtractor fieldExtractor;

    public DocumentProcessor(DocumentRepository repository, FileStorage storage,
            TextExtractionService textExtraction, FieldExtractor fieldExtractor) {
        this.repository = repository;
        this.storage = storage;
        this.textExtraction = textExtraction;
        this.fieldExtractor = fieldExtractor;
    }

    @Async(AsyncConfig.DOCUMENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentQueued(DocumentQueuedEvent event) {
        process(event.documentId());
    }

    void process(UUID id) {
        Document document = repository.findById(id).orElse(null);
        if (document == null) {
            log.warn("Document {} was deleted before it could be processed", id);
            return;
        }

        document.markProcessing();
        document = repository.save(document);

        try {
            byte[] content = storage.load(document.getStorageKey());
            String text = textExtraction.extract(content, document.getDocumentType());
            if (text.isBlank()) {
                throw new IllegalStateException("No readable text found in the document");
            }
            Map<String, Object> fields = fieldExtractor.extract(text);
            document.markCompleted(text, fields);
            log.info("Document {} processed ({} characters of text)", id, text.length());
        } catch (RuntimeException e) {
            String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.warn("Document {} failed: {}", id, reason);
            document.markFailed(reason);
        }
        repository.save(document);
    }
}
