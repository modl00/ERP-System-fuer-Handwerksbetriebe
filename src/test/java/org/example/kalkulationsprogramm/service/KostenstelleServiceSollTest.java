package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Kostenstelle;
import org.example.kalkulationsprogramm.domain.KostenstellenTyp;
import org.example.kalkulationsprogramm.dto.KostenstelleDto;
import org.example.kalkulationsprogramm.repository.KostenstelleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Geschätzter Jahresbetrag (Soll) je Kostenstelle (#237): Prüfung, Speichern ohne Verlust, nur Fixkosten. Nur Dummy-Daten. */
class KostenstelleServiceSollTest {

    private final KostenstelleRepository repo = mock(KostenstelleRepository.class);
    private final KostenstelleService service = new KostenstelleService(repo);
    private Kostenstelle miete;

    @BeforeEach
    void setUp() {
        miete = new Kostenstelle();
        miete.setId(5L);
        miete.setBezeichnung("Miete");
        miete.setTyp(KostenstellenTyp.GEMEINKOSTEN);
        miete.setIstFixkosten(true);
        miete.setJahresSoll(new BigDecimal("6000.00"));
        when(repo.findById(5L)).thenReturn(Optional.of(miete));
        when(repo.save(any(Kostenstelle.class))).thenAnswer(i -> i.getArgument(0));
    }

    private KostenstelleDto dtoVon(Kostenstelle k) {
        KostenstelleDto dto = new KostenstelleDto();
        dto.setId(k.getId());
        dto.setBezeichnung(k.getBezeichnung());
        dto.setTyp(k.getTyp());
        dto.setIstFixkosten(k.isIstFixkosten());
        dto.setAktiv(true);
        dto.setJahresSoll(k.getJahresSoll());
        return dto;
    }

    @Test
    void daSollBleibtBeimSpeichernErhaltenWennDerEditorEsMitschickt() {
        KostenstelleDto dto = dtoVon(miete);
        dto.setBeschreibung("Werkstatt und Büro");

        KostenstelleDto gespeichert = service.speichern(dto);

        assertThat(gespeichert.getJahresSoll()).isEqualByComparingTo("6000.00");
        assertThat(miete.getJahresSoll()).isEqualByComparingTo("6000.00");
    }

    @Test
    void einLeeresSollLoeschtEsAusdruecklich() {
        KostenstelleDto dto = dtoVon(miete);
        dto.setJahresSoll(null);

        assertThat(service.speichern(dto).getJahresSoll()).isNull();
    }

    @Test
    void speichernRundetAufZweiStellen() {
        KostenstelleDto dto = dtoVon(miete);
        dto.setJahresSoll(new BigDecimal("6000.456"));

        assertThat(service.speichern(dto).getJahresSoll()).isEqualByComparingTo("6000.46");
    }

    @Test
    void negativeOderZuHoheWerteWerdenMitKlartextAbgelehnt() {
        KostenstelleDto negativ = dtoVon(miete);
        negativ.setJahresSoll(new BigDecimal("-1"));
        assertThatThrownBy(() -> service.speichern(negativ)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nicht negativ");

        KostenstelleDto zuHoch = dtoVon(miete);
        zuHoch.setJahresSoll(new BigDecimal("100000001"));
        assertThatThrownBy(() -> service.speichern(zuHoch)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("zu hoch");
        verify(repo, never()).save(any());
    }

    @Test
    void einSollGibtEsNurBeiFixkosten() {
        KostenstelleDto lager = dtoVon(miete);
        lager.setTyp(KostenstellenTyp.LAGER);
        lager.setIstFixkosten(false);

        assertThatThrownBy(() -> service.speichern(lager)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Gemeinkosten");

        lager.setJahresSoll(null);
        assertThat(service.speichern(lager).getJahresSoll()).isNull();
    }

    @Test
    void setzeSollAendertNurDasSollUndLoeschtMitNull() {
        miete.setBeschreibung("bleibt");

        assertThat(service.setzeSoll(5L, new BigDecimal("7200")).getJahresSoll()).isEqualByComparingTo("7200.00");
        assertThat(miete.getBeschreibung()).isEqualTo("bleibt");
        assertThat(service.setzeSoll(5L, null).getJahresSoll()).isNull();
    }

    @Test
    void setzeSollMeldetEineUnbekannteKostenstelle() {
        assertThatThrownBy(() -> service.setzeSoll(99L, BigDecimal.TEN)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nicht gefunden");
    }
}
