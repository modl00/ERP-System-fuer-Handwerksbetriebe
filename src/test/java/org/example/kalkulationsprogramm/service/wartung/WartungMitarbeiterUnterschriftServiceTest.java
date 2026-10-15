package org.example.kalkulationsprogramm.service.wartung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.wartung.WartungMitarbeiterUnterschrift;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungMitarbeiterUnterschriftRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class WartungMitarbeiterUnterschriftServiceTest {

    private static final byte[] PNG = ByteBuffer.allocate(33)
            .put(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})
            .putInt(13).put(new byte[] {'I', 'H', 'D', 'R'}).putInt(360).putInt(120).put(new byte[] {8, 6, 0, 0, 0}).array();
    private static final String BILD = WartungUnterschriftPruefer.PRAEFIX + Base64.getEncoder().encodeToString(PNG);

    @Mock private WartungMitarbeiterUnterschriftRepository repository;
    @Mock private MitarbeiterRepository mitarbeiterRepository;

    @InjectMocks private WartungMitarbeiterUnterschriftService service;

    @Test
    @DisplayName("Die erste Unterschrift wird gespeichert, der Name getrimmt")
    void speichertDieErsteUnterschrift() {
        when(mitarbeiterRepository.existsById(3L)).thenReturn(true);
        when(repository.findById(3L)).thenReturn(Optional.empty());

        var dto = service.speichern(3L, BILD, "  Erika Beispiel ");

        ArgumentCaptor<WartungMitarbeiterUnterschrift> captor = ArgumentCaptor.forClass(WartungMitarbeiterUnterschrift.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue()).extracting(WartungMitarbeiterUnterschrift::getMitarbeiterId, WartungMitarbeiterUnterschrift::getBild, WartungMitarbeiterUnterschrift::getName)
                .containsExactly(3L, BILD, "Erika Beispiel");
        assertThat(captor.getValue().getGeaendertAm()).isNotNull();
        assertThat(dto.name()).isEqualTo("Erika Beispiel");
    }

    @Test
    @DisplayName("Eine neue Unterschrift ersetzt die vorhandene des Mitarbeiters, es entsteht keine zweite")
    void ersetztDieVorhandene() {
        WartungMitarbeiterUnterschrift vorhanden = new WartungMitarbeiterUnterschrift();
        vorhanden.setMitarbeiterId(3L);
        vorhanden.setBild("alt");
        vorhanden.setName("Alt");
        when(mitarbeiterRepository.existsById(3L)).thenReturn(true);
        when(repository.findById(3L)).thenReturn(Optional.of(vorhanden));

        service.speichern(3L, BILD, null);

        assertThat(vorhanden.getBild()).isEqualTo(BILD);
        assertThat(vorhanden.getName()).isNull();
        verify(repository).save(vorhanden);
    }

    @Test
    @DisplayName("Ohne Mitarbeiter am Benutzer: 409. Ein Bild, das kein PNG ist, wird abgelehnt und nichts gespeichert")
    void fehler() {
        assertThatThrownBy(() -> service.speichern(null, BILD, "X"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        when(mitarbeiterRepository.existsById(3L)).thenReturn(true);
        assertThatThrownBy(() -> service.speichern(3L, "data:text/html;base64,PHNjcmlwdD4=", "X"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Lesen und Löschen: ohne Unterschrift oder ohne Mitarbeiter passiert nichts Unerwartetes")
    void lesenUndLoeschen() {
        assertThat(service.finde(null)).isEmpty();
        when(repository.findById(3L)).thenReturn(Optional.empty());
        assertThat(service.finde(3L)).isEmpty();

        service.loeschen(null);
        when(repository.existsById(3L)).thenReturn(false);
        service.loeschen(3L);
        verify(repository, never()).deleteById(any());

        when(repository.existsById(4L)).thenReturn(true);
        service.loeschen(4L);
        verify(repository).deleteById(4L);
    }
}
