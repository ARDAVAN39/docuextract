package io.github.ardavan39.docuextract.document;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * File formats the service accepts. The type is detected from the first bytes of the file, not
 * from the file name or the Content-Type header, because both of those are chosen by the client.
 */
public enum DocumentType {

    PDF("application/pdf"),
    PNG("image/png"),
    JPEG("image/jpeg"),
    TIFF("image/tiff");

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] TIFF_LITTLE_ENDIAN = {'I', 'I', 0x2A, 0x00};
    private static final byte[] TIFF_BIG_ENDIAN = {'M', 'M', 0x00, 0x2A};

    private final String mediaType;

    DocumentType(String mediaType) {
        this.mediaType = mediaType;
    }

    public String mediaType() {
        return mediaType;
    }

    public boolean isImage() {
        return this != PDF;
    }

    public static Optional<DocumentType> detect(byte[] content) {
        if (content == null) {
            return Optional.empty();
        }
        if (startsWith(content, PDF_MAGIC)) {
            return Optional.of(PDF);
        }
        if (startsWith(content, PNG_MAGIC)) {
            return Optional.of(PNG);
        }
        if (startsWith(content, JPEG_MAGIC)) {
            return Optional.of(JPEG);
        }
        if (startsWith(content, TIFF_LITTLE_ENDIAN) || startsWith(content, TIFF_BIG_ENDIAN)) {
            return Optional.of(TIFF);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (content[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
