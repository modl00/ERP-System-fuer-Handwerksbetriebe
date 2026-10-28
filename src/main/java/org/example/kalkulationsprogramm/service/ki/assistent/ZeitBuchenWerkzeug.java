package org.example.kalkulationsprogramm.service.ki.assistent;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

import org.example.kalkulationsprogramm.domain.ErfassungsQuelle;
import org.example.kalkulationsprogramm.domain.Funktion;
import org.example.kalkulationsprogramm.domain.FunktionsAktion;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.ArbeitsgangRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.service.FunktionsRechteService;
import org.example.kalkulationsprogramm.service.ZeiterfassungNachtragService;
import org.example.kalkulationsprogramm.service.ZeiterfassungNachtragService.Eingabe;
import org.example.kalkulationsprogramm.service.ZeiterfassungNachtragService.HinweisNoetigException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

/**
 * Änderung (nur als Vorschlag mit Bestätigung): trägt <b>eigene</b> Arbeitszeit oder Pause rückwirkend ein. Die Buchung macht der
 * {@link ZeiterfassungNachtragService} mit denselben Regeln wie in der Handy-App (Rechte, gesperrter Monat, Überschneidungen); im Prüfprotokoll steht als Quelle der KI-Assistent.
 * Gebucht wird immer für den angemeldeten Benutzer, nie für andere. Braucht ein Eintrag eine Bestätigung (zum Beispiel Urlaub am selben Tag, sehr langer Tag),
 * bucht die KI <b>nicht</b>, sondern nennt den Grund; solche Zeiten trägt der Mensch selbst in der Zeiterfassung ein.
 */
@Component
@RequiredArgsConstructor
public class ZeitBuchenWerkzeug implements KiWerkzeug {

    private static final DateTimeFormatter TAG = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final int MAX_MINUTEN = 24 * 60;

    private final ZeiterfassungNachtragService zeiten;
    private final FunktionsRechteService rechte;
    private final FrontendUserProfileRepository profile;
    private final ProjektRepository projekte;
    private final ArbeitsgangRepository arbeitsgaenge;

    @Override
    public String name() {
        return "zeit_buchen";
    }

    @Override
    public String beschreibung() {
        return "Trägt EIGENE Arbeitszeit oder Pause nachträglich ein (nur nach Bestätigung des Benutzers). Bei Arbeit sind Projekt-ID (aus projekte_suchen) und Arbeitsgang-ID "
                + "(aus arbeitsgaenge_auflisten) Pflicht. Entweder Ende oder Dauer in Minuten angeben. Nur für den angemeldeten Benutzer, nie für andere.";
    }

    @Override
    public JsonNode parameter() {
        ObjectNode o = WerkzeugHilfe.JSON.createObjectNode().put("type", "object");
        ObjectNode p = o.putObject("properties");
        p.putObject("art").put("type", "string").put("description", "ARBEIT (Standard) oder PAUSE").putArray("enum").add("ARBEIT").add("PAUSE");
        p.putObject("datum").put("type", "string").put("description", "Tag der Buchung im Format JJJJ-MM-TT");
        p.putObject("start").put("type", "string").put("description", "Beginn im Format HH:MM");
        p.putObject("ende").put("type", "string").put("description", "Ende im Format HH:MM (oder stattdessen dauerMinuten)");
        p.putObject("dauerMinuten").put("type", "integer").put("description", "Dauer in Minuten, wenn kein Ende angegeben ist");
        p.putObject("projektId").put("type", "integer").put("description", "Nur bei ARBEIT: ID des Projekts (aus projekte_suchen)");
        p.putObject("arbeitsgangId").put("type", "integer").put("description", "Nur bei ARBEIT: ID des Arbeitsgangs (aus arbeitsgaenge_auflisten)");
        p.putObject("notiz").put("type", "string").put("description", "Kurze Notiz zur Buchung (optional)");
        o.putArray("required").add("datum").add("start");
        return o;
    }

    @Override
    public boolean aendert() {
        return true;
    }

    @Override
    public boolean verfuegbar(KiKontext k) {
        if (!k.admin() && !rechte.darfProfil(k.profilId(), Funktion.ZEITERFASSUNG, FunktionsAktion.ANLEGEN)) {
            return false;
        }
        return token(k) != null;
    }

    /** Der Zugangsschlüssel des Mitarbeiters, der zum angemeldeten Benutzer gehört; {@code null}, wenn der Benutzer keinem Mitarbeiter zugeordnet ist. */
    private String token(KiKontext k) {
        if (k.profilId() == null) {
            return null;
        }
        return profile.findById(k.profilId()).map(p -> p.getMitarbeiter()).map(Mitarbeiter::getLoginToken).filter(t -> !t.isBlank()).orElse(null);
    }

    private record Angaben(Eingabe eingabe, String projektName, String arbeitsgangName) { }

