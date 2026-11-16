package io.github.ardavan39.docuextract.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStorageTest {

    @TempDir
    Path directory;

    private LocalFileStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalFileStorage(directory.resolve("uploads"));
    }

    @Test
    void createsTheDirectoryOnStartup() {
        assertThat(directory.resolve("uploads")).isDirectory();
    }

    @Test
    void returnsWhatWasStored() {
        byte[] content = {1, 2, 3, 4};

        storage.store("doc-1", content);

        assertThat(storage.load("doc-1")).isEqualTo(content);
    }

    @Test
    void deleteRemovesTheFileAndCanBeRepeated() {
        storage.store("doc-1", new byte[] {1});

        storage.delete("doc-1");

        assertThat(Files.exists(directory.resolve("uploads/doc-1"))).isFalse();
        assertThatCode(() -> storage.delete("doc-1")).doesNotThrowAnyException();
    }

    @Test
    void loadingAMissingKeyFails() {
        assertThatThrownBy(() -> storage.load("nope")).isInstanceOf(StorageException.class);
    }

    @Test
    void keysCannotEscapeTheStorageDirectory() {
        assertThatThrownBy(() -> storage.store("../outside", new byte[] {1})).isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> storage.load("../../etc/passwd")).isInstanceOf(StorageException.class);
        assertThat(Files.exists(directory.resolve("outside"))).isFalse();
    }
}
