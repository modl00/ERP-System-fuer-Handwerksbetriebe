package org.example.kalkulationsprogramm.service.wartung;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Base64;

/**
 * Prüft das Bild einer Unterschrift. Das Bild wird später als {@code <img src>} ausgeliefert, deshalb
 * wird nur ein echtes PNG als Data-URL angenommen: kein SVG, kein HTML, keine fremde URL.
 */
public final class WartungUnterschriftPruefer {

    public static final String PRAEFIX = "data:image/png;base64,";
    /** Eine gezeichnete Unterschrift braucht wenige KB. 700.000 Zeichen sind großzügig, aber begrenzt. */
    static final int MAX_ZEICHEN = 700_000;
    private static final byte[] PNG_KOPF = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private WartungUnterschriftPruefer() {
    }

    /** Wirft eine Meldung für den Nutzer, wenn das Bild nicht angenommen werden kann. */
    public static void pruefe(String bild) {
        if (!bild.startsWith(PRAEFIX)) {
            throw falsch("Die Unterschrift muss ein PNG-Bild sein.");
        }
        if (bild.length() > MAX_ZEICHEN) {
            throw falsch("Die Unterschrift ist zu groß. Bitte noch einmal kleiner zeichnen.");
        }
        byte[] daten;
        try {
            daten = Base64.getDecoder().decode(bild.substring(PRAEFIX.length()));
        } catch (IllegalArgumentException e) {
            throw falsch("Die Unterschrift ist beschädigt. Bitte noch einmal zeichnen.");
        }
        if (daten.length < PNG_KOPF.length) {
            throw falsch("Die Unterschrift ist leer.");
        }
        for (int i = 0; i < PNG_KOPF.length; i++) {
            if (daten[i] != PNG_KOPF[i]) {
                throw falsch("Die Unterschrift muss ein PNG-Bild sein.");
            }
        }
        pruefeMasse(daten);
    }

    /** Höchste Kantenlänge: Der PDF-Dienst dekodiert das Bild, ein winziges PNG mit riesigen Maßen würde viel Speicher belegen. */
    private static final int MAX_KANTE = 4000;

    private static void pruefeMasse(byte[] daten) {
        // Nach der Signatur folgt der IHDR-Chunk: Länge (4), "IHDR" (4), Breite (4), Höhe (4)
        if (daten.length < 24 || daten[12] != 'I' || daten[13] != 'H' || daten[14] != 'D' || daten[15] != 'R') {
            throw falsch("Die Unterschrift ist beschädigt. Bitte noch einmal zeichnen.");
        }
        long breite = lies32(daten, 16);
        long hoehe = lies32(daten, 20);
        if (breite < 1 || hoehe < 1 || breite > MAX_KANTE || hoehe > MAX_KANTE) {
            throw falsch("Die Unterschrift hat ein ungültiges Format. Bitte noch einmal zeichnen.");
        }
    }

    private static long lies32(byte[] daten, int ab) {
        return ((long) (daten[ab] & 0xFF) << 24) | ((daten[ab + 1] & 0xFF) << 16) | ((daten[ab + 2] & 0xFF) << 8) | (daten[ab + 3] & 0xFF);
    }

    private static ResponseStatusException falsch(String meldung) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, meldung);
    }
}
