package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.EmailZuordnungTyp;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.service.EmailKiAbgleichService.Zuordnung;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** KI-Abgleich im E-Mail-Center: Vorschlag ohne Speichern, Übernahme nur mit Bestätigung. Nur Dummy-Daten. */
class EmailKiAbgleichServiceTest {

    private final EmailRepository emails = mock(EmailRepository.class);
    private final EmailAutoAssignmentService auto = mock(EmailAutoAssignmentService.class);
    private final ProjektRepository projekte = mock(ProjektRepository.class);
    private final AnfrageRepository anfragen = mock(AnfrageRepository.class);
    private final LieferantenRepository lieferanten = mock(LieferantenRepository.class);
    private final org.springframework.transaction.PlatformTransactionManager tm = mock(org.springframework.transaction.PlatformTransactionManager.class);
    private final EmailKiAbgleichService service = new EmailKiAbgleichService(emails, auto, projekte, anfragen, lieferanten, tm);

    private Email mail(long id, EmailDirection richtung, EmailZuordnungTyp typ) {
        Email e = new Email();
        e.setId(id);
        e.setSubject("Betreff " + id);
        e.setFromAddress("max@mustermann.example");
        e.setDirection(richtung);
        e.setZuordnungTyp(typ);
        return e;
    }

    private SimpleTransactionStatus status;

    @BeforeEach
    void setUp() {
        status = new SimpleTransactionStatus();
        when(tm.getTransaction(any())).thenReturn(status);
    }

    @Test
    void derVorschlagNenntDasZielUndSpeichertNichts() {
        Email e = mail(1, EmailDirection.IN, EmailZuordnungTyp.KEINE);
        when(emails.findById(1L)).thenReturn(Optional.of(e));
        Projekt p = new Projekt();
        p.setId(5L);
        p.setBauvorhaben("Geländer Halle");
        when(auto.tryAutoAssign(e)).thenAnswer(i -> { e.assignToProjekt(p); return true; });

        var v = service.vorschlaege(List.of(1L)).get(0);

        assertThat(v.art()).isEqualTo("PROJEKT");
        assertThat(v.zielId()).isEqualTo(5L);
        assertThat(v.ziel()).isEqualTo("Geländer Halle");
        verify(emails, never()).save(any());
        assertThat(status.isRollbackOnly()).as("die Probe wird immer zurückgerollt").isTrue();
    }

    @Test
    void ausgehendeZugeordneteUndUnbekannteMailsBekommenKeinenVorschlagMitGrund() {
        when(emails.findById(1L)).thenReturn(Optional.of(mail(1, EmailDirection.OUT, EmailZuordnungTyp.KEINE)));
        when(emails.findById(2L)).thenReturn(Optional.of(mail(2, EmailDirection.IN, EmailZuordnungTyp.PROJEKT)));
        when(emails.findById(3L)).thenReturn(Optional.empty());
        Email offen = mail(4, EmailDirection.IN, EmailZuordnungTyp.KEINE);
        when(emails.findById(4L)).thenReturn(Optional.of(offen));
        when(auto.tryAutoAssign(offen)).thenReturn(false);

        var v = service.vorschlaege(List.of(1L, 2L, 3L, 4L));

        assertThat(v).extracting(EmailKiAbgleichService.Vorschlag::art).containsOnly("KEINE");
        assertThat(v).extracting(EmailKiAbgleichService.Vorschlag::hinweis).containsExactly(
                "Nur eingehende E-Mails lassen sich zuordnen.", "Ist schon zugeordnet.", "Die E-Mail gibt es nicht mehr.", "Keine passende Zuordnung gefunden.");
    }

    @Test
    void einFehlerBeiEinerMailStopptNichtDieAnderen() {
        Email e = mail(1, EmailDirection.IN, EmailZuordnungTyp.KEINE);
        when(emails.findById(1L)).thenReturn(Optional.of(e));
        when(auto.tryAutoAssign(e)).thenThrow(new IllegalStateException("KI nicht erreichbar"));
        Email f = mail(2, EmailDirection.IN, EmailZuordnungTyp.KEINE);
        when(emails.findById(2L)).thenReturn(Optional.of(f));

        var v = service.vorschlaege(List.of(1L, 2L));

        assertThat(v).hasSize(2);
        assertThat(v.get(0).hinweis()).isEqualTo("Der Abgleich ist fehlgeschlagen.");
    }

    @Test
    void leereOderZuGrosseAuswahlWirdAbgewiesen() {
        assertThatThrownBy(() -> service.vorschlaege(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.vorschlaege(LongStream.rangeClosed(1, 51).boxed().toList())).hasMessageContaining("höchstens 50");
    }

    @Test
    void uebernommenWirdNurWasNochOffenIstUndDasZielExistiert() {
        Email offen = mail(1, EmailDirection.IN, EmailZuordnungTyp.KEINE);
        Email schonZu = mail(2, EmailDirection.IN, EmailZuordnungTyp.ANFRAGE);
        Email ohneZiel = mail(3, EmailDirection.IN, EmailZuordnungTyp.KEINE);
        when(emails.findById(1L)).thenReturn(Optional.of(offen));
        when(emails.findById(2L)).thenReturn(Optional.of(schonZu));
        when(emails.findById(3L)).thenReturn(Optional.of(ohneZiel));
        Projekt p = new Projekt();
        p.setId(5L);
        when(projekte.findById(5L)).thenReturn(Optional.of(p));
        when(anfragen.findById(99L)).thenReturn(Optional.<Anfrage>empty());

        var r = service.uebernehme(List.of(new Zuordnung(1L, "PROJEKT", 5L), new Zuordnung(2L, "PROJEKT", 5L), new Zuordnung(3L, "ANFRAGE", 99L)));

        assertThat(r.zugeordnet()).isEqualTo(1);
        assertThat(r.abgelehnt()).hasSize(2);
        assertThat(offen.getZuordnungTyp()).isEqualTo(EmailZuordnungTyp.PROJEKT);
        assertThat(schonZu.getZuordnungTyp()).isEqualTo(EmailZuordnungTyp.ANFRAGE);
        verify(emails).save(offen);
        verify(emails, never()).save(ohneZiel);
    }

    @Test
    void ungueltigeArtOderLeereListeWirdAbgelehnt() {
        Email offen = mail(1, EmailDirection.IN, EmailZuordnungTyp.KEINE);
        when(emails.findById(1L)).thenReturn(Optional.of(offen));
        assertThat(service.uebernehme(List.of(new Zuordnung(1L, "STEUERBERATER", 1L))).zugeordnet()).isZero();
        assertThatThrownBy(() -> service.uebernehme(List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
