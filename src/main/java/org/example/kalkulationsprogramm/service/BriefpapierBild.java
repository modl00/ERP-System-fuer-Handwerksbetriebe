package org.example.kalkulationsprogramm.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import lombok.extern.slf4j.Slf4j;

/**
 * Bereitet ein hochgeladenes Briefpapier-Bild für das PDF auf: Egal wie groß die Datei ist, es wird auf eine vernünftige Auflösung
 * (A4 bei 300 dpi, 2480 × 3508 Pixel) verkleinert, ohne das ganze Bild im Speicher zu entfalten (Teilabtastung beim Lesen). Das hält
 * die PDFs klein und schützt den Server vor riesigen Bildern. Im PDF wird das Bild später ohnehin auf die ganze A4-Seite gezogen.
 */
@Slf4j
final class BriefpapierBild {

    /** A4 bei 300 dpi. */
    static final int MAX_BREITE = 2480;
    static final int MAX_HOEHE = 3508;
    /** Mehr Pixel lesen wir nicht einmal an (Schutz vor Dekompressionsbomben). */
    static final long MAX_PIXEL = 400_000_000L;

    /** Das aufbereitete Bild samt Dateiendung ("png" oder "jpg"). */
    record Ergebnis(byte[] bytes, String endung, int breite, int hoehe, boolean verkleinert) {
    }

    private BriefpapierBild() {
    }

    /**
     * @param daten    die hochgeladene Datei (PNG oder JPEG)
     * @param endung   "png" oder "jpg", bestimmt das Ausgabeformat
     * @throws IllegalArgumentException wenn es kein lesbares Bild ist oder zu groß
     */
    static Ergebnis aufbereiten(byte[] daten, String endung) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(daten))) {
            if (in == null) {
                throw new IllegalArgumentException("Die Datei ist kein lesbares Bild.");
            }
            Iterator<ImageReader> leser = ImageIO.getImageReaders(in);
            if (!leser.hasNext()) {
                throw new IllegalArgumentException("Die Datei ist kein lesbares Bild (PNG oder JPEG).");
            }
            ImageReader reader = leser.next();
            try {
                reader.setInput(in, true, true);
                int breite = reader.getWidth(0);
                int hoehe = reader.getHeight(0);
                if (breite <= 0 || hoehe <= 0 || (long) breite * hoehe > MAX_PIXEL) {
                    throw new IllegalArgumentException("Das Bild ist zu groß (mehr als 400 Megapixel). Bitte verkleinern.");
                }
                int faktor = Math.max(1, Math.max(ceilDiv(breite, MAX_BREITE), ceilDiv(hoehe, MAX_HOEHE)));
                if (faktor == 1) {
                    return new Ergebnis(daten, endung, breite, hoehe, false);
                }
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(faktor, faktor, 0, 0);
                BufferedImage klein = reader.read(0, param);
                return new Ergebnis(schreibe(klein, endung), endung, klein.getWidth(), klein.getHeight(), true);
            } catch (IIOException e) {
                log.debug("Briefpapier-Bild nicht lesbar", e);
                throw new IllegalArgumentException("Das Bild lässt sich nicht lesen (kein gültiges PNG oder JPEG).", e);
            } finally {
                reader.dispose();
            }
        }
    }

    private static int ceilDiv(int a, int b) {
        return (a + b - 1) / b;
    }

    private static byte[] schreibe(BufferedImage bild, String endung) throws IOException {
        BufferedImage ziel = bild;
        if ("jpg".equals(endung) && bild.getType() != BufferedImage.TYPE_INT_RGB) {
            // JPEG kennt keine Transparenz: auf Weiß setzen
            ziel = new BufferedImage(bild.getWidth(), bild.getHeight(), BufferedImage.TYPE_INT_RGB);
            var g = ziel.createGraphics();
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, ziel.getWidth(), ziel.getHeight());
            g.drawImage(bild, 0, 0, null);
            g.dispose();
        }
        ByteArrayOutputStream aus = new ByteArrayOutputStream();
        if (!ImageIO.write(ziel, "jpg".equals(endung) ? "jpeg" : "png", aus)) {
            throw new IOException("Das Bild konnte nicht gespeichert werden.");
        }
        return aus.toByteArray();
    }
}
