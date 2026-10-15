package org.example.kalkulationsprogramm.service.planung;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.domain.Feiertag;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.planung.PlanAbhaengigkeit;
import org.example.kalkulationsprogramm.domain.planung.PlanProjekt;
import org.example.kalkulationsprogramm.domain.planung.PlanVorgang;
import org.example.kalkulationsprogramm.domain.planung.PlanZuteilung;
import org.example.kalkulationsprogramm.domain.planung.PlanVorgangstyp;
import org.example.kalkulationsprogramm.repository.AbteilungRepository;
import org.example.kalkulationsprogramm.repository.FeiertagRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentProjektAnteilRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.SystemSettingRepository;
import org.example.kalkulationsprogramm.service.FeiertagService;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.repository.planung.PlanAbhaengigkeitRepository;
import org.example.kalkulationsprogramm.repository.planung.PlanProjektRepository;
import org.example.kalkulationsprogramm.repository.planung.PlanVorgangRepository;
import org.example.kalkulationsprogramm.repository.planung.PlanVorgangstypRepository;
import org.example.kalkulationsprogramm.repository.planung.PlanZuteilungRepository;
import org.example.kalkulationsprogramm.service.planung.PlanungsRechner.Kante;
import org.example.kalkulationsprogramm.service.planung.PlanungsRechner.Knoten;
import org.example.kalkulationsprogramm.service.planung.PlanungsRechner.Zeitraum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import org.springframework.scheduling.annotation.Scheduled;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Die Plantafel (#184, Stufe 1): Vorgänge je Auftrag mit Abhängigkeiten. Nach <b>jeder</b> Änderung rechnet das Programm Start und Ende des Auftrags neu
 * ({@link PlanungsRechner}); das Ende des letzten Vorgangs ist die Lieferzeit. Das Programm schlägt nur vor: Feste Starts verschiebt es nie, sondern meldet einen Konflikt.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanungsService {

    private static final Pattern FARBE = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final Set<String> STATUS = Set.of(PlanVorgang.STATUS_GEPLANT, PlanVorgang.STATUS_LAEUFT, PlanVorgang.STATUS_FERTIG);
    private static final int MAX_DAUER = 730;

    private final PlanVorgangstypRepository typen;
    private final PlanVorgangRepository vorgaenge;
    private final PlanAbhaengigkeitRepository abhaengigkeiten;
    private final PlanProjektRepository termine;
    private final ProjektRepository projekte;
    private final FeiertagRepository feiertage;
    private final SystemSettingRepository einstellungenRepository;
    private final AbteilungRepository abteilungen;
    private final AngebotsZeiten angebotsZeiten;
    private final PlanungsEinstellungen einstellungen;
    private final PlanZuteilungRepository zuteilungen;
    private final PlanKapazitaetQuelle kapazitaet;
    private final PlanungsHistorie historie;
    private final PlanungsIst ist;
    private final LieferantenRepository lieferanten;
    private final LieferantDokumentProjektAnteilRepository lieferantenAnteile;

    // ───────────────────────────── Antworten ─────────────────────────────

    public record AbhaengigkeitDto(Long vorgaengerId, int versatzTage) { }

    public record VorgangDto(Long id, Long typId, String typName, String farbe, String bezeichnung, int dauerTage, String dauerHerkunft, LocalDate festerStart,
            LocalDate start, LocalDate ende, boolean konflikt, String status, String notiz, List<AbhaengigkeitDto> vorgaenger, Long ressourceId, String prioritaet,
            List<Long> mitarbeiterIds, BigDecimal aufwandStunden, String aufwandHerkunft, LocalDate fruehestensAb, Integer rang, String hinweis, boolean wartet, boolean buero,
            boolean ohneZuweisungGeschaetzt, int stand, Long lieferantId, String lieferantName, LocalDate bestelltAm, LocalDate lieferterminLieferant, Long fremdleistungId, BigDecimal menge,
            String mengenEinheit, BigDecimal istStunden) {

        public VorgangDto(Long id, Long typId, String typName, String farbe, String bezeichnung, int dauerTage, String dauerHerkunft, LocalDate festerStart, LocalDate start, LocalDate ende,
                boolean konflikt, String status, String notiz, List<AbhaengigkeitDto> vorgaenger, Long ressourceId, String prioritaet, List<Long> mitarbeiterIds) {
            this(id, typId, typName, farbe, bezeichnung, dauerTage, dauerHerkunft, festerStart, start, ende, konflikt, status, notiz, vorgaenger, ressourceId, prioritaet, mitarbeiterIds,
                    null, "GESCHAETZT", null, null, null, false, false, false, 0, null, null, null, null, null, null, null, null);
        }
    }

    /**
     * {@code ampel}: OK, KNAPP (weniger Arbeitstage Luft als eingestellt), VERSPAETET oder OHNE_ZIEL. {@code liefertermin} = Ende des letzten Vorgangs.
     * {@code verspaetungTage}: um wie viele Arbeitstage der Liefertermin nach dem Zieltermin liegt; sonst {@code null}.
     */
    public record AuftragDto(Long projektId, String bezeichnung, String auftragsnummer, LocalDate startDatum, LocalDate zielDatum, LocalDate liefertermin, String ampel,
            Integer verspaetungTage, List<VorgangDto> vorgaenge, Long kundeId, String kundeName) {

        public AuftragDto(Long projektId, String bezeichnung, String auftragsnummer, LocalDate startDatum, LocalDate zielDatum, LocalDate liefertermin, String ampel,
                Integer verspaetungTage, List<VorgangDto> vorgaenge) {
            this(projektId, bezeichnung, auftragsnummer, startDatum, zielDatum, liefertermin, ampel, verspaetungTage, vorgaenge, null, null);
        }

        public AuftragDto(Long projektId, String bezeichnung, String auftragsnummer, LocalDate startDatum, LocalDate zielDatum, LocalDate liefertermin, String ampel,
                List<VorgangDto> vorgaenge) {
            this(projektId, bezeichnung, auftragsnummer, startDatum, zielDatum, liefertermin, ampel, null, vorgaenge, null, null);
        }
    }

    public record TypDto(Long id, String name, String farbe, int sortierung, int standardDauerTage, boolean aktiv, Long abteilungId, BigDecimal stundenProTag, boolean wartet, boolean buero) { }

    /** Ein Vorschlag für die Dauer eines Vorgangs aus der Arbeitszeit des Auftrags. {@code vorgeschlageneDauer} ist leer, wenn es keinen Vorschlag gibt ({@code hinweis} sagt warum). */
    public record DauerVorschlag(Long vorgangId, String bezeichnung, String art, int aktuelleDauer, String aktuelleHerkunft, Integer vorgeschlageneDauer, BigDecimal stunden,
            BigDecimal stundenProTag, String abteilung, String hinweis) { }

    public record VorschlagAntwort(String quelle, List<DauerVorschlag> zeilen, List<String> hinweise) { }

    /** Eingabe für Anlegen und Ändern. {@code festerStart} ersetzt immer den bisherigen (leer = nicht fixiert); alle anderen leeren Felder bleiben unverändert. */
    public record VorgangEingabe(Long typId, String bezeichnung, Integer dauerTage, LocalDate festerStart, String status, String notiz, BigDecimal aufwandStunden, Integer stand) {
        public VorgangEingabe(Long typId, String bezeichnung, Integer dauerTage, LocalDate festerStart, String status, String notiz, BigDecimal aufwandStunden) {
            this(typId, bezeichnung, dauerTage, festerStart, status, notiz, aufwandStunden, null);
        }

        public VorgangEingabe(Long typId, String bezeichnung, Integer dauerTage, LocalDate festerStart, String status, String notiz) {
            this(typId, bezeichnung, dauerTage, festerStart, status, notiz, null, null);
        }
    }

    // ───────────────────────────── Lesen ─────────────────────────────

    @Transactional(readOnly = true)
    public List<AuftragDto> auftraege() {
        List<AuftragDto> liste = new ArrayList<>();
        for (Long id : vorgaenge.projektIdsMitVorgaengen()) {
            liste.add(auftrag(id));
        }
        liste.sort(Comparator.comparing((AuftragDto a) -> a.liefertermin() == null ? LocalDate.MAX : a.liefertermin()).thenComparing(AuftragDto::projektId));
        return liste;
    }

    @Transactional(readOnly = true)
    public AuftragDto auftrag(Long projektId) {
        Projekt projekt = projekt(projektId);
        List<PlanVorgang> alle = vorgaenge.findByProjektIdOrderBySortierungAscIdAsc(projektId);
        Map<Long, List<AbhaengigkeitDto>> vorgaengerJe = new HashMap<>();
        for (PlanAbhaengigkeit a : abhaengigkeiten.findeJeProjekt(projektId)) {
            vorgaengerJe.computeIfAbsent(a.getVorgang().getId(), x -> new ArrayList<>()).add(new AbhaengigkeitDto(a.getVorgaenger().getId(), a.getVersatzTage()));
        }
        Map<Long, BigDecimal> istJe = ist.jeVorgang(alle); // gestempelte Stunden je Vorgang
        Map<Long, String> lieferantenNamen = new HashMap<>();
        Set<Long> lieferantIds = new HashSet<>();
        alle.forEach(v -> {
            if (v.getLieferantId() != null) {
                lieferantIds.add(v.getLieferantId());
            }
        });
        if (!lieferantIds.isEmpty()) {
            lieferanten.findAllById(lieferantIds).forEach(l -> lieferantenNamen.put(l.getId(), l.getLieferantenname()));
        }
        List<VorgangDto> dtos = new ArrayList<>();
        LocalDate ende = null;
        for (PlanVorgang v : alle) {
            dtos.add(new VorgangDto(v.getId(), v.getTyp().getId(), v.getTyp().getName(), v.getTyp().getFarbe(), v.getBezeichnung(), v.getDauerTage(), v.getDauerHerkunft(),
                    v.getFesterStart(), v.getStartDatum(), v.getEndeDatum(), v.isKonflikt(), v.getStatus(), v.getNotiz(), vorgaengerJe.getOrDefault(v.getId(), List.of()),
                    v.getRessourceId(), v.getPrioritaet(), v.getMitarbeiterIds().stream().sorted().toList(), v.getAufwandStunden(), v.getAufwandHerkunft(), v.getFruehestensAb(),
                    v.getRang(), v.getHinweis(), v.getTyp().isWartet(), v.getTyp().isBuero(), v.isOhneZuweisungGeschaetzt(), v.getBearbeitung(),
                    v.getLieferantId(), v.getLieferantId() == null ? null : lieferantenNamen.get(v.getLieferantId()), v.getBestelltAm(), v.getLieferterminLieferant(), v.getFremdleistungId(), v.getMenge(), v.getMengenEinheit(), istJe.get(v.getId())));
            if (v.getEndeDatum() != null && (ende == null || v.getEndeDatum().isAfter(ende))) {
                ende = v.getEndeDatum();
            }
        }
        PlanProjekt pt = termine.findById(projektId).orElse(null);
        LocalDate ziel = pt == null ? null : pt.getZielDatum();
        String name = projekt.getBauvorhaben() != null && !projekt.getBauvorhaben().isBlank() ? projekt.getBauvorhaben() : "Auftrag " + projekt.getId();
        return new AuftragDto(projektId, name, projekt.getAuftragsnummer(), pt == null ? null : pt.getStartDatum(), ziel, ende, ampelFuer(ende, ziel), verspaetungTage(ende, ziel), dtos,
                projekt.getKundenId() == null ? null : projekt.getKundenId().getId(), projekt.getKundenId() == null ? null : projekt.getKundenId().getName());
    }

    @Transactional(readOnly = true)
    public List<TypDto> typen() {
        return typen.findAllByOrderBySortierungAscNameAsc().stream().map(PlanungsService::dto).toList();
    }

    // ───────────────────────────── Auftrag ─────────────────────────────

    @Transactional
    public AuftragDto setzeTermine(Long projektId, LocalDate start, LocalDate ziel) {
        projekt(projektId);
        if (start != null && ziel != null && ziel.isBefore(start)) {
            throw new IllegalArgumentException("Der Zieltermin darf nicht vor dem Start liegen.");
        }
        PlanProjekt pt = termine.findById(projektId).orElseGet(() -> {
            PlanProjekt neu = new PlanProjekt();
            neu.setProjektId(projektId);
            return neu;
        });
        pt.setStartDatum(start);
        pt.setZielDatum(ziel);
        termine.save(pt);
        neuBerechnen(projektId, LocalDate.now());
        return auftrag(projektId);
    }

    // ───────────────────────────── Vorgänge ─────────────────────────────

    @Transactional
    public AuftragDto vorgangAnlegen(Long projektId, VorgangEingabe e) {
        Projekt projekt = projekt(projektId);
        PlanVorgangstyp typ = aktiverTyp(e == null ? null : e.typId());
        PlanVorgang v = new PlanVorgang();
        v.setProjekt(projekt);
        v.setTyp(typ);
        v.setBezeichnung(text(e.bezeichnung(), typ.getName()));
        if (e.aufwandStunden() != null && !typ.isWartet()) {
            v.setAufwandStunden(aufwand(e.aufwandStunden()));
            v.setAufwandHerkunft(PlanVorgang.HERKUNFT_VON_HAND);
            v.setDauerHerkunft(PlanVorgang.HERKUNFT_VON_HAND);
            v.setDauerTage(typ.getStandardDauerTage());
        } else if (e.dauerTage() != null) {
            v.setDauerTage(dauer(e.dauerTage()));
            v.setDauerHerkunft(PlanVorgang.HERKUNFT_VON_HAND);
            v.setAufwandHerkunft(PlanVorgang.HERKUNFT_VON_HAND);
        } else {
            v.setDauerTage(typ.getStandardDauerTage());
            v.setDauerHerkunft(PlanVorgang.HERKUNFT_GESCHAETZT);
        }
        v.setFesterStart(e.festerStart());
        v.setStatus(status(e.status(), PlanVorgang.STATUS_GEPLANT));
        v.setNotiz(notiz(e.notiz()));
        v.setSortierung(vorgaenge.hoechsteSortierung(projektId) + 10);
        vorgaenge.save(v);
        historie.protokolliereOhneRueckgaengig(v.getId(), projektId, "„" + v.getBezeichnung() + "“ (" + auftragsName(projekt) + ") angelegt");
        neuBerechnen(projektId, LocalDate.now());
        return auftrag(projektId);
    }

    /**
     * Nimmt einen Auftrag in die Plantafel auf: ein Vorgang je aktiver Vorgangsart in der Reihenfolge der Arten, jeder beginnt nach dem vorigen.
     * Die Dauern sind die Schätzwerte der Arten (als "geschätzt" gekennzeichnet) und lassen sich danach ändern.
     */
    @Transactional
    public AuftragDto standardkette(Long projektId) {
        Projekt projekt = projekt(projektId);
        if (!vorgaenge.findByProjektIdOrderBySortierungAscIdAsc(projektId).isEmpty()) {
            throw new IllegalArgumentException("Dieser Auftrag hat schon Vorgänge in der Plantafel.");
        }
        List<PlanVorgangstyp> arten = typen.findAllByOrderBySortierungAscNameAsc().stream().filter(PlanVorgangstyp::isAktiv).toList();
        if (arten.isEmpty()) {
            throw new IllegalArgumentException("Es gibt keine Vorgangsart. Bitte zuerst eine Vorgangsart anlegen.");
        }
        PlanVorgang vorher = null;
        int sortierung = 0;
        for (PlanVorgangstyp art : arten) {
            PlanVorgang v = new PlanVorgang();
            v.setProjekt(projekt);
            v.setTyp(art);
            v.setBezeichnung(art.getName());
            v.setDauerTage(art.getStandardDauerTage());
            v.setDauerHerkunft(PlanVorgang.HERKUNFT_GESCHAETZT);
            v.setSortierung(sortierung += 10);
            vorgaenge.save(v);
            if (vorher != null) {
                PlanAbhaengigkeit a = new PlanAbhaengigkeit();
                a.setVorgang(v);
                a.setVorgaenger(vorher);
                abhaengigkeiten.save(a);
            }
            vorher = v;
        }
        vorgaenge.flush();
        neuBerechnen(projektId, LocalDate.now());
        return auftrag(projektId);
    }

    /**
     * Ein Schritt der Kette beim Planen (Fenster bei der Auftragsbestätigung). {@code aufwandStunden} nur bei Arbeitsvorgängen, {@code dauerTage} nur bei Wartevorgängen
     * (leer = Schätzwert der Art bzw. übliche Lieferzeit des Lieferanten); {@code mitPersonen}: Die gewählten Mitarbeiter werden diesem Vorgang zugewiesen.
     */
    public record KettenSchritt(Long typId, String bezeichnung, BigDecimal aufwandStunden, Integer dauerTage, Long lieferantId, Long fremdleistungId, BigDecimal menge, String mengenEinheit,
            boolean mitPersonen) { }

    /**
     * Legt die Kette eines Auftrags an, wie der Planer sie im Fenster bei der Auftragsbestätigung bestätigt hat: ein Vorgang je Schritt, jeder beginnt nach dem vorigen; Wichtigkeit,
     * Zieltermin und Mitarbeiter gelten gleich mit. Ein Auftrag, dessen Planung mit „Später“ übersprungen wurde ({@code planungOffen}), wird dabei neu aufgebaut; sonst gilt: nur
     * einmal. Danach ist der Auftrag nicht mehr „offen“. Ein einziges Mal gerechnet, nicht je Vorgang.
     */
    @Transactional
    public AuftragDto planeKette(Long projektId, List<KettenSchritt> schritte, LocalDate ziel, String prioritaet, java.util.Collection<Long> personen) {
        Projekt projekt = projekt(projektId);
        if (schritte == null || schritte.isEmpty()) {
            throw new IllegalArgumentException("Bitte mindestens einen Vorgang planen.");
        }
        if (schritte.size() > 50) {
            throw new IllegalArgumentException("Es können höchstens 50 Vorgänge auf einmal geplant werden.");
        }
        String prio = prioritaet == null || prioritaet.isBlank() ? PlanVorgang.PRIO_NORMAL : prioritaet.strip().toUpperCase(java.util.Locale.ROOT);
        if (!Set.of(PlanVorgang.PRIO_DRINGEND, PlanVorgang.PRIO_NORMAL, PlanVorgang.PRIO_NICHT_DRINGEND).contains(prio)) {
            throw new IllegalArgumentException("Die Priorität muss DRINGEND, NORMAL oder NICHT_DRINGEND sein.");
        }
        PlanProjekt pt = termine.findById(projektId).orElseGet(() -> {
            PlanProjekt neu = new PlanProjekt();
            neu.setProjektId(projektId);
            return neu;
        });
        List<PlanVorgang> vorhandene = vorgaenge.findByProjektIdOrderBySortierungAscIdAsc(projektId);
        if (!vorhandene.isEmpty()) {
            if (!pt.isPlanungOffen()) {
                throw new IllegalArgumentException("Dieser Auftrag hat schon Vorgänge in der Plantafel.");
            }
            vorgaenge.deleteAll(vorhandene);
            vorgaenge.flush();
        }
        if (ziel != null && pt.getStartDatum() != null && ziel.isBefore(pt.getStartDatum())) {
            throw new IllegalArgumentException("Der Zieltermin darf nicht vor dem Start liegen.");
        }
        Set<Long> wer = new HashSet<>(personen == null ? List.of() : personen);
        PlanVorgang vorher = null;
        int sortierung = 0;
        for (KettenSchritt k : schritte) {
            PlanVorgangstyp art = aktiverTyp(k.typId());
            PlanVorgang v = new PlanVorgang();
            v.setProjekt(projekt);
            v.setTyp(art);
            v.setBezeichnung(text(k.bezeichnung(), art.getName()));
            v.setPrioritaet(prio);
            v.setSortierung(sortierung += 10);
            if (art.isWartet()) {
                v.setDauerTage(dauer(k.dauerTage() != null ? k.dauerTage() : art.getStandardDauerTage()));
                v.setDauerHerkunft(k.dauerTage() != null ? PlanVorgang.HERKUNFT_VON_HAND : PlanVorgang.HERKUNFT_GESCHAETZT);
                v.setLieferantId(k.lieferantId());
                v.setFremdleistungId(k.fremdleistungId());
                v.setMenge(k.menge());
                v.setMengenEinheit(k.mengenEinheit());
            } else {
                v.setDauerTage(art.getStandardDauerTage());
                v.setDauerHerkunft(PlanVorgang.HERKUNFT_GESCHAETZT);
                if (k.aufwandStunden() != null) {
                    v.setAufwandStunden(aufwand(k.aufwandStunden()));
                    v.setAufwandHerkunft(PlanVorgang.HERKUNFT_VON_HAND);
                }
                if (k.mitPersonen()) {
                    v.getMitarbeiterIds().addAll(wer);
                }
            }
            vorgaenge.save(v);
            if (vorher != null) {
                PlanAbhaengigkeit a = new PlanAbhaengigkeit();
                a.setVorgang(v);
                a.setVorgaenger(vorher);
                abhaengigkeiten.save(a);
            }
            vorher = v;
        }
        pt.setZielDatum(ziel);
        pt.setPlanungOffen(false);
        termine.save(pt);
        vorgaenge.flush();
        historie.protokolliereOhneRueckgaengig(null, projektId, "Auftrag „" + auftragsName(projekt) + "“ geplant (" + schritte.size() + " Vorgänge)");
        neuBerechnen(projektId, LocalDate.now());
        return auftrag(projektId);
    }

    /**
     * Nimmt einen Auftrag komplett aus der Plantafel: alle Vorgänge, Abhängigkeiten, Zuteilungen und die Termine des Auftrags. Der Auftrag selbst bleibt unberührt.
     * Nicht rückgängig zu machen (steht aber in der Änderungsliste); die anderen Aufträge rechnen sich neu, weil die Mitarbeiter wieder frei werden.
     */
    @Transactional
    public void auftragEntfernen(Long projektId) {
        Projekt projekt = projekt(projektId);
        List<PlanVorgang> alle = vorgaenge.findByProjektIdOrderBySortierungAscIdAsc(projektId);
        if (alle.isEmpty() && termine.findById(projektId).isEmpty()) {
            throw new IllegalArgumentException("Dieser Auftrag steht nicht in der Plantafel.");
        }
        historie.protokolliereOhneRueckgaengig(null, projektId, "Auftrag „" + auftragsName(projekt) + "“ aus der Plantafel entfernt (" + alle.size() + " Vorgänge)");
        vorgaenge.deleteAll(alle);
        vorgaenge.flush();
        termine.findById(projektId).ifPresent(termine::delete);
        termine.flush();
        neuBerechnen(projektId, LocalDate.now());
    }

    /** Setzt die Wichtigkeit aller noch nicht erledigten Vorgänge eines Auftrags auf einmal. */
    @Transactional
    public AuftragDto auftragPrioritaet(Long projektId, String prioritaet) {
        projekt(projektId);
        String prio = prioritaet == null ? "" : prioritaet.strip().toUpperCase(java.util.Locale.ROOT);
        if (!Set.of(PlanVorgang.PRIO_DRINGEND, PlanVorgang.PRIO_NORMAL, PlanVorgang.PRIO_NICHT_DRINGEND).contains(prio)) {
            throw new IllegalArgumentException("Die Priorität muss DRINGEND, NORMAL oder NICHT_DRINGEND sein.");
        }
        for (PlanVorgang v : vorgaenge.findByProjektIdOrderBySortierungAscIdAsc(projektId)) {
            if (!PlanVorgang.STATUS_FERTIG.equals(v.getStatus()) && !prio.equals(v.getPrioritaet())) {
                Map<String, Object> vorher = historie.zustand(v);
                v.setPrioritaet(prio);
                v.setBearbeitung(v.getBearbeitung() + 1);
                vorgaenge.save(v);
                historie.protokolliere(v, vorher);
            }
        }
        vorgaenge.flush();
        neuBerechnen(projektId, LocalDate.now());
        return auftrag(projektId);
    }

    /** „Später“: Die Standardkette mit geschätzten Werten, der Auftrag steht als „neu, noch nicht eingeplant“ beim Planer. */
    @Transactional
    public AuftragDto planungSpaeter(Long projektId) {
        AuftragDto a = standardkette(projektId);
        PlanProjekt pt = termine.findById(projektId).orElseGet(() -> {
            PlanProjekt neu = new PlanProjekt();
            neu.setProjektId(projektId);
            return neu;
        });
        pt.setPlanungOffen(true);
        termine.save(pt);
        return a;
    }

    /** Ein Auftrag, dessen Planung noch aussteht. */
    public record OffenerAuftrag(Long projektId, String bezeichnung, String auftragsnummer) { }

    /** Die Aufträge, deren Planung mit „Später“ übersprungen wurde: neu, noch nicht eingeplant. */
    @Transactional(readOnly = true)
    public List<OffenerAuftrag> offeneAuftraege() {
        List<OffenerAuftrag> liste = new ArrayList<>();
        for (PlanProjekt pt : termine.findByPlanungOffenTrue()) {
            projekte.findById(pt.getProjektId()).ifPresent(p -> liste.add(new OffenerAuftrag(p.getId(), auftragsName(p), p.getAuftragsnummer())));
        }
        liste.sort(Comparator.comparing(OffenerAuftrag::projektId));
        return liste;
    }

    /** Ein Schritt einer Kette: die Vorgangsart und die Dauer in Arbeitstagen. */
    public record Schritt(Long typId, int dauerTage) { }

    /**
     * Nimmt einen Auftrag mit einer ausgewählten Kette in die Plantafel auf (zum Beispiel aus dem KI-Vorschlag): ein Vorgang je Schritt, jeder beginnt nach dem vorigen.
     * Die Dauern gelten als "geschätzt" und lassen sich danach ändern.
     */
    @Transactional
    public AuftragDto kettePerAuswahl(Long projektId, List<Schritt> schritte) {
        Projekt projekt = projekt(projektId);
        if (schritte == null || schritte.isEmpty()) {
            throw new IllegalArgumentException("Bitte mindestens einen Vorgang auswählen.");
        }
        if (!vorgaenge.findByProjektIdOrderBySortierungAscIdAsc(projektId).isEmpty()) {
            throw new IllegalArgumentException("Dieser Auftrag hat schon Vorgänge in der Plantafel.");
        }
        PlanVorgang vorher = null;
        int sortierung = 0;
        for (Schritt schritt : schritte) {
            PlanVorgangstyp art = aktiverTyp(schritt.typId());
            PlanVorgang v = new PlanVorgang();
            v.setProjekt(projekt);
            v.setTyp(art);
            v.setBezeichnung(art.getName());
            v.setDauerTage(dauer(schritt.dauerTage()));
            v.setDauerHerkunft(PlanVorgang.HERKUNFT_GESCHAETZT);
            v.setSortierung(sortierung += 10);
            vorgaenge.save(v);
            if (vorher != null) {
                PlanAbhaengigkeit a = new PlanAbhaengigkeit();
                a.setVorgang(v);
                a.setVorgaenger(vorher);
                abhaengigkeiten.save(a);
            }
            vorher = v;
        }
        vorgaenge.flush();
        neuBerechnen(projektId, LocalDate.now());
        return auftrag(projektId);
    }

    @Transactional
    public AuftragDto vorgangAendern(Long vorgangId, VorgangEingabe e) {
        PlanVorgang v = vorgang(vorgangId);
        pruefeStand(v, e.stand());
        Map<String, Object> vorher = historie.zustand(v);
        if (e.typId() != null) {
            v.setTyp(aktiverTyp(e.typId()));
        }
        if (e.bezeichnung() != null) {
            v.setBezeichnung(text(e.bezeichnung(), v.getTyp().getName()));
        }
        if (e.aufwandStunden() != null && !v.getTyp().isWartet()) {
            v.setAufwandStunden(aufwand(e.aufwandStunden()));
            v.setAufwandHerkunft(PlanVorgang.HERKUNFT_VON_HAND);
        } else if (e.dauerTage() != null) {
            v.setDauerTage(dauer(e.dauerTage()));
            v.setDauerHerkunft(PlanVorgang.HERKUNFT_VON_HAND);
            if (!v.getTyp().isWartet()) { // bei einem Arbeitsvorgang ist die Dauer ein Wunsch in Tagen: daraus wird Aufwand in Stunden
                v.setAufwandStunden(BigDecimal.valueOf(v.getDauerTage()).multiply(einstellungen.rhythmusLesen().arbeitstagStunden()).setScale(2, RoundingMode.HALF_UP));
                v.setAufwandHerkunft(PlanVorgang.HERKUNFT_VON_HAND);
            }
        }
        v.setFesterStart(e.festerStart());
        if (e.status() != null) {
            v.setStatus(status(e.status(), v.getStatus()));
        }
        if (e.notiz() != null) {
            v.setNotiz(notiz(e.notiz()));
        }
        v.setBearbeitung(v.getBearbeitung() + 1);
        vorgaenge.save(v);
        historie.protokolliere(v, vorher);
        Long projektId = v.getProjekt().getId();
        neuBerechnen(projektId, LocalDate.now());
        return auftrag(projektId);
    }

    /** Setzt "frühestens ab" (leer = aufheben) und rechnet alles neu. Gibt es einen festen Start, bleibt er unberührt. */
    @Transactional
    public AuftragDto fruehestensSetzen(Long vorgangId, LocalDate datum) {
        return fruehestensSetzen(vorgangId, datum, null);
    }

    /** Wie {@link #fruehestensSetzen(Long, LocalDate)}; {@code stand} ist der Stand, den der Anwender gesehen hat (leer = nicht prüfen). */
    @Transactional
    public AuftragDto fruehestensSetzen(Long vorgangId, LocalDate datum, Integer stand) {
        PlanVorgang v = vorgang(vorgangId);
        pruefeStand(v, stand);
        Map<String, Object> vorher = historie.zustand(v);
        v.setFruehestensAb(datum);
        v.setBearbeitung(v.getBearbeitung() + 1);
        vorgaenge.saveAndFlush(v);
        historie.protokolliere(v, vorher);
        neuBerechnen(v.getProjekt().getId(), LocalDate.now());
        return auftrag(v.getProjekt().getId());
    }

    /**
     * Setzt Lieferant, Bestelldatum und den von Hand bestätigten Liefertermin eines Wartevorgangs (ersetzt immer alle drei; leer = aufheben).
     * Die Lieferzeit zählt ab der Bestellung, der Lieferant gibt seine übliche Lieferzeit und den Termin aus der Auftragsbestätigung vor.
     */
    @Transactional
    public AuftragDto lieferungSetzen(Long vorgangId, Long lieferantId, LocalDate bestelltAm, LocalDate lieferterminLieferant, Integer stand) {
        PlanVorgang v = vorgang(vorgangId);
        if (!v.getTyp().isWartet()) {
            throw new IllegalArgumentException("Nur ein Wartevorgang (Material, Fremdleistung) hat einen Lieferanten und eine Lieferzeit.");
        }
        pruefeStand(v, stand);
        if (lieferantId != null && !lieferanten.existsById(lieferantId)) {
            throw new IllegalArgumentException("Den Lieferanten gibt es nicht.");
        }
        if (bestelltAm != null && lieferterminLieferant != null && lieferterminLieferant.isBefore(bestelltAm)) {
            throw new IllegalArgumentException("Der Liefertermin darf nicht vor dem Bestelldatum liegen.");
        }
        Map<String, Object> vorher = historie.zustand(v);
        v.setLieferantId(lieferantId);
        v.setBestelltAm(bestelltAm);
        v.setLieferterminLieferant(lieferterminLieferant);
        v.setBearbeitung(v.getBearbeitung() + 1);
        vorgaenge.saveAndFlush(v);
        historie.protokolliere(v, vorher);
        neuBerechnen(v.getProjekt().getId(), LocalDate.now());
        return auftrag(v.getProjekt().getId());
    }

    /** Legt die Reihenfolge unter gleich wichtigen Vorgängen fest (kleiner = zuerst, leer = nicht entschieden). */
    @Transactional
    public AuftragDto rangSetzen(Long vorgangId, Integer rang) {
        return rangSetzen(vorgangId, rang, null);
    }

    @Transactional
    public AuftragDto rangSetzen(Long vorgangId, Integer rang, Integer stand) {
        if (rang != null && (rang < 0 || rang > 100_000)) {
            throw new IllegalArgumentException("Die Reihenfolge muss zwischen 0 und 100000 liegen.");
        }
        PlanVorgang v = vorgang(vorgangId);
        pruefeStand(v, stand);
        Map<String, Object> vorher = historie.zustand(v);
        v.setRang(rang);
        v.setBearbeitung(v.getBearbeitung() + 1);
        vorgaenge.saveAndFlush(v);
        historie.protokolliere(v, vorher);
        neuBerechnen(v.getProjekt().getId(), LocalDate.now());
        return auftrag(v.getProjekt().getId());
    }

    @Transactional
    public AuftragDto vorgangLoeschen(Long vorgangId) {
        PlanVorgang v = vorgang(vorgangId);
        Long projektId = v.getProjekt().getId();
        historie.protokolliereOhneRueckgaengig(null, projektId, "„" + v.getBezeichnung() + "“ (" + auftragsName(v.getProjekt()) + ") gelöscht");
        vorgaenge.delete(v);
        vorgaenge.flush();
        neuBerechnen(projektId, LocalDate.now());
        return auftrag(projektId);
    }

    // ───────────────────────────── Abhängigkeiten ─────────────────────────────

    @Transactional
    public AuftragDto abhaengigkeitSetzen(Long vorgangId, Long vorgaengerId, int versatzTage) {
        if (vorgangId.equals(vorgaengerId)) {
            throw new IllegalArgumentException("Ein Vorgang kann nicht von sich selbst abhängen.");
        }
        if (versatzTage < 0 || versatzTage > MAX_DAUER) {
            throw new IllegalArgumentException("Der Puffer muss zwischen 0 und " + MAX_DAUER + " Arbeitstagen liegen.");
        }
        PlanVorgang v = vorgang(vorgangId);
        PlanVorgang vor = vorgang(vorgaengerId);
        if (!v.getProjekt().getId().equals(vor.getProjekt().getId())) {
            throw new IllegalArgumentException("Beide Vorgänge müssen zum selben Auftrag gehören.");
        }
        PlanAbhaengigkeit a = abhaengigkeiten.findeJeProjekt(v.getProjekt().getId()).stream()
                .filter(x -> x.getVorgang().getId().equals(vorgangId) && x.getVorgaenger().getId().equals(vorgaengerId)).findFirst().orElseGet(PlanAbhaengigkeit::new);
        a.setVorgang(v);
        a.setVorgaenger(vor);
        a.setVersatzTage(versatzTage);
        abhaengigkeiten.saveAndFlush(a);
        Long projektId = v.getProjekt().getId();
        neuBerechnen(projektId, LocalDate.now()); // wirft bei einem Kreis, die Änderung wird zurückgenommen
        return auftrag(projektId);
    }

    @Transactional
    public AuftragDto abhaengigkeitLoeschen(Long vorgangId, Long vorgaengerId) {
        PlanVorgang v = vorgang(vorgangId);
        Long projektId = v.getProjekt().getId();
        abhaengigkeiten.findeJeProjekt(projektId).stream()
                .filter(x -> x.getVorgang().getId().equals(vorgangId) && x.getVorgaenger().getId().equals(vorgaengerId))
                .forEach(abhaengigkeiten::delete);
        abhaengigkeiten.flush();
        neuBerechnen(projektId, LocalDate.now());
        return auftrag(projektId);
    }

    // ───────────────────────────── Vorgangsarten ─────────────────────────────

    public TypDto typSpeichern(Long id, String name, String farbe, Integer sortierung, Integer standardDauerTage, Boolean aktiv, Long abteilungId, BigDecimal stundenProTag) {
        return typSpeichern(id, name, farbe, sortierung, standardDauerTage, aktiv, abteilungId, stundenProTag, null, null);
    }

    /** {@code wartet} (Wartevorgang) und {@code buero} (Büroarbeit) bleiben unverändert, wenn sie leer sind. */
    @Transactional
    public TypDto typSpeichern(Long id, String name, String farbe, Integer sortierung, Integer standardDauerTage, Boolean aktiv, Long abteilungId, BigDecimal stundenProTag,
            Boolean wartet, Boolean buero) {
        PlanVorgangstyp t = id == null ? new PlanVorgangstyp() : typen.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Die Vorgangsart gibt es nicht."));
        String n = name == null ? null : name.strip();
        if (id == null || name != null) {
            if (n == null || n.isEmpty()) {
                throw new IllegalArgumentException("Bitte einen Namen eingeben.");
            }
            if (n.length() > 100) {
                throw new IllegalArgumentException("Der Name ist zu lang (höchstens 100 Zeichen).");
            }
            boolean doppelt = typen.findAll().stream().anyMatch(x -> x.getName().equalsIgnoreCase(n) && !x.getId().equals(t.getId()));
            if (doppelt) {
                throw new IllegalArgumentException("Es gibt schon eine Vorgangsart „" + n + "“.");
            }
            t.setName(n);
        }
        if (farbe != null) {
            if (!FARBE.matcher(farbe).matches()) {
                throw new IllegalArgumentException("Die Farbe muss als #rrggbb angegeben werden.");
            }
            t.setFarbe(farbe.toLowerCase());
        }
        if (sortierung != null) {
            t.setSortierung(sortierung);
        }
        if (standardDauerTage != null) {
            t.setStandardDauerTage(dauer(standardDauerTage));
        }
        if (aktiv != null) {
            t.setAktiv(aktiv);
        }
        // Abteilung und Stunden ersetzen immer den bisherigen Wert (leer = kein Vorschlag aus dem Angebot)
        if (abteilungId != null && !abteilungen.existsById(abteilungId)) {
            throw new IllegalArgumentException("Die Abteilung gibt es nicht.");
        }
        if (stundenProTag != null && (stundenProTag.signum() <= 0 || stundenProTag.compareTo(new BigDecimal("1000")) > 0)) {
            throw new IllegalArgumentException("Die Stunden pro Arbeitstag müssen größer als 0 und höchstens 1000 sein.");
        }
        t.setAbteilungId(abteilungId);
        t.setStundenProTag(stundenProTag == null ? null : stundenProTag.setScale(2, RoundingMode.HALF_UP));
        if (wartet != null) {
            t.setWartet(wartet);
        }
        if (buero != null) {
            t.setBuero(buero);
        }
        TypDto gespeichert = dto(typen.save(t));
        if (wartet != null || buero != null) {
            typen.flush();
            neuBerechnen(null, LocalDate.now());
        }
        return gespeichert;
    }

    // ───────────────────────────── Vorschlag aus dem Angebot ─────────────────────────────

    /**
     * Schlägt für jeden Vorgang, dessen Art eine Abteilung und Stunden pro Arbeitstag hat, die Dauer aus der Arbeitszeit des Auftrags vor:
     * Stunden der Abteilung (aus Angebot oder Auftragsbestätigung) geteilt durch die Stunden pro Arbeitstag, aufgerundet, mindestens 1 Tag. Ändert nichts.
     */
    @Transactional(readOnly = true)
    public VorschlagAntwort vorschlag(Long projektId) {
        projekt(projektId);
        AngebotsZeiten.Zeiten zeiten = angebotsZeiten.fuerProjekt(projektId);
        Map<Long, String> abteilungsNamen = new HashMap<>();
        abteilungen.findAll().forEach(a -> abteilungsNamen.put(a.getId(), a.getName()));
        List<DauerVorschlag> zeilen = new ArrayList<>();
        for (PlanVorgang v : vorgaenge.findByProjektIdOrderBySortierungAscIdAsc(projektId)) {
            PlanVorgangstyp art = v.getTyp();
            if (art.getAbteilungId() == null || art.isWartet()) {
                continue; // diese Art rechnet nicht aus dem Angebot
            }
            BigDecimal proTag = art.getStundenProTag() != null ? art.getStundenProTag() : einstellungen.rhythmusLesen().arbeitstagStunden();
            BigDecimal stunden = zeiten.stundenJeAbteilung().getOrDefault(art.getAbteilungId(), BigDecimal.ZERO);
            Integer dauer = null;
            String hinweis = null;
            if (stunden.signum() > 0) {
                dauer = Math.max(1, stunden.divide(proTag, 0, RoundingMode.CEILING).intValueExact());
            } else {
                hinweis = zeiten.quelle() == null ? "Der Auftrag hat kein Angebot und keine Auftragsbestätigung mit Leistungen." : "Im Angebot gibt es keine Arbeitszeit für diese Abteilung.";
            }
            zeilen.add(new DauerVorschlag(v.getId(), v.getBezeichnung(), art.getName(), v.getDauerTage(), v.getDauerHerkunft(), dauer, stunden, proTag,
                    abteilungsNamen.getOrDefault(art.getAbteilungId(), "?"), hinweis));
        }
        return new VorschlagAntwort(zeiten.quelle(), zeilen, zeiten.hinweise());
    }

    /** Übernimmt die Vorschläge für die gewählten Vorgänge. Der Server rechnet sie noch einmal; geschickt werden nur die Nummern der Vorgänge. */
    @Transactional
    public AuftragDto vorschlagUebernehmen(Long projektId, List<Long> vorgangIds) {
        if (vorgangIds == null || vorgangIds.isEmpty()) {
            throw new IllegalArgumentException("Bitte mindestens einen Vorgang auswählen.");
        }
        Map<Long, DauerVorschlag> nachId = new HashMap<>();
        vorschlag(projektId).zeilen().forEach(z -> nachId.put(z.vorgangId(), z));
        for (Long id : new HashSet<>(vorgangIds)) {
            DauerVorschlag z = nachId.get(id);
            if (z == null || z.vorgeschlageneDauer() == null) {
                throw new IllegalArgumentException("Für diesen Vorgang gibt es keinen Vorschlag.");
            }
            PlanVorgang v = vorgang(id);
            v.setDauerTage(z.vorgeschlageneDauer());
            v.setDauerHerkunft(PlanVorgang.HERKUNFT_KALKULATION);
            v.setAufwandStunden(z.stunden().setScale(2, RoundingMode.HALF_UP));
            v.setAufwandHerkunft(PlanVorgang.HERKUNFT_KALKULATION);
            vorgaenge.save(v);
        }
        vorgaenge.flush();
        neuBerechnen(projektId, LocalDate.now());
        return auftrag(projektId);
    }

    // ───────────────────────────── Rechnung ─────────────────────────────

    /**
     * Rechnet Start, Ende, Konflikte und die Zuteilung <b>aller</b> Vorgänge neu und speichert sie: Die Mitarbeiter teilen sich die Aufträge, also hängt jeder Auftrag von allen
     * anderen ab. {@code projektId} sagt nur, welcher Auftrag gerade geändert wurde (für eine verständliche Fehlermeldung). Wirft bei einem Kreis.
     */
    void neuBerechnen(Long projektId, LocalDate heute) {
        List<PlanVorgang> alle = vorgaenge.findAll();
        if (alle.isEmpty()) {
            zuteilungen.loescheAlle();
            return;
        }
        BigDecimal standardTag = einstellungen.rhythmusLesen().arbeitstagStunden();
        for (PlanVorgang v : alle) {
            if (!v.getTyp().isWartet() && v.getAufwandStunden() == null) { // erste Rechnung für einen alten Vorgang: Tage mal Standard-Arbeitstag
                v.setAufwandStunden(alsAufwand(v, standardTag));
                v.setAufwandHerkunft(v.getDauerHerkunft());
            }
        }
        Berechnung b = berechne(alle, heute, null, null);
        Map<Long, PlanVorgang> nachId = new HashMap<>();
        alle.forEach(v -> nachId.put(v.getId(), v));
        for (PlanVorgang v : alle) {
            PlanungsStunden.Ergebnis z = b.plan().vorgaenge().get(v.getId());
            if (PlanVorgang.STATUS_FERTIG.equals(v.getStatus()) && v.getStartDatum() != null && v.getEndeDatum() != null) {
                continue; // erledigt: Termine bleiben
            }
            v.setStartDatum(z.start());
            v.setEndeDatum(z.ende());
            v.setKonflikt(z.konflikt());
            v.setOhneZuweisungGeschaetzt(z.geschaetzt());
            String hinweis = String.join(" · ", z.hinweise());
            if (v.getTyp().isWartet()) {
                Lieferung l = lieferungVon(v, new HashMap<>());
                hinweis = l.text();
                if (l.dauer() != v.getDauerTage() && !PlanVorgang.HERKUNFT_VON_HAND.equals(v.getDauerHerkunft())) {
                    v.setDauerTage(l.dauer());
                }
            }
            v.setHinweis(hinweis.isEmpty() ? null : hinweis.length() > 1000 ? hinweis.substring(0, 1000) : hinweis);
            if (!v.getTyp().isWartet()) {
                v.setDauerTage(Math.max(1, arbeitstageZwischen(z.start(), z.ende(), b.arbeitstag())));
            }
        }
        vorgaenge.saveAll(alle);
        zuteilungen.loescheAlle();
        List<PlanZuteilung> neu = new ArrayList<>();
        for (PlanungsStunden.Zuteilung z : b.plan().zuteilungen()) {
            PlanZuteilung pz = new PlanZuteilung();
            pz.setVorgang(nachId.get(z.vorgangId()));
            pz.setMitarbeiterId(z.person());
            pz.setDatum(z.tag());
            pz.setStunden(z.stunden());
            neu.add(pz);
        }
        zuteilungen.saveAll(neu);
    }

    private static BigDecimal alsAufwand(PlanVorgang v, BigDecimal standardTag) {
        return BigDecimal.valueOf(v.getDauerTage()).multiply(standardTag).setScale(2, RoundingMode.HALF_UP);
    }

    /** Das Ergebnis einer Rechnung, ohne dass etwas gespeichert wurde. */
    record Berechnung(PlanungsStunden.Plan plan, Predicate<LocalDate> arbeitstag) { }

    /**
     * Rechnet alle Vorgänge, ohne etwas zu verändern oder zu speichern. Mit {@code probe} wird für den Vorgang {@code probeVorgangId} so gerechnet, als wäre er geändert
     * (Vorschau „was ändert sich“); die Vorgänge selbst bleiben unberührt.
     */
    private Berechnung berechne(List<PlanVorgang> alle, LocalDate heute, Long probeVorgangId, Probe probe) {
        PlanungsEinstellungen.Rhythmus rhythmus = einstellungen.rhythmusLesen();
        Map<Long, List<Kante>> kanten = new HashMap<>();
        for (PlanAbhaengigkeit a : abhaengigkeiten.findAll()) {
            kanten.computeIfAbsent(a.getVorgang().getId(), x -> new ArrayList<>()).add(new Kante(a.getVorgaenger().getId(), a.getVersatzTage()));
        }
        Map<Long, PlanProjekt> projektTermine = new HashMap<>();
        termine.findAll().forEach(t -> projektTermine.put(t.getProjektId(), t));
        Set<Long> personen = new HashSet<>();
        List<PlanungsStunden.Eingang> eingaenge = new ArrayList<>();
        Map<String, java.util.Optional<LocalDate>> abCache = new HashMap<>();
        Map<Long, BigDecimal> istJe = ist.jeVorgang(alle); // gestempelte Stunden zählen vom Aufwand ab
        int nr = 0;
        for (PlanVorgang v : alle) {
            boolean probiert = probe != null && v.getId().equals(probeVorgangId);
            boolean wartet = v.getTyp().isWartet();
            BigDecimal aufwand = v.getAufwandStunden() != null ? v.getAufwandStunden() : alsAufwand(v, rhythmus.arbeitstagStunden());
            int dauer = v.getDauerTage();
            LocalDate fest = v.getFesterStart();
            LocalDate frueh = v.getFruehestensAb();
            Set<Long> wer = v.getMitarbeiterIds();
            String prio = v.getPrioritaet();
            if (probiert) {
                if (probe.aufwandStunden() != null) {
                    aufwand = probe.aufwandStunden();
                }
                if (probe.dauerTage() != null) {
                    dauer = probe.dauerTage();
                }
                if (probe.festerStartSetzen()) {
                    fest = probe.festerStart();
                }
                if (probe.fruehestensAbSetzen()) {
                    frueh = probe.fruehestensAb();
                }
                if (probe.mitarbeiterIds() != null) {
                    wer = new HashSet<>(probe.mitarbeiterIds());
                }
                if (probe.prioritaet() != null) {
                    prio = probe.prioritaet();
                }
            }
            LocalDate bestellt = null;
            LocalDate lieferFix = null;
            if (wartet) {
                Lieferung l = lieferungVon(v, abCache);
                dauer = probe != null && probiert && probe.dauerTage() != null ? probe.dauerTage() : l.dauer();
                bestellt = v.getBestelltAm();
                lieferFix = l.fix();
            }
            boolean fertig = PlanVorgang.STATUS_FERTIG.equals(v.getStatus());
            BigDecimal offen = wartet ? null : fertig ? BigDecimal.ZERO : aufwand.subtract(istJe.getOrDefault(v.getId(), BigDecimal.ZERO)).max(BigDecimal.ZERO); // Erledigtes belegt nichts mehr
            boolean erledigt = fertig && v.getStartDatum() != null && v.getEndeDatum() != null;
            PlanProjekt pt = projektTermine.get(v.getProjekt().getId());
            personen.addAll(wer);
            eingaenge.add(new PlanungsStunden.Eingang(v.getId(), v.getProjekt().getId(), v.getBezeichnung(), auftragsName(v.getProjekt()), prio, v.getRang(),
                    pt == null ? null : pt.getZielDatum(), wartet, v.getTyp().isBuero(), offen, dauer, fest, frueh,
                    pt == null ? null : pt.getStartDatum(), wer, kanten.getOrDefault(v.getId(), List.of()),
                    erledigt ? v.getStartDatum() : null, erledigt ? v.getEndeDatum() : null, nr++, bestellt, lieferFix));
        }
        Predicate<LocalDate> arbeitstag = arbeitstag(heute);
        PlanungsStunden.Kapazitaet kap = kapazitaet.laden(personen, heute, heute.plusDays(PlanungsStunden.MAX_TAGE + 30L), arbeitstag, rhythmus);
        return new Berechnung(PlanungsStunden.berechne(eingaenge, heute, kap), arbeitstag);
    }

    /** Wie ein Wartevorgang liefert: {@code dauer} in Arbeitstagen, {@code fix} = bestätigter Liefertermin, {@code text} = woher die Zahlen kommen. */
    record Lieferung(int dauer, LocalDate fix, String text) { }

    /**
     * Dauer und Termin eines Wartevorgangs: Von Hand eingetragene Dauer gilt immer; sonst die übliche Lieferzeit des Lieferanten, sonst der Schätzwert. Den Liefertermin
     * gibt der Lieferant mit seiner Auftragsbestätigung vor (von Hand eingetragen oder aus dem Dokument gelesen). Die Lieferzeit zählt ab der Bestellung.
     */
    private Lieferung lieferungVon(PlanVorgang v, Map<String, java.util.Optional<LocalDate>> abCache) {
        int dauer = v.getDauerTage();
        List<String> teile = new ArrayList<>();
        Lieferanten l = v.getLieferantId() == null ? null : lieferanten.findById(v.getLieferantId()).orElse(null);
        if (l != null && l.getStandardLieferzeitTage() != null && !PlanVorgang.HERKUNFT_VON_HAND.equals(v.getDauerHerkunft())) {
            dauer = l.getStandardLieferzeitTage();
            teile.add("übliche Lieferzeit von " + l.getLieferantenname() + ": " + dauer + " Arbeitstage");
        }
        if (v.getBestelltAm() != null) {
            teile.add("bestellt am " + v.getBestelltAm().format(java.time.format.DateTimeFormatter.ofPattern("d.M.")));
        }
        LocalDate fix = v.getLieferterminLieferant();
        if (fix != null) {
            teile.add("Liefertermin von Hand: " + fix.format(java.time.format.DateTimeFormatter.ofPattern("d.M.")));
        } else if (l != null) {
            fix = abLiefertermin(v.getProjekt().getId(), l.getId(), abCache);
            if (fix != null) {
                teile.add("Liefertermin laut Auftragsbestätigung von " + l.getLieferantenname() + ": " + fix.format(java.time.format.DateTimeFormatter.ofPattern("d.M.")));
            }
        }
        return new Lieferung(dauer, fix, String.join(", ", teile));
    }

    /** Der jüngste Liefertermin aus einer Auftragsbestätigung dieses Lieferanten, die dem Auftrag zugeordnet ist (je Auftrag und Lieferant einmal gelesen). */
    private LocalDate abLiefertermin(Long projektId, Long lieferantId, Map<String, java.util.Optional<LocalDate>> cache) {
        return cache.computeIfAbsent(projektId + ":" + lieferantId, k -> {
            org.example.kalkulationsprogramm.domain.LieferantDokument juengstes = null;
            LocalDate termin = null;
            for (var a : lieferantenAnteile.findByProjektIdEager(projektId)) {
                var d = a.getDokument();
                if (d == null || d.getTyp() != org.example.kalkulationsprogramm.domain.LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG || d.getLieferant() == null || !lieferantId.equals(d.getLieferant().getId())
                        || d.getGeschaeftsdaten() == null || d.getGeschaeftsdaten().getLiefertermin() == null) {
                    continue;
                }
                if (juengstes == null || d.getUploadDatum() != null && (juengstes.getUploadDatum() == null || d.getUploadDatum().isAfter(juengstes.getUploadDatum()))) {
                    juengstes = d;
                    termin = d.getGeschaeftsdaten().getLiefertermin();
                }
            }
            return java.util.Optional.ofNullable(termin);
        }).orElse(null);
    }

    /** Eine mögliche Änderung an einem Vorgang, nur zum Ausprobieren. Leere Felder bleiben unverändert; „setzen“ heißt: auch ein leerer Wert gilt (Termin aufheben). */
    public record Probe(BigDecimal aufwandStunden, Integer dauerTage, LocalDate fruehestensAb, boolean fruehestensAbSetzen, LocalDate festerStart, boolean festerStartSetzen,
            List<Long> mitarbeiterIds, String prioritaet) { }

    /** Wie sich ein Auftrag durch die Änderung verschiebt. {@code tage}: Kalendertage zwischen alter und neuer Lieferzeit (plus = später). */
    public record AuftragAenderung(Long projektId, String bezeichnung, LocalDate altLiefertermin, LocalDate neuLiefertermin, Integer tage, String altAmpel, String neuAmpel,
            Integer neuVerspaetungTage, boolean eigener) { }

    public record VorgangAenderung(Long vorgangId, Long projektId, String bezeichnung, String auftrag, LocalDate altStart, LocalDate altEnde, LocalDate neuStart, LocalDate neuEnde) { }

    /** Die Vorschau: welche Aufträge und Vorgänge sich verschieben, plus Warnungen (Konflikt, nicht planbar). */
    public record Vorschau(List<AuftragAenderung> auftraege, List<VorgangAenderung> vorgaenge, List<String> warnungen) { }

    /** Rechnet die Änderung durch und sagt, was sich verschiebt. Ändert und speichert nichts. */
    @Transactional(readOnly = true)
    public Vorschau vorschau(Long vorgangId, Probe probe) {
        PlanVorgang geprobt = vorgang(vorgangId);
        if (probe == null) {
            throw new IllegalArgumentException("Es fehlt die Änderung, die ausprobiert werden soll.");
        }
        if (probe.aufwandStunden() != null) {
            aufwand(probe.aufwandStunden());
        }
        if (probe.dauerTage() != null) {
            dauer(probe.dauerTage());
        }
        LocalDate heute = LocalDate.now();
        List<PlanVorgang> alle = vorgaenge.findAll();
        Berechnung neu = berechne(alle, heute, vorgangId, probe);
        Map<Long, LocalDate> altEnde = new HashMap<>();
        Map<Long, LocalDate> neuEnde = new HashMap<>();
        List<VorgangAenderung> verschoben = new ArrayList<>();
        List<String> warnungen = new ArrayList<>();
        for (PlanVorgang v : alle) {
            PlanungsStunden.Ergebnis z = neu.plan().vorgaenge().get(v.getId());
            if (z == null) {
                continue;
            }
            altEnde.merge(v.getProjekt().getId(), v.getEndeDatum() == null ? LocalDate.MIN : v.getEndeDatum(), (a, b) -> a.isAfter(b) ? a : b);
            neuEnde.merge(v.getProjekt().getId(), z.ende(), (a, b) -> a.isAfter(b) ? a : b);
            boolean erledigt = PlanVorgang.STATUS_FERTIG.equals(v.getStatus()) && v.getStartDatum() != null && v.getEndeDatum() != null;
            if (!erledigt && (!z.start().equals(v.getStartDatum()) || !z.ende().equals(v.getEndeDatum()))) {
                verschoben.add(new VorgangAenderung(v.getId(), v.getProjekt().getId(), v.getBezeichnung(), auftragsName(v.getProjekt()), v.getStartDatum(), v.getEndeDatum(), z.start(), z.ende()));
            }
            if (v.getId().equals(vorgangId)) {
                if (z.konflikt()) {
                    warnungen.add("Der feste Start liegt vor dem Ende der Vorgänger: Das ergibt einen Konflikt.");
                }
                if (!z.planbar()) {
                    warnungen.add("Für diesen Vorgang findet das Programm in den nächsten Jahren keine freie Zeit.");
                }
            }
        }
        Long eigenes = geprobt.getProjekt().getId();
        List<AuftragAenderung> auftraegeListe = new ArrayList<>();
        for (Map.Entry<Long, LocalDate> e : neuEnde.entrySet()) {
            LocalDate alt = altEnde.get(e.getKey());
            LocalDate alteLiefer = alt == null || alt.equals(LocalDate.MIN) ? null : alt;
            LocalDate neueLiefer = e.getValue();
            boolean eigener = e.getKey().equals(eigenes);
            if (!eigener && neueLiefer.equals(alteLiefer)) {
                continue;
            }
            Projekt p = projekt(e.getKey());
            PlanProjekt pt = termine.findById(e.getKey()).orElse(null);
            LocalDate ziel = pt == null ? null : pt.getZielDatum();
            auftraegeListe.add(new AuftragAenderung(e.getKey(), auftragsName(p), alteLiefer, neueLiefer, alteLiefer == null ? null : (int) java.time.temporal.ChronoUnit.DAYS.between(alteLiefer, neueLiefer),
                    ampelFuer(alteLiefer, ziel), ampelFuer(neueLiefer, ziel), verspaetungTage(neueLiefer, ziel), eigener));
        }
        auftraegeListe.sort(Comparator.comparing((AuftragAenderung a) -> !a.eigener()).thenComparing(AuftragAenderung::projektId));
        verschoben.sort(Comparator.comparing(VorgangAenderung::projektId).thenComparing(VorgangAenderung::vorgangId));
        return new Vorschau(auftraegeListe, verschoben, warnungen);
    }

    /** Rechnet die ganze Plantafel neu (zum Beispiel nach Urlaub oder neuem Termin, oder weil ein neuer Tag begonnen hat). */
    @Transactional
    public void neuBerechnenAlle() {
        neuBerechnen(null, LocalDate.now());
    }

    /** Die Plantafel rechnet sich selbst: regelmäßig, weil Urlaub, Termine und der Tag selbst sich ändern, ohne dass jemand die Plantafel anfasst. */
    @Scheduled(fixedDelay = 900_000, initialDelay = 180_000)
    void regelmaessigNeuBerechnen() {
        try {
            neuBerechnenAlle();
        } catch (RuntimeException e) {
            log.warn("Die Plantafel konnte nicht neu gerechnet werden: {}", e.getMessage());
        }
    }

    private static int arbeitstageZwischen(LocalDate von, LocalDate bis, Predicate<LocalDate> arbeitstag) {
        int n = 0;
        for (LocalDate d = von; !d.isAfter(bis) && n < 5000; d = d.plusDays(1)) {
            if (arbeitstag.test(d)) {
                n++;
            }
        }
        return n;
    }

    private static String auftragsName(Projekt p) {
        return p.getBauvorhaben() != null && !p.getBauvorhaben().isBlank() ? p.getBauvorhaben() : "Auftrag " + p.getId();
    }

    /** Was ein Arbeitstag ist, bestimmen die Einstellungen der Firma (Arbeitswoche, Betriebsurlaub) und die Feiertage ab diesem Datum. */
    private Predicate<LocalDate> arbeitstag(LocalDate ab) {
        PlanungsEinstellungen.Werte werte = einstellungen.lesen();
        Set<LocalDate> frei = feiertageAb(ab);
        return d -> werte.istArbeitstag(d, frei);
    }

    private Set<LocalDate> feiertageAb(LocalDate datum) {
        Set<LocalDate> menge = new HashSet<>();
        for (int jahr = datum.getYear(); jahr <= datum.getYear() + 3; jahr++) {
            for (Feiertag f : feiertage.findByJahrAndBundesland(jahr, FeiertagService.bundeslandAus(einstellungenRepository))) {
                if (!f.isHalbTag()) {
                    menge.add(f.getDatum());
                }
            }
        }
        return menge;
    }

    /** Um wie viele Arbeitstage der Liefertermin nach dem Zieltermin liegt (Arbeitswoche, Feiertage und Betriebsurlaub der Firma); {@code null}, wenn er nicht danach liegt. */
    private Integer verspaetungTage(LocalDate liefertermin, LocalDate ziel) {
        if (liefertermin == null || ziel == null || !liefertermin.isAfter(ziel)) {
            return null;
        }
        Predicate<LocalDate> arbeitstag = arbeitstag(ziel);
        int tage = 0;
        for (LocalDate d = ziel.plusDays(1); !d.isAfter(liefertermin) && tage < MAX_DAUER * 4; d = d.plusDays(1)) {
            if (arbeitstag.test(d)) {
                tage++;
            }
        }
        return tage;
    }

    /** Ampel mit den Einstellungen der Firma. Die Arbeitstage werden nur nachgeschlagen, wenn die Antwort davon abhängt. */
    private String ampelFuer(LocalDate liefertermin, LocalDate ziel) {
        if (liefertermin == null || ziel == null || liefertermin.isAfter(ziel)) {
            return ampel(liefertermin, ziel);
        }
        return ampel(liefertermin, ziel, einstellungen.lesen().ampelSchwelleTage(), arbeitstag(liefertermin));
    }

    /** Ampel mit den Vorgaben, mit denen die Plantafel bisher gerechnet hat (Montag bis Freitag, zwei Arbeitstage Luft). */
    static String ampel(LocalDate liefertermin, LocalDate ziel) {
        return ampel(liefertermin, ziel, 2, PlanungsRechner.arbeitstag(Set.of()));
    }

    static String ampel(LocalDate liefertermin, LocalDate ziel, int schwelleTage, Predicate<LocalDate> arbeitstag) {
        if (liefertermin == null || ziel == null) {
            return "OHNE_ZIEL";
        }
        if (liefertermin.isAfter(ziel)) {
            return "VERSPAETET";
        }
        // weniger als die eingestellte Zahl Arbeitstage Luft bis zum Ziel
        return PlanungsRechner.plusArbeitstage(liefertermin, schwelleTage, arbeitstag).isAfter(ziel) ? "KNAPP" : "OK";
    }

    // ───────────────────────────── Rückgängig ─────────────────────────────

    /** Nimmt die jüngste Änderung zurück: Der Vorgang bekommt den Zustand davor, alles rechnet sich neu. */
    @Transactional
    public AuftragDto rueckgaengig(Long aenderungId) {
        org.example.kalkulationsprogramm.domain.planung.PlanAenderung a = historie.fuerRueckgaengig(aenderungId);
        PlanVorgang v = vorgaenge.findById(a.getVorgangId()).orElseThrow(() -> new IllegalArgumentException("Den Vorgang gibt es nicht mehr."));
        historie.wiederherstellen(v, a);
        v.setBearbeitung(v.getBearbeitung() + 1);
        vorgaenge.saveAndFlush(v);
        Long projektId = v.getProjekt().getId();
        neuBerechnen(projektId, LocalDate.now());
        return auftrag(projektId);
    }

    /** Jemand anderes hat den Vorgang geändert, seit der Anwender ihn geladen hat: Nichts überschreiben, sondern melden (leerer Stand = nicht prüfen). */
    void pruefeStand(PlanVorgang v, Integer stand) {
        if (stand != null && stand != v.getBearbeitung()) {
            throw new PlanKonfliktException("Der Vorgang „" + v.getBezeichnung() + "“ wurde inzwischen von jemand anderem geändert. Die Ansicht lädt neu; bitte die Änderung noch einmal machen.");
        }
    }

    // ───────────────────────────── Hilfen ─────────────────────────────

    private Projekt projekt(Long id) {
        return projekte.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Den Auftrag gibt es nicht."));
    }

    private PlanVorgang vorgang(Long id) {
        return vorgaenge.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Den Vorgang gibt es nicht."));
    }

    private PlanVorgangstyp aktiverTyp(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Bitte eine Vorgangsart wählen.");
        }
        PlanVorgangstyp t = typen.findById(id).orElseThrow(() -> new IllegalArgumentException("Die Vorgangsart gibt es nicht."));
        if (!t.isAktiv()) {
            throw new IllegalArgumentException("Die Vorgangsart „" + t.getName() + "“ ist ausgeblendet.");
        }
        return t;
    }

    private static BigDecimal aufwand(BigDecimal stunden) {
        if (stunden.signum() < 0 || stunden.compareTo(new BigDecimal("100000")) > 0) {
            throw new IllegalArgumentException("Der Aufwand muss zwischen 0 und 100000 Stunden liegen.");
        }
        return stunden.setScale(2, RoundingMode.HALF_UP);
    }

    private static int dauer(int tage) {
        if (tage < 0 || tage > MAX_DAUER) {
            throw new IllegalArgumentException("Die Dauer muss zwischen 0 und " + MAX_DAUER + " Arbeitstagen liegen.");
        }
        return tage;
    }

    private static String text(String wert, String ersatz) {
        String t = wert == null ? "" : wert.strip();
        if (t.length() > 200) {
            throw new IllegalArgumentException("Die Bezeichnung ist zu lang (höchstens 200 Zeichen).");
        }
        return t.isEmpty() ? ersatz : t;
    }

    private static String notiz(String wert) {
        String t = wert == null ? "" : wert.strip();
        if (t.length() > 1000) {
            throw new IllegalArgumentException("Die Notiz ist zu lang (höchstens 1000 Zeichen).");
        }
        return t.isEmpty() ? null : t;
    }

    private static String status(String wert, String ersatz) {
        if (wert == null || wert.isBlank()) {
            return ersatz;
        }
        String s = wert.strip().toUpperCase(java.util.Locale.ROOT);
        if (!STATUS.contains(s)) {
            throw new IllegalArgumentException("Der Status muss GEPLANT, LAEUFT oder FERTIG sein.");
        }
        return s;
    }

    private static TypDto dto(PlanVorgangstyp t) {
        return new TypDto(t.getId(), t.getName(), t.getFarbe(), t.getSortierung(), t.getStandardDauerTage(), t.isAktiv(), t.getAbteilungId(), t.getStundenProTag(), t.isWartet(), t.isBuero());
    }
}
