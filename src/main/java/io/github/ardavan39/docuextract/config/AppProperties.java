package io.github.ardavan39.docuextract.config;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed view of everything under the {@code docuextract.*} configuration prefix.
 */
@ConfigurationProperties(prefix = "docuextract")
public record AppProperties(Storage storage, Ocr ocr, Ollama ollama) {

    /** Where uploaded files are kept. */
    public record Storage(Path directory) {
    }

    /**
     * @param binary       name or path of the tesseract executable
     * @param language     tesseract language code(s), for example {@code eng} or {@code eng+ita}
     * @param dpi          resolution used when rendering PDF pages for OCR
     * @param maxPages     upper bound on pages read from one PDF
     * @param minTextChars a PDF whose text layer is shorter than this is treated as scanned
     * @param timeout      limit for a single OCR call
     */
    public record Ocr(String binary, String language, int dpi, int maxPages, int minTextChars, Duration timeout) {
    }

    /**
     * @param maxInputChars document text is cut to this length before it is sent to the model
     */
    public record Ollama(String baseUrl, String model, Duration timeout, int maxInputChars) {
    }
}
