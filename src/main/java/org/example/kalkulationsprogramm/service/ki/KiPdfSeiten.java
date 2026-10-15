package org.example.kalkulationsprogramm.service.ki;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.example.kalkulationsprogramm.service.ki.KiTypen.Anhang;

/**
 * Macht aus einem PDF Seitenbilder, wenn der gewählte KI-Anbieter zwar Bilder, aber keine PDFs lesen kann (Mistral, lokale Modelle mit Bild).
 * So lassen sich auch gescannte Rechnungen mit solchen Anbietern auslesen. Es werden höchstens {@value #MAX_SEITEN} Seiten verwendet.
 */
public final class KiPdfSeiten {

    public static final int MAX_SEITEN = 6;
    private static final float DPI = 110f;

    private KiPdfSeiten() {
    }

    /** Ersetzt PDF-Anhänge durch PNG-Seitenbilder, wenn der Anbieter PDFs nicht, aber Bilder liest; alles andere bleibt unverändert. */
    public static List<Anhang> fuer(KiAnbieter anbieter, List<Anhang> dateien) throws IOException {
        if (anbieter.liest("application/pdf") || !anbieter.liest("image/png")) {
            return dateien;
        }
        List<Anhang> ergebnis = new ArrayList<>();
        for (Anhang a : dateien) {
            if (a.istPdf()) {
                ergebnis.addAll(seiten(a.daten()));
            } else {
                ergebnis.add(a);
            }
        }
        return ergebnis;
    }

    static List<Anhang> seiten(byte[] pdf) throws IOException {
        try (PDDocument dokument = Loader.loadPDF(pdf)) {
            PDFRenderer renderer = new PDFRenderer(dokument);
            int anzahl = Math.min(dokument.getNumberOfPages(), MAX_SEITEN);
            List<Anhang> seiten = new ArrayList<>();
            for (int i = 0; i < anzahl; i++) {
                BufferedImage bild = renderer.renderImageWithDPI(i, DPI);
                ByteArrayOutputStream aus = new ByteArrayOutputStream();
                ImageIO.write(bild, "png", aus);
                seiten.add(new Anhang("image/png", aus.toByteArray()));
            }
            if (seiten.isEmpty()) {
                throw new IOException("Das PDF hat keine Seiten.");
            }
            return seiten;
        }
    }
}
