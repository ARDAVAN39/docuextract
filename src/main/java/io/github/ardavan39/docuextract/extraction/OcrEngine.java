package io.github.ardavan39.docuextract.extraction;

import java.awt.image.BufferedImage;

/** Reads the text printed in an image. */
public interface OcrEngine {

    String recognize(BufferedImage image);
}
