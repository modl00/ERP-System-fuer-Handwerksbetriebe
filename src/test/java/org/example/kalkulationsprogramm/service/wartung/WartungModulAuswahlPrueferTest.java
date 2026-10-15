package org.example.kalkulationsprogramm.service.wartung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.wartung.WartungModul;
import org.example.kalkulationsprogramm.domain.wartung.WartungModulKategorie;
import org.example.kalkulationsprogramm.domain.wartung.WartungModulUntertyp;
import org.example.kalkulationsprogramm.dto.wartung.WartungModulAuswahlDto;
import org.example.kalkulationsprogramm.repository.wartung.WartungModulRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungModulUntertypRepository;
import org.example.kalkulationsprogramm.service.wartung.WartungModulAuswahlPruefer.GewaehltesModul;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class WartungModulAuswahlPrueferTest {

    private static final long BST = 1L;
    private static final long RS = 2L;
    private static final long FSA = 3L;
    private static final long BODEN = 4L;
    private static final long GLAS = 5L;

    @Mock
    private WartungModulRepository modulRepository;
    @Mock
    private WartungModulUntertypRepository untertypRepository;

    private WartungModulAuswahlPruefer pruefer;
    private WartungModul bst;
    private WartungModul rs;
    private WartungModul fsa;
    private WartungModul boden;
    private WartungModul glas;

    @BeforeEach
    void setUp() {
        pruefer = new WartungModulAuswahlPruefer(modulRepository, untertypRepository);
        bst = modul(BST, "m_bst", "Brandschutztür", WartungModulKategorie.BASIS, 1, Set.of());
        rs = modul(RS, "m_rs", "Rauchschutztür", WartungModulKategorie.BASIS, 3, Set.of());
        fsa = modul(FSA, "m_fsa", "Feststellanlage", WartungModulKategorie.ZUSATZ, 4, Set.of(BST, RS));
        boden = modul(BODEN, "m_boden", "Bodendichtung", WartungModulKategorie.ZUSATZ, 7, Set.of(RS));
        glas = modul(GLAS, "m_glas", "Glasfüllung", WartungModulKategorie.ZUSATZ, 5, Set.of());
        lenient().when(modulRepository.findAllById(any())).thenAnswer(aufruf -> {
            Iterable<Long> ids = aufruf.getArgument(0);
            List<WartungModul> treffer = new java.util.ArrayList<>();
            for (Long id : ids) {
                for (WartungModul m : List.of(bst, rs, fsa, boden, glas)) {
                    if (m.getId().equals(id)) {
                        treffer.add(m);
                    }
                }
            }
            return treffer;
        });
        // Brandschutztür und Rauchschutztür haben Bauarten, Feststellanlage hat zwei andere.
        lenient().when(untertypRepository.findByModulIdInOrderBySortierungAscIdAsc(any())).thenReturn(List.of(
                untertyp(BST, "einflg", "1-flügelig"), untertyp(BST, "zweiflg", "2-flügelig"),
                untertyp(RS, "einflg", "1-flügelig"),
                untertyp(FSA, "magnet", "Haftmagnet"), untertyp(FSA, "freilauf", "Freilauf")));
    }

    @Test
    @DisplayName("Brandschutztür mit Bauart und passender Feststellanlage wird angenommen, Basis-Element steht vorn")
    void gueltigeAuswahl() {
        List<GewaehltesModul> ergebnis = pruefer.pruefe(List.of(
                new WartungModulAuswahlDto(FSA, "magnet"),
                new WartungModulAuswahlDto(BST, "zweiflg")), true);

        assertThat(ergebnis).extracting(g -> g.modul().getModulKey()).containsExactly("m_bst", "m_fsa");
        assertThat(ergebnis.get(0).untertyp().getSchluessel()).isEqualTo("zweiflg");
        assertThat(ergebnis.get(1).untertyp().getLabel()).isEqualTo("Haftmagnet");
    }

    @Test
    @DisplayName("Zwei Basis-Elemente gleichzeitig werden abgelehnt")
    void zweiBasisElemente() {
        assertThatThrownBy(() -> pruefer.pruefe(List.of(
                new WartungModulAuswahlDto(BST, "einflg"), new WartungModulAuswahlDto(RS, "einflg")), true))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("nur ein Basis-Element")
                .hasMessageContaining("Brandschutztür")
                .hasMessageContaining("Rauchschutztür");
    }

    @Test
    @DisplayName("Bodendichtung passt nur an eine Rauchschutztür, nicht an eine Brandschutztür")
    void zusatzmodulAnFalschesBasisElement() {
        assertThatThrownBy(() -> pruefer.pruefe(List.of(
                new WartungModulAuswahlDto(BST, "einflg"), new WartungModulAuswahlDto(BODEN, null)), true))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Bodendichtung")
                .hasMessageContaining("Rauchschutztür");
    }

    @Test
    @DisplayName("Ein Zusatzmodul ohne Basis-Element wird abgelehnt, wenn es Vorgaben zum Andocken hat")
    void zusatzmodulOhneBasis() {
        assertThatThrownBy(() -> pruefer.pruefe(List.of(new WartungModulAuswahlDto(FSA, "magnet")), true))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Feststellanlage");
    }

    @Test
    @DisplayName("Die Glasfüllung ohne Andock-Vorgabe passt an jedes Basis-Element")
    void zusatzmodulOhneVorgabe() {
        assertThat(pruefer.pruefe(List.of(
                new WartungModulAuswahlDto(RS, "einflg"), new WartungModulAuswahlDto(GLAS, null)), true)).hasSize(2);
    }

    @Test
    @DisplayName("Fehlt die Bauart bei einem Modul, das Bauarten hat, kommt eine Meldung")
    void bauartFehlt() {
        assertThatThrownBy(() -> pruefer.pruefe(List.of(new WartungModulAuswahlDto(BST, null)), true))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Bauart");
        assertThatThrownBy(() -> pruefer.pruefe(List.of(new WartungModulAuswahlDto(BST, "  ")), true))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Bauart");
    }

    @Test
    @DisplayName("Eine unbekannte Bauart und eine Bauart bei einem Modul ohne Bauarten werden abgelehnt")
    void bauartFalsch() {
        assertThatThrownBy(() -> pruefer.pruefe(List.of(new WartungModulAuswahlDto(RS, "schiebe")), true))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Bauart");
        assertThatThrownBy(() -> pruefer.pruefe(List.of(
                new WartungModulAuswahlDto(RS, "einflg"), new WartungModulAuswahlDto(GLAS, "einflg")), true))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("keine Bauart");
    }

    @Test
    @DisplayName("Doppelt gewähltes, unbekanntes und deaktiviertes Modul werden abgelehnt")
    void ungueltigeModule() {
        assertThatThrownBy(() -> pruefer.pruefe(List.of(
                new WartungModulAuswahlDto(BST, "einflg"), new WartungModulAuswahlDto(BST, "einflg")), true))
                .hasMessageContaining("doppelt");
        assertThatThrownBy(() -> pruefer.pruefe(List.of(new WartungModulAuswahlDto(Long.MAX_VALUE, null)), true))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        glas.setAktiv(false);
        assertThatThrownBy(() -> pruefer.pruefe(List.of(
                new WartungModulAuswahlDto(RS, "einflg"), new WartungModulAuswahlDto(GLAS, null)), true))
                .hasMessageContaining("nicht mehr aktiv");
    }

    @Test
    @DisplayName("Eine leere Auswahl ist beim Protokoll ein Fehler, an der Tür erlaubt")
    void leereAuswahl() {
        assertThatThrownBy(() -> pruefer.pruefe(List.of(), true)).hasMessageContaining("mindestens ein Modul");
        assertThatThrownBy(() -> pruefer.pruefe(null, true)).hasMessageContaining("mindestens ein Modul");
        assertThat(pruefer.pruefe(List.of(), false)).isEmpty();
        assertThat(pruefer.pruefe(null, false)).isEmpty();
    }

    private static WartungModul modul(long id, String key, String name, WartungModulKategorie kategorie, int sortierung, Set<Long> andockbar) {
        WartungModul m = new WartungModul();
        m.setId(id);
        m.setModulKey(key);
        m.setName(name);
        m.setKategorie(kategorie);
        m.setSortierung(sortierung);
        m.setAktiv(true);
        m.setAndockbarAnBasisModulIds(new HashSet<>(andockbar));
        return m;
    }

    private static WartungModulUntertyp untertyp(long modulId, String schluessel, String label) {
        WartungModulUntertyp u = new WartungModulUntertyp();
        u.setModulId(modulId);
        u.setSchluessel(schluessel);
        u.setLabel(label);
        return u;
    }
}
