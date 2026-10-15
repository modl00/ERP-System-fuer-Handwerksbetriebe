package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.MitarbeiterArt;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Legt für einen Administrator, der noch keinem Mitarbeiter zugeordnet ist, mit einem
 * Klick einen Mitarbeiter an und verknüpft ihn (#85).
 *
 * <p>Belege, Kasse und deren Prüfprotokoll halten fest, <em>welcher Mitarbeiter</em> etwas
 * getan hat. Ein Benutzer ohne Mitarbeiter kann deshalb nicht dort arbeiten. Der neue
 * Mitarbeiter führt <strong>kein Zeitkonto</strong>, damit er in der Zeiterfassung und
 * Urlaubsplanung nicht als zusätzliche Arbeitskraft auftaucht; alles Weitere (Abteilung,
 * Anschrift) lässt sich später unter Mitarbeiter ergänzen.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminMitarbeiterService {

    private final FrontendUserProfileRepository profilRepository;
    private final MitarbeiterRepository mitarbeiterRepository;

    /**
     * @return der verknüpfte Mitarbeiter; war schon einer verknüpft, bleibt er unverändert
     * @throws IllegalStateException wenn der Benutzer kein aktiver Administrator ist
     */
    @Transactional
    public Mitarbeiter legeFuerAdministratorAn(Long profilId) {
        FrontendUserProfile profil = profilRepository.findById(profilId)
                .orElseThrow(() -> new IllegalStateException("Benutzer nicht gefunden."));
        if (!profil.isActive() || profil.getRoleSet() == null
                || !profil.getRoleSet().contains(FrontendUserRole.ADMIN)) {
            throw new IllegalStateException("Nur Administratoren können sich selbst als Mitarbeiter anlegen.");
        }
        if (profil.getMitarbeiter() != null) {
            return profil.getMitarbeiter();
        }

        String[] name = teileName(profil.getDisplayName(), profil.getUsername());
        Mitarbeiter m = new Mitarbeiter();
        m.setVorname(name[0]);
        m.setNachname(name[1]);
        String benutzername = profil.getUsername();
        if (benutzername != null && benutzername.contains("@")) {
            m.setEmail(benutzername.trim());
        }
        m.setArt(MitarbeiterArt.MENSCH);
        m.setAktiv(true);
        m.setFuehrtZeitkonto(false);
        Mitarbeiter gespeichert = mitarbeiterRepository.save(m);

        profil.setMitarbeiter(gespeichert);
        profilRepository.save(profil);
        log.info("Mitarbeiter {} für Administrator-Benutzer {} angelegt und verknüpft", gespeichert.getId(), profil.getId());
        return gespeichert;
    }

    /** "Max Mustermann" ergibt Vorname "Max" und Nachname "Mustermann"; ein einzelnes Wort wird zum Nachnamen. */
    static String[] teileName(String anzeigename, String benutzername) {
        String basis = anzeigename != null && !anzeigename.isBlank() ? anzeigename.trim()
                : benutzername != null && !benutzername.isBlank() ? benutzername.trim() : "Administrator";
        if (basis.contains("@")) {
            basis = basis.substring(0, basis.indexOf('@'));
        }
        basis = basis.replaceAll("\\s+", " ");
        int leer = basis.indexOf(' ');
        String vorname = leer > 0 ? basis.substring(0, leer) : "";
        String nachname = leer > 0 ? basis.substring(leer + 1) : basis;
        if (basis.isBlank()) {
            nachname = "Administrator";
        }
        return new String[] { kuerze(vorname, 100), kuerze(nachname, 100) };
    }

    private static String kuerze(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
