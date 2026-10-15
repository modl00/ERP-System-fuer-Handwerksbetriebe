package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Werkstoff;
import org.example.kalkulationsprogramm.repository.WerkstoffRepository;
import org.example.kalkulationsprogramm.service.WerkstoffService.Eingabe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class WerkstoffServiceTest {

    private final WerkstoffRepository repo = mock(WerkstoffRepository.class);
    private WerkstoffService service;

    private static Werkstoff werkstoff(long id, String name, String anzeige) {
        Werkstoff w = new Werkstoff();
        w.setId(id);
        w.setName(name);
        w.setAnzeigename(anzeige);
        return w;
    }

    private static Eingabe eingabe(String name) {
        return new Eingabe(name, null, null, null, null, null, null, null);
    }

    @BeforeEach
    void setUp() {
        service = new WerkstoffService(repo);
        when(repo.save(any(Werkstoff.class))).thenAnswer(a -> a.getArgument(0));
    }

    @Test
    void listeIstNachAnzeigenameSortiertUndZaehltDieArtikel() {
        when(repo.findAll()).thenReturn(List.of(werkstoff(1, "S235JR", "Baustahl"), werkstoff(2, "1.4301", null), werkstoff(3, "EN AW-6060", "Aluminium")));
        when(repo.zaehleArtikelJeWerkstoff()).thenReturn(List.<Object[]>of(new Object[] { 1L, 12L }, new Object[] { 3L, 1L }));

        var zeilen = service.alle();

        assertThat(zeilen).extracting(WerkstoffService.Zeile::name).containsExactly("1.4301", "EN AW-6060", "S235JR");
        assertThat(zeilen).extracting(WerkstoffService.Zeile::artikelAnzahl).containsExactly(0L, 1L, 12L);
    }

    @Test
    void anlegenSpeichertAlleAngabenGetrimmtUndLeereAlsNull() {
        var z = service.anlegen(new Eingabe("  S355J2  ", " Baustahl hochfest ", "", new BigDecimal("7.85"), " EN 10025 ", true, null, "  geeignet  "));

        assertThat(z.name()).isEqualTo("S355J2");
        assertThat(z.anzeigename()).isEqualTo("Baustahl hochfest");
        assertThat(z.werkstattname()).isNull();
        assertThat(z.dichte()).isEqualByComparingTo("7.850");
        assertThat(z.werkstoffnorm()).isEqualTo("EN 10025");
        assertThat(z.verzinkungsgeeignet()).isTrue();
        assertThat(z.pulverbeschichtungsgeeignet()).isFalse();
        assertThat(z.beschichtungshinweis()).isEqualTo("geeignet");
        assertThat(z.artikelAnzahl()).isZero();
    }

    @Test
    void ohneNameDoppelterNameOderUnsinnigeDichteGibtKlartext() {
        assertThatThrownBy(() -> service.anlegen(eingabe("  "))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("Namen");
        assertThatThrownBy(() -> service.anlegen(null)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("fehlen");
        when(repo.findByNameIgnoreCase("s235jr")).thenReturn(Optional.of(werkstoff(1, "S235JR", null)));
        assertThatThrownBy(() -> service.anlegen(eingabe("s235jr"))).isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT)).hasMessageContaining("S235JR").hasMessageContaining("schon");
        assertThatThrownBy(() -> service.anlegen(new Eingabe("X", null, null, new BigDecimal("78.5"), null, null, null, null)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Dichte");
        assertThatThrownBy(() -> service.anlegen(eingabe("x".repeat(101)))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("zu lang");
        assertThatThrownBy(() -> service.anlegen(new Eingabe("X", null, "w".repeat(65), null, null, null, null, null))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("Werkstattname");
        verify(repo, never()).save(any());
    }

    @Test
    void aendernDarfDenEigenenNamenBehaltenUndMeldetFremdeDoppelte() {
        Werkstoff vorhanden = werkstoff(1, "S235JR", "Baustahl");
        when(repo.findById(1L)).thenReturn(Optional.of(vorhanden));
        when(repo.findByNameIgnoreCase("S235JR")).thenReturn(Optional.of(vorhanden));
        when(repo.zaehleArtikel(1L)).thenReturn(4L);

        var z = service.aendern(1L, new Eingabe("S235JR", "Baustahl neu", null, null, null, null, null, null));

        assertThat(z.anzeigename()).isEqualTo("Baustahl neu");
        assertThat(z.artikelAnzahl()).isEqualTo(4L);

        when(repo.findByNameIgnoreCase("1.4301")).thenReturn(Optional.of(werkstoff(2, "1.4301", null)));
        assertThatThrownBy(() -> service.aendern(1L, eingabe("1.4301"))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("schon");
        assertThatThrownBy(() -> service.aendern(99L, eingabe("Y"))).isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void einBenutzterWerkstoffLaesstSichNichtLoeschenEinFreierSchon() {
        Werkstoff w = werkstoff(1, "S235JR", null);
        when(repo.findById(1L)).thenReturn(Optional.of(w));
        when(repo.zaehleArtikel(1L)).thenReturn(3L);

        assertThatThrownBy(() -> service.loeschen(1L)).isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("3 Artikeln");
        verify(repo, never()).delete(any());

        when(repo.zaehleArtikel(1L)).thenReturn(0L);
        service.loeschen(1L);
        verify(repo).delete(w);
    }
}
