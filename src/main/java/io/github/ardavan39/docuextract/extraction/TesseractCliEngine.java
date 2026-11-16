package io.github.ardavan39.docuextract.extraction;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.github.ardavan39.docuextract.config.AppProperties;

/**
 * Runs the {@code tesseract} command-line program. Calling the binary keeps native libraries out
 * of the JVM: a crash or a hang in the OCR engine costs one process, not the whole service.
 */
@Component
public class TesseractCliEngine implements OcrEngine {

    private final String binary;
    private final String language;
    private final Duration timeout;

    @Autowired
    public TesseractCliEngine(AppProperties properties) {
        this(properties.ocr().binary(), properties.ocr().language(), properties.ocr().timeout());
    }

    TesseractCliEngine(String binary, String language, Duration timeout) {
        this.binary = binary;
        this.language = language;
        this.timeout = timeout;
    }

    @Override
    public String recognize(BufferedImage image) {
        Path input = null;
        try {
            input = Files.createTempFile("docuextract-ocr-", ".png");
            ImageIO.write(image, "png", input.toFile());
            return run(input);
        } catch (IOException e) {
            throw new TextExtractionException("OCR failed: " + e.getMessage(), e);
        } finally {
            deleteQuietly(input);
        }
    }

    private String run(Path input) throws IOException {
        Process process = new ProcessBuilder(binary, input.toString(), "stdout", "-l", language)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        // Drain stdout on another thread, otherwise a full pipe buffer would block tesseract
        // and the timeout below would be the only way out.
        CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> readAll(process.getInputStream()));
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new TextExtractionException("OCR timed out after " + timeout.toSeconds() + "s");
            }
            if (process.exitValue() != 0) {
                throw new TextExtractionException("tesseract exited with code " + process.exitValue());
            }
            return output.get(5, TimeUnit.SECONDS).strip();
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new TextExtractionException("OCR was interrupted", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new TextExtractionException("Could not read OCR output", e);
        }
    }

    private static String readAll(InputStream stream) {
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // a leftover temp file is not worth failing the request for
        }
    }
}
