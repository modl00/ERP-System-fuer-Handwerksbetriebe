package org.example.kalkulationsprogramm.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.service.FrontendUserProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Benutzer und Mitarbeiter gegen eine echte Datenbank (H2), #241: ein Mitarbeiter gehört zu höchstens einem Benutzer. Nur Dummy-Daten. */
@DataJpaTest
@Import({FrontendUserProfileService.class, FrontendUserMitarbeiterZuordnungTest.Konfig.class})
class FrontendUserMitarbeiterZuordnungTest {

    @TestConfiguration
    static class Konfig {
        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(4);
        }
    }

    @Autowired private FrontendUserProfileService service;
    @Autowired private FrontendUserProfileRepository benutzer;
    @Autowired private MitarbeiterRepository mitarbeiter;

    private Mitarbeiter mitarbeiter(String vor, String nach) {
        Mitarbeiter m = new Mitarbeiter();
        m.setVorname(vor);
        m.setNachname(nach);
        return mitarbeiter.saveAndFlush(m);
    }

    private FrontendUserProfile anlegen(String name, String login, Long mitarbeiterId) {
        FrontendUserProfile p = new FrontendUserProfile();
        p.setDisplayName(name);
        FrontendUserProfile neu = service.saveOrUpdate(p, null, mitarbeiterId, login, "passwort123", Set.of(FrontendUserRole.USER), true, null);
        benutzer.flush();
        return neu;
    }

    @Test
    void einBenutzerMitMitarbeiterLaesstSichAnlegen() {
        Mitarbeiter m = mitarbeiter("Max", "Mustermann");
        FrontendUserProfile neu = anlegen("Max Mustermann", "max", m.getId());
        assertThat(neu.getId()).isNotNull();
        assertThat(neu.getMitarbeiter().getId()).isEqualTo(m.getId());
    }

    @Test
    void gleichesKuerzelBeiZweiBenutzernIstKeinProblem() {
        anlegen("Max Mustermann", "max", null);
        assertThat(anlegen("Maria Muster", "maria", null).getShortCode()).isEqualTo("MM");
    }

    @Test
    void einMitarbeiterKannNichtZuZweiBenutzernGehoerenUndDasWirdInKlartextGemeldet() {
        Mitarbeiter m = mitarbeiter("Max", "Mustermann");
        anlegen("Max Mustermann", "max", m.getId());

        assertThatThrownBy(() -> anlegen("Max Zweitkonto", "max2", m.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Max Mustermann")
                .hasMessageContaining("gehört schon zum Benutzer „Max Mustermann“");
    }

    @Test
    void einBenutzerDarfSeinenEigenenMitarbeiterBeimSpeichernBehalten() {
        Mitarbeiter m = mitarbeiter("Max", "Mustermann");
        FrontendUserProfile max = anlegen("Max Mustermann", "max", m.getId());

        FrontendUserProfile aenderung = new FrontendUserProfile();
        aenderung.setId(max.getId());
        aenderung.setDisplayName("Max M.");
        FrontendUserProfile gespeichert = service.saveOrUpdate(aenderung, null, m.getId(), "max", null, Set.of(FrontendUserRole.USER), true, null);

        assertThat(gespeichert.getDisplayName()).isEqualTo("Max M.");
        assertThat(gespeichert.getMitarbeiter().getId()).isEqualTo(m.getId());
    }

    @Test
    void wirdDerMitarbeiterEinemAnderenBenutzerZugeordnet_istDerAlteFreiUndDerNeueGesperrt() {
        Mitarbeiter m = mitarbeiter("Max", "Mustermann");
        FrontendUserProfile erster = anlegen("Max Mustermann", "max", m.getId());
        FrontendUserProfile zweiter = anlegen("Maria Muster", "maria", null);

        FrontendUserProfile loesen = new FrontendUserProfile();
        loesen.setId(erster.getId());
        loesen.setDisplayName("Max Mustermann");
        service.saveOrUpdate(loesen, null, null, "max", null, Set.of(FrontendUserRole.USER), true, null);
        benutzer.flush();

        FrontendUserProfile zuordnen = new FrontendUserProfile();
        zuordnen.setId(zweiter.getId());
        zuordnen.setDisplayName("Maria Muster");
        FrontendUserProfile ergebnis = service.saveOrUpdate(zuordnen, null, m.getId(), "maria", null, Set.of(FrontendUserRole.USER), true, null);
        benutzer.flush();
        assertThat(ergebnis.getMitarbeiter().getId()).isEqualTo(m.getId());
    }

    @Test
    void dieAbfrageNenntDenBenutzerEinesMitarbeiters() {
        Mitarbeiter m = mitarbeiter("Max", "Mustermann");
        Mitarbeiter frei = mitarbeiter("Erika", "Muster");
        anlegen("Max Mustermann", "max", m.getId());

        assertThat(mitarbeiter.benutzerNamen(m.getId())).isEqualTo(List.of("Max Mustermann"));
        assertThat(mitarbeiter.benutzerNamen(frei.getId())).isEmpty();
    }

    @Test
    void einMitarbeiterOhneAbhaengigeDatenLaesstSichLoeschen() {
        Mitarbeiter m = mitarbeiter("Erika", "Muster");
        mitarbeiter.deleteById(m.getId());
        mitarbeiter.flush();
        assertThat(mitarbeiter.findById(m.getId())).isEmpty();
    }
}
