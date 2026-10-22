package org.example.kalkulationsprogramm.service.planung;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.Abwesenheit;
import org.example.kalkulationsprogramm.domain.Feiertag;
import org.example.kalkulationsprogramm.domain.wartung.WartungTuer;
import org.example.kalkulationsprogramm.repository.FeiertagRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungTuerRepository;
import org.example.kalkulationsprogramm.service.FeiertagService;
import org.example.kalkulationsprogramm.domain.KalenderEintrag;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.planung.PlanRessource;
import org.example.kalkulationsprogramm.domain.planung.PlanVorgang;
import org.example.kalkulationsprogramm.repository.AbwesenheitRepository;
import org.example.kalkulationsprogramm.repository.KalenderEintragRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.planung.PlanRessourceRepository;
import org.example.kalkulationsprogramm.repository.planung.PlanVorgangRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Plantafel (#323), Stufe 2: Wer ist wann womit belegt? Zeilen sind Mitarbeiter und Ressourcen, dazu eine Zeile für alles ohne Zuweisung. Zusammengeführt werden die
 * Vorgänge der Plantafel, die Kalendertermine und die Abwesenheiten (Urlaub, Krank …). Nur Anzeige: Das Programm ändert hier nichts. Doppelte Belegung meldet es als Hinweis.
 */
@Service
@RequiredArgsConstructor
public class PlanBelegung {

    /** Zwei Jahre plus Reserve: Die Ansicht zeigt Zeitstufen bis zu zwei Jahren. */
    static final int MAX_TAGE = 800;
    /** Nur bei diesen Arten ist eine zweite Belegung zur gleichen Zeit ein Hinweis: Eine Werkstatt oder ein Büro nimmt mehrere Aufträge zugleich auf. */
    private static final Set<String> EINZELNE_RESSOURCEN = Set.of("FAHRZEUG", "MASCHINE");

    private final PlanVorgangRepository vorgaenge;
    private final PlanRessourceRepository ressourcen;
    private final MitarbeiterRepository mitarbeiter;
    private final KalenderEintragRepository kalender;
    private final AbwesenheitRepository abwesenheiten;
    private final WartungTuerRepository wartungen;
    private final FeiertagRepository feiertage;
    private final FeiertagService feiertagService;
    private final PlanungsEinstellungen planungsEinstellungen;
    private final PlanungsIst ist;

    /** {@code quelle}: VORGANG, TERMIN, ABWESENHEIT oder WARTUNG (fällige Wartung). Zeiten nur bei Terminen mit Uhrzeit. {@code hinweis} gesetzt = doppelt belegt oder Abwesenheit. */
    public record Eintrag(String id, String quelle, String titel, String untertitel, LocalDate von, LocalDate bis, LocalTime vonZeit, LocalTime bisZeit, String farbe, Long projektId,
            String prioritaet, boolean fest, String status, String hinweis, BigDecimal aufwandStunden, BigDecimal istStunden) {

        /** Ohne Fortschritt (alles außer Arbeitsvorgängen). */
        public Eintrag(String id, String quelle, String titel, String untertitel, LocalDate von, LocalDate bis, LocalTime vonZeit, LocalTime bisZeit, String farbe, Long projektId,
                String prioritaet, boolean fest, String status, String hinweis) {
            this(id, quelle, titel, untertitel, von, bis, vonZeit, bisZeit, farbe, projektId, prioritaet, fest, status, hinweis, null, null);
        }

        Eintrag mitHinweis(String neuerHinweis) {
            return new Eintrag(id, quelle, titel, untertitel, von, bis, vonZeit, bisZeit, farbe, projektId, prioritaet, fest, status, neuerHinweis, aufwandStunden, istStunden);
        }
    }

    /** {@code gruppe}: MITARBEITER, RESSOURCE oder ALLGEMEIN. */
    public record Zeile(String schluessel, String gruppe, String name, List<Eintrag> eintraege) { }

    /** Ein Tag, an dem nicht gearbeitet wird: {@code art} FEIERTAG oder BETRIEBSURLAUB. */
    public record FreierTag(LocalDate datum, String art, String name) { }

    public record Antwort(LocalDate von, LocalDate bis, List<Zeile> zeilen, List<FreierTag> freieTage) {
        public Antwort(LocalDate von, LocalDate bis, List<Zeile> zeilen) {
            this(von, bis, zeilen, List.of());
        }
    }

    @Transactional(readOnly = true)
    public Antwort belegung(LocalDate von, LocalDate bis) {
        if (von == null || bis == null || bis.isBefore(von)) {
            throw new IllegalArgumentException("Bitte einen Zeitraum von bis angeben.");
        }
        if (von.plusDays(MAX_TAGE).isBefore(bis)) {
            throw new IllegalArgumentException("Der Zeitraum darf höchstens " + MAX_TAGE + " Tage lang sein.");
        }
        Map<String, Zeile> zeilen = new LinkedHashMap<>();
        zeilen.put("A", new Zeile("A", "ALLGEMEIN", "Ohne Zuweisung", new ArrayList<>()));
        List<Mitarbeiter> personen = mitarbeiter.findAll().stream().filter(m -> !Boolean.FALSE.equals(m.getAktiv()))
                .sorted(Comparator.comparing(PlanBelegung::name, String.CASE_INSENSITIVE_ORDER)).toList();
        for (Mitarbeiter m : personen) {
            zeilen.put("M" + m.getId(), new Zeile("M" + m.getId(), "MITARBEITER", name(m), new ArrayList<>()));
        }
        Map<Long, PlanRessource> ressourcenJeId = new LinkedHashMap<>();
        for (PlanRessource r : ressourcen.findAllByOrderBySortierungAscNameAsc()) {
            ressourcenJeId.put(r.getId(), r);
            if (r.isAktiv()) {
                zeilen.put("R" + r.getId(), new Zeile("R" + r.getId(), "RESSOURCE", r.getName(), new ArrayList<>()));
            }
        }

        // Abwesenheiten zuerst: aufeinanderfolgende Tage derselben Art zu einem Eintrag zusammenfassen
        Map<String, Eintrag> abwesend = new LinkedHashMap<>();
        for (Abwesenheit a : abwesenheiten.findAllByDatumBetween(von, bis)) {
            Zeile z = zeilen.get("M" + a.getMitarbeiter().getId());
            if (z == null || a.getDatum() == null) {
                continue;
            }
            String art = abwesenheitsName(a);
            Eintrag letzter = z.eintraege().stream().filter(e -> "ABWESENHEIT".equals(e.quelle()) && e.titel().equals(art) && e.bis().plusDays(1).equals(a.getDatum())).reduce((x, y) -> y).orElse(null);
            if (letzter != null) {
                z.eintraege().set(z.eintraege().indexOf(letzter), new Eintrag(letzter.id(), letzter.quelle(), letzter.titel(), null, letzter.von(), a.getDatum(), null, null, letzter.farbe(), null, null, false, null, null));
            } else {
                z.eintraege().add(new Eintrag("A" + a.getId(), "ABWESENHEIT", art, null, a.getDatum(), a.getDatum(), null, null, "#94a3b8", null, null, false, null, null));
            }
        }

        List<PlanVorgang> imZeitraum = vorgaenge.beruehrenZeitraum(von, bis);
        Map<Long, BigDecimal> gestempelt = ist.jeVorgang(imZeitraum); // Fortschritt: gestempelte Stunden je Vorgang
        for (PlanVorgang v : imZeitraum) {
            Projekt p = v.getProjekt();
            String auftrag = (p.getBauvorhaben() != null && !p.getBauvorhaben().isBlank() ? p.getBauvorhaben() : "Auftrag " + p.getId())
                    + (p.getAuftragsnummer() != null ? " (" + p.getAuftragsnummer() + ")" : "");
            Eintrag e = new Eintrag("V" + v.getId(), "VORGANG", v.getBezeichnung(), auftrag, v.getStartDatum(), v.getEndeDatum(), null, null, v.getTyp().getFarbe(), p.getId(),
                    v.getPrioritaet(), v.getFesterStart() != null, v.getStatus(), null, v.getTyp().isWartet() ? null : v.getAufwandStunden(), gestempelt.get(v.getId()));
            boolean zugewiesen = false;
            for (Long id : v.getMitarbeiterIds()) {
                Zeile z = zeilen.get("M" + id);
                if (z != null) {
                    z.eintraege().add(e);
                    zugewiesen = true;
                }
            }
            if (v.getRessourceId() != null && zeilen.containsKey("R" + v.getRessourceId())) {
                zeilen.get("R" + v.getRessourceId()).eintraege().add(e);
                zugewiesen = true;
            }
            if (!zugewiesen) {
                zeilen.get("A").eintraege().add(e);
            }
        }

        for (KalenderEintrag k : kalender.findeUeberlappende(von, bis)) {
            boolean mehrtaegig = k.getDatumBis() != null && k.getDatumBis().isAfter(k.getDatum());
            LocalTime vonZeit = k.isGanztaegig() || mehrtaegig ? null : k.getStartZeit();
            LocalTime bisZeit = k.isGanztaegig() || mehrtaegig ? null : k.getEndeZeit();
            String unter = k.getProjekt() != null && k.getProjekt().getBauvorhaben() != null ? k.getProjekt().getBauvorhaben() : null;
            Eintrag e = new Eintrag("T" + k.getId(), "TERMIN", k.getTitel(), unter, k.getDatum(), mehrtaegig ? k.getDatumBis() : k.getDatum(), vonZeit, bisZeit, k.getFarbe() == null || k.getFarbe().isBlank() ? "#64748b" : k.getFarbe(),
                    k.getProjekt() == null ? null : k.getProjekt().getId(), null, false, null, null);
            boolean zugewiesen = false;
            for (Mitarbeiter m : k.getTeilnehmer()) {
                Zeile z = zeilen.get("M" + m.getId());
                if (z != null) {
                    z.eintraege().add(e);
                    zugewiesen = true;
                }
            }
            if (!zugewiesen) {
                zeilen.get("A").eintraege().add(e);
            }
        }

        // Fällige Wartungen (Anlagen mit Fälligkeit im Zeitraum), je Objekt zusammengefasst, in einer eigenen Zeile der Gruppe Allgemein
        List<Eintrag> faellig = new ArrayList<>();
        Map<Long, List<WartungTuer>> jeObjekt = new LinkedHashMap<>();
        for (WartungTuer t : wartungen.findeFaelligImZeitraum(von, bis)) {
            jeObjekt.computeIfAbsent(t.getObjekt().getId(), x -> new ArrayList<>()).add(t);
        }
        for (List<WartungTuer> anlagen : jeObjekt.values()) {
            WartungTuer erste = anlagen.get(0); // die Liste ist nach Fälligkeit sortiert
            LocalDate ersteFaellig = erste.getNaechsteFaelligkeit();
            LocalDate letzteFaellig = anlagen.get(anlagen.size() - 1).getNaechsteFaelligkeit();
            String objekt = erste.getObjekt().getBezeichnung() == null || erste.getObjekt().getBezeichnung().isBlank() ? "Objekt" : erste.getObjekt().getBezeichnung();
            if (anlagen.size() == 1) { // eine einzelne Anlage: Klick öffnet sie
                String name = erste.getBezeichnung() == null || erste.getBezeichnung().isBlank() ? "Wartung fällig" : erste.getBezeichnung() + " (Wartung)";
                faellig.add(new Eintrag("W" + erste.getId(), "WARTUNG", name, objekt, ersteFaellig, ersteFaellig, null, null, "#0d9488", null, null, false, null, null));
            } else { // mehrere Anlagen eines Objekts: ein Eintrag für das Objekt, Klick öffnet die Wartungsübersicht
                String namen = anlagen.stream().limit(3).map(t -> t.getBezeichnung() == null ? "Anlage" : t.getBezeichnung()).collect(java.util.stream.Collectors.joining(", ")) + (anlagen.size() > 3 ? " …" : "");
                faellig.add(new Eintrag("O" + erste.getObjekt().getId(), "WARTUNG", objekt + ": " + anlagen.size() + " Anlagen fällig", namen, ersteFaellig, letzteFaellig, null, null, "#0d9488", null, null, false, null, null));
            }
        }
        if (!faellig.isEmpty()) {
            zeilen.put("W", new Zeile("W", "ALLGEMEIN", "Fällige Wartungen", faellig));
        }

        List<Zeile> ergebnis = new ArrayList<>();
        for (Zeile z : zeilen.values()) {
            boolean pruefen = z.gruppe().equals("MITARBEITER") || z.gruppe().equals("RESSOURCE") && EINZELNE_RESSOURCEN.contains(ressourceArt(z, ressourcenJeId));
            List<Eintrag> liste = pruefen ? mitHinweisen(z.eintraege()) : z.eintraege();
            liste.sort(Comparator.comparing(Eintrag::von).thenComparing(Eintrag::id));
            if (!liste.isEmpty() || !z.gruppe().equals("ALLGEMEIN")) {
                ergebnis.add(new Zeile(z.schluessel(), z.gruppe(), z.name(), liste));
            }
        }
        return new Antwort(von, bis, ergebnis, freieTage(von, bis));
    }

    /** Feiertage (Bundesland aus der Einstellung) und Betriebsurlaub im Zeitraum; an diesen Tagen arbeitet niemand. */
    private List<FreierTag> freieTage(LocalDate von, LocalDate bis) {
        List<FreierTag> tage = new ArrayList<>();
        String bundesland = feiertagService.aktuellesBundesland();
        for (int jahr = von.getYear(); jahr <= bis.getYear(); jahr++) {
            for (Feiertag f : feiertage.findByJahrAndBundesland(jahr, bundesland)) {
                if (!f.isHalbTag() && !f.getDatum().isBefore(von) && !f.getDatum().isAfter(bis)) {
                    tage.add(new FreierTag(f.getDatum(), "FEIERTAG", f.getBezeichnung()));
                }
            }
        }
        for (PlanungsEinstellungen.Urlaub u : planungsEinstellungen.lesen().betriebsurlaub()) {
            for (LocalDate d = u.von().isBefore(von) ? von : u.von(); !d.isAfter(u.bis()) && !d.isAfter(bis); d = d.plusDays(1)) {
                tage.add(new FreierTag(d, "BETRIEBSURLAUB", u.bezeichnung() == null || u.bezeichnung().isBlank() ? "Betriebsurlaub" : u.bezeichnung()));
            }
        }
        tage.sort(Comparator.comparing(FreierTag::datum));
        return tage;
    }

    /** Hinweis an Vorgängen, die sich in einer Zeile überschneiden (nicht fertige) oder in eine Abwesenheit fallen. Termine melden nichts: Ein Termin neben einem Vorgang ist normal. */
    static List<Eintrag> mitHinweisen(List<Eintrag> eintraege) {
        List<Eintrag> neu = new ArrayList<>();
        for (Eintrag e : eintraege) {
            String hinweis = null;
            if ("VORGANG".equals(e.quelle()) && !"FERTIG".equals(e.status())) {
                for (Eintrag a : eintraege) {
                    if (a == e || !ueberschneidet(e, a)) {
                        continue;
                    }
                    if ("ABWESENHEIT".equals(a.quelle())) {
                        hinweis = "Eingeplant, obwohl: " + a.titel();
                        break;
                    }
                    if ("VORGANG".equals(a.quelle()) && !"FERTIG".equals(a.status()) && !e.id().equals(a.id())) {
                        hinweis = "Doppelt belegt mit „" + a.titel() + "“ (" + a.untertitel() + ")";
                        break;
                    }
                }
            }
            neu.add(hinweis == null ? e : e.mitHinweis(hinweis));
        }
        return neu;
    }

    private static boolean ueberschneidet(Eintrag a, Eintrag b) {
        return !a.bis().isBefore(b.von()) && !b.bis().isBefore(a.von());
    }

    private static String ressourceArt(Zeile z, Map<Long, PlanRessource> je) {
        PlanRessource r = je.get(Long.valueOf(z.schluessel().substring(1)));
        return r == null ? "" : r.getArt();
    }

    private static String name(Mitarbeiter m) {
        return ((m.getVorname() == null ? "" : m.getVorname()) + " " + (m.getNachname() == null ? "" : m.getNachname())).strip();
    }

    private static String abwesenheitsName(Abwesenheit a) {
        if (a.getTyp() == null) {
            return "Abwesend";
        }
        return switch (a.getTyp()) {
            case URLAUB -> "Urlaub";
            case KRANKHEIT -> "Krank";
            case FORTBILDUNG -> "Fortbildung";
            case ZEITAUSGLEICH -> "Zeitausgleich";
        };
    }
}
