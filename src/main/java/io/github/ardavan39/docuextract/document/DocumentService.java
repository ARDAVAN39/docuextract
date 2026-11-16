package io.github.ardavan39.docuextract.document;

import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.ardavan39.docuextract.document.DocumentExceptions.DocumentBusyException;
import io.github.ardavan39.docuextract.document.DocumentExceptions.DocumentNotFoundException;
import io.github.ardavan39.docuextract.document.DocumentExceptions.DocumentNotReadyException;
import io.github.ardavan39.docuextract.document.DocumentExceptions.InvalidUploadException;
import io.github.ardavan39.docuextract.document.DocumentExceptions.UnsupportedDocumentTypeException;
import io.github.ardavan39.docuextract.storage.FileStorage;

@Service
public class DocumentService {

    private static final int MAX_FILENAME_LENGTH = 255;

    private final DocumentRepository repository;
    private final FileStorage storage;
    private final ApplicationEventPublisher events;

    public DocumentService(DocumentRepository repository, FileStorage storage, ApplicationEventPublisher events) {
        this.repository = repository;
        this.storage = storage;
        this.events = events;
    }

    /**
     * Stores the file and queues it. The event is only delivered after the transaction commits,
     * so a worker never looks for a row that is not there yet.
     */
    @Transactional
    public Document upload(String originalFilename, byte[] content) {
        if (content == null || content.length == 0) {
            throw new InvalidUploadException("Uploaded file is empty");
        }
        DocumentType type = DocumentType.detect(content).orElseThrow(UnsupportedDocumentTypeException::new);

        UUID id = UUID.randomUUID();
        String storageKey = id.toString();
        storage.store(storageKey, content);

        Document document = repository.save(
                Document.queued(id, cleanFilename(originalFilename), type, content.length, storageKey));
        events.publishEvent(new DocumentQueuedEvent(id));
        return document;
    }

    @Transactional(readOnly = true)
    public Document get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new DocumentNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<Document> list(DocumentStatus status, Pageable pageable) {
        return status == null ? repository.findAll(pageable) : repository.findByStatus(status, pageable);
    }

    @Transactional(readOnly = true)
    public String getText(UUID id) {
        Document document = get(id);
        if (document.getStatus() != DocumentStatus.COMPLETED) {
            throw new DocumentNotReadyException(id, document.getStatus());
        }
        return document.getExtractedText();
    }

    /** Runs a finished or failed document through the pipeline again, for example after a model change. */
    @Transactional
    public Document reprocess(UUID id) {
        Document document = get(id);
        if (document.getStatus() == DocumentStatus.QUEUED || document.getStatus() == DocumentStatus.PROCESSING) {
            throw new DocumentBusyException(id, document.getStatus());
        }
        document.markQueued();
        events.publishEvent(new DocumentQueuedEvent(id));
        return document;
    }

    @Transactional
    public void delete(UUID id) {
        Document document = get(id);
        repository.delete(document);
        storage.delete(document.getStorageKey());
    }

    /** Keeps only the last path segment, so a name like {@code ../../etc/passwd} is stored as {@code passwd}. */
    static String cleanFilename(String original) {
        if (original == null || original.isBlank()) {
            return "unnamed";
        }
        String normalised = original.replace('\\', '/');
        String name = normalised.substring(normalised.lastIndexOf('/') + 1).strip();
        if (name.isEmpty()) {
            return "unnamed";
        }
        return name.length() > MAX_FILENAME_LENGTH ? name.substring(0, MAX_FILENAME_LENGTH) : name;
    }
}
