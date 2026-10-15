package org.example.kalkulationsprogramm.service.lieferschein;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.example.kalkulationsprogramm.domain.Funktion;
import org.example.kalkulationsprogramm.domain.FunktionsAktion;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.dto.lieferschein.LieferscheinKopieDto;
import org.example.kalkulationsprogramm.repository.lieferschein.LieferscheinRepository;
import org.example.kalkulationsprogramm.service.FunktionsRechteService;
import org.example.kalkulationsprogramm.service.NummernkreisService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AppLieferscheinServiceTest {

    FunktionsRechteService rechte = mock(FunktionsRechteService.class);
    LieferscheinService fach = mock(LieferscheinService.class);
    NummernbereichService bereiche = mock(NummernbereichService.class);
    LieferscheinRepository repo = mock(LieferscheinRepository.class);
    AppLieferscheinService service = new AppLieferscheinService(rechte, fach, bereiche, repo);

    @Test
    void ohneRechtGeschiehtNichts() {
        when(rechte.verlangeFuerToken("t", Funktion.LIEFERSCHEINE, FunktionsAktion.AENDERN)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Dafür fehlt die Berechtigung"));
        when(rechte.verlangeFuerToken("t", Funktion.LIEFERSCHEINE, FunktionsAktion.LOESCHEN)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Dafür fehlt die Berechtigung"));
        when(rechte.verlangeFuerToken("t", Funktion.LIEFERSCHEINE, FunktionsAktion.ANLEGEN)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Dafür fehlt die Berechtigung"));

        assertThatThrownBy(() -> service.kopieSenden("t", 1L, new LieferscheinKopieDto("a@example.org", true))).hasMessageContaining("Berechtigung");
        assertThatThrownBy(() -> service.loeschen("t", 1L)).hasMessageContaining("Berechtigung");
        assertThatThrownBy(() -> service.nummernbereich("t", 20)).hasMessageContaining("Berechtigung");
        assertThatThrownBy(() -> service.unterschreiben("t", 1L, null)).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(fach, bereiche);
    }

    @Test
    void ohneGueltigenZugangGibtEs401() {
        when(rechte.verlangeFuerToken(any(), any(), any())).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Der Zugang ist nicht gültig."));
        assertThatThrownBy(() -> service.imProjekt("falsch", 7L)).satisfies(e -> org.assertj.core.api.Assertions.assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
        verifyNoInteractions(fach);
    }

    @Test
    void dasRechtZumAnlegenGiltFuerDenNummernbereichDesGeraets() {
        Mitarbeiter max = new Mitarbeiter();
        max.setId(1L);
        when(rechte.verlangeFuerToken("t", Funktion.LIEFERSCHEINE, FunktionsAktion.ANLEGEN)).thenReturn(max);

        service.nummernbereich("t", 20);

        verify(bereiche).auffuellen(org.mockito.ArgumentMatchers.eq(max), org.mockito.ArgumentMatchers.eq(NummernkreisService.LIEFERSCHEIN), org.mockito.ArgumentMatchers.eq(20), any());
    }
}
