package org.example.kalkulationsprogramm.service.demo;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.Fremdleistung;
import org.example.kalkulationsprogramm.domain.KalenderEintrag;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Leistung;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.repository.AbwesenheitRepository;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.repository.FremdleistungRepository;
import org.example.kalkulationsprogramm.repository.KalenderEintragRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.LeistungRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.repository.ZeitkontoVersionRepository;
import org.example.kalkulationsprogramm.service.planung.PlanungsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Entfernt die früheren <b>Demo-Daten</b> (erfundene Mitarbeiter, Kunden, Lieferanten, Leistungen, Aufträge, Termine und Abwesenheiten) wieder. Das Laden der Demo-Daten gibt es nicht mehr;
 * als Ausgangspunkt dienen die fertigen Metallbau-Leistungen (Leistungen → Vorlagen übernehmen).
 * <p>Entfernt wird <b>nur, was die Demo-Daten selbst angelegt haben</b>, erkennbar an ihren Marken: Kunden „DEMO-K…“, Aufträge „DEMO-…“, Leistungen „… (Demo)“, Mitarbeiter mit der Adresse
 * „…@demo.invalid“, Lieferanten „… Demo GmbH“ mit ihren Fremdleistungen und Termine mit der Beschreibung „Demo-Termin“. Abteilungen und Arbeitsgänge bleiben stehen, weil sie auch
 * schon vorher da gewesen sein können. Klappt etwas nicht (zum Beispiel weil an einem Demo-Auftrag echte Buchungen hängen), wird nichts entfernt.</p>
 */
@Service
@RequiredArgsConstructor
public class DemoDatenService {

    static final String KUNDEN_MARKE = "DEMO-K";
    static final String AUFTRAGS_MARKE = "DEMO-";
    static final String LEISTUNGS_MARKE = "(Demo)";
    static final String MAIL_MARKE = "@demo.invalid";
    static final String TERMIN_MARKE = "Demo-Termin";
    static final Set<String> LIEFERANTEN = Set.of("Stahlhandel Demo GmbH", "Feuerverzinkerei Demo GmbH", "Pulverbeschichtung Demo GmbH");

    private final KundeRepository kunden;
    private final ProjektRepository projekte;
    private final AusgangsGeschaeftsDokumentRepository dokumente;
    private final LeistungRepository leistungen;
    private final FremdleistungRepository fremdleistungen;
    private final LieferantenRepository lieferanten;
    private final MitarbeiterRepository mitarbeiter;
    private final AbwesenheitRepository abwesenheiten;
    private final ZeitkontoVersionRepository zeitkonten;
    private final KalenderEintragRepository kalender;
    private final PlanungsService planung;

    /** Was entfernt wurde, zum Anzeigen. */
    public record Ergebnis(int mitarbeiter, int kunden, int lieferanten, int leistungen, int auftraege, int termine) { }

    private List<Kunde> demoKunden() {
        return kunden.findAll().stream().filter(k -> k.getKundennummer() != null && k.getKundennummer().toUpperCase().startsWith(KUNDEN_MARKE)).toList();
    }

    private List<Projekt> demoProjekte() {
        return projekte.findAll().stream().filter(p -> p.getAuftragsnummer() != null && p.getAuftragsnummer().startsWith(AUFTRAGS_MARKE)).toList();
    }

    private List<Leistung> demoLeistungen() {
        return leistungen.findAll().stream().filter(l -> l.getBezeichnung() != null && l.getBezeichnung().endsWith(LEISTUNGS_MARKE)).toList();
    }

    private List<Mitarbeiter> demoMitarbeiter() {
        return mitarbeiter.findAll().stream().filter(m -> m.getEmail() != null && m.getEmail().endsWith(MAIL_MARKE)).toList();
    }

    private List<Lieferanten> demoLieferanten() {
        return lieferanten.findAll().stream().filter(l -> LIEFERANTEN.contains(l.getLieferantenname())).toList();
    }

    private List<KalenderEintrag> demoTermine() {
        return kalender.findAll().stream().filter(k -> TERMIN_MARKE.equals(k.getBeschreibung())).toList();
    }

    /** Gibt es noch Demo-Daten, die sich entfernen lassen? */
    @Transactional(readOnly = true)
    public boolean vorhanden() {
        return !demoKunden().isEmpty() || !demoProjekte().isEmpty() || !demoLeistungen().isEmpty() || !demoMitarbeiter().isEmpty() || !demoLieferanten().isEmpty() || !demoTermine().isEmpty();
    }

    /** Entfernt alle Demo-Daten (nur diese, siehe oben). Alles oder nichts. */
    @Transactional
    public Ergebnis entfernen() {
        List<KalenderEintrag> termine = demoTermine();
        kalender.deleteAll(termine);
        kalender.flush();

        List<Projekt> auftraege = demoProjekte();
        for (Projekt p : auftraege) {
            try {
                planung.auftragEntfernen(p.getId()); // Vorgänge und Plan des Auftrags
            } catch (IllegalArgumentException keinPlan) {
                // der Auftrag stand nicht in der Plantafel
            }
            dokumente.deleteAll(dokumente.findByProjektIdOrderByDatumDesc(p.getId()));
        }
        dokumente.flush();
        projekte.deleteAll(auftraege);
        projekte.flush();

        List<Leistung> leistungsListe = demoLeistungen();
        leistungen.deleteAll(leistungsListe);
        leistungen.flush();

        List<Lieferanten> lieferantenListe = demoLieferanten();
        for (Lieferanten l : lieferantenListe) {
            List<Fremdleistung> fl = fremdleistungen.findByLieferantIdOrderByBezeichnungAscIdAsc(l.getId());
            fremdleistungen.deleteAll(fl);
        }
        fremdleistungen.flush();
        lieferanten.deleteAll(lieferantenListe);
        lieferanten.flush();

        List<Mitarbeiter> personen = demoMitarbeiter();
        for (Mitarbeiter m : personen) {
            abwesenheiten.deleteAll(abwesenheiten.findByMitarbeiterIdOrderByDatumDesc(m.getId()));
            zeitkonten.deleteAll(zeitkonten.findImZeitraum(m.getId(), LocalDate.of(1900, 1, 1), LocalDate.of(2999, 12, 31)));
        }
        abwesenheiten.flush();
        zeitkonten.flush();
        mitarbeiter.deleteAll(new ArrayList<>(personen));
        mitarbeiter.flush();

        List<Kunde> kundenListe = demoKunden();
        kunden.deleteAll(kundenListe);
        kunden.flush();
        return new Ergebnis(personen.size(), kundenListe.size(), lieferantenListe.size(), leistungsListe.size(), auftraege.size(), termine.size());
    }
}
