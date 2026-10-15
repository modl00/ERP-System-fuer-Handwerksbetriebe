package org.example.kalkulationsprogramm.service.wartung;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class WartungUnterschriftPrueferTest {

    private static final byte[] PNG_KOPF = pngMitMassen(360, 120);

    /** Signatur plus IHDR-Chunk mit den angegebenen Maßen (mehr braucht die Prüfung nicht). */
    private static byte[] pngMitMassen(int breite, int hoehe) {
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(33);
        b.put(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        b.putInt(13).put(new byte[] {'I', 'H', 'D', 'R'}).putInt(breite).putInt(hoehe).put(new byte[] {8, 6, 0, 0, 0});
        return b.array();
    }

    private static String pngAlsDataUrl(byte[] daten) {
        return WartungUnterschriftPruefer.PRAEFIX + Base64.getEncoder().encodeToString(daten);
    }

    @Test
    @DisplayName("Ein echtes PNG als Data-URL wird angenommen")
    void gueltigesPng() {
        assertThatCode(() -> WartungUnterschriftPruefer.pruefe(pngAlsDataUrl(PNG_KOPF))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Ein PNG mit riesigen Maßen oder ohne IHDR wird abgelehnt, bevor das PDF es dekodiert")
    void unsinnigeMasse() {
        for (byte[] boese : new byte[][] {pngMitMassen(100_000, 100_000), pngMitMassen(0, 10), pngMitMassen(-1, 10),
                java.util.Arrays.copyOf(pngMitMassen(10, 10), 12)}) {
            assertThatThrownBy(() -> WartungUnterschriftPruefer.pruefe(pngAlsDataUrl(boese)))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> org.assertj.core.api.Assertions.assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
    }

    @Test
    @DisplayName("SVG und HTML werden abgelehnt, weil sie Skripte enthalten könnten")
    void svgUndHtmlAbgelehnt() {
        String svg = "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString("<svg onload=alert(1)/>".getBytes());
        String html = "data:text/html;base64," + Base64.getEncoder().encodeToString("<script>alert(1)</script>".getBytes());
        for (String boese : new String[] {svg, html, "javascript:alert(1)", "https://boese.example/x.png"}) {
            assertThatThrownBy(() -> WartungUnterschriftPruefer.pruefe(boese))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> org.assertj.core.api.Assertions.assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
    }

    @Test
    @DisplayName("Eine Datei, die nur als PNG beschriftet ist, aber keine PNG-Daten enthält, wird abgelehnt")
    void falscherInhaltMitPngEtikett() {
        String getarnt = pngAlsDataUrl("<script>alert(1)</script>".getBytes());
        assertThatThrownBy(() -> WartungUnterschriftPruefer.pruefe(getarnt))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("PNG");
    }

    @Test
    @DisplayName("Kaputtes Base64 und leere Daten werden mit einer Meldung abgelehnt")
    void kaputtOderLeer() {
        assertThatThrownBy(() -> WartungUnterschriftPruefer.pruefe(WartungUnterschriftPruefer.PRAEFIX + "%%%kein-base64%%%"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> WartungUnterschriftPruefer.pruefe(WartungUnterschriftPruefer.PRAEFIX))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("Überlange Bilder werden abgelehnt")
    void zuGross() {
        String riesig = WartungUnterschriftPruefer.PRAEFIX + "A".repeat(WartungUnterschriftPruefer.MAX_ZEICHEN + 1);
        assertThatThrownBy(() -> WartungUnterschriftPruefer.pruefe(riesig))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("zu groß");
    }
}
