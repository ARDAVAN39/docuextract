package io.github.ardavan39.docuextract.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

import io.github.ardavan39.docuextract.TestFixtures;
import io.github.ardavan39.docuextract.document.DocumentType;

class TextExtractionServiceTest {

    private final OcrEngine ocrEngine = mock(OcrEngine.class);
    private final TextExtractionService service = new TextExtractionService(ocrEngine, TestFixtures.properties());

    @Test
    void readsTheTextLayerOfAPdfWithoutRunningOcr() {
        byte[] pdf = TestFixtures.pdfWithText("Invoice 2026-001 Total 120.50 EUR");

        String text = service.extract(pdf, DocumentType.PDF);

        assertThat(text).contains("Invoice 2026-001").contains("120.50 EUR");
        verifyNoInteractions(ocrEngine);
    }

    @Test
    void fallsBackToOcrPageByPageForAScannedPdf() {
        when(ocrEngine.recognize(any(BufferedImage.class))).thenReturn("first page", "second page");

        String text = service.extract(TestFixtures.pdfWithoutText(2), DocumentType.PDF);

        assertThat(text).isEqualTo("first page\n\nsecond page");
        verify(ocrEngine, times(2)).recognize(any(BufferedImage.class));
    }

    @Test
    void sendsImagesStraightToOcr() {
        when(ocrEngine.recognize(any(BufferedImage.class))).thenReturn("  Receipt 15.00 EUR \n");

        String text = service.extract(TestFixtures.blankPng(), DocumentType.PNG);

        assertThat(text).isEqualTo("Receipt 15.00 EUR");
    }

    @Test
    void reportsACorruptPdf() {
        byte[] broken = "%PDF-1.7 but nothing else".getBytes();

        assertThatThrownBy(() -> service.extract(broken, DocumentType.PDF))
                .isInstanceOf(TextExtractionException.class)
                .hasMessageContaining("Cannot read PDF");
    }

    @Test
    void reportsAnImageThatCannotBeDecoded() {
        byte[] broken = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};

        assertThatThrownBy(() -> service.extract(broken, DocumentType.PNG))
                .isInstanceOf(TextExtractionException.class);
    }
}
