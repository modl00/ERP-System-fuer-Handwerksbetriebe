package org.example.kalkulationsprogramm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

/** Briefpapier-Bilder: egal wie groß, sie werden auf A4-Auflösung verkleinert. */
class BriefpapierBildTest {

    private static byte[] bild(int breite, int hoehe, String format, int typ) throws IOException {
        BufferedImage b = new BufferedImage(breite, hoehe, typ);
        var g = b.createGraphics();
        g.setColor(new Color(240, 240, 250));
        g.fillRect(0, 0, breite, hoehe);
        g.setColor(Color.RED);
        g.fillRect(0, 0, breite / 10, hoehe / 10);
        g.dispose();
        ByteArrayOutputStream aus = new ByteArrayOutputStream();
        ImageIO.write(b, format, aus);
        return aus.toByteArray();
    }

    private static BufferedImage lies(byte[] bytes) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    @Test
    void kleinesBildBleibtUnveraendert() throws IOException {
        byte[] original = bild(1240, 1754, "png", BufferedImage.TYPE_INT_RGB);

        BriefpapierBild.Ergebnis e = BriefpapierBild.aufbereiten(original, "png");

        assertFalse(e.verkleinert());
        assertEquals(original, e.bytes(), "kein unnötiges Neukodieren");
        assertEquals(1240, e.breite());
    }

    @Test
    void riesigesBildWirdAufA4AufloesungVerkleinert() throws IOException {
        byte[] gross = bild(7000, 9900, "png", BufferedImage.TYPE_INT_RGB);

        BriefpapierBild.Ergebnis e = BriefpapierBild.aufbereiten(gross, "png");

        assertTrue(e.verkleinert());
        BufferedImage klein = lies(e.bytes());
        assertTrue(klein.getWidth() <= BriefpapierBild.MAX_BREITE, "Breite " + klein.getWidth());
        assertTrue(klein.getHeight() <= BriefpapierBild.MAX_HOEHE, "Höhe " + klein.getHeight());
        assertTrue(klein.getWidth() >= 1000, "nicht zu klein gerechnet");
        assertTrue(e.bytes().length < gross.length);
        assertEquals(new Color(240, 240, 250).getRGB() & 0xFFFFFF, klein.getRGB(klein.getWidth() / 2, klein.getHeight() / 2) & 0xFFFFFF, "Inhalt bleibt erhalten");
    }

    @Test
    void breitesBildWirdAnDerLaengerenSeiteVerkleinert() throws IOException {
        BriefpapierBild.Ergebnis e = BriefpapierBild.aufbereiten(bild(6000, 3000, "jpg", BufferedImage.TYPE_INT_RGB), "jpg");

        BufferedImage klein = lies(e.bytes());
        assertTrue(klein.getWidth() <= BriefpapierBild.MAX_BREITE);
        assertEquals(2.0, klein.getWidth() / (double) klein.getHeight(), 0.05, "Seitenverhältnis bleibt");
    }

    @Test
    void pngMitTransparenzAlsJpegWirdWeissUndNichtSchwarz() throws IOException {
        byte[] argb = bild(6000, 8000, "png", BufferedImage.TYPE_INT_ARGB);

        BriefpapierBild.Ergebnis e = BriefpapierBild.aufbereiten(argb, "jpg");

        assertTrue(e.verkleinert());
        assertEquals("jpg", e.endung());
        assertTrue(lies(e.bytes()).getWidth() <= BriefpapierBild.MAX_BREITE);
    }

    @Test
    void keinBildWirdMitVerstaendlicherMeldungAbgelehnt() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> BriefpapierBild.aufbereiten("Das ist kein Bild".getBytes(), "png"));
        assertTrue(e.getMessage().contains("Bild"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> BriefpapierBild.aufbereiten(new byte[0], "png"));
        assertThrows(IllegalArgumentException.class, () -> BriefpapierBild.aufbereiten(new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47 }, "png"));
    }
}
