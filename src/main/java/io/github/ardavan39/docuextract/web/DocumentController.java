package io.github.ardavan39.docuextract.web;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.github.ardavan39.docuextract.document.Document;
import io.github.ardavan39.docuextract.document.DocumentService;
import io.github.ardavan39.docuextract.document.DocumentStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/documents")
@Tag(name = "Documents")
public class DocumentController {

    private static final int MAX_PAGE_SIZE = 100;

    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    @Operation(summary = "Upload a PDF or image. Processing runs in the background; poll the returned location.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> upload(@RequestPart("file") MultipartFile file) throws IOException {
        Document document = service.upload(file.getOriginalFilename(), file.getBytes());
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/documents/" + document.getId()))
                .body(DocumentResponse.from(document));
    }

    @Operation(summary = "Get a document with its status and extracted fields")
    @GetMapping("/{id}")
    public DocumentResponse get(@PathVariable UUID id) {
        return DocumentResponse.from(service.get(id));
    }

    @Operation(summary = "Get the plain text read from a completed document")
    @GetMapping(value = "/{id}/text", produces = MediaType.TEXT_PLAIN_VALUE)
    public String text(@PathVariable UUID id) {
        return service.getText(id);
    }

    @Operation(summary = "List documents, newest first")
    @GetMapping
    public PageResponse<DocumentResponse> list(
            @RequestParam(required = false) DocumentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(service.list(status, pageRequest), DocumentResponse::from);
    }

    @Operation(summary = "Run a completed or failed document through the pipeline again")
    @PostMapping("/{id}/reprocess")
    public ResponseEntity<DocumentResponse> reprocess(@PathVariable UUID id) {
        return ResponseEntity.accepted().body(DocumentResponse.from(service.reprocess(id)));
    }

    @Operation(summary = "Delete a document and its stored file")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
