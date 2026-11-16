package io.github.ardavan39.docuextract.document;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Page<Document> findByStatus(DocumentStatus status, Pageable pageable);
}
