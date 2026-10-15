package org.example.kalkulationsprogramm.service.ki.assistent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.example.kalkulationsprogramm.service.ki.AnbieterTyp;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterService;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterService.FunktionsEinstellung;
import org.example.kalkulationsprogramm.service.ki.KiFunktion;
import org.example.kalkulationsprogramm.service.ki.KiObergrenze;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class KiDokumentServiceTest {

    KiAnbieterService anbieter = mock(KiAnbieterService.class);
    KiDokumentService service = new KiDokumentService(anbieter);

    @BeforeEach
    void setUp() {
        when(anbieter.eingriff()).thenReturn(KiObergrenze.Eingriff.MIT_BESTAETIGUNG);
        FunktionsEinstellung e = new FunktionsEinstellung("ASSISTENT", "KI-Assistent", AnbieterTyp.LOKAL, null);
        when(anbieter.einstellung(KiFunktion.ASSISTENT)).thenReturn(e);
        when(anbieter.sperrtProgrammdaten(any())).thenReturn(false);
    }

    private static byte[] pdf(String text) throws Exception {
        try (PDDocument d = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage seite = new PDPage();
            d.addPage(seite);
            try (PDPageContentStream c = new PDPageContentStream(d, seite)) {
                c.beginText();
                c.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                c.newLineAtOffset(50, 700);
                c.showText(text);
                c.endText();
            }
            d.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void einPdfWirdGelesenOhneEtwasZuSpeichern() throws Exception {
        var d = service.lies(new MockMultipartFile("datei", "angebot.pdf", "application/pdf", pdf("Angebot 4711 fuer Max Mustermann")));
        assertThat(d.name()).isEqualTo("angebot.pdf");
        assertThat(d.text()).contains("Angebot 4711").contains("Max Mustermann");
        assertThat(d.seiten()).isEqualTo(1);
        assertThat(d.gekuerzt()).isFalse();
    }

    @Test
    void eineTextdateiWirdGelesenUndLangeTexteWerdenGekuerzt() throws Exception {
        var kurz = service.lies(new MockMultipartFile("datei", "notiz.txt", "text/plain", "Liefertermin 12.10.".getBytes(StandardCharsets.UTF_8)));
        assertThat(kurz.text()).isEqualTo("Liefertermin 12.10.");

        var lang = service.lies(new MockMultipartFile("datei", "lang.txt", "text/plain", "a".repeat(KiDokumentService.MAX_ZEICHEN + 500).getBytes(StandardCharsets.UTF_8)));
        assertThat(lang.text()).hasSize(KiDokumentService.MAX_ZEICHEN);
        assertThat(lang.gekuerzt()).isTrue();
        assertThat(lang.hinweis()).contains("Anfang");
    }

    @Test
    void einBildOderEinScanHatKeinenTextUndDerHinweisNenntDenAusweg() throws Exception {
        var bild = service.lies(new MockMultipartFile("datei", "foto.jpg", "image/jpeg", new byte[] {1, 2, 3}));
        assertThat(bild.text()).isNull();
        assertThat(bild.hinweis()).contains("Als Beleg ablegen");

        var leer = service.lies(new MockMultipartFile("datei", "scan.pdf", "application/pdf", pdf("")));
        assertThat(leer.text()).isNull();
        assertThat(leer.hinweis()).contains("kein lesbarer Text");
    }

    @Test
    void leereOderZuGrosseDateienUndGesperrteKiWerdenAbgelehnt() {
        assertThatThrownBy(() -> service.lies(new MockMultipartFile("datei", "x.txt", "text/plain", new byte[0]))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("Datei auswählen");
        assertThatThrownBy(() -> service.lies(new MockMultipartFile("datei", "x.txt", "text/plain", new byte[(int) KiDokumentService.MAX_BYTES + 1]))).hasMessageContaining("zu groß");

        when(anbieter.sperrtProgrammdaten(AnbieterTyp.LOKAL)).thenReturn(true);
        assertThatThrownBy(() -> service.lies(new MockMultipartFile("datei", "x.txt", "text/plain", "a".getBytes()))).hasMessageContaining("nicht an diesen KI-Anbieter");

        when(anbieter.sperrtProgrammdaten(any())).thenReturn(false);
        when(anbieter.eingriff()).thenReturn(KiObergrenze.Eingriff.AUS);
        assertThatThrownBy(() -> service.lies(new MockMultipartFile("datei", "x.txt", "text/plain", "a".getBytes()))).hasMessageContaining("ausgeschaltet");
    }

    @Test
    void derDateinameWirdVonPfadenBereinigt() throws Exception {
        var d = service.lies(new MockMultipartFile("datei", "../../etc/pass:wd.txt", "text/plain", "Text".getBytes()));
        assertThat(d.name()).doesNotContain("/").doesNotContain(":").doesNotContain("..");
    }
}
