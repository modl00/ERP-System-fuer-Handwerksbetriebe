package org.example.kalkulationsprogramm.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.Funktion;
import org.example.kalkulationsprogramm.domain.FunktionsAktion;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.MitarbeiterArt;
import org.example.kalkulationsprogramm.dto.BelegDto;
import org.example.kalkulationsprogramm.dto.Rechte.RechteUebersichtDto;
import org.example.kalkulationsprogramm.dto.Rechte.RechteUebersichtDto.Benutzer;
import org.example.kalkulationsprogramm.dto.Rechte.RechteUebersichtDto.Bereich;
import org.example.kalkulationsprogramm.dto.Rechte.RechteUebersichtDto.Feld;
import org.example.kalkulationsprogramm.dto.Rechte.RechteUebersichtDto.Warnung;
import org.example.kalkulationsprogramm.repository.AbteilungDokumentBerechtigungRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Baut die Übersicht "Wer darf was?" (Issue #87, Stufe 1). Der Dienst <b>entscheidet nichts selbst</b>: Er fragt dieselben Dienste und Regeln ab, die bei jedem
 * Zugriff gelten ({@link FunktionsRechteService}, {@link BelegService#getPermissions}, die Abteilungs-Schalter), und erklärt zu jedem Feld, woher es kommt.
 * So kann die Übersicht nicht von der Wirklichkeit abweichen, und sie ändert nichts.
 *
 * <p>Gezeigt wird die Sicht am PC. Die Handy-App nutzt für die Funktionsrechte dieselben Abteilungsrechte, nimmt Administratoren aber nicht aus.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RechteUebersichtService {

    static final String ZIEL_BENUTZER = "/benutzer";
    static final String ZIEL_ABTEILUNGEN = "/abteilung-berechtigungen";
    private static final String BELEG_TYP = "BELEG";

    private static final String GRUPPE_PROGRAMM = "Programm";
    private static final String GRUPPE_FUNKTIONEN = "Funktionen";
    private static final String GRUPPE_BUCHHALTUNG = "Buchhaltung";
    private static final String GRUPPE_DOKUMENTE = "Dokumente und Telefon";

    private final FrontendUserProfileRepository profile;
    private final FunktionsRechteService funktionsRechte;
    private final BelegService belegService;
    private final AbteilungDokumentBerechtigungRepository dokumentRechte;

    public RechteUebersichtDto uebersicht() {
        List<Bereich> bereiche = bereiche();
        List<FrontendUserProfile> aktive = profile.findAll().stream()
                .filter(FrontendUserProfile::isActive)
                .sorted(Comparator.comparing((FrontendUserProfile p) -> p.getDisplayName() == null ? "" : p.getDisplayName().toLowerCase(Locale.ROOT)))
                .toList();
        List<Benutzer> benutzer = new ArrayList<>();
        for (FrontendUserProfile p : aktive) {
            benutzer.add(benutzer(p));
        }
        return new RechteUebersichtDto(bereiche, benutzer, warnungen(aktive, benutzer));
    }

    // ------------------------------------------------------------------ Bereiche

    private List<Bereich> bereiche() {
        List<Bereich> b = new ArrayList<>();
        b.add(new Bereich("EINSTELLUNGEN", "Einstellungen und Benutzer", GRUPPE_PROGRAMM, "Firma, Einstellungen, Benutzer und Rechte ändern. Das dürfen nur Administratoren."));
        for (Funktion f : Funktion.values()) {
            b.add(new Bereich("FUNKTION_" + f.name(), f.getAnzeige(), GRUPPE_FUNKTIONEN, f.getBeschreibung()));
        }
        b.add(new Bereich("BELEGE", "Belege und Kasse", GRUPPE_BUCHHALTUNG, "Belege scannen und ansehen, Kassenbuch."));
        b.add(new Bereich("EINGANGSRECHNUNGEN_SEHEN", "Eingangsrechnungen sehen", GRUPPE_BUCHHALTUNG, "Offene Eingangsrechnungen der Lieferanten ansehen."));
        b.add(new Bereich("EINGANGSRECHNUNGEN_GENEHMIGEN", "Eingangsrechnungen genehmigen", GRUPPE_BUCHHALTUNG, "Eingangsrechnungen zur Zahlung freigeben."));
        b.add(new Bereich("MONATSABSCHLUSS", "Monate abschließen", GRUPPE_BUCHHALTUNG, "Einen Monat der Zeiterfassung abschließen oder wieder öffnen."));
        b.add(new Bereich("DOKUMENTE", "Lieferantendokumente", GRUPPE_DOKUMENTE, "Anfragen, Auftragsbestätigungen, Lieferscheine und Rechnungen der Lieferanten ansehen."));
        b.add(new Bereich("TELEFON", "Anrufe und Anrufbeantworter", GRUPPE_DOKUMENTE, "Anrufliste und Sprachnachrichten ansehen."));
        return b;
    }

    // ------------------------------------------------------------------ Benutzer

    private Benutzer benutzer(FrontendUserProfile p) {
        Mitarbeiter m = p.getMitarbeiter();
        boolean admin = istAdmin(p);
        List<Abteilung> abteilungen = m == null || m.getAbteilungen() == null ? List.of()
                : m.getAbteilungen().stream().sorted(Comparator.comparing(a -> a.getName() == null ? "" : a.getName().toLowerCase(Locale.ROOT))).toList();
        String abteilungsText = abteilungen.stream().map(Abteilung::getName).collect(Collectors.joining(", "));
        Map<String, Feld> felder = new LinkedHashMap<>();

        felder.put("EINSTELLUNGEN", admin
                ? new Feld("ALLES", "Ja", "Als Administrator.")
                : new Feld("NICHTS", "Nein", "Nur Administratoren dürfen Einstellungen, Benutzer und Rechte ändern."));

        Map<Funktion, FunktionsRechteService.Rechte> rechte = funktionsRechte.wirksam(m);
        for (Funktion f : Funktion.values()) {
            felder.put("FUNKTION_" + f.name(), funktionsFeld(rechte.get(f), admin, m, abteilungen, abteilungsText));
        }

        felder.put("BELEGE", belegeFeld(m, admin));
        felder.put("EINGANGSRECHNUNGEN_SEHEN", schalter(m, abteilungen, Abteilung::getDarfRechnungenSehen, false, false, "Eingangsrechnungen sehen"));
        felder.put("EINGANGSRECHNUNGEN_GENEHMIGEN", schalter(m, abteilungen, Abteilung::getDarfRechnungenGenehmigen, false, false, "Eingangsrechnungen genehmigen"));
        felder.put("MONATSABSCHLUSS", schalter(m, abteilungen, Abteilung::getDarfMonatAbschliessen, true, admin, "Monate abschließen"));
        felder.put("DOKUMENTE", dokumenteFeld(m, abteilungen));
        felder.put("TELEFON", schalter(m, abteilungen, Abteilung::getDarfTelefonSehen, true, admin, "Anrufe und Anrufbeantworter"));

        return new Benutzer(p.getId(), p.getDisplayName(), p.getUsername(), admin, m == null ? null : m.getId(), m == null ? null : name(m),
                abteilungen.stream().map(Abteilung::getName).toList(), felder);
    }

    private Feld funktionsFeld(FunktionsRechteService.Rechte r, boolean admin, Mitarbeiter m, List<Abteilung> abteilungen, String abteilungsText) {
        if (admin) {
            return new Feld("ALLES", "Alles", "Als Administrator am PC nicht eingeschränkt.");
        }
        List<String> erlaubt = new ArrayList<>();
        for (FunktionsAktion a : FunktionsAktion.values()) {
            if (r.erlaubt(a)) {
                erlaubt.add(a.getAnzeige());
            }
        }
        String stufe = erlaubt.size() == FunktionsAktion.values().length ? "ALLES" : erlaubt.isEmpty() ? "NICHTS" : "TEILWEISE";
        String text = "ALLES".equals(stufe) ? "Alles" : "NICHTS".equals(stufe) ? "Nein" : String.join(", ", erlaubt);
        String grund;
        if (m == null) {
            grund = "Kein Mitarbeiter mit diesem Benutzer verknüpft. Dann greifen keine Abteilungsrechte und es ist alles erlaubt.";
        } else if (abteilungen.isEmpty()) {
            grund = "Der Mitarbeiter hat keine Abteilung. Dann greifen keine Abteilungsrechte und es ist alles erlaubt.";
        } else {
            grund = "Über " + (abteilungen.size() == 1 ? "die Abteilung " : "die Abteilungen ") + abteilungsText
                    + (abteilungen.size() > 1 ? " (zählt zusammen, was eine der Abteilungen erlaubt)." : ".");
        }
        return new Feld(stufe, text, grund);
    }

    private Feld belegeFeld(Mitarbeiter m, boolean admin) {
        if (admin) {
            return new Feld("ALLES", "Sehen und scannen", "Als Administrator.");
        }
        BelegDto.PermissionResponse r = belegService.getPermissions(m);
        if (r.isDarfSehen() && r.isDarfScannen()) {
            return new Feld("ALLES", "Sehen und scannen", "Über das Dokumentenrecht „Belege und Kasse“ der Abteilung.");
        }
        if (r.isDarfSehen() || r.isDarfScannen()) {
            return new Feld("TEILWEISE", r.isDarfSehen() ? "Nur sehen" : "Nur scannen", "Über das Dokumentenrecht „Belege und Kasse“ der Abteilung.");
        }
        String grund = BelegDto.PermissionResponse.KEIN_MITARBEITER.equals(r.getGrund()) ? "Kein Mitarbeiter mit diesem Benutzer verknüpft."
                : BelegDto.PermissionResponse.KEINE_ABTEILUNG.equals(r.getGrund()) ? "Der Mitarbeiter hat keine Abteilung."
                : "Keine seiner Abteilungen hat das Dokumentenrecht „Belege und Kasse“.";
        return new Feld("NICHTS", "Nein", grund);
    }

    /**
     * Ein Schalter an der Abteilung (Ja/Nein). {@code nurMensch}: Das Recht gilt nur für aktive Mitarbeiter, die Menschen sind (Monatsabschluss, Telefon).
     * {@code admin}: nur für den Hinweis, dass Administratoren das Recht nicht automatisch haben.
     */
    private Feld schalter(Mitarbeiter m, List<Abteilung> abteilungen, java.util.function.Function<Abteilung, Boolean> flag, boolean nurMensch, boolean admin, String name) {
        String zusatz = admin ? " Administratoren bekommen dieses Recht nicht automatisch." : "";
        if (m == null) {
            return new Feld("NICHTS", "Nein", "Kein Mitarbeiter mit diesem Benutzer verknüpft." + zusatz);
        }
        if (nurMensch && (m.getArt() != MitarbeiterArt.MENSCH || !Boolean.TRUE.equals(m.getAktiv()))) {
            return new Feld("NICHTS", "Nein", "Der verknüpfte Mitarbeiter ist nicht aktiv." + zusatz);
        }
        if (abteilungen.isEmpty()) {
            return new Feld("NICHTS", "Nein", "Der Mitarbeiter hat keine Abteilung." + zusatz);
        }
        List<String> mit = abteilungen.stream().filter(a -> Boolean.TRUE.equals(flag.apply(a))).map(Abteilung::getName).toList();
        if (mit.isEmpty()) {
            return new Feld("NICHTS", "Nein", "Keine seiner Abteilungen hat das Recht „" + name + "“." + zusatz);
        }
        return new Feld("ALLES", "Ja", "Über " + (mit.size() == 1 ? "die Abteilung " : "die Abteilungen ") + String.join(", ", mit) + ".");
    }

    private Feld dokumenteFeld(Mitarbeiter m, List<Abteilung> abteilungen) {
        if (m == null) {
            return new Feld("NICHTS", "Nein", "Kein Mitarbeiter mit diesem Benutzer verknüpft.");
        }
        if (abteilungen.isEmpty()) {
            return new Feld("NICHTS", "Nein", "Der Mitarbeiter hat keine Abteilung.");
        }
        Set<String> sichtbar = Set.copyOf(dokumentRechte.findSichtbareTypenByAbteilungIds(abteilungen.stream().map(Abteilung::getId).toList()));
        List<String> typen = new ArrayList<>();
        for (LieferantDokumentTyp t : LieferantDokumentTyp.values()) {
            if (!BELEG_TYP.equals(t.name()) && sichtbar.contains(t.name())) {
                typen.add(typName(t));
            }
        }
        long moegliche = java.util.Arrays.stream(LieferantDokumentTyp.values()).filter(t -> !BELEG_TYP.equals(t.name())).count();
        if (typen.isEmpty()) {
            return new Feld("NICHTS", "Nein", "Keine seiner Abteilungen darf Lieferantendokumente ansehen.");
        }
        return new Feld(typen.size() == moegliche ? "ALLES" : "TEILWEISE", typen.size() == moegliche ? "Alle Arten" : String.join(", ", typen),
                "Über das Dokumentenrecht der Abteilung" + (abteilungen.size() > 1 ? "en" : "") + ".");
    }

    // ------------------------------------------------------------------ Warnungen

    private List<Warnung> warnungen(List<FrontendUserProfile> aktive, List<Benutzer> benutzer) {
        List<Warnung> w = new ArrayList<>();
        for (Benutzer b : benutzer) {
            if (b.administrator()) {
                continue;
            }
            if (b.mitarbeiterId() == null) {
                w.add(new Warnung("OHNE_MITARBEITER", b.anzeigename() + " ist mit keinem Mitarbeiter verknüpft. Dadurch greifen die Abteilungsrechte nicht: Lager, Wartung und Bestellungen sind uneingeschränkt, Belege, Telefon und Monatsabschluss sind gesperrt.",
                        ZIEL_BENUTZER, b.profilId()));
            } else if (b.abteilungen().isEmpty()) {
                w.add(new Warnung("OHNE_ABTEILUNG", b.anzeigename() + " (Mitarbeiter " + b.mitarbeiterName() + ") hat keine Abteilung. Dadurch ist bei Lager, Wartung und Bestellungen alles erlaubt, bei Belegen, Telefon und Monatsabschluss nichts.",
                        ZIEL_BENUTZER, b.profilId()));
            }
        }
        for (Bereich bereich : bereiche()) {
            if ("EINSTELLUNGEN".equals(bereich.schluessel()) || bereich.schluessel().startsWith("FUNKTION_")) {
                continue;
            }
            boolean jemand = benutzer.stream().anyMatch(b -> !"NICHTS".equals(b.felder().get(bereich.schluessel()).stufe()));
            if (!jemand) {
                w.add(new Warnung("NIEMAND", "Niemand darf „" + bereich.name() + "“. " + bereich.beschreibung(), ZIEL_ABTEILUNGEN, null));
            }
        }
        if (aktive.stream().noneMatch(this::istAdmin)) {
            w.add(new Warnung("KEIN_ADMIN", "Es gibt keinen aktiven Administrator. Niemand kann dann Einstellungen, Benutzer und Rechte ändern.", ZIEL_BENUTZER, null));
        }
        return w;
    }

    // ------------------------------------------------------------------ Hilfen

    private boolean istAdmin(FrontendUserProfile p) {
        return p.getRoleSet() != null && p.getRoleSet().contains(FrontendUserRole.ADMIN);
    }

    private static String name(Mitarbeiter m) {
        return ((m.getVorname() == null ? "" : m.getVorname()) + " " + (m.getNachname() == null ? "" : m.getNachname())).strip();
    }

    private static String typName(LieferantDokumentTyp t) {
        return switch (t) {
            case ANGEBOT -> "Angebote";
            case AUFTRAGSBESTAETIGUNG -> "Auftragsbestätigungen";
            case LIEFERSCHEIN -> "Lieferscheine";
            case RECHNUNG -> "Rechnungen";
            case GUTSCHRIFT -> "Gutschriften";
            default -> t.name().charAt(0) + t.name().substring(1).toLowerCase(Locale.ROOT);
        };
    }
}
