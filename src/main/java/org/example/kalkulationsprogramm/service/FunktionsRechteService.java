package org.example.kalkulationsprogramm.service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.AbteilungFunktionsrecht;
import org.example.kalkulationsprogramm.domain.Funktion;
import org.example.kalkulationsprogramm.domain.FunktionsAktion;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.AbteilungFunktionsrechtRepository;
import org.example.kalkulationsprogramm.repository.AbteilungRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/**
 * Rechte an den Funktionen des Programms ({@link Funktion}), eingestellt je Abteilung und gleich für das Programm am PC und die Handy-App.
 *
 * <p>Regeln: Ohne gespeicherte Zeile gilt "erlaubt" (so ändert sich nach dem Update nichts). Ein Mitarbeiter in mehreren Abteilungen hat die Summe
 * der Rechte seiner Abteilungen. Ohne Abteilung (oder ohne Mitarbeiter am Benutzer) gilt "erlaubt". Anlegen, Ändern und Löschen setzen Ansehen
 * voraus. Administratoren am PC sind nicht eingeschränkt (das prüft der Aufrufer), damit sich niemand aussperrt.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FunktionsRechteService {

    /** Die vier Rechte einer Abteilung oder eines Mitarbeiters an einer Funktion. */
    public record Rechte(boolean ansehen, boolean anlegen, boolean aendern, boolean loeschen) {

        public static final Rechte ALLES = new Rechte(true, true, true, true);

        /** Anlegen, Ändern und Löschen gelten nur zusammen mit Ansehen. */
        Rechte bereinigt() {
            return ansehen ? this : new Rechte(false, false, false, false);
        }

        public boolean erlaubt(FunktionsAktion aktion) {
            return switch (aktion) {
                case ANSEHEN -> ansehen;
                case ANLEGEN -> anlegen;
                case AENDERN -> aendern;
                case LOESCHEN -> loeschen;
            };
        }

        Rechte oder(Rechte andere) {
            return new Rechte(ansehen || andere.ansehen, anlegen || andere.anlegen, aendern || andere.aendern, loeschen || andere.loeschen);
        }
    }

    /** Alle Funktionen mit ihren Rechten für eine Abteilung (Standard: alles erlaubt). */
    public record AbteilungRechte(Long abteilungId, String abteilungName, Map<Funktion, Rechte> rechte) { }

    private final AbteilungRepository abteilungRepository;
    private final AbteilungFunktionsrechtRepository rechteRepository;
    private final FrontendUserProfileRepository profileRepository;
    private final MitarbeiterRepository mitarbeiterRepository;

    /** Alle Abteilungen mit ihren Rechten, für die Rechteverwaltung. */
    public List<AbteilungRechte> alle() {
        List<AbteilungRechte> ergebnis = new ArrayList<>();
        for (Abteilung a : abteilungRepository.findAll()) {
            ergebnis.add(fuer(a, rechteRepository.findByAbteilungId(a.getId())));
        }
        return ergebnis;
    }

    /**
     * Speichert die Rechte einer Abteilung. {@code eingabe} nennt die Funktionen beim Namen; unbekannte Namen werden abgelehnt, nicht genannte
     * Funktionen bleiben unverändert. Ohne Ansehen werden die übrigen Rechte der Funktion gespeichert als "nein".
     */
    @Transactional
    public AbteilungRechte speichern(Long abteilungId, Map<String, Rechte> eingabe) {
        Abteilung abteilung = abteilungRepository.findById(abteilungId == null ? -1L : abteilungId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Diese Abteilung gibt es nicht."));
        if (eingabe == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte die Rechte angeben.");
        }
        Map<Funktion, Rechte> neu = new EnumMap<>(Funktion.class);
        for (Map.Entry<String, Rechte> e : eingabe.entrySet()) {
            Funktion f = Funktion.ausName(e.getKey())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Diese Funktion gibt es nicht."));
            if (e.getValue() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Für " + f.getAnzeige() + " fehlen die Rechte.");
            }
            neu.put(f, e.getValue().bereinigt());
        }
        Map<String, AbteilungFunktionsrecht> vorhanden = new HashMap<>();
        for (AbteilungFunktionsrecht r : rechteRepository.findByAbteilungId(abteilung.getId())) {
            vorhanden.put(r.getFunktion(), r);
        }
        for (Map.Entry<Funktion, Rechte> e : neu.entrySet()) {
            AbteilungFunktionsrecht zeile = vorhanden.get(e.getKey().name());
            if (zeile == null) {
                zeile = new AbteilungFunktionsrecht();
                zeile.setAbteilung(abteilung);
                zeile.setFunktion(e.getKey().name());
                vorhanden.put(e.getKey().name(), zeile);
            }
            zeile.setAnsehen(e.getValue().ansehen());
            zeile.setAnlegen(e.getValue().anlegen());
            zeile.setAendern(e.getValue().aendern());
            zeile.setLoeschen(e.getValue().loeschen());
            rechteRepository.save(zeile);
        }
        return fuer(abteilung, new ArrayList<>(vorhanden.values()));
    }

    /** Die Rechte eines Mitarbeiters: Summe seiner Abteilungen, ohne Abteilung alles erlaubt. */
    public Map<Funktion, Rechte> wirksam(Mitarbeiter mitarbeiter) {
        Map<Funktion, Rechte> ergebnis = new EnumMap<>(Funktion.class);
        Set<Abteilung> abteilungen = mitarbeiter == null ? Set.of() : mitarbeiter.getAbteilungen();
        if (abteilungen.isEmpty()) {
            for (Funktion f : Funktion.values()) {
                ergebnis.put(f, Rechte.ALLES);
            }
            return ergebnis;
        }
        List<Long> ids = abteilungen.stream().map(Abteilung::getId).toList();
        Map<Long, List<AbteilungFunktionsrecht>> proAbteilung = new HashMap<>();
        for (AbteilungFunktionsrecht r : rechteRepository.findByAbteilungIdIn(ids)) {
            proAbteilung.computeIfAbsent(r.getAbteilung().getId(), k -> new ArrayList<>()).add(r);
        }
        for (Funktion f : Funktion.values()) {
            Rechte summe = new Rechte(false, false, false, false);
            for (Abteilung a : abteilungen) {
                Rechte einzel = proAbteilung.getOrDefault(a.getId(), List.of()).stream()
                        .filter(r -> r.getFunktion().equals(f.name())).findFirst().map(FunktionsRechteService::rechte).orElse(Rechte.ALLES);
                summe = summe.oder(einzel);
            }
            ergebnis.put(f, summe);
        }
        return ergebnis;
    }

    public boolean darf(Mitarbeiter mitarbeiter, Funktion funktion, FunktionsAktion aktion) {
        return wirksam(mitarbeiter).get(funktion).erlaubt(aktion);
    }

    /** Wie {@link #darf(Mitarbeiter, Funktion, FunktionsAktion)}, für einen angemeldeten Benutzer am PC (über sein Profil). */
    public boolean darfProfil(Long profilId, Funktion funktion, FunktionsAktion aktion) {
        return wirksamFuerProfil(profilId).get(funktion).erlaubt(aktion);
    }

    /** Die Rechte eines angemeldeten Benutzers am PC. Ohne Profil oder ohne verknüpften Mitarbeiter gilt alles erlaubt. */
    public Map<Funktion, Rechte> wirksamFuerProfil(Long profilId) {
        Mitarbeiter m = profilId == null ? null : profileRepository.findById(profilId).map(p -> p.getMitarbeiter()).orElse(null);
        return wirksam(m);
    }

    /** Die Rechte des aktiven Mitarbeiters zum Zugangslink der Handy-App; leer bei unbekanntem oder inaktivem Zugang. */
    public java.util.Optional<Map<Funktion, Rechte>> wirksamFuerToken(String token) {
        if (token == null || token.isBlank()) {
            return java.util.Optional.empty();
        }
        return mitarbeiterRepository.findByLoginTokenAndAktivTrue(token.strip()).map(this::wirksam);
    }

    /**
     * Prüft eine Aktion für den Zugangslink der Handy-App und gibt den Mitarbeiter zurück: 401 bei unbekanntem Zugang, 403 mit Klartext ohne Recht.
     * Das ist der Weg für alle Endpunkte der App, damit sie dieselben Rechte wie das Programm am PC beachten.
     */
    public Mitarbeiter verlangeFuerToken(String token, Funktion funktion, FunktionsAktion aktion) {
        Mitarbeiter m = (token == null || token.isBlank()) ? null : mitarbeiterRepository.findByLoginTokenAndAktivTrue(token.strip()).orElse(null);
        if (m == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Der Zugang ist nicht gültig. Bitte neu anmelden.");
        }
        verlange(m, funktion, aktion);
        return m;
    }

    /** Wirft 403 mit Klartext, wenn der Mitarbeiter die Aktion nicht darf. Für Endpunkte der Handy-App (Anmeldung über den Zugangslink). */
    public void verlange(Mitarbeiter mitarbeiter, Funktion funktion, FunktionsAktion aktion) {
        if (!darf(mitarbeiter, funktion, aktion)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, fehlermeldung(funktion, aktion));
        }
    }

    /** Die Meldung für eine verweigerte Aktion, zum Beispiel "Dafür fehlt die Berechtigung: Lager, Anlegen." */
    public static String fehlermeldung(Funktion funktion, FunktionsAktion aktion) {
        return "Dafür fehlt die Berechtigung: " + funktion.getAnzeige() + ", " + aktion.getAnzeige() + ". Bitte in der Rechteverwaltung prüfen.";
    }

    private AbteilungRechte fuer(Abteilung a, List<AbteilungFunktionsrecht> zeilen) {
        Map<Funktion, Rechte> rechte = new EnumMap<>(Funktion.class);
        for (Funktion f : Funktion.values()) {
            rechte.put(f, zeilen.stream().filter(z -> z.getFunktion().equals(f.name())).findFirst().map(FunktionsRechteService::rechte).orElse(Rechte.ALLES));
        }
        return new AbteilungRechte(a.getId(), a.getName(), rechte);
    }

    private static Rechte rechte(AbteilungFunktionsrecht z) {
        return new Rechte(z.isAnsehen(), z.erlaubt(FunktionsAktion.ANLEGEN), z.erlaubt(FunktionsAktion.AENDERN), z.erlaubt(FunktionsAktion.LOESCHEN));
    }
}
