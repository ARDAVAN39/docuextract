package io.github.ardavan39.docuextract;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import io.github.ardavan39.docuextract.config.AppProperties;

/** Small in-memory documents and settings shared by the tests. */
public final class TestFixtures {

    private TestFixtures() {
    }

    public static AppProperties properties() {
        return new AppProperties(
                new AppProperties.Storage(Path.of("build-test-storage")),
                new AppProperties.Ocr("tesseract", "eng", 72, 20, 20, Duration.ofSeconds(30)),
                new AppProperties.Ollama("http://ollama.test", "test-model", Duration.ofSeconds(5), 50));
    }

    /** A one-page PDF with a real text layer. */
    public static byte[] pdfWithText(String text) {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            pdf.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 700);
                content.showText(text);
                content.endText();
            }
            pdf.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A PDF with empty pages, which is what a scan looks like to a text extractor. */
    public static byte[] pdfWithoutText(int pages) {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) {
                pdf.addPage(new PDPage());
            }
            pdf.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] blankPng() {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB), "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
