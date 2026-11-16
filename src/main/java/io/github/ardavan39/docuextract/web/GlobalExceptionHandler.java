package io.github.ardavan39.docuextract.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import io.github.ardavan39.docuextract.document.DocumentExceptions.DocumentBusyException;
import io.github.ardavan39.docuextract.document.DocumentExceptions.DocumentNotFoundException;
import io.github.ardavan39.docuextract.document.DocumentExceptions.DocumentNotReadyException;
import io.github.ardavan39.docuextract.document.DocumentExceptions.InvalidUploadException;
import io.github.ardavan39.docuextract.document.DocumentExceptions.UnsupportedDocumentTypeException;

/**
 * Errors are returned as RFC 9457 problem details. Spring's own MVC exceptions (missing part,
 * upload too large, bad parameter type) are handled by the base class in the same format.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(DocumentNotFoundException.class)
    ProblemDetail notFound(DocumentNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(InvalidUploadException.class)
    ProblemDetail invalidUpload(InvalidUploadException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(UnsupportedDocumentTypeException.class)
    ProblemDetail unsupportedType(UnsupportedDocumentTypeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNSUPPORTED_MEDIA_TYPE, e.getMessage());
    }

    @ExceptionHandler({DocumentNotReadyException.class, DocumentBusyException.class})
    ProblemDetail conflict(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }
}
