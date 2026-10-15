package org.example.kalkulationsprogramm.service.lieferschein;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.lieferschein.Nummernreservierung;
import org.example.kalkulationsprogramm.domain.lieferschein.Nummernreservierung.Status;
import org.example.kalkulationsprogramm.repository.lieferschein.NummernreservierungRepository;
import org.example.kalkulationsprogramm.service.NummernkreisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class NummernbereichServiceTest {

    NummernreservierungRepository repo = mock(NummernreservierungRepository.class);
    NummernkreisService kreise = mock(NummernkreisService.class);
    NummernbereichService service = new NummernbereichService(repo, kreise);
    Mitarbeiter max = mitarbeiter(1L, "Max", "Mustermann");
    Mitarbeiter erika = mitarbeiter(2L, "Erika", "Musterfrau");
    List<Nummernreservierung> gespeichert = new ArrayList<>();
    AtomicInteger zaehler = new AtomicInteger(0);

    static Mitarbeiter mitarbeiter(Long id, String vor, String nach) {
        Mitarbeiter m = new Mitarbeiter();
        m.setId(id);
        m.setVorname(vor);
        m.setNachname(nach);
        return m;
    }

    @BeforeEach
    void setUp() {
        when(repo.save(any(Nummernreservierung.class))).thenAnswer(a -> { Nummernreservierung r = a.getArgument(0); if (!gespeichert.contains(r)) gespeichert.add(r); return r; });
        when(repo.findByArtAndMitarbeiterIdAndStatusOrderByIdAsc(any(), any(), any())).thenAnswer(a -> gespeichert.stream()
                .filter(r -> r.getMitarbeiter().getId().equals(a.getArgument(1)) && r.getStatus() == a.getArgument(2)).toList());
        when(kreise.naechsteNummer(eq("LIEFERSCHEIN"), any())).thenAnswer(a -> Optional.of(String.format("LS-2026-%04d", zaehler.incrementAndGet())));
    }

    @Test
    void fuelltBisZurGewuenschtenZahlAufUndGibtAlleFreienZurueck() {
        var erst = service.auffuellen(max, "LIEFERSCHEIN", 3, n -> false);
        assertThat(erst.nummern()).containsExactly("LS-2026-0001", "LS-2026-0002", "LS-2026-0003");

        var nochmal = service.auffuellen(max, "LIEFERSCHEIN", 5, n -> false);
        assertThat(nochmal.nummern()).as("vorhandene bleiben, nur zwei kommen dazu").containsExactly("LS-2026-0001", "LS-2026-0002", "LS-2026-0003", "LS-2026-0004", "LS-2026-0005");
        assertThat(gespeichert).allSatisfy(r -> assertThat(r.getMitarbeiter()).isSameAs(max));
    }

    @Test
    void jedesGeraetBekommtEigeneNummernUndDieBereicheUeberschneidenSichNicht() {
        var a = service.auffuellen(max, "LIEFERSCHEIN", 2, n -> false);
        var b = service.auffuellen(erika, "LIEFERSCHEIN", 2, n -> false);
        assertThat(a.nummern()).doesNotContainAnyElementsOf(b.nummern());
    }

    @Test
    void verwendeteNummernZaehlenNichtMehrZumBereichUndWerdenErsetzt() {
        service.auffuellen(max, "LIEFERSCHEIN", 2, n -> false);
        gespeichert.get(0).setStatus(Status.VERWENDET);

        var danach = service.auffuellen(max, "LIEFERSCHEIN", 2, n -> false);

        assertThat(danach.nummern()).containsExactly("LS-2026-0002", "LS-2026-0003");
    }

    @Test
    void dieGroesseDesBereichsIstBegrenzt() {
        assertThatThrownBy(() -> service.auffuellen(max, "LIEFERSCHEIN", 0, n -> false)).hasMessageContaining("1 bis 50");
        assertThatThrownBy(() -> service.auffuellen(max, "LIEFERSCHEIN", 51, n -> false)).hasMessageContaining("1 bis 50");
        verify(kreise, never()).naechsteNummer(any(), any());
    }

    @Test
    void verwendenPrueftBesitzerUndStatusUndSperrt() {
        Nummernreservierung frei = reservierung(max, "LS-2026-0001", Status.RESERVIERT);
        when(repo.findeMitSperre("LIEFERSCHEIN", "LS-2026-0001")).thenReturn(Optional.of(frei));
        service.verwenden(max, "LIEFERSCHEIN", "LS-2026-0001");
        assertThat(frei.getStatus()).isEqualTo(Status.VERWENDET);
        assertThat(frei.getVerwendetAm()).isNotNull();

        assertThatThrownBy(() -> service.verwenden(max, "LIEFERSCHEIN", "LS-2026-0001")).hasMessageContaining("schon verwendet");

        Nummernreservierung fremd = reservierung(erika, "LS-2026-0002", Status.RESERVIERT);
        when(repo.findeMitSperre("LIEFERSCHEIN", "LS-2026-0002")).thenReturn(Optional.of(fremd));
        assertThatThrownBy(() -> service.verwenden(max, "LIEFERSCHEIN", "LS-2026-0002")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("anderen Gerät");
        assertThat(fremd.getStatus()).isEqualTo(Status.RESERVIERT);

        when(repo.findeMitSperre("LIEFERSCHEIN", "LS-2026-0099")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.verwenden(max, "LIEFERSCHEIN", "LS-2026-0099")).satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void dasProtokollZeigtVerwendetUndNichtVerwendet() {
        Nummernreservierung a = reservierung(max, "LS-2026-0001", Status.VERWENDET);
        Nummernreservierung b = reservierung(max, "LS-2026-0002", Status.RESERVIERT);
        when(repo.findByArtOrderByIdDesc("LIEFERSCHEIN")).thenReturn(List.of(b, a));

        var protokoll = service.protokoll("LIEFERSCHEIN");

        assertThat(protokoll).extracting(NummernbereichService.Eintrag::status).containsExactly("nicht verwendet", "verwendet");
        assertThat(protokoll.get(0).mitarbeiter()).isEqualTo("Max Mustermann");
    }

    private static Nummernreservierung reservierung(Mitarbeiter m, String nummer, Status status) {
        Nummernreservierung r = new Nummernreservierung();
        r.setArt("LIEFERSCHEIN");
        r.setNummer(nummer);
        r.setMitarbeiter(m);
        r.setStatus(status);
        r.setReserviertAm(java.time.LocalDateTime.now());
        return r;
    }
}
