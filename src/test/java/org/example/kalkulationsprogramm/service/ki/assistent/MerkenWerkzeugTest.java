package org.example.kalkulationsprogramm.service.ki.assistent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.example.kalkulationsprogramm.service.KiMerkzettelService;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class MerkenWerkzeugTest {

    private final ObjectMapper json = new ObjectMapper();
    private final KiMerkzettelService dienst = mock(KiMerkzettelService.class);
    private final MerkenWerkzeug werkzeug = new MerkenWerkzeug(dienst);
    private final KiKontext admin = new KiKontext(1L, "admin", true);
    private final KiKontext anna = new KiKontext(2L, "anna", false);

    @Test
    void nurAdministratorenSehenDasWerkzeugUndEsIstEineAenderung() {
        assertThat(werkzeug.aendert()).isTrue();
        assertThat(werkzeug.verfuegbar(admin)).isTrue();
        assertThat(werkzeug.verfuegbar(anna)).isFalse();
    }

    @Test
    void dieVorschauZeigtDenBereinigtenSatzUndSpeichertNichts() {
        when(dienst.pruefe(anyString())).thenAnswer(i -> ((String) i.getArgument(0)).strip());
        String v = werkzeug.vorschau(json.createObjectNode().put("satz", "  Lieferant Muster liefert nur freitags  "));
        assertThat(v).contains("Merkliste").contains("Lieferant Muster liefert nur freitags");
        verify(dienst, never()).merke(anyString(), anyString());
    }

    @Test
    void unzulaessigeSaetzeWerdenMitKlartextAbgelehnt() {
        when(dienst.pruefe(anyString())).thenThrow(new IllegalArgumentException("Der Satz enthält eine E-Mail-Adresse."));
        assertThatThrownBy(() -> werkzeug.vorschau(json.createObjectNode().put("satz", "Mail an x@y.example"))).isInstanceOf(KiWerkzeugFehler.class).hasMessageContaining("E-Mail-Adresse");
    }

    @Test
    void ausfuehrenMerktMitDemBestaetigerUndKeinAdminOderVollGibtKlartext() {
        when(dienst.pruefe(anyString())).thenAnswer(i -> i.getArgument(0));
        assertThat(werkzeug.ausfuehren(json.createObjectNode().put("satz", "Hinweis eins"), admin)).contains("Gemerkt");
        verify(dienst).merke(eq("Hinweis eins"), eq("admin"));

        assertThatThrownBy(() -> werkzeug.ausfuehren(json.createObjectNode().put("satz", "Hinweis eins"), anna)).isInstanceOf(KiWerkzeugFehler.class).hasMessageContaining("Administratoren");

        when(dienst.merke(anyString(), anyString())).thenThrow(new IllegalStateException("Die Merkliste ist voll (50 Einträge)."));
        assertThatThrownBy(() -> werkzeug.ausfuehren(json.createObjectNode().put("satz", "Hinweis eins"), admin)).hasMessageContaining("voll");
    }
}
