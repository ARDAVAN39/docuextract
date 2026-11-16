package io.github.ardavan39.docuextract.extraction;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.github.ardavan39.docuextract.config.AppProperties;
import io.github.ardavan39.docuextract.document.DocumentType;

/**
 * Gets plain text out of a file.
 *
 * <p>PDFs that carry a text layer are read directly, which is fast and exact. PDFs without one
 * (scans) are rendered page by page and sent through OCR, as are plain images.
 */
@Service
public class TextExtractionService {

    private static final Logger log = LoggerFactory.getLogger(TextExtractionService.class);

    private final OcrEngine ocrEngine;
    private final AppProperties.Ocr settings;

    public TextExtractionService(OcrEngine ocrEngine, AppProperties properties) {
        this.ocrEngine = ocrEngine;
        this.settings = properties.ocr();
    }

    public String extract(byte[] content, DocumentType type) {
        return switch (type) {
            case PDF -> extractFromPdf(content);
            case PNG, JPEG, TIFF -> extractFromImage(content);
        };
    }

    private String extractFromPdf(byte[] content) {
        try (PDDocument pdf = Loader.loadPDF(content)) {
            int pages = Math.min(pdf.getNumberOfPages(), settings.maxPages());
            if (pages == 0) {
                return "";
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setEndPage(pages);
            String text = stripper.getText(pdf).strip();
            if (text.length() >= settings.minTextChars()) {
                return text;
            }

            log.debug("PDF has no usable text layer, running OCR on {} page(s)", pages);
            return ocrPages(pdf, pages);
        } catch (IOException e) {
            throw new TextExtractionException("Cannot read PDF: " + e.getMessage(), e);
        }
    }

    private String ocrPages(PDDocument pdf, int pages) throws IOException {
        PDFRenderer renderer = new PDFRenderer(pdf);
        StringBuilder text = new StringBuilder();
        for (int page = 0; page < pages; page++) {
            BufferedImage image = renderer.renderImageWithDPI(page, settings.dpi(), ImageType.GRAY);
            String pageText = ocrEngine.recognize(image);
            if (!pageText.isBlank()) {
                if (!text.isEmpty()) {
                    text.append("\n\n");
                }
                text.append(pageText.strip());
            }
        }
        return text.toString();
    }

    private String extractFromImage(byte[] content) {
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(content));
        } catch (IOException e) {
            throw new TextExtractionException("Cannot read image: " + e.getMessage(), e);
        }
        if (image == null) {
            throw new TextExtractionException("Image format is not readable");
        }
        return ocrEngine.recognize(image).strip();
    }
}
