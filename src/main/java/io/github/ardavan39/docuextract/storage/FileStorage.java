package io.github.ardavan39.docuextract.storage;

/**
 * Keeps the original uploaded files. The interface is small on purpose so the local-disk
 * implementation can be swapped for object storage (Azure Blob, S3) without touching callers.
 */
public interface FileStorage {

    /** Stores the content under the given key, replacing anything already there. */
    void store(String key, byte[] content);

    byte[] load(String key);

    /** Removes the content. Does nothing if the key does not exist. */
    void delete(String key);
}
