package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Abwesenheit;
import org.example.kalkulationsprogramm.domain.AbwesenheitsTyp;
import org.example.kalkulationsprogramm.domain.Arbeitsgang;
import org.example.kalkulationsprogramm.domain.ArbeitsgangStundensatz;
import org.example.kalkulationsprogramm.domain.BuchungsTyp;
import org.example.kalkulationsprogramm.domain.ErfassungsQuelle;
import org.example.kalkulationsprogramm.domain.Funktion;
import org.example.kalkulationsprogramm.domain.FunktionsAktion;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.Zeitbuchung;
import org.example.kalkulationsprogramm.repository.AbwesenheitRepository;
import org.example.kalkulationsprogramm.repository.ArbeitsgangRepository;
import org.example.kalkulationsprogramm.repository.ArbeitsgangStundensatzRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/**
 * Arbeitszeit, Pausen und Fahrten in der Handy-App rückwirkend erfassen, ändern und löschen (Issue #70).
 *
 * <p>Regeln: keine Frist außer dem Monatsabschluss (abgeschlossene Monate sind gesperrt), keine Freigabe durch das Büro, keine Buchungen in der
 * Zukunft, keine Überschneidung mit anderen Buchungen. Bei Urlaub, Krankheit oder Zeitausgleich am selben Tag und bei sehr langen Tagen kommt ein
 * <em>Hinweis</em>, den der Mitarbeiter bestätigen muss. Jede Erfassung steht im Prüfprotokoll als nachträglich erfasst (wer, wann, für welchen Tag).
 * Wer das darf, bestimmt die Rechteverwaltung (Funktion {@link Funktion#ZEITERFASSUNG}); die App arbeitet mit dem Zugangslink des Mitarbeiters und
 * jede Aktion prüft das Recht auf dem Server.</p>
 */
@Service
@RequiredArgsConstructor
public class ZeiterfassungNachtragService {

    /** Ab dieser Tagesarbeitszeit (Minuten) fragt die App nach: Arbeitszeitgesetz, zehn Stunden. */
    static final int LANGER_TAG_MINUTEN = 600;

    private static final DateTimeFormatter TAG = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter UHR = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Eingabe für eine neue Buchung. Entweder {@code ende} oder {@code dauerMinuten} (dann wird das Ende berechnet).
     *
     * @param typ        ARBEIT oder PAUSE (Fahrten sind Arbeit mit dem Arbeitsgang Fahrzeit)
     * @param bestaetigt der Mitarbeiter hat die Hinweise gesehen und will trotzdem speichern
     */
    public record Eingabe(String token, String typ, LocalDate datum, LocalTime start, LocalTime ende, Integer dauerMinuten, Long projektId,
            Long arbeitsgangId, Long produktkategorieId, String notiz, boolean bestaetigt, String idempotencyKey) { }

    /** Änderung einer eigenen Buchung (alle Zeitangaben wie bei {@link Eingabe}); {@code grund} ist Pflicht und kommt ins Prüfprotokoll. */
    public record Aenderung(String token, LocalDate datum, LocalTime start, LocalTime ende, Integer dauerMinuten, Long projektId, Long arbeitsgangId,
            Long produktkategorieId, String notiz, String grund, boolean bestaetigt) { }

    /** Ergebnis: die Buchung in der Form der Tagesbuchungen, dazu die bestätigten Hinweise. */
    public record Ergebnis(Map<String, Object> buchung, List<String> hinweise) { }

    /** Der Mitarbeiter muss etwas bestätigen, bevor gespeichert wird. */
    public static class HinweisNoetigException extends RuntimeException {
        private final transient List<String> hinweise;

        public HinweisNoetigException(List<String> hinweise) {
            super(String.join(" ", hinweise));
            this.hinweise = List.copyOf(hinweise);
        }

        public List<String> getHinweise() {
            return hinweise;
        }
    }

    private final FunktionsRechteService rechte;
    private final MitarbeiterRepository mitarbeiterRepository;
    private final ZeitbuchungRepository zeitbuchungRepository;
    private final ProjektRepository projektRepository;
    private final ArbeitsgangRepository arbeitsgangRepository;
    private final ArbeitsgangStundensatzRepository stundensatzRepository;
    private final AbwesenheitRepository abwesenheitRepository;
    private final MonatsSaldoService monatsSaldoService;
    private final ZeitkontoService zeitkontoService;
    private final ZeitbuchungAuditService auditService;

