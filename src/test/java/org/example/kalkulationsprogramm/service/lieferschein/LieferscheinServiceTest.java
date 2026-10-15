package org.example.kalkulationsprogramm.service.lieferschein;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.lieferschein.Lieferschein;
import org.example.kalkulationsprogramm.dto.lieferschein.LieferscheinAnlegenDto;
import org.example.kalkulationsprogramm.dto.lieferschein.LieferscheinKopieDto;
import org.example.kalkulationsprogramm.dto.lieferschein.LieferscheinUnterschriftDto;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.repository.lieferschein.LieferscheinRepository;
import org.example.kalkulationsprogramm.service.NummernkreisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class LieferscheinServiceTest {

    // Ein 1x1-Pixel-PNG
    static final String PNG = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

    LieferscheinRepository repo = mock(LieferscheinRepository.class);
    KundeRepository kunden = mock(KundeRepository.class);
    ProjektRepository projekte = mock(ProjektRepository.class);
    NummernkreisService kreise = mock(NummernkreisService.class);
    NummernbereichService bereiche = mock(NummernbereichService.class);
    LieferscheinMailVersand mail = mock(LieferscheinMailVersand.class);
    LieferscheinPdfService pdf = mock(LieferscheinPdfService.class);
    LieferscheinService service = new LieferscheinService(repo, kunden, projekte, kreise, bereiche, mail, pdf);
    Mitarbeiter max = new Mitarbeiter();
    Kunde kunde = new Kunde();
    Projekt projekt = new Projekt();

    @BeforeEach
    void setUp() {
        max.setId(1L);
        kunde.setId(5L);
        kunde.setName("Max Mustermann GmbH");
        projekt.setId(7L);
        projekt.setBauvorhaben("Musterhaus");
        projekt.setKundenId(kunde);
        when(kunden.findById(5L)).thenReturn(Optional.of(kunde));
        when(projekte.findById(7L)).thenReturn(Optional.of(projekt));
        when(repo.findByClientId(anyString())).thenReturn(Optional.empty());
        when(repo.save(any(Lieferschein.class))).thenAnswer(a -> a.getArgument(0));
        when(kreise.naechsteNummer(any(), any())).thenReturn(Optional.of("LS-2026-0001"));
    }

    private LieferscheinAnlegenDto dto(String nummer, String bild) {
        return new LieferscheinAnlegenDto("geraet-1", nummer, null, 7L, "Erika Mustermann", null, " Hinterhof ",
                List.of(new LieferscheinAnlegenDto.Position(" Rigips 12,5 mm ", new BigDecimal("24"), "Stk")), bild);
    }

    @Test
    void legtMitServerNummerAnUndHatKundenUndProjektAusDemProjekt() {
        var l = service.anlegen(max, dto(null, null));

        assertThat(l.nummer()).isEqualTo("LS-2026-0001");
        assertThat(l.kundeId()).isEqualTo(5L);
        assertThat(l.kundeName()).isEqualTo("Max Mustermann GmbH");
        assertThat(l.projektName()).isEqualTo("Musterhaus");
        assertThat(l.positionen()).extracting("bezeichnung").containsExactly("Rigips 12,5 mm");
        assertThat(l.bemerkung()).isEqualTo("Hinterhof");
        assertThat(l.unterschrieben()).isFalse();
        verify(bereiche, never()).verwenden(any(), any(), any());
    }

    @Test
    void mitReservierterNummerWirdDieseVerwendetUndNichtGezaehlt() {
        var l = service.anlegen(max, dto("LS-2026-0042", null));

        assertThat(l.nummer()).isEqualTo("LS-2026-0042");
        verify(bereiche).verwenden(max, NummernkreisService.LIEFERSCHEIN, "LS-2026-0042");
        verify(kreise, never()).naechsteNummer(any(), any());
    }

    @Test
    void eineSchonVergebeneNummerWirdAbgelehnt() {
        when(repo.existsByNummer("LS-2026-0042")).thenReturn(true);
        assertThatThrownBy(() -> service.anlegen(max, dto("LS-2026-0042", null))).hasMessageContaining("schon einem Lieferschein");
        verify(repo, never()).save(any());
    }

    @Test
    void dieselbeKennungErgibtDenselbenLieferscheinAuchNachAbbruch() {
        Lieferschein vorhanden = new Lieferschein();
        vorhanden.setNummer("LS-2026-0009");
        vorhanden.setEmpfaengerName("Erika Mustermann");
        vorhanden.setDatum(java.time.LocalDate.now());
        when(repo.findByClientId("geraet-1")).thenReturn(Optional.of(vorhanden));

        var l = service.anlegen(max, dto("LS-2026-0042", null));

        assertThat(l.nummer()).isEqualTo("LS-2026-0009");
        verify(repo, never()).save(any());
        verify(bereiche, never()).verwenden(any(), any(), any());
    }

    @Test
    void mitUnterschriftIstDerLieferscheinFertigUndKaputteBilderWerdenAbgelehnt() {
        var l = service.anlegen(max, dto(null, PNG));
        assertThat(l.unterschrieben()).isTrue();
        assertThat(l.unterschriftName()).isEqualTo("Erika Mustermann");

        assertThatThrownBy(() -> service.anlegen(max, dto(null, "data:image/svg+xml;base64,AAAA"))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("PNG");
    }

    @Test
    void ohneKundeUndProjektOderOhneKundeAmProjektGehtEsNicht() {
        var ohne = new LieferscheinAnlegenDto(null, null, null, null, "Max", null, null, List.of(new LieferscheinAnlegenDto.Position("x", BigDecimal.ONE, null)), null);
        assertThatThrownBy(() -> service.anlegen(max, ohne)).hasMessageContaining("Kunden oder ein Projekt");
        projekt.setKundenId(null);
        assertThatThrownBy(() -> service.anlegen(max, dto(null, null))).hasMessageContaining("keinen Kunden");
    }

    @Test
    void nachtraeglichUnterschreibenEinmalDannIstEsGesperrtUndDasLoeschenAuch() {
        Lieferschein entwurf = new Lieferschein();
        entwurf.setNummer("LS-2026-0001");
        entwurf.setEmpfaengerName("Erika Mustermann");
        entwurf.setDatum(java.time.LocalDate.now());
        when(repo.findById(3L)).thenReturn(Optional.of(entwurf));

        var fertig = service.unterschreiben(3L, new LieferscheinUnterschriftDto(PNG, "  "));
        assertThat(fertig.unterschrieben()).isTrue();
        assertThat(fertig.unterschriftName()).as("ohne Namen gilt der Empfänger").isEqualTo("Erika Mustermann");

        assertThatThrownBy(() -> service.unterschreiben(3L, new LieferscheinUnterschriftDto(PNG, "Anderer"))).hasMessageContaining("schon unterschrieben");
        assertThatThrownBy(() -> service.loeschen(3L)).hasMessageContaining("nicht gelöscht");
        verify(repo, never()).delete(any());
    }

    @Test
    void einEntwurfLaesstSichLoeschen() {
        Lieferschein entwurf = new Lieferschein();
        when(repo.findById(4L)).thenReturn(Optional.of(entwurf));
        service.loeschen(4L);
        verify(repo).delete(entwurf);
    }

    @Test
    void dieKopieGehtNurMitBestaetigungUndNurWennUnterschrieben() {
        Lieferschein l = new Lieferschein();
        l.setNummer("LS-2026-0001");
        l.setEmpfaengerName("Erika Mustermann");
        l.setDatum(java.time.LocalDate.now());
        when(repo.findById(3L)).thenReturn(Optional.of(l));
        when(pdf.erstelle(l)).thenReturn(new LieferscheinPdfService.PdfDatei("LS.pdf", new byte[] { 1, 2 }));

        assertThatThrownBy(() -> service.kopieSenden(3L, new LieferscheinKopieDto("erika@example.org", false))).hasMessageContaining("bestätigen");
        assertThatThrownBy(() -> service.kopieSenden(3L, new LieferscheinKopieDto("erika@example.org", true))).hasMessageContaining("noch nicht unterschrieben");
        verify(mail, never()).sende(any(), any(), any(), any(), any());

        service.unterschreiben(3L, new LieferscheinUnterschriftDto(PNG, null));
        var danach = service.kopieSenden(3L, new LieferscheinKopieDto(" erika@example.org ", true));

        verify(mail).sende(org.mockito.ArgumentMatchers.eq("erika@example.org"), org.mockito.ArgumentMatchers.eq("Lieferschein LS-2026-0001"), any(), org.mockito.ArgumentMatchers.eq(new byte[] { 1, 2 }), org.mockito.ArgumentMatchers.eq("LS.pdf"));
        assertThat(danach.kopieAn()).isEqualTo("erika@example.org");
        assertThat(danach.kopieGesendetAm()).isNotNull();
    }
}
