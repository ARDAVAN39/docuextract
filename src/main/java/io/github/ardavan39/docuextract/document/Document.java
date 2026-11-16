package io.github.ardavan39.docuextract.document;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * An uploaded file and the result of processing it. State changes go through the
 * {@code mark...} methods so the status, result and error message always agree with each other.
 */
@Entity
@Table(name = "documents")
public class Document {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String filename;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 10)
    private DocumentType documentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus status;

    @Column(name = "extracted_text", columnDefinition = "text")
    private String extractedText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extracted_fields", columnDefinition = "jsonb")
    private Map<String, Object> extractedFields;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Document() {
        // for JPA
    }

    private Document(UUID id, String filename, DocumentType documentType, long sizeBytes, String storageKey) {
        Instant now = Instant.now();
        this.id = id;
        this.filename = filename;
        this.documentType = documentType;
        this.sizeBytes = sizeBytes;
        this.storageKey = storageKey;
        this.status = DocumentStatus.QUEUED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Document queued(UUID id, String filename, DocumentType documentType, long sizeBytes,
            String storageKey) {
        return new Document(id, filename, documentType, sizeBytes, storageKey);
    }

    public void markQueued() {
        transitionTo(DocumentStatus.QUEUED);
        this.extractedText = null;
        this.extractedFields = null;
        this.errorMessage = null;
    }

    public void markProcessing() {
        transitionTo(DocumentStatus.PROCESSING);
        this.errorMessage = null;
    }

    public void markCompleted(String text, Map<String, Object> fields) {
        transitionTo(DocumentStatus.COMPLETED);
        this.extractedText = text;
        this.extractedFields = fields;
        this.errorMessage = null;
    }

    public void markFailed(String reason) {
        transitionTo(DocumentStatus.FAILED);
        this.errorMessage = reason;
    }

    private void transitionTo(DocumentStatus next) {
        this.status = next;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getFilename() {
        return filename;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public String getExtractedText() {
        return extractedText;
    }

    public Map<String, Object> getExtractedFields() {
        return extractedFields;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
