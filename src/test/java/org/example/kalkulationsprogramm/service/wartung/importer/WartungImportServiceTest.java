package org.example.kalkulationsprogramm.service.wartung.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungGlobalesDatenfeldRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungModulDatenfeldRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungModulPruefpunktRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungModulRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungModulUntertypRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungObjektRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungTuerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;

class WartungImportServiceTest {

    private final ObjectMapper json = new ObjectMapper();
    private final WartungModulRepository module = mock(WartungModulRepository.class);
    private final KundeRepository kunden = mock(KundeRepository.class);
    private final WartungImportService service = new WartungImportService(module, mock(WartungModulUntertypRepository.class), mock(WartungModulPruefpunktRepository.class),
            mock(WartungGlobalesDatenfeldRepository.class), mock(WartungModulDatenfeldRepository.class), kunden, mock(WartungObjektRepository.class), mock(WartungTuerRepository.class));

    @Test
    @DisplayName("Eine Datei, die nicht aus dem HandwerkPro-Export stammt, wird abgelehnt, ohne etwas zu lesen")
    void fremdeDatei() {
        assertThatThrownBy(() -> service.vorschau(null)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("keine Exportdatei");
        assertThatThrownBy(() -> service.vorschau(json.createObjectNode().put("quelle", "Anderes"))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.vorschau(json.createObjectNode().put("quelle", "HandwerkPro"))).isInstanceOf(ResponseStatusException.class);
        verify(module, never()).findAllByOrderBySortierungAscIdAsc();
    }

    @Test
    @DisplayName("Zu viele Wartungen werden abgelehnt")
    void zuViele() {
        var export = json.createObjectNode().put("quelle", "HandwerkPro");
        var liste = export.putArray("wartungen");
        for (int i = 0; i < 5001; i++) {
            liste.addObject().put("id", i);
        }

        assertThatThrownBy(() -> service.vorschau(export)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("mehr als 5000");
    }

    @Test
    @DisplayName("Eine leere, aber gültige Datei ergibt eine leere Vorschau und liest nur (nichts wird gespeichert)")
    void leereDatei() {
        var export = json.createObjectNode().put("quelle", "HandwerkPro");
        export.putArray("wartungen");

        var v = service.vorschau(export);

        assertThat(v.zusammenfassung().wartungen()).isZero();
        verify(kunden, never()).save(any());
    }
}
