package org.example.kalkulationsprogramm.service.wartung;

import lombok.AllArgsConstructor;
import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Findet zum angemeldeten Benutzer den Mitarbeiter (für "meine Unterschrift"). Reine Admin-Konten haben keinen. */
@Service
@AllArgsConstructor
public class WartungBenutzerService {

    private final FrontendUserProfileRepository profileRepository;

    /** Nummer des Mitarbeiters des angemeldeten Benutzers oder {@code null}. */
    @Transactional(readOnly = true)
    public Long mitarbeiterId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof FrontendUserPrincipal principal) || principal.getId() == null) {
            return null;
        }
        return profileRepository.findById(principal.getId())
                .filter(FrontendUserProfile::isActive)
                .map(FrontendUserProfile::getMitarbeiter)
                .map(m -> m.getId())
                .orElse(null);
    }
}
