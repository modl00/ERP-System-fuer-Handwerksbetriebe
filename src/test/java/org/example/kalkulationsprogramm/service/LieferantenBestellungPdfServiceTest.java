package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.BestellHerkunft;
import org.example.kalkulationsprogramm.domain.BestellStatus;
import org.example.kalkulationsprogramm.dto.Bestellwesen.BestellungDto;
import org.example.kalkulationsprogramm.dto.FirmeninformationDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

/** Nur Dummy-Daten. */
class LieferantenBestellungPdfServiceTest {

    private final LieferantenBestellungService bestellungen = mock(LieferantenBestellungService.class);
    private final FirmeninformationService firma = mock(FirmeninformationService.class);
    private final FormularTemplateService formularTemplateService = mock(FormularTemplateService.class);
    private final RechnungPdfService rechnungPdfService = mock(RechnungPdfService.class);
    private final LieferantenBestellungPdfService pdf =
            new LieferantenBestellungPdfService(bestellungen, firma, formularTemplateService, rechnungPdfService);

    {
        // Kein Formulareditor-Template → Fallback auf Hardcode-Layout
        when(formularTemplateService.getPreferredTemplateForDokumenttyp(any(), isNull()))
                .thenReturn(Optional.empty());
    }

    private static BestellungDto bestellung(String nummer, BestellStatus status, BigDecimal preis) {
        BestellungDto.Position p1 = new BestellungDto.Position(1L, 10L, "R-4040", "Rohr 40x40", new BigDecimal("12.5"), "m", preis,
                preis == null ? null : preis.multiply(new BigDecimal("12.5")), BestellHerkunft.PROJEKT, 7L, "2026/014 Anbau Halle", 55L, "Zuschnitt 3 m", null);
        BestellungDto.Position p2 = new BestellungDto.Position(2L, 11L, null, "Schraube M8", new BigDecimal("200"), "Stk", null, null, BestellHerkunft.LAGER,
                null, null, null, null, null);
        return new BestellungDto(5L, nummer, status, status.name(), 3L, "Stahlhandel Muster", List.of(), "Bitte Anfang der Woche liefern",
                LocalDateTime.of(2026, 10, 5, 8, 0), "admin", nummer == null ? null : LocalDateTime.of(2026, 10, 5, 9, 30), "admin", "EMAIL", null, null,
                preis == null ? BigDecimal.ZERO : preis.multiply(new BigDecimal("12.5")), 2, 0, 0, false, 7, List.of("2026/014 Anbau Halle"), List.of(p1, p2));
    }

    private String text(byte[] bytes) throws Exception {
        PdfReader r = new PdfReader(bytes);
        try {
            return new PdfTextExtractor(r).getTextFromPage(1).replaceAll("\\s+", " ");
        } finally {
            r.close();
        }
    }

    @Test
    @DisplayName("Bestellte Bestellung: Bestellnummer, Lieferant, Positionen mit Menge, Projekt und Hinweis stehen im PDF")
    void bestellformular() throws Exception {
        when(firma.getFirmeninformation()).thenReturn(firmendaten());

        byte[] bytes = pdf.erzeuge(bestellung("B-2026-10-0001", BestellStatus.BESTELLT, new BigDecimal("4.20")));

        assertThat(new String(bytes, 0, 5)).isEqualTo("%PDF-");
        String t = text(bytes);
        assertThat(t).contains("Bestellung B-2026-10-0001").contains("Stahlhandel Muster").contains("Rohr 40x40").contains("R-4040").contains("12,5")
                .contains("2026/014 Anbau Halle – Zuschnitt 3 m").contains("Schraube M8").contains("Bitte Anfang der Woche liefern")
                .contains("05.10.2026").contains("Muster Metallbau");
        assertThat(t).doesNotContain("Entwurf");
    }

    @Test
    @DisplayName("Entwurf trägt den Vermerk und hat keine Bestellnummer; ohne Preise gibt es keine Preisspalte und keine Summe")
    void entwurf() throws Exception {
        when(firma.getFirmeninformation()).thenReturn(firmendaten());

        String t = text(pdf.erzeuge(bestellung(null, BestellStatus.ENTWURF, null)));

        assertThat(t).contains("(Entwurf)").contains("noch nicht bestellt");
        assertThat(t).doesNotContain("Summe netto").doesNotContain("Preis");
    }

    @Test
    @DisplayName("Dateiname ist frei von Sonderzeichen")
    void dateiname() {
        assertThat(LieferantenBestellungPdfService.dateiname(bestellung("B-2026-10-0001", BestellStatus.BESTELLT, null))).isEqualTo("Bestellung_B-2026-10-0001.pdf");
        assertThat(LieferantenBestellungPdfService.dateiname(bestellung(null, BestellStatus.ENTWURF, null))).isEqualTo("Bestellung_Entwurf-5.pdf");
        assertThat(LieferantenBestellungPdfService.dateiname(bestellung("B/../x y", BestellStatus.BESTELLT, null))).isEqualTo("Bestellung_B_.._x_y.pdf");
    }

    private static FirmeninformationDto firmendaten() {
        FirmeninformationDto f = new FirmeninformationDto();
        f.setFirmenname("Muster Metallbau GmbH");
        f.setStrasse("Musterweg 1");
        f.setPlz("12345");
        f.setOrt("Musterstadt");
        return f;
    }
}
