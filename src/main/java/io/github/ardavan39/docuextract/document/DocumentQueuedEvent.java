package io.github.ardavan39.docuextract.document;

import java.util.UUID;

/** Published when a document has been stored (or reset) and is ready for a worker. */
public record DocumentQueuedEvent(UUID documentId) {
}