    private Angaben lies(JsonNode a, String token) {
        String art = a.path("art").asText("ARBEIT").strip().toUpperCase(Locale.ROOT);
        if (art.isEmpty()) {
            art = "ARBEIT";
        }
        if (!art.equals("ARBEIT") && !art.equals("PAUSE")) {
            throw new KiWerkzeugFehler("Die Art muss ARBEIT oder PAUSE sein.");
        }
        LocalDate datum = datum(WerkzeugHilfe.text(a, "datum", 10, true, "den Tag (JJJJ-MM-TT)"));
        LocalTime start = uhr(WerkzeugHilfe.text(a, "start", 5, true, "den Beginn (HH:MM)"), "Beginn");
        String endeText = WerkzeugHilfe.text(a, "ende", 5, false, "das Ende");
        LocalTime ende = endeText == null ? null : uhr(endeText, "Ende");
        Long dauer = WerkzeugHilfe.nummer(a, "dauerMinuten", "die Dauer");
        if (ende == null && dauer == null) {
            throw new KiWerkzeugFehler("Bitte das Ende oder die Dauer in Minuten angeben.");
        }
        if (ende != null && dauer != null) {
            throw new KiWerkzeugFehler("Bitte entweder das Ende oder die Dauer angeben, nicht beides.");
        }
        if (dauer != null && (dauer <= 0 || dauer > MAX_MINUTEN)) {
            throw new KiWerkzeugFehler("Die Dauer muss zwischen 1 und " + MAX_MINUTEN + " Minuten liegen.");
        }
        Long projektId = WerkzeugHilfe.nummer(a, "projektId", "die Projekt-ID");
        Long arbeitsgangId = WerkzeugHilfe.nummer(a, "arbeitsgangId", "die Arbeitsgang-ID");
        String projektName = null;
        String arbeitsgangName = null;
        if (art.equals("ARBEIT")) {
            if (projektId == null) {
                throw new KiWerkzeugFehler("Bitte das Projekt angeben (zuerst mit projekte_suchen suchen).");
            }
            if (arbeitsgangId == null) {
                throw new KiWerkzeugFehler("Bitte den Arbeitsgang angeben (zuerst mit arbeitsgaenge_auflisten nachsehen).");
            }
            projektName = projekte.findById(projektId).map(p -> p.getBauvorhaben()).orElseThrow(() -> new KiWerkzeugFehler("Das Projekt mit dieser ID gibt es nicht."));
            arbeitsgangName = arbeitsgaenge.findById(arbeitsgangId).map(g -> g.getBeschreibung()).orElseThrow(() -> new KiWerkzeugFehler("Den Arbeitsgang mit dieser ID gibt es nicht."));
        } else if (projektId != null || arbeitsgangId != null) {
            throw new KiWerkzeugFehler("Projekt und Arbeitsgang gibt es nur bei Arbeit, nicht bei einer Pause.");
        }
        String notiz = WerkzeugHilfe.text(a, "notiz", 500, false, "die Notiz");
        return new Angaben(new Eingabe(token, art, datum, start, ende, dauer == null ? null : dauer.intValue(), projektId, arbeitsgangId, null, notiz, false, null), projektName, arbeitsgangName);
    }

    private static LocalDate datum(String text) {
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            throw new KiWerkzeugFehler("Das Datum muss im Format JJJJ-MM-TT angegeben werden, zum Beispiel 2026-10-03.");
        }
    }

    private static LocalTime uhr(String text, String name) {
        try {
            return LocalTime.parse(text.length() == 4 ? "0" + text : text);
        } catch (DateTimeParseException e) {
            throw new KiWerkzeugFehler("Der " + name + " muss im Format HH:MM angegeben werden, zum Beispiel 08:30.");
        }
    }

    private static String beschreibe(Angaben x) {
        Eingabe e = x.eingabe();
        StringBuilder sb = new StringBuilder(e.typ().equals("PAUSE") ? "Pause" : "Arbeitszeit").append(" am ").append(TAG.format(e.datum())).append(" von ").append(e.start());
        if (e.ende() != null) {
            sb.append(" bis ").append(e.ende());
        } else {
            sb.append(" für ").append(e.dauerMinuten() / 60).append(" Std. ").append(e.dauerMinuten() % 60).append(" Min.");
        }
        if (x.projektName() != null) {
            sb.append(", Projekt \"").append(WerkzeugHilfe.kuerzen(x.projektName(), 80)).append("\", Arbeitsgang \"").append(WerkzeugHilfe.kuerzen(x.arbeitsgangName(), 60)).append('"');
        }
        if (e.notiz() != null) {
            sb.append(" (Notiz: ").append(WerkzeugHilfe.kuerzen(e.notiz(), 120)).append(')');
        }
        return sb.toString();
    }

    @Override
    public String vorschau(JsonNode angaben) {
        // Die Vorschau zeigt nur die Angaben; die Prüfungen (Rechte, gesperrter Monat, Überschneidung) macht die Buchung selbst
        return "Eintragen: " + beschreibe(lies(angaben, null));
    }

    @Override
    public String ausfuehren(JsonNode angaben, KiKontext kontext) {
        if (!verfuegbar(kontext)) {
            throw new KiWerkzeugFehler(FunktionsRechteService.fehlermeldung(Funktion.ZEITERFASSUNG, FunktionsAktion.ANLEGEN));
        }
        Angaben x = lies(angaben, token(kontext));
        try {
            zeiten.erfasse(x.eingabe(), ErfassungsQuelle.DESKTOP, "KI-Assistent am PC");
            return "Eingetragen: " + beschreibe(x) + ".";
        } catch (HinweisNoetigException h) {
            throw new KiWerkzeugFehler("Nicht eingetragen, weil das Programm dafür eine Bestätigung verlangt: " + String.join(" ", h.getHinweise())
                    + " Bitte diese Zeit selbst unter Zeiterfassung eintragen und die Hinweise dort bestätigen.");
        } catch (ResponseStatusException e) {
            throw new KiWerkzeugFehler(e.getReason() == null ? "Die Buchung ist nicht möglich." : e.getReason());
        }
    }
}
