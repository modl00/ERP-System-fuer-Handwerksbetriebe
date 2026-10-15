package org.example.kalkulationsprogramm.service.ki.assistent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.service.LieferantEmailResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class LieferantWerkzeugeTest {

    private final ObjectMapper json = new ObjectMapper();
    private final KiKontext anna = new KiKontext(1L, "anna", false);
    private final LieferantenRepository repo = mock(LieferantenRepository.class);
    private final LieferantEmailResolver resolver = mock(LieferantEmailResolver.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final LieferantAnlegenWerkzeug anlegen = new LieferantAnlegenWerkzeug(repo, resolver, events);

    private static Lieferanten lieferant(long id, String name, String ort) {
        Lieferanten l = new Lieferanten();
        l.setId(id);
        l.setLieferantenname(name);
        l.setOrt(ort);
        return l;
    }

    private JsonNode meier() {
        return json.createObjectNode().put("name", "Mustermann Pulverbeschichtung").put("strasse", "Musterweg 47").put("plz", "12345").put("ort", "Musterstadt")
                .put("telefon", "01234 - 567890").put("email", "Info@Mustermann-Pulver.example");
    }

    @BeforeEach
    void setUp() {
        when(repo.findByLieferantennameIgnoreCase(any())).thenReturn(Optional.empty());
        when(repo.findAll()).thenReturn(List.of());
        when(repo.save(any())).thenAnswer(i -> {
            Lieferanten l = i.getArgument(0);
            l.setId(42L);
            return l;
        });
    }

    @Test
    void dasAnlegenIstEineAenderungMitVorschauAllerWerte() {
        assertThat(anlegen.aendert()).isTrue();
        assertThat(anlegen.loescht()).isFalse();
        String v = anlegen.vorschau(meier());
        assertThat(v).contains("Mustermann Pulverbeschichtung", "Musterweg 47", "12345 Musterstadt", "Tel. 01234 - 567890", "info@mustermann-pulver.example");
        verify(repo, never()).save(any());
    }

    @Test
    void ausfuehrenLegtDenLieferantenWieAmPcAnUndNormalisiertDieEmail() {
        String r = anlegen.ausfuehren(meier(), anna);

        ArgumentCaptor<Lieferanten> c = ArgumentCaptor.forClass(Lieferanten.class);
        verify(repo).save(c.capture());
        Lieferanten l = c.getValue();
        assertThat(l.getLieferantenname()).isEqualTo("Mustermann Pulverbeschichtung");
        assertThat(l.getStrasse()).isEqualTo("Musterweg 47");
        assertThat(l.getPlz()).isEqualTo("12345");
        assertThat(l.getOrt()).isEqualTo("Musterstadt");
        assertThat(l.getIstAktiv()).isTrue();
        assertThat(l.getKundenEmails()).containsExactly("info@mustermann-pulver.example");
        assertThat(r).contains("wurde angelegt").contains("ID 42");
        verify(resolver).refresh();
        verify(events).publishEvent(any(Object.class));
    }

    @Test
    void einVorhandenerNameWirdAbgelehntUndNichtDoppeltAngelegt() {
        when(repo.findByLieferantennameIgnoreCase("Mustermann Pulverbeschichtung")).thenReturn(Optional.of(lieferant(7, "Mustermann Pulverbeschichtung", "Musterstadt")));
        assertThatThrownBy(() -> anlegen.vorschau(meier())).isInstanceOf(KiWerkzeugFehler.class).hasMessageContaining("gibt es schon").hasMessageContaining("ID 7");
        assertThatThrownBy(() -> anlegen.ausfuehren(meier(), anna)).isInstanceOf(KiWerkzeugFehler.class);
        verify(repo, never()).save(any());
    }

    @Test
    void aehnlicheNamenStehenAlsWarnungInDerVorschau() {
        when(repo.findAll()).thenReturn(List.of(lieferant(9, "Mustermann Pulver GmbH", "Musterstadt"), lieferant(10, "Beispiel Schrauben", "Beispielstadt")));
        String v = anlegen.vorschau(meier());
        assertThat(v).contains("ACHTUNG").contains("ID 9").doesNotContain("Beispiel Schrauben");
    }

    @Test
    void ungueltigeAngabenWerdenMitKlartextAbgelehnt() {
        assertThatThrownBy(() -> anlegen.vorschau(json.createObjectNode())).isInstanceOf(KiWerkzeugFehler.class).hasMessageContaining("Namen");
        assertThatThrownBy(() -> anlegen.vorschau(json.createObjectNode().put("name", "X"))).hasMessageContaining("zu kurz");
        assertThatThrownBy(() -> anlegen.vorschau(json.createObjectNode().put("name", "Firma").put("email", "keine-mail"))).hasMessageContaining("nicht gültig");
        assertThatThrownBy(() -> anlegen.vorschau(json.createObjectNode().put("name", "a".repeat(300)))).hasMessageContaining("höchstens");
    }

    @Test
    void nurDerNameIstPflichtUndLeereFelderWerdenNichtAngelegt() {
        anlegen.ausfuehren(json.createObjectNode().put("name", "Nur Name GmbH"), anna);
        ArgumentCaptor<Lieferanten> c = ArgumentCaptor.forClass(Lieferanten.class);
        verify(repo).save(c.capture());
        assertThat(c.getValue().getStrasse()).isNull();
        assertThat(c.getValue().getKundenEmails()).isEmpty();
    }

    @Test
    void dieSucheFindetLieferantenOhneEmailAdressenAuszugeben() {
        when(repo.searchByNameOrEmail("pulver")).thenReturn(List.of(lieferant(7, "Mustermann Pulverbeschichtung", "Musterstadt")));
        when(repo.searchByNameOrEmail("nix")).thenReturn(List.of());
        LieferantenSuchenWerkzeug suche = new LieferantenSuchenWerkzeug(repo);

        assertThat(suche.aendert()).isFalse();
        assertThat(suche.ausfuehren(json.createObjectNode().put("suche", "pulver"), anna)).contains("ID 7", "Mustermann Pulverbeschichtung", "Musterstadt").doesNotContain("@");
        assertThat(suche.ausfuehren(json.createObjectNode().put("suche", "nix"), anna)).contains("Kein Lieferant gefunden");
    }
}
