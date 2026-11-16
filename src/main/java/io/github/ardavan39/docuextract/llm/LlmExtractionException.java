package io.github.ardavan39.docuextract.llm;

public class LlmExtractionException extends RuntimeException {

    public LlmExtractionException(String message) {
        super(message);
    }

    public LlmExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
