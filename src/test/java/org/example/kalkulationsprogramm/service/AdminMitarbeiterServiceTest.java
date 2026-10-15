package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Nur Dummy-Daten (Max Mustermann). */
@ExtendWith(MockitoExtension.class)
class AdminMitarbeiterServiceTest {

    @Mock private FrontendUserProfileRepository profilRepository;
    @Mock private MitarbeiterRepository mitarbeiterRepository;

    private AdminMitarbeiterService service;

    @BeforeEach
    void setUp() {
        service = new AdminMitarbeiterService(profilRepository, mitarbeiterRepository);
    }

    private static FrontendUserProfile profil(boolean aktiv, FrontendUserRole rolle, String anzeige, String benutzer) {
        FrontendUserProfile p = new FrontendUserProfile();
        p.setActive(aktiv);
        p.setRoleSet(Set.of(rolle));
        p.setDisplayName(anzeige);
        p.setUsername(benutzer);
        return p;
    }

    @Test
    @DisplayName("legt für einen Administrator ohne Mitarbeiter einen an, ohne Zeitkonto, und verknüpft ihn")
    void legtMitarbeiterAnUndVerknuepft() {
        FrontendUserProfile p = profil(true, FrontendUserRole.ADMIN, "Max Mustermann", "max@example.org");
        given(profilRepository.findById(5L)).willReturn(Optional.of(p));
        given(mitarbeiterRepository.save(any(Mitarbeiter.class))).willAnswer(a -> {
            Mitarbeiter m = a.getArgument(0);
            m.setId(77L);
            return m;
        });

        Mitarbeiter m = service.legeFuerAdministratorAn(5L);

        assertThat(m.getVorname()).isEqualTo("Max");
        assertThat(m.getNachname()).isEqualTo("Mustermann");
        assertThat(m.getEmail()).isEqualTo("max@example.org");
        assertThat(m.getFuehrtZeitkonto()).as("kein zusätzlicher Eintrag in Zeiterfassung und Urlaub").isFalse();
        assertThat(m.getAktiv()).isTrue();
        assertThat(p.getMitarbeiter()).isSameAs(m);
        verify(profilRepository).save(p);
    }

    @Test
    @DisplayName("ist schon ein Mitarbeiter verknüpft, bleibt alles unverändert (kein Doppelt-Anlegen)")
    void idempotent() {
        FrontendUserProfile p = profil(true, FrontendUserRole.ADMIN, "Max Mustermann", "max");
        Mitarbeiter vorhanden = new Mitarbeiter();
        vorhanden.setId(9L);
        p.setMitarbeiter(vorhanden);
        given(profilRepository.findById(5L)).willReturn(Optional.of(p));

        assertThat(service.legeFuerAdministratorAn(5L)).isSameAs(vorhanden);
        verify(mitarbeiterRepository, never()).save(any());
    }

    @Test
    @DisplayName("Benutzer ohne Administrator-Rolle und deaktivierte Benutzer werden abgelehnt")
    void nurAktiveAdministratoren() {
        given(profilRepository.findById(1L)).willReturn(Optional.of(profil(true, FrontendUserRole.USER, "Erika Muster", "erika")));
        given(profilRepository.findById(2L)).willReturn(Optional.of(profil(false, FrontendUserRole.ADMIN, "Max Mustermann", "max")));

        assertThatThrownBy(() -> service.legeFuerAdministratorAn(1L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.legeFuerAdministratorAn(2L)).isInstanceOf(IllegalStateException.class);
        verify(mitarbeiterRepository, never()).save(any());
    }

    @Test
    @DisplayName("unbekannter Benutzer gibt eine Meldung statt eines Fehlers")
    void unbekannterBenutzer() {
        given(profilRepository.findById(3L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.legeFuerAdministratorAn(3L)).hasMessageContaining("nicht gefunden");
    }

    @Test
    @DisplayName("Namensteilung: zwei Wörter, ein Wort, nur E-Mail, leer, sehr lang")
    void namensteilung() {
        assertThat(AdminMitarbeiterService.teileName("Max Mustermann", "x")).containsExactly("Max", "Mustermann");
        assertThat(AdminMitarbeiterService.teileName("Hans Peter Mustermann", "x")).containsExactly("Hans", "Peter Mustermann");
        assertThat(AdminMitarbeiterService.teileName("Chef", "x")).containsExactly("", "Chef");
        assertThat(AdminMitarbeiterService.teileName(" ", "max.mustermann@example.org")).containsExactly("", "max.mustermann");
        assertThat(AdminMitarbeiterService.teileName(null, null)).containsExactly("", "Administrator");
        assertThat(AdminMitarbeiterService.teileName("a".repeat(300), null)[1]).hasSize(100);
    }
}
