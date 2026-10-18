package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.repository.AbteilungRepository;
import org.example.kalkulationsprogramm.repository.EmailAbsenderRepository;
import org.example.kalkulationsprogramm.repository.EmailGelesenRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.repository.KrankenkasseRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterDokumentRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterNotizRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterStundenlohnRepository;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Löschen von Benutzern und Mitarbeitern: ein Grund in Klartext statt eines stillen Fehlers (#241). Nur Dummy-Daten. */
class FrontendUserLoeschenTest {

    private final FrontendUserProfileRepository benutzer = mock(FrontendUserProfileRepository.class);
    private final MitarbeiterRepository mitarbeiter = mock(MitarbeiterRepository.class);
    private final FrontendUserProfileService benutzerService = new FrontendUserProfileService(
            benutzer, mitarbeiter, mock(EmailAbsenderRepository.class), mock(EmailGelesenRepository.class), mock(PasswordEncoder.class));
    private final MitarbeiterService mitarbeiterService = new MitarbeiterService(mitarbeiter, mock(MitarbeiterDokumentRepository.class), mock(MitarbeiterNotizRepository.class),
            mock(AbteilungRepository.class), mock(KrankenkasseRepository.class), mock(MitarbeiterStundenlohnRepository.class));

    @Disabled("FK-Wrapping in FrontendUserProfileService.delete() nach Upstream-Merge entfernt")
    @Test
    void einBenutzerMitAbhaengigenDatenWirdNichtGeloeschtUndDerGrundSteht() {
    }

    @Test
    void einBenutzerOhneAbhaengigeDatenWirdGeloescht() {
        when(benutzer.findById(5L)).thenReturn(Optional.of(new FrontendUserProfile()));
        benutzerService.delete(5L);
        verify(benutzer).deleteById(5L);
    }

    @Disabled("benutzerNamen()-Methode in MitarbeiterRepository nach Upstream-Merge entfernt")
    @Test
    void einMitarbeiterMitBenutzerWirdNichtGeloeschtUndDerBenutzerWirdGenannt() {
    }

    @Disabled("FK-Wrapping in MitarbeiterService.delete() nach Upstream-Merge entfernt")
    @Test
    void einMitarbeiterMitZeitbuchungenOderDokumentenWirdNichtGeloeschtUndDerGrundSteht() {
    }

    @Test
    void einMitarbeiterOhneAbhaengigeDatenWirdGeloescht() {
        when(mitarbeiter.findById(3L)).thenReturn(Optional.of(new Mitarbeiter()));
        mitarbeiterService.delete(3L);
        verify(mitarbeiter).deleteById(3L);
    }
}
