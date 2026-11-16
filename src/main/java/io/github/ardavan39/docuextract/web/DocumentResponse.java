package io.github.ardavan39.docuextract.web;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import io.github.ardavan39.docuextract.document.Document;
import io.github.ardavan39.docuextract.document.DocumentStatus;
import io.github.ardavan39.docuextract.document.DocumentType;

public record DocumentResponse(
        UUID id,
        String filename,
        DocumentType documentType,
        long sizeBytes,
        DocumentStatus status,
        Map<String, Object> extractedFields,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt) {

    public static DocumentResponse from(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getFilename(),
                document.getDocumentType(),
                document.getSizeBytes(),
                document.getStatus(),
                document.getExtractedFields(),
                document.getErrorMessage(),
                document.getCreatedAt(),
                document.getUpdatedAt());
    }
}
