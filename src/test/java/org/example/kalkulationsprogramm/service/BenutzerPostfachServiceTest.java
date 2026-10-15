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

import org.example.kalkulationsprogramm.domain.BenutzerPostfach;
import org.example.kalkulationsprogramm.repository.BenutzerPostfachRepository;
import org.example.kalkulationsprogramm.service.BenutzerPostfachService.Eingabe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Persönliche Postfächer verwalten: Eigentum, Prüfungen, Passwort nie im Klartext. Nur Dummy-Daten. */
class BenutzerPostfachServiceTest {

    private final BenutzerPostfachRepository repo = mock(BenutzerPostfachRepository.class);
    private final PasswortSchutz schutz = new PasswortSchutz(new byte[32]);
    private final BenutzerPostfachService service = new BenutzerPostfachService(repo, schutz);

    private static Eingabe eingabe(String passwort) {
        return new Eingabe("Mein Büro", "max@mustermann.example", "imap.mustermann.example", 993, "smtp.mustermann.example", 465, null, passwort, null);
    }

    @BeforeEach
    void setUp() {
        when(repo.save(any())).thenAnswer(i -> { BenutzerPostfach p = i.getArgument(0); if (p.getId() == null) p.setId(1L); return p; });
    }

    @Test
    void anlegenSpeichertVerschluesseltUndGibtNieDasPasswortZurueck() {
        var dto = service.anlegen(7L, eingabe("Geheim1"));

        org.mockito.ArgumentCaptor<BenutzerPostfach> c = org.mockito.ArgumentCaptor.forClass(BenutzerPostfach.class);
        verify(repo).save(c.capture());
        assertThat(c.getValue().getProfilId()).isEqualTo(7L);
        assertThat(c.getValue().getPasswortVerschluesselt()).doesNotContain("Geheim1");
        assertThat(schutz.entschluessele(c.getValue().getPasswortVerschluesselt())).isEqualTo("Geheim1");
        assertThat(c.getValue().getBenutzername()).as("ohne Angabe die E-Mail-Adresse").isEqualTo("max@mustermann.example");
        assertThat(dto.passwortGesetzt()).isTrue();
        assertThat(dto.toString()).doesNotContain("Geheim1");
    }

    @Test
    void ohneEingabeOderPasswortOderMitUnsinnigenWertenWirdAbgelehnt() {
        assertThatThrownBy(() -> service.anlegen(7L, eingabe(""))).hasMessageContaining("Passwort");
        assertThatThrownBy(() -> service.anlegen(7L, new Eingabe(" ", "a@b.de", "imap.x.de", 993, null, null, null, "pw", null))).hasMessageContaining("Namen");
        assertThatThrownBy(() -> service.anlegen(7L, new Eingabe("X", "keine-adresse", "imap.x.de", 993, null, null, null, "pw", null))).hasMessageContaining("E-Mail-Adresse");
        assertThatThrownBy(() -> service.anlegen(7L, new Eingabe("X", "a@b.de", "imap x/de", 993, null, null, null, "pw", null))).hasMessageContaining("Empfangs-Server");
        verify(repo, never()).save(any());
    }

    @Test
    void nurDieUeblichenMailPortsSindErlaubtDamitDerServerKeinPortScannerWird() {
        assertThatThrownBy(() -> service.anlegen(7L, new Eingabe("X", "a@b.de", "intern.local", 3306, null, null, null, "pw", null))).hasMessageContaining("143 oder 993");
        assertThatThrownBy(() -> service.anlegen(7L, new Eingabe("X", "a@b.de", "imap.x.de", 993, "smtp.x.de", 22, null, "pw", null))).hasMessageContaining("25, 465 oder 587");
    }

    @Test
    void hoechstensZehnPostfaecherJeBenutzer() {
        when(repo.countByProfilId(7L)).thenReturn(10L);
        assertThatThrownBy(() -> service.anlegen(7L, eingabe("pw"))).hasMessageContaining("höchstens 10");
    }

    @Test
    void aendernBehaeltDasPasswortBeiLeeremFeldUndErsetztEsSonst() {
        BenutzerPostfach p = new BenutzerPostfach();
        p.setId(3L);
        p.setProfilId(7L);
        p.setPasswortVerschluesselt(schutz.verschluessele("alt"));
        when(repo.findByIdAndProfilId(3L, 7L)).thenReturn(Optional.of(p));

        service.aendern(7L, 3L, eingabe(null));
        assertThat(schutz.entschluessele(p.getPasswortVerschluesselt())).isEqualTo("alt");
        service.aendern(7L, 3L, eingabe("neu"));
        assertThat(schutz.entschluessele(p.getPasswortVerschluesselt())).isEqualTo("neu");
    }

    @Test
    void fremdePostfaecherSindWederAenderbarNochLoeschbarNochTestbar() {
        when(repo.findByIdAndProfilId(3L, 8L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.aendern(8L, 3L, eingabe("pw"))).hasMessageContaining("gibt es nicht");
        assertThatThrownBy(() -> service.loesche(8L, 3L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.teste(8L, 3L, eingabe(""))).isInstanceOf(IllegalArgumentException.class);
        verify(repo, never()).delete(any());
    }

    @Test
    void dieListeEnthaeltNurDasEigenePostfachOhnePasswort() {
        BenutzerPostfach p = new BenutzerPostfach();
        p.setId(3L);
        p.setProfilId(7L);
        p.setBezeichnung("Büro");
        p.setPasswortVerschluesselt(schutz.verschluessele("pw"));
        when(repo.findByProfilIdOrderByBezeichnungAscIdAsc(7L)).thenReturn(List.of(p));

        var liste = service.liste(7L);

        assertThat(liste).singleElement().satisfies(d -> {
            assertThat(d.passwortGesetzt()).isTrue();
            assertThat(d.toString()).doesNotContain("v1:");
        });
    }

    @Test
    void testenOhnePasswortUndOhneGespeichertesVerlangtEines() {
        assertThatThrownBy(() -> service.teste(7L, null, eingabe(""))).hasMessageContaining("Passwort");
    }
}
