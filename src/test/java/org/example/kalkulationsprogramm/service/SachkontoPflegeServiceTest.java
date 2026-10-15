package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Sachkonto;
import org.example.kalkulationsprogramm.domain.SachkontoTyp;
import org.example.kalkulationsprogramm.dto.SachkontoDto;
import org.example.kalkulationsprogramm.repository.SachkontoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SachkontoPflegeServiceTest {

    @Mock private SachkontoRepository repository;
    private SachkontoPflegeService service;

    private static Sachkonto konto(Long id, String nummer, String name, SachkontoTyp typ) {
        Sachkonto k = new Sachkonto();
        k.setId(id);
        k.setNummer(nummer);
        k.setBezeichnung(name);
        k.setKontoTyp(typ);
        k.setAktiv(true);
        return k;
    }

    private static SachkontoDto.UpsertRequest req(String nummer, String name, String typ) {
        return new SachkontoDto.UpsertRequest(nummer, name, typ, null, null, null);
    }

    @BeforeEach
    void setUp() {
        service = new SachkontoPflegeService(repository);
        org.mockito.Mockito.lenient().when(repository.save(any(Sachkonto.class))).thenAnswer(a -> a.getArgument(0));
        org.mockito.Mockito.lenient().when(repository.findAll()).thenReturn(List.of(
                konto(1L, "4920", "Telefon", SachkontoTyp.AUFWAND),
                konto(2L, "4120", "Loehne & Gehaelter", SachkontoTyp.AUFWAND),
                konto(3L, "8400", "Erloese 19%", SachkontoTyp.ERTRAG)));
    }

    @Test
    @DisplayName("anlegen: Konto mit Nummer, Name, Art und Beschreibung wird gespeichert, Name und Nummer getrimmt")
    void anlegen() {
        SachkontoDto.UpsertRequest r = req(" 4970 ", "  Bankgebuehren  ", "aufwand");
        r.setBeschreibung("  Kontofuehrung  ");

        Sachkonto neu = service.anlegen(r);

        assertThat(neu.getNummer()).isEqualTo("4970");
        assertThat(neu.getBezeichnung()).isEqualTo("Bankgebuehren");
        assertThat(neu.getKontoTyp()).isEqualTo(SachkontoTyp.AUFWAND);
        assertThat(neu.getBeschreibung()).isEqualTo("Kontofuehrung");
        assertThat(neu.isAktiv()).isTrue();
        assertThat(neu.getSortierung()).isEqualTo(900);
    }

    @Test
    @DisplayName("anlegen: Pflichtangaben, Ziffern, Längen und die Art werden in Klartext geprüft, ohne zu speichern")
    void anlegenPruefungen() {
        assertThatThrownBy(() -> service.anlegen(req("4970", " ", "AUFWAND"))).hasMessageContaining("Namen");
        assertThatThrownBy(() -> service.anlegen(req("4970", "Konto", null))).hasMessageContaining("Art des Kontos wählen");
        assertThatThrownBy(() -> service.anlegen(req("4970", "Konto", "BLAU"))).hasMessageContaining("unbekannt");
        assertThatThrownBy(() -> service.anlegen(req("49x0", "Konto", "AUFWAND"))).hasMessageContaining("nur aus Ziffern");
        assertThatThrownBy(() -> service.anlegen(req("12", "Konto", "AUFWAND"))).hasMessageContaining("3 bis 8");
        assertThatThrownBy(() -> service.anlegen(req("4970", "x".repeat(121), "AUFWAND"))).hasMessageContaining("zu lang");
        SachkontoDto.UpsertRequest lang = req("4970", "Konto", "AUFWAND");
        lang.setBeschreibung("x".repeat(501));
        assertThatThrownBy(() -> service.anlegen(lang)).hasMessageContaining("Beschreibung ist zu lang");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("anlegen: doppelte Nummer und doppelter Name (auch in anderer Schreibweise) werden abgelehnt")
    void anlegenEindeutig() {
        assertThatThrownBy(() -> service.anlegen(req("4920", "Handy", "AUFWAND"))).hasMessageContaining("4920").hasMessageContaining("Telefon");
        assertThatThrownBy(() -> service.anlegen(req("4999", "TELEFON", "AUFWAND"))).hasMessageContaining("gibt es schon");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("anlegen: ohne Nummer ist erlaubt (Konto ohne Rahmen-Nummer)")
    void anlegenOhneNummer() {
        assertThat(service.anlegen(req("  ", "Sonstiges", "NEUTRAL")).getNummer()).isNull();
    }

    @Test
    @DisplayName("ändern: Name, Nummer und Beschreibung eines normalen Kontos lassen sich ändern; sich selbst zählt nicht als Dublette")
    void aendern() {
        given(repository.findById(1L)).willReturn(Optional.of(konto(1L, "4920", "Telefon", SachkontoTyp.AUFWAND)));
        SachkontoDto.UpsertRequest r = req("4920", "Telefon", "AUFWAND");
        r.setBeschreibung("Mobilfunk");
        r.setAktiv(false);

        Sachkonto k = service.aendern(1L, r);

        assertThat(k.getBeschreibung()).isEqualTo("Mobilfunk");
        assertThat(k.isAktiv()).isFalse();
    }

    @Test
    @DisplayName("ändern: die Nummer eines anderen Kontos ist tabu")
    void aendernKollision() {
        given(repository.findById(1L)).willReturn(Optional.of(konto(1L, "4920", "Telefon", SachkontoTyp.AUFWAND)));

        assertThatThrownBy(() -> service.aendern(1L, req("4120", "Telefon", "AUFWAND"))).hasMessageContaining("schon vergeben");
    }

    @Test
    @DisplayName("ändern: Konten, die das Programm für die Kasse braucht, behalten Nummer, Art und Sichtbarkeit; Name und Beschreibung gehen")
    void systemkonten() {
        Sachkonto lohn = konto(2L, "4120", "Loehne & Gehaelter", SachkontoTyp.AUFWAND);
        given(repository.findById(2L)).willReturn(Optional.of(lohn));

        assertThatThrownBy(() -> service.aendern(2L, req("4121", "Loehne & Gehaelter", "AUFWAND"))).hasMessageContaining("braucht das Programm");
        SachkontoDto.UpsertRequest aus = req(null, null, null);
        aus.setAktiv(false);
        assertThatThrownBy(() -> service.aendern(2L, aus)).hasMessageContaining("braucht das Programm");
        assertThatThrownBy(() -> service.aendern(2L, req("4120", "Loehne & Gehaelter", "ERTRAG"))).hasMessageContaining("braucht das Programm");

        SachkontoDto.UpsertRequest umbenennen = req(null, "Gehaelter und Loehne", null);
        umbenennen.setBeschreibung("Brutto");
        assertThat(service.aendern(2L, umbenennen).getBezeichnung()).isEqualTo("Gehaelter und Loehne");
    }

    @Test
    @DisplayName("ändern: unbekanntes Konto; eine unbekannte Art wird gemeldet statt still ignoriert")
    void aendernFehler() {
        given(repository.findById(9L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.aendern(9L, req("4999", "X", "AUFWAND"))).isInstanceOf(NoSuchElementException.class);

        given(repository.findById(1L)).willReturn(Optional.of(konto(1L, "4920", "Telefon", SachkontoTyp.AUFWAND)));
        assertThatThrownBy(() -> service.aendern(1L, req(null, null, "BLAU"))).hasMessageContaining("unbekannt");
    }

    @Test
    @DisplayName("Systemkonten: genau die Nummern, die der Code per findByNummer sucht")
    void systemkontenListe() {
        assertThat(SachkontoPflegeService.SYSTEMKONTEN).containsExactlyInAnyOrder("1200", "1800", "1890", "4120", "8400");
        assertThat(SachkontoPflegeService.istSystemkonto("1890")).isTrue();
        assertThat(SachkontoPflegeService.istSystemkonto("4920")).isFalse();
        assertThat(SachkontoPflegeService.istSystemkonto(null)).isFalse();
    }
}
