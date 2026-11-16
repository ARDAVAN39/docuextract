package io.github.ardavan39.docuextract.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import io.github.ardavan39.docuextract.TestFixtures;

class DocumentTypeTest {

    @Test
    void detectsPdf() {
        assertThat(DocumentType.detect(TestFixtures.pdfWithText("hello"))).contains(DocumentType.PDF);
    }

    @Test
    void detectsPng() {
        assertThat(DocumentType.detect(TestFixtures.blankPng())).contains(DocumentType.PNG);
    }

    @Test
    void detectsJpeg() {
        byte[] jpegHeader = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
        assertThat(DocumentType.detect(jpegHeader)).contains(DocumentType.JPEG);
    }

    @Test
    void detectsBothTiffByteOrders() {
        assertThat(DocumentType.detect(new byte[] {'I', 'I', 0x2A, 0x00, 0x08})).contains(DocumentType.TIFF);
        assertThat(DocumentType.detect(new byte[] {'M', 'M', 0x00, 0x2A, 0x00})).contains(DocumentType.TIFF);
    }

    @Test
    void rejectsOtherContent() {
        assertThat(DocumentType.detect("just some text".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(DocumentType.detect(new byte[0])).isEmpty();
        assertThat(DocumentType.detect(null)).isEmpty();
    }

    @Test
    void doesNotTrustAFileThatOnlyLooksLikeAPdfByName() {
        // the name is never consulted, so a renamed text file is still rejected
        assertThat(DocumentType.detect("PDF report".getBytes(StandardCharsets.UTF_8))).isEmpty();
    }
}
