package io.github.ardavan39.docuextract.llm;

import java.util.Map;

/** Turns the raw text of a document into named fields. */
public interface FieldExtractor {

    Map<String, Object> extract(String documentText);
}
