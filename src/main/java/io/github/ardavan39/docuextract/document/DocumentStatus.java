package io.github.ardavan39.docuextract.document;

public enum DocumentStatus {
    /** Stored and waiting for a worker. */
    QUEUED,
    /** A worker is running OCR or waiting for the model. */
    PROCESSING,
    /** Text and fields are available. */
    COMPLETED,
    /** Processing stopped with an error; see the error message. Can be reprocessed. */
    FAILED
}
