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

import org.example.kalkulationsprogramm.domain.Fremdleistung;
import org.example.kalkulationsprogramm.domain.Fremdleistung.Einheit;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.dto.Fremdleistung.FremdleistungEingabeDto;
import org.example.kalkulationsprogramm.repository.FremdleistungRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class FremdleistungServiceTest {

    FremdleistungRepository repo = mock(FremdleistungRepository.class);
    LieferantenRepository lieferanten = mock(LieferantenRepository.class);
    FremdleistungService service = new FremdleistungService(repo, lieferanten);

    private static BigDecimal d(String s) {
        return new BigDecimal(s);
    }

    private Lieferanten lieferant(long id, String name) {
        Lieferanten l = new Lieferanten();
        l.setId(id);
        l.setLieferantenname(name);
        when(lieferanten.findById(id)).thenReturn(Optional.of(l));
        return l;
    }

    private Fremdleistung angebot(long id, Lieferanten l, String bezeichnung, String preis, String mindest, boolean aktiv) {
        Fremdleistung f = new Fremdleistung();
        f.setId(id);
        f.setLieferant(l);
        f.setBezeichnung(bezeichnung);
        f.setEinheit(Einheit.KG);
        f.setPreisJeEinheit(d(preis));
        f.setMindestpreis(mindest == null ? null : d(mindest));
        f.setAktiv(aktiv);
        return f;
    }

    private FremdleistungEingabeDto eingabe(String einheit, BigDecimal preis) {
        return new FremdleistungEingabeDto(1L, " Feuerverzinken ", einheit, preis, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    @BeforeEach
    void setUp() {
        lieferant(1L, "Verzinkerei Muster");
        when(repo.save(any(Fremdleistung.class))).thenAnswer(a -> { Fremdleistung f = a.getArgument(0); if (f.getId() == null) f.setId(5L); return f; });
        when(repo.zaehleVerwendung(any())).thenReturn(0L);
    }

    @Test
    void legtAnUndNormalisiert() {
        var dto = service.anlegen(eingabe("kg", d("1.2")));
        assertThat(dto.bezeichnung()).isEqualTo("Feuerverzinken");
        assertThat(dto.einheit()).isEqualTo("KG");
        assertThat(dto.transportArt()).isEqualTo("KEINE");
        assertThat(dto.aktiv()).isTrue();
        assertThat(dto.gueltigAb()).isNotNull();
        assertThat(dto.lieferantName()).isEqualTo("Verzinkerei Muster");
    }

    @Test
    void pruefungen() {
        assertThatThrownBy(() -> service.anlegen(eingabe("Pfund", d("1")))).hasMessageContaining("Abrechnungseinheit");
        assertThatThrownBy(() -> service.anlegen(eingabe("KG", null))).hasMessageContaining("mindestens eine Kostenart");
        assertThatThrownBy(() -> service.anlegen(eingabe("KG", d("-1")))).hasMessageContaining("zwischen 0");
        assertThatThrownBy(() -> service.anlegen(new FremdleistungEingabeDto(99L, "x", "KG", d("1"), null, null, null, null, null, null, null, null, null, null, null, null, null))).hasMessageContaining("nicht gefunden");
        assertThatThrownBy(() -> service.anlegen(new FremdleistungEingabeDto(1L, "x", "KG", d("1"), null, null, d("50"), null, null, null, null, null, null, null, null, null, null))).hasMessageContaining("Charge");
        assertThatThrownBy(() -> service.anlegen(new FremdleistungEingabeDto(1L, "x", "KG", d("1"), null, null, null, null, null, "JE_KM", d("0.8"), null, null, null, null, null, null))).hasMessageContaining("Strecke");
        assertThatThrownBy(() -> service.anlegen(new FremdleistungEingabeDto(1L, "x", "KG", d("1"), null, null, null, null, null, "PAUSCHAL", null, null, null, null, null, null, null))).hasMessageContaining("Transportbetrag");
        assertThatThrownBy(() -> service.anlegen(new FremdleistungEingabeDto(1L, "x", "KG", d("1"), null, null, null, null, d("2000"), null, null, null, null, null, null, null, null))).hasMessageContaining("Prozentzuschlag");
        assertThatThrownBy(() -> service.anlegen(new FremdleistungEingabeDto(1L, "x", "KG", d("1"), null, null, null, null, null, "SCHIFF", d("1"), null, null, null, null, null, null))).hasMessageContaining("Transportart");
        verify(repo, never()).save(any());
    }

    @Test
    void loeschenGehtNurWennNichtVerwendet() {
        Fremdleistung f = angebot(5, lieferant(1L, "Verzinkerei Muster"), "Feuerverzinken", "1.2", null, true);
        when(repo.findById(5L)).thenReturn(Optional.of(f));
        when(repo.zaehleVerwendung(5L)).thenReturn(2L);

        assertThatThrownBy(() -> service.loeschen(5L)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("2 Kalkulationszeilen").hasMessageContaining("ausschalten");
        verify(repo, never()).delete(any(Fremdleistung.class));

        when(repo.zaehleVerwendung(5L)).thenReturn(0L);
        service.loeschen(5L);
        verify(repo).delete(f);
    }

    @Test
    void vergleichSortiertNachSummeUndMarkiertDenGuenstigsten() {
        Lieferanten a = lieferant(1L, "Verzinkerei A");
        Lieferanten b = lieferant(2L, "Verzinkerei B");
        Lieferanten c = lieferant(3L, "Verzinkerei C");
        when(repo.findeAktiveMitBezeichnung("Feuerverzinken")).thenReturn(List.of(
                angebot(1, a, "Feuerverzinken", "1.40", null, true),      // 100 kg = 140
                angebot(2, b, "Feuerverzinken", "1.00", "150.00", true),   // Mindestpreis hebt auf 150
                angebot(3, c, "Feuerverzinken", "1.10", null, true)));    // 110

        var v = service.vergleich(" Feuerverzinken ", d("100"), null);

        assertThat(v).extracting("lieferantName").containsExactly("Verzinkerei C", "Verzinkerei A", "Verzinkerei B");
        assertThat(v).extracting("guenstigster").containsExactly(true, false, false);
        assertThat(v.get(0).summe()).isEqualByComparingTo("110.00");
        assertThat(v.get(0).summeJeEinheit()).isEqualByComparingTo("1.10");
        assertThat(v.get(2).kostenarten().get(0).art()).isEqualTo("MINDESTPREIS");
    }

    @Test
    void vergleichPruefungenUndLeereListe() {
        assertThatThrownBy(() -> service.vergleich(" ", d("1"), null)).hasMessageContaining("verglichen");
        assertThatThrownBy(() -> service.vergleich("x", d("0"), null)).hasMessageContaining("Menge");
        when(repo.findeAktiveMitBezeichnung("Pulvern")).thenReturn(List.of());
        assertThat(service.vergleich("Pulvern", d("1"), null)).isEmpty();
    }

    @Test
    void ohneGewichtUnvollstaendigeAngeboteWerdenNichtAlsGuenstigsterMarkiert() {
        Lieferanten a = lieferant(1L, "Veredler A");
        Fremdleistung f = angebot(1, a, "Pulvern", "5", null, true);
        f.setEinheit(Einheit.QUADRATMETER);
        f.setTransportArt(Fremdleistung.TransportArt.JE_KG);
        f.setTransportBetrag(d("0.1"));
        when(repo.findeAktiveMitBezeichnung("Pulvern")).thenReturn(List.of(f));

        var v = service.vergleich("Pulvern", d("10"), null);

        assertThat(v).hasSize(1);
        assertThat(v.get(0).gewichtFehlt()).isTrue();
        assertThat(v.get(0).guenstigster()).isFalse();
    }
}
