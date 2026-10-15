package org.example.kalkulationsprogramm.service.planung;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import org.example.kalkulationsprogramm.domain.planung.PlanAenderung;
import org.example.kalkulationsprogramm.domain.planung.PlanVorgang;
import org.example.kalkulationsprogramm.repository.planung.PlanAenderungRepository;
import org.example.kalkulationsprogramm.repository.planung.PlanVorgangstypRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Die Änderungsliste der Plantafel (Issue #331, Abschnitt 7): Jede Änderung an einem Vorgang von Hand steht mit Zeit, Benutzer und Text in der Liste; die jüngste lässt sich
 * zurücknehmen („Rückgängig“). Dafür merkt sich das Programm den Zustand des Vorgangs vor der Änderung. Anlegen und Löschen stehen in der Liste, lassen sich aber nicht zurücknehmen.
 * Die Liste behält die letzten {@value #BEHALTEN} Änderungen.
 */
@Service
@RequiredArgsConstructor
public class PlanungsHistorie {

    static final int BEHALTEN = 200;
    private static final DateTimeFormatter TAG = DateTimeFormatter.ofPattern("d.M.");
    private static final TypeReference<LinkedHashMap<String, Object>> MAP = new TypeReference<>() { };

    private final PlanAenderungRepository aenderungen;
    private final PlanVorgangstypRepository typen;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Ein Eintrag der Liste. {@code rueckgaengigMoeglich}: nur die jüngste, noch nicht zurückgenommene Änderung mit gemerktem Zustand. */
    public record Eintrag(Long id, LocalDateTime zeitpunkt, String benutzer, String beschreibung, Long vorgangId, Long projektId, boolean zurueckgenommen, boolean rueckgaengigMoeglich) { }

    /** Der änderbare Zustand eines Vorgangs, als einfache Werte (Texte und Zahlen), damit er sich als JSON merken lässt. */
    Map<String, Object> zustand(PlanVorgang v) {
        Map<String, Object> z = new LinkedHashMap<>();
        z.put("typId", v.getTyp().getId());
        z.put("bezeichnung", v.getBezeichnung());
        z.put("dauerTage", v.getDauerTage());
        z.put("dauerHerkunft", v.getDauerHerkunft());
        z.put("aufwandStunden", v.getAufwandStunden() == null ? null : v.getAufwandStunden().toPlainString());
        z.put("aufwandHerkunft", v.getAufwandHerkunft());
        z.put("festerStart", v.getFesterStart() == null ? null : v.getFesterStart().toString());
        z.put("fruehestensAb", v.getFruehestensAb() == null ? null : v.getFruehestensAb().toString());
        z.put("rang", v.getRang());
        z.put("status", v.getStatus());
        z.put("prioritaet", v.getPrioritaet());
        z.put("ressourceId", v.getRessourceId());
        z.put("mitarbeiterIds", new TreeSet<>(v.getMitarbeiterIds()));
        z.put("notiz", v.getNotiz());
        z.put("lieferantId", v.getLieferantId());
        z.put("bestelltAm", v.getBestelltAm() == null ? null : v.getBestelltAm().toString());
        z.put("lieferterminLieferant", v.getLieferterminLieferant() == null ? null : v.getLieferterminLieferant().toString());
        return z;
    }

    /** Hält die Änderung fest, wenn sich der Zustand wirklich geändert hat; sonst nichts. */
    @Transactional
    public void protokolliere(PlanVorgang v, Map<String, Object> vorher) {
        Map<String, Object> jetzt = zustand(v);
        String text = beschreibung(vorher, jetzt);
        if (text.isEmpty()) {
            return;
        }
        try {
            speichere(v, "„" + v.getBezeichnung() + "“ (" + auftrag(v) + "): " + text, mapper.writeValueAsString(vorher));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Die Änderung konnte nicht festgehalten werden.", e);
        }
    }

    /** Anlegen und Löschen: in der Liste, aber nicht rückgängig zu machen. */
    @Transactional
    public void protokolliereOhneRueckgaengig(Long vorgangId, Long projektId, String text) {
        PlanAenderung a = neu(text, null);
        a.setVorgangId(vorgangId);
        a.setProjektId(projektId);
        aenderungen.save(a);
        bereinige(a);
    }

    private void speichere(PlanVorgang v, String text, String vorherJson) {
        PlanAenderung a = neu(text, vorherJson);
        a.setVorgangId(v.getId());
        a.setProjektId(v.getProjekt().getId());
        aenderungen.save(a);
        bereinige(a);
    }

    private PlanAenderung neu(String text, String vorherJson) {
        PlanAenderung a = new PlanAenderung();
        a.setZeitpunkt(LocalDateTime.now());
        a.setBenutzer(benutzer());
        a.setBeschreibung(text.length() > 500 ? text.substring(0, 500) : text);
        a.setVorher(vorherJson);
        return a;
    }

    private void bereinige(PlanAenderung neuste) {
        if (neuste.getId() != null && neuste.getId() > BEHALTEN) {
            aenderungen.loescheAelterAls(neuste.getId() - BEHALTEN + 1);
        }
    }

    private static String benutzer() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String name = auth == null ? null : auth.getName();
        return name == null || name.isBlank() || "anonymousUser".equals(name) ? "unbekannt" : name.length() > 100 ? name.substring(0, 100) : name;
    }

    private static String auftrag(PlanVorgang v) {
        String name = v.getProjekt().getBauvorhaben();
        return name != null && !name.isBlank() ? name : "Auftrag " + v.getProjekt().getId();
    }

    @Transactional(readOnly = true)
    public List<Eintrag> liste(int limit) {
        int n = Math.min(Math.max(limit, 1), 50);
        List<PlanAenderung> neueste = aenderungen.neueste(PageRequest.of(0, n));
        List<PlanAenderung> offen = aenderungen.offeneNeueste(PageRequest.of(0, 1));
        Long ruecknehmbar = offen.isEmpty() || offen.get(0).getVorher() == null ? null : offen.get(0).getId();
        List<Eintrag> liste = new ArrayList<>();
        for (PlanAenderung a : neueste) {
            liste.add(new Eintrag(a.getId(), a.getZeitpunkt(), a.getBenutzer(), a.getBeschreibung(), a.getVorgangId(), a.getProjektId(), a.getRueckgaengigAm() != null, a.getId().equals(ruecknehmbar)));
        }
        return liste;
    }

    /** Die Änderung, die sich zurücknehmen lässt: nur die jüngste, noch nicht zurückgenommene mit gemerktem Zustand. */
    @Transactional(readOnly = true)
    public PlanAenderung fuerRueckgaengig(Long id) {
        PlanAenderung a = aenderungen.findById(id).orElseThrow(() -> new IllegalArgumentException("Diese Änderung gibt es nicht (mehr)."));
        if (a.getRueckgaengigAm() != null) {
            throw new IllegalArgumentException("Diese Änderung ist schon zurückgenommen.");
        }
        List<PlanAenderung> offen = aenderungen.offeneNeueste(PageRequest.of(0, 1));
        if (offen.isEmpty() || !offen.get(0).getId().equals(a.getId())) {
            throw new IllegalArgumentException("Es lässt sich nur die letzte Änderung zurücknehmen.");
        }
        if (a.getVorher() == null || a.getVorgangId() == null) {
            throw new IllegalArgumentException("Diese Änderung lässt sich nicht zurücknehmen (Anlegen und Löschen).");
        }
        return a;
    }

    /** Setzt den Vorgang auf den gemerkten Zustand zurück und vermerkt die Änderung als zurückgenommen. */
    @Transactional
    public void wiederherstellen(PlanVorgang v, PlanAenderung a) {
        Map<String, Object> z;
        try {
            z = mapper.readValue(a.getVorher(), MAP);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Der alte Zustand ist nicht lesbar.", e);
        }
        Long typId = zahl(z.get("typId"));
        if (typId != null) {
            typen.findById(typId).ifPresent(v::setTyp); // gibt es die Art nicht mehr, bleibt die jetzige
        }
        v.setBezeichnung(text(z.get("bezeichnung"), v.getBezeichnung()));
        Long dauer = zahl(z.get("dauerTage"));
        if (dauer != null) {
            v.setDauerTage(dauer.intValue());
        }
        v.setDauerHerkunft(text(z.get("dauerHerkunft"), v.getDauerHerkunft()));
        v.setAufwandStunden(z.get("aufwandStunden") == null ? null : new BigDecimal(String.valueOf(z.get("aufwandStunden"))));
        v.setAufwandHerkunft(text(z.get("aufwandHerkunft"), v.getAufwandHerkunft()));
        v.setFesterStart(datum(z.get("festerStart")));
        v.setFruehestensAb(datum(z.get("fruehestensAb")));
        Long rang = zahl(z.get("rang"));
        v.setRang(rang == null ? null : rang.intValue());
        v.setStatus(text(z.get("status"), v.getStatus()));
        v.setPrioritaet(text(z.get("prioritaet"), v.getPrioritaet()));
        v.setRessourceId(zahl(z.get("ressourceId")));
        Set<Long> personen = new HashSet<>();
        if (z.get("mitarbeiterIds") instanceof List<?> ids) {
            ids.forEach(id -> personen.add(zahl(id)));
        }
        v.getMitarbeiterIds().clear();
        v.getMitarbeiterIds().addAll(personen);
        v.setNotiz(z.get("notiz") == null ? null : String.valueOf(z.get("notiz")));
        if (z.containsKey("lieferantId")) { // ältere Einträge kennen die Lieferung noch nicht
            v.setLieferantId(zahl(z.get("lieferantId")));
            v.setBestelltAm(datum(z.get("bestelltAm")));
            v.setLieferterminLieferant(datum(z.get("lieferterminLieferant")));
        }
        a.setRueckgaengigAm(LocalDateTime.now());
        aenderungen.save(a);
    }

    private static Long zahl(Object o) {
        return o instanceof Number n ? Long.valueOf(n.longValue()) : null;
    }

    private static String text(Object o, String ersatz) {
        return o == null ? ersatz : String.valueOf(o);
    }

    private static LocalDate datum(Object o) {
        return o == null ? null : LocalDate.parse(String.valueOf(o));
    }

    // ───────────────────────────── Text der Änderung ─────────────────────────────

    /** Was sich geändert hat, in Worten für die Liste; leer, wenn nichts. */
    String beschreibung(Map<String, Object> vorher, Map<String, Object> jetzt) {
        List<String> teile = new ArrayList<>();
        if (!gleich(vorher, jetzt, "bezeichnung")) {
            teile.add("umbenannt in „" + jetzt.get("bezeichnung") + "“");
        }
        if (!gleich(vorher, jetzt, "typId")) {
            teile.add("andere Vorgangsart");
        }
        if (!zahlGleich(vorher.get("aufwandStunden"), jetzt.get("aufwandStunden"))) {
            teile.add("Aufwand von " + stunden(vorher.get("aufwandStunden")) + " auf " + stunden(jetzt.get("aufwandStunden")));
        } else if (!gleich(vorher, jetzt, "dauerTage") && "VON_HAND".equals(jetzt.get("dauerHerkunft")) && !gleich(vorher, jetzt, "dauerHerkunft")) {
            teile.add("Dauer von " + vorher.get("dauerTage") + " auf " + jetzt.get("dauerTage") + " Tage");
        }
        if (!gleich(vorher, jetzt, "festerStart")) {
            teile.add(jetzt.get("festerStart") == null ? "fester Start aufgehoben" : "fester Start am " + tag(jetzt.get("festerStart")));
        }
        if (!gleich(vorher, jetzt, "fruehestensAb")) {
            teile.add(jetzt.get("fruehestensAb") == null ? "„frühestens ab“ aufgehoben" : "frühestens ab " + tag(jetzt.get("fruehestensAb")));
        }
        if (!gleich(vorher, jetzt, "rang")) {
            teile.add(jetzt.get("rang") == null ? "Reihenfolge nicht mehr festgelegt" : "Reihenfolge " + jetzt.get("rang"));
        }
        if (!gleich(vorher, jetzt, "prioritaet")) {
            teile.add("Wichtigkeit von " + prioText(vorher.get("prioritaet")) + " auf " + prioText(jetzt.get("prioritaet")));
        }
        if (!gleich(vorher, jetzt, "status")) {
            teile.add("Stand " + String.valueOf(jetzt.get("status")).toLowerCase(Locale.ROOT).replace("laeuft", "läuft"));
        }
        if (!Objects.equals(vorher.get("mitarbeiterIds"), jetzt.get("mitarbeiterIds"))) {
            teile.add("Mitarbeiter geändert");
        }
        if (!gleich(vorher, jetzt, "ressourceId")) {
            teile.add("Ressource geändert");
        }
        if (!gleich(vorher, jetzt, "lieferantId")) {
            teile.add("Lieferant geändert");
        }
        if (!gleich(vorher, jetzt, "bestelltAm")) {
            teile.add(jetzt.get("bestelltAm") == null ? "Bestelldatum entfernt" : "bestellt am " + tag(jetzt.get("bestelltAm")));
        }
        if (!gleich(vorher, jetzt, "lieferterminLieferant")) {
            teile.add(jetzt.get("lieferterminLieferant") == null ? "Liefertermin entfernt" : "Liefertermin " + tag(jetzt.get("lieferterminLieferant")));
        }
        if (!gleich(vorher, jetzt, "notiz")) {
            teile.add("Notiz geändert");
        }
        return String.join(", ", teile);
    }

    private static boolean gleich(Map<String, Object> a, Map<String, Object> b, String schluessel) {
        return Objects.equals(a.get(schluessel), b.get(schluessel));
    }

    private static boolean zahlGleich(Object a, Object b) {
        if (a == null || b == null) {
            return a == b;
        }
        return new BigDecimal(String.valueOf(a)).compareTo(new BigDecimal(String.valueOf(b))) == 0;
    }

    private static String stunden(Object o) {
        return o == null ? "–" : new BigDecimal(String.valueOf(o)).stripTrailingZeros().toPlainString().replace('.', ',') + " Stunden";
    }

    private static String tag(Object iso) {
        return LocalDate.parse(String.valueOf(iso)).format(TAG);
    }

    private static String prioText(Object p) {
        return switch (String.valueOf(p)) {
            case "DRINGEND" -> "dringend";
            case "NICHT_DRINGEND" -> "nicht dringend";
            default -> "normal";
        };
    }
}
