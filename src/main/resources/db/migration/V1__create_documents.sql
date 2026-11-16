CREATE TABLE documents (
    id               UUID         PRIMARY KEY,
    filename         VARCHAR(255) NOT NULL,
    document_type    VARCHAR(10)  NOT NULL,
    size_bytes       BIGINT       NOT NULL,
    storage_key      VARCHAR(255) NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    extracted_text   TEXT,
    extracted_fields JSONB,
    error_message    TEXT,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_documents_status ON documents (status);
CREATE INDEX idx_documents_created_at ON documents (created_at DESC);
