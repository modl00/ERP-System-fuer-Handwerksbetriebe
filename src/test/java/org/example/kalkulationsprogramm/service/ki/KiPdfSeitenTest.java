package org.example.kalkulationsprogramm.service.ki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.example.kalkulationsprogramm.service.ki.KiTypen.Anhang;
import org.junit.jupiter.api.Test;

/** PDFs werden nur dann zu Seitenbildern, wenn der Anbieter keine PDFs, aber Bilder liest. Nur ein leeres Dummy-PDF. */
class KiPdfSeitenTest {

    private static byte[] pdfMitSeiten(int n) throws Exception {
        try (PDDocument d = new PDDocument(); ByteArrayOutputStream aus = new ByteArrayOutputStream()) {
            for (int i = 0; i < n; i++) {
                d.addPage(new PDPage());
            }
            d.save(aus);
            return aus.toByteArray();
        }
    }

    private static KiAnbieter anbieter(boolean pdf, boolean bild) {
        KiAnbieter a = mock(KiAnbieter.class);
        when(a.liest("application/pdf")).thenReturn(pdf);
        when(a.liest("image/png")).thenReturn(bild);
        return a;
    }

    @Test
    void einAnbieterDerBilderAberKeinePdfsLiestBekommtDieSeitenAlsPng() throws Exception {
        List<Anhang> r = KiPdfSeiten.fuer(anbieter(false, true), List.of(new Anhang("application/pdf", pdfMitSeiten(2))));

        assertThat(r).hasSize(2);
        assertThat(r).allSatisfy(a -> assertThat(a.mime()).isEqualTo("image/png"));
    }

    @Test
    void esWerdenHoechstensSechsSeitenGeschickt() throws Exception {
        assertThat(KiPdfSeiten.fuer(anbieter(false, true), List.of(new Anhang("application/pdf", pdfMitSeiten(9))))).hasSize(KiPdfSeiten.MAX_SEITEN);
    }

    @Test
    void einAnbieterDerPdfsLiestBekommtDasPdfUnveraendert() throws Exception {
        List<Anhang> vorher = List.of(new Anhang("application/pdf", pdfMitSeiten(1)));
        assertThat(KiPdfSeiten.fuer(anbieter(true, true), vorher)).isSameAs(vorher);
    }

    @Test
    void liestDerAnbieterWederPdfNochBilderBleibtAllesUnveraendertDamitDerFehlerKlarKommt() throws Exception {
        List<Anhang> vorher = List.of(new Anhang("application/pdf", pdfMitSeiten(1)));
        assertThat(KiPdfSeiten.fuer(anbieter(false, false), vorher)).isSameAs(vorher);
    }

    @Test
    void bilderBleibenBilder() throws Exception {
        Anhang foto = new Anhang("image/jpeg", new byte[] { 1 });
        assertThat(KiPdfSeiten.fuer(anbieter(false, true), List.of(foto))).containsExactly(foto);
    }
}