    // ------------------------------------------------------------------ Status eines Tages

    /** Was die App vor dem Nachtragen über einen Tag wissen muss: gesperrt? Abwesenheit? Arbeitszeit bisher? */
    @Transactional(readOnly = true)
    public Map<String, Object> status(String token, LocalDate datum) {
        Mitarbeiter m = rechte.verlangeFuerToken(token, Funktion.ZEITERFASSUNG, FunktionsAktion.ANSEHEN);
        if (datum == null) {
            throw ungueltig("Bitte ein Datum angeben.");
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("datum", datum.toString());
        String sperre = sperrgrund(m, datum);
        r.put("gesperrt", sperre != null);
        r.put("sperrgrund", sperre);
        r.put("zukunft", datum.isAfter(LocalDate.now()));
        r.put("abwesenheiten", abwesenheiten(m, datum).stream().map(a -> bezeichnung(a.getTyp())).toList());
        r.put("arbeitMinuten", arbeitMinuten(tagesBuchungen(m, datum), null));
        r.put("darfAnlegen", rechte.darf(m, Funktion.ZEITERFASSUNG, FunktionsAktion.ANLEGEN));
        r.put("darfAendern", rechte.darf(m, Funktion.ZEITERFASSUNG, FunktionsAktion.AENDERN));
        r.put("darfLoeschen", rechte.darf(m, Funktion.ZEITERFASSUNG, FunktionsAktion.LOESCHEN));
        return r;
    }

    // ------------------------------------------------------------------ Erfassen

    @Transactional
    public Ergebnis erfasse(Eingabe e) {
        return erfasse(e, ErfassungsQuelle.MOBILE_APP, "Handy-App");
    }

    /**
     * Wie {@link #erfasse(Eingabe)}, mit Angabe der Quelle für das Prüfprotokoll (zum Beispiel der KI-Assistent am PC).
     *
     * @param quelleText steht im Prüfprotokoll hinter dem Datum, zum Beispiel "Handy-App"
     */
    @Transactional
    public Ergebnis erfasse(Eingabe e, ErfassungsQuelle quelle, String quelleText) {
        if (e == null) {
            throw ungueltig("Bitte die Angaben zur Buchung machen.");
        }
        rechte.verlangeFuerToken(e.token(), Funktion.ZEITERFASSUNG, FunktionsAktion.ANLEGEN);
        Mitarbeiter m = sperren(e.token());

        if (e.idempotencyKey() != null && !e.idempotencyKey().isBlank()) {
            Optional<Zeitbuchung> vorhanden = zeitbuchungRepository.findByIdempotencyKey(e.idempotencyKey().strip());
            if (vorhanden.isPresent()) {
                pruefeEigentuemer(m, vorhanden.get());
                return new Ergebnis(zeile(vorhanden.get()), List.of());
            }
        }

        BuchungsTyp typ = typ(e.typ());
        Zeitraum z = zeitraum(e.datum(), e.start(), e.ende(), e.dauerMinuten());
        pruefeTag(m, z.datum());
        Projekt projekt = null;
        Arbeitsgang arbeitsgang = null;
        if (typ == BuchungsTyp.ARBEIT) {
            projekt = projekt(e.projektId());
            arbeitsgang = arbeitsgang(e.arbeitsgangId());
        }
        List<Zeitbuchung> tag = tagesBuchungen(m, z.datum());
        pruefeUeberschneidung(tag, z, null);
        List<String> hinweise = hinweise(m, z, typ, tag, null, true, true);
        if (!hinweise.isEmpty() && !e.bestaetigt()) {
            throw new HinweisNoetigException(hinweise);
        }

        Zeitbuchung b = new Zeitbuchung();
        b.setMitarbeiter(m);
        b.setTyp(typ);
        b.setProjekt(projekt);
        b.setArbeitsgang(arbeitsgang);
        if (arbeitsgang != null) {
            SatzWahl.anwenden(b, stundensatz(arbeitsgang.getId(), z.datum().getYear()));
        }
        if (typ == BuchungsTyp.ARBEIT && e.produktkategorieId() != null) {
            setzeKategorie(b, projekt, e.produktkategorieId());
        }
        setzeZeiten(b, z);
        b.setNotiz(notiz(e.notiz()));
        b.setErfasstVon(m);
        b.setErfasstAm(LocalDateTime.now());
        b.setErfasstVia(quelle);
        b.setVersion(1);
        if (e.idempotencyKey() != null && !e.idempotencyKey().isBlank()) {
            b.setIdempotencyKey(e.idempotencyKey().strip());
        }
        Zeitbuchung gespeichert = zeitbuchungRepository.save(b);
        auditService.protokolliereErstellung(gespeichert, m, quelle,
                "Nachträglich erfasst am " + TAG.format(LocalDate.now()) + " " + UHR.format(LocalTime.now()) + " für den " + TAG.format(z.datum()) + " (" + quelleText + ")");
        monatsSaldoService.invalidiereFuerDateTime(m.getId(), gespeichert.getStartZeit());
        return new Ergebnis(zeile(gespeichert), hinweise);
    }

    // ------------------------------------------------------------------ Ändern

    @Transactional
    public Ergebnis aendere(Long id, Aenderung a) {
        if (a == null) {
            throw ungueltig("Bitte die Angaben zur Änderung machen.");
        }
        rechte.verlangeFuerToken(a.token(), Funktion.ZEITERFASSUNG, FunktionsAktion.AENDERN);
        Mitarbeiter m = sperren(a.token());
        Zeitbuchung b = eigeneBuchung(m, id);
        pruefeBeendet(b);
        String grund = grund(a.grund());

        LocalDate altesDatum = b.getStartZeit().toLocalDate();
        long alteDauer = Duration.between(b.getStartZeit(), b.getEndeZeit()).toMinutes();
        pruefeTag(m, altesDatum);
        LocalDate datum = a.datum() != null ? a.datum() : altesDatum;
        Zeitraum z = zeitraum(datum, a.start() != null ? a.start() : b.getStartZeit().toLocalTime(),
                a.ende(), a.ende() == null && a.dauerMinuten() == null ? Integer.valueOf((int) Duration.between(b.getStartZeit(), b.getEndeZeit()).toMinutes()) : a.dauerMinuten());
        if (!datum.equals(altesDatum)) {
            pruefeTag(m, datum);
        }
        if (b.getTyp() == BuchungsTyp.ARBEIT) {
            if (a.projektId() != null) {
                b.setProjekt(projekt(a.projektId()));
                b.setProjektProduktkategorie(null);
            }
            if (a.arbeitsgangId() != null) {
                Arbeitsgang ag = arbeitsgang(a.arbeitsgangId());
                b.setArbeitsgang(ag);
                // Nur neu wählen, wenn sich etwas ändert (anderer Arbeitsgang, anderes Jahr): sonst bleibt der festgehaltene Satz
                if (SatzWahl.neuFuerAenderung(stundensatzRepository, b, ag.getId(), z.datum().getYear()).isPresent() || b.getArbeitsgangStundensatz() == null) {
                    SatzWahl.anwenden(b, stundensatz(ag.getId(), z.datum().getYear()));
                }
            }
            if (a.produktkategorieId() != null) {
                setzeKategorie(b, b.getProjekt(), a.produktkategorieId());
            }
        }
        List<Zeitbuchung> tag = tagesBuchungen(m, z.datum());
        pruefeUeberschneidung(tag, z, b.getId());
        // Beim Ändern fragt der Dienst nur nach, was die Änderung selbst auslöst: ein längerer Tag, wenn die Buchung länger wird, und Abwesenheit, wenn sie auf einen
        // anderen Tag wandert. Sonst käme bei jeder kleinen Korrektur an einem schon langen Tag oder Urlaubstag dieselbe Rückfrage.
        boolean laenger = Duration.between(z.start(), z.ende()).toMinutes() > alteDauer;
        List<String> hinweise = hinweise(m, z, b.getTyp(), tag, b.getId(), laenger, !datum.equals(altesDatum));
        if (!hinweise.isEmpty() && !a.bestaetigt()) {
            throw new HinweisNoetigException(hinweise);
        }

        setzeZeiten(b, z);
        if (a.notiz() != null) {
            b.setNotiz(notiz(a.notiz()));
        }
        b.setAutomatischBeendet(false);
        b.markiereAlsGeaendert(m);
        auditService.protokolliereAenderung(b, m, ErfassungsQuelle.MOBILE_APP, grund);
        Zeitbuchung gespeichert = zeitbuchungRepository.save(b);
        monatsSaldoService.invalidiereFuerDateTime(m.getId(), gespeichert.getStartZeit());
        if (!datum.equals(altesDatum)) {
            monatsSaldoService.invalidiereFuerDateTime(m.getId(), altesDatum.atStartOfDay());
        }
        return new Ergebnis(zeile(gespeichert), hinweise);
    }

    // ------------------------------------------------------------------ Löschen (Storno)

    @Transactional
    public void storniere(String token, Long id, String grundRoh) {
        rechte.verlangeFuerToken(token, Funktion.ZEITERFASSUNG, FunktionsAktion.LOESCHEN);
        Mitarbeiter m = sperren(token);
        Zeitbuchung b = eigeneBuchung(m, id);
        pruefeBeendet(b);
        String grund = grund(grundRoh);
        LocalDateTime start = b.getStartZeit();
        pruefeTag(m, start.toLocalDate());

        b.markiereAlsGeaendert(m);
        zeitbuchungRepository.save(b);
        auditService.protokolliereStorno(b, m, ErfassungsQuelle.MOBILE_APP, grund);
        zeitbuchungRepository.deleteById(b.getId());
        monatsSaldoService.invalidiereFuerDateTime(m.getId(), start);
    }

    // ------------------------------------------------------------------ Prüfungen

    private Mitarbeiter sperren(String token) {
        return mitarbeiterRepository.findByLoginTokenAndAktivTrueForUpdate(token == null ? "" : token.strip())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Der Zugang ist nicht gültig. Bitte neu anmelden."));
    }

    private Zeitbuchung eigeneBuchung(Mitarbeiter m, Long id) {
        Zeitbuchung b = zeitbuchungRepository.findById(id == null ? -1L : id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Die Buchung gibt es nicht (mehr)."));
        pruefeEigentuemer(m, b);
        return b;
    }

    private static void pruefeEigentuemer(Mitarbeiter m, Zeitbuchung b) {
        if (b.getMitarbeiter() == null || !Objects.equals(b.getMitarbeiter().getId(), m.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Diese Buchung gehört nicht zu Ihrem Zugang.");
        }
    }

    private static void pruefeBeendet(Zeitbuchung b) {
        if (b.getStartZeit() == null || b.getEndeZeit() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Diese Buchung läuft noch. Bitte erst beenden, dann lässt sie sich ändern.");
        }
    }

    /** Zeiterfassung an, Arbeitszeit für den Tag hinterlegt (Geschäftsführer ausgenommen), Monat nicht abgeschlossen. */
    private void pruefeTag(Mitarbeiter m, LocalDate tag) {
        String sperre = sperrgrund(m, tag);
        if (sperre != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, sperre);
        }
    }

    private String sperrgrund(Mitarbeiter m, LocalDate tag) {
        if (!Boolean.TRUE.equals(m.getFuehrtZeitkonto())) {
            return "Die Zeiterfassung ist für Sie ausgeschaltet.";
        }
        if (monatsSaldoService.isMonatFestgeschrieben(m.getId(), tag.getYear(), tag.getMonthValue())) {
            return "Der Monat " + tag.getMonth().getDisplayName(java.time.format.TextStyle.FULL, Locale.GERMAN) + " " + tag.getYear()
                    + " ist abgeschlossen. Nachträge sind dort nicht mehr möglich; wende dich ans Büro.";
        }
        if (!Boolean.TRUE.equals(m.getIstGeschaeftsfuehrer()) && zeitkontoService.versionAm(m.getId(), tag).isEmpty()) {
            return "Für den " + TAG.format(tag) + " ist keine Arbeitszeit hinterlegt. Bitte im Büro nachfragen.";
        }
        return null;
    }

    private record Zeitraum(LocalDate datum, LocalDateTime start, LocalDateTime ende) { }

    private static Zeitraum zeitraum(LocalDate datum, LocalTime start, LocalTime ende, Integer dauerMinuten) {
        if (datum == null) {
            throw ungueltig("Bitte das Datum angeben.");
        }
        if (datum.isAfter(LocalDate.now())) {
            throw ungueltig("Für die Zukunft lässt sich nichts nachtragen.");
        }
        if (start == null) {
            throw ungueltig("Bitte die Startzeit angeben.");
        }
        LocalDateTime s = datum.atTime(start.withSecond(0).withNano(0));
        LocalDateTime e;
        if (ende != null) {
            e = datum.atTime(ende.withSecond(0).withNano(0));
        } else if (dauerMinuten != null) {
            if (dauerMinuten <= 0) {
                throw ungueltig("Die Dauer muss größer als 0 sein.");
            }
            e = s.plusMinutes(dauerMinuten);
        } else {
            throw ungueltig("Bitte das Ende oder die Dauer angeben.");
        }
        if (!e.isAfter(s)) {
            throw ungueltig("Das Ende muss nach dem Start liegen.");
        }
        if (!e.toLocalDate().equals(datum)) {
            throw ungueltig("Eine Buchung endet am selben Tag. Für eine Schicht über Mitternacht bitte zwei Buchungen eintragen.");
        }
        if (e.isAfter(LocalDateTime.now())) {
            throw ungueltig("Das Ende liegt in der Zukunft. Für die Zukunft lässt sich nichts nachtragen.");
        }
        return new Zeitraum(datum, s, e);
    }

    private static BuchungsTyp typ(String roh) {
        if (roh == null || roh.isBlank()) {
            return BuchungsTyp.ARBEIT;
        }
        try {
            return BuchungsTyp.valueOf(roh.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ungueltig("Die Art der Buchung ist unbekannt. Möglich sind Arbeit und Pause.");
        }
    }

    private Projekt projekt(Long id) {
        if (id == null) {
            throw ungueltig("Bitte ein Projekt wählen.");
        }
        return projektRepository.findById(id).orElseThrow(() -> ungueltig("Das Projekt gibt es nicht."));
    }

    private Arbeitsgang arbeitsgang(Long id) {
        if (id == null) {
            throw ungueltig("Bitte einen Arbeitsgang wählen.");
        }
        return arbeitsgangRepository.findById(id).orElseThrow(() -> ungueltig("Den Arbeitsgang gibt es nicht."));
    }

    private ArbeitsgangStundensatz stundensatz(Long arbeitsgangId, int jahr) {
        return SatzWahl.finde(stundensatzRepository, arbeitsgangId, jahr)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Für diesen Arbeitsgang ist noch kein Stundensatz hinterlegt. Bitte im Büro nachfragen."));
    }

    private static void setzeKategorie(Zeitbuchung b, Projekt projekt, Long produktkategorieId) {
        b.setProjektProduktkategorie(projekt == null || projekt.getProjektProduktkategorien() == null ? null
                : projekt.getProjektProduktkategorien().stream()
                        .filter(pk -> pk.getProduktkategorie() != null && produktkategorieId.equals(pk.getProduktkategorie().getId()))
                        .findFirst().orElse(null));
    }

    private static void setzeZeiten(Zeitbuchung b, Zeitraum z) {
        b.setStartZeit(z.start());
        b.setEndeZeit(z.ende());
        b.setAnzahlInStunden(BigDecimal.valueOf(Duration.between(z.start(), z.ende()).toMinutes()).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP));
    }

    private static String notiz(String roh) {
        if (roh == null || roh.isBlank()) {
            return null;
        }
        String n = roh.strip();
        if (n.length() > 500) {
            throw ungueltig("Die Notiz ist zu lang (höchstens 500 Zeichen).");
        }
        return n;
    }

    private static String grund(String roh) {
        String g = roh == null ? "" : roh.strip();
        if (g.length() < 3) {
            throw ungueltig("Bitte kurz sagen, warum (zum Beispiel „Falsche Zeit eingetragen“). Das steht im Prüfprotokoll.");
        }
        return g.length() > 250 ? g.substring(0, 250) : g;
    }

    private List<Zeitbuchung> tagesBuchungen(Mitarbeiter m, LocalDate tag) {
        return zeitbuchungRepository.findByMitarbeiterIdAndStartZeitBetween(m.getId(), tag.atStartOfDay(), tag.plusDays(1).atStartOfDay().minusNanos(1));
    }

    private List<Abwesenheit> abwesenheiten(Mitarbeiter m, LocalDate tag) {
        return abwesenheitRepository.findByMitarbeiterIdAndDatumBetween(m.getId(), tag, tag);
    }

    private static LocalDateTime endeFuerVergleich(Zeitbuchung b) {
        if (b.getEndeZeit() != null) {
            return b.getEndeZeit();
        }
        // läuft noch: bis jetzt, mindestens ein Augenblick nach dem Start
        LocalDateTime jetzt = LocalDateTime.now();
        return jetzt.isAfter(b.getStartZeit()) ? jetzt : b.getStartZeit().plusMinutes(1);
    }

    private static void pruefeUeberschneidung(List<Zeitbuchung> tag, Zeitraum z, Long eigeneId) {
        for (Zeitbuchung b : tag) {
            if (b.getStartZeit() == null || (eigeneId != null && eigeneId.equals(b.getId()))) {
                continue;
            }
            LocalDateTime bEnde = endeFuerVergleich(b);
            if (z.start().isBefore(bEnde) && z.ende().isAfter(b.getStartZeit())) {
                String was = b.getTyp() == BuchungsTyp.PAUSE ? "Pause" : b.getProjekt() != null ? "Buchung auf " + nz(b.getProjekt().getBauvorhaben()) : "Buchung";
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Das überschneidet sich mit einer anderen Buchung (" + was.strip() + " von "
                        + UHR.format(b.getStartZeit()) + " bis " + (b.getEndeZeit() == null ? "jetzt" : UHR.format(b.getEndeZeit())) + " Uhr). Bitte die Zeiten anpassen.");
            }
        }
    }

    private List<String> hinweise(Mitarbeiter m, Zeitraum z, BuchungsTyp typ, List<Zeitbuchung> tag, Long eigeneId, boolean langenTagPruefen, boolean abwesenheitPruefen) {
        List<String> h = new ArrayList<>();
        List<Abwesenheit> abw = abwesenheitPruefen ? abwesenheiten(m, z.datum()) : List.of();
        if (!abw.isEmpty()) {
            h.add("Am " + TAG.format(z.datum()) + " ist bereits " + String.join(" und ", abw.stream().map(a -> bezeichnung(a.getTyp())).distinct().toList())
                    + " eingetragen.");
        }
        if (typ == BuchungsTyp.ARBEIT && langenTagPruefen) {
            long minuten = arbeitMinuten(tag, eigeneId) + Duration.between(z.start(), z.ende()).toMinutes();
            if (minuten > LANGER_TAG_MINUTEN) {
                h.add("Mit dieser Buchung kommen Sie am " + TAG.format(z.datum()) + " auf " + stundenText(minuten) + " Arbeitszeit (mehr als 10 Stunden).");
            }
        }
        return h;
    }

    private static long arbeitMinuten(List<Zeitbuchung> tag, Long ohneId) {
        long summe = 0;
        for (Zeitbuchung b : tag) {
            if (b.getTyp() == BuchungsTyp.PAUSE || b.getStartZeit() == null || (ohneId != null && ohneId.equals(b.getId()))) {
                continue;
            }
            summe += Duration.between(b.getStartZeit(), endeFuerVergleich(b)).toMinutes();
        }
        return summe;
    }

    private static String stundenText(long minuten) {
        return String.format(Locale.GERMAN, "%d:%02d Stunden", minuten / 60, minuten % 60);
    }

    private static String bezeichnung(AbwesenheitsTyp typ) {
        return switch (typ) {
            case URLAUB -> "Urlaub";
            case KRANKHEIT -> "Krankheit";
            case FORTBILDUNG -> "Fortbildung";
            case ZEITAUSGLEICH -> "Zeitausgleich";
        };
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static ResponseStatusException ungueltig(String text) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, text);
    }

    /** Die Buchung in der Form der Tagesbuchungen der App (Minuten ab Mitternacht). */
    private static Map<String, Object> zeile(Zeitbuchung b) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("id", b.getId());
        e.put("startMinuten", b.getStartZeit().getHour() * 60 + b.getStartZeit().getMinute());
        if (b.getEndeZeit() != null) {
            e.put("endeMinuten", b.getEndeZeit().getHour() * 60 + b.getEndeZeit().getMinute());
            e.put("dauerMinuten", (int) Duration.between(b.getStartZeit(), b.getEndeZeit()).toMinutes());
        }
        e.put("projektId", b.getProjekt() == null ? null : b.getProjekt().getId());
        e.put("projektName", b.getProjekt() != null ? b.getProjekt().getBauvorhaben() : b.getTyp() == BuchungsTyp.PAUSE ? "Pause" : null);
        e.put("arbeitsgangId", b.getArbeitsgang() == null ? null : b.getArbeitsgang().getId());
        e.put("taetigkeit", b.getArbeitsgang() == null ? null : b.getArbeitsgang().getBeschreibung());
        e.put("kommentar", b.getNotiz());
        e.put("typ", b.getTyp() == null ? "ARBEIT" : b.getTyp().name());
        return e;
    }
}
