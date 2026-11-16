package io.github.ardavan39.docuextract.document;

import java.util.UUID;

/** Errors the API reports back to the client. Mapped to HTTP statuses in {@code GlobalExceptionHandler}. */
public final class DocumentExceptions {

    private DocumentExceptions() {
    }

    public static class DocumentNotFoundException extends RuntimeException {
        public DocumentNotFoundException(UUID id) {
            super("Document " + id + " does not exist");
        }
    }

    public static class InvalidUploadException extends RuntimeException {
        public InvalidUploadException(String message) {
            super(message);
        }
    }

    public static class UnsupportedDocumentTypeException extends RuntimeException {
        public UnsupportedDocumentTypeException() {
            super("Unsupported file type. Supported types: PDF, PNG, JPEG, TIFF");
        }
    }

    public static class DocumentNotReadyException extends RuntimeException {
        public DocumentNotReadyException(UUID id, DocumentStatus status) {
            super("Document " + id + " is " + status + ", not COMPLETED");
        }
    }

    public static class DocumentBusyException extends RuntimeException {
        public DocumentBusyException(UUID id, DocumentStatus status) {
            super("Document " + id + " is already " + status);
        }
    }
}
