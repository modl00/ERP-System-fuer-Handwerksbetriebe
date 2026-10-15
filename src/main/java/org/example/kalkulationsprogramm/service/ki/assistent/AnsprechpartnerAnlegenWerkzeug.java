package org.example.kalkulationsprogramm.service.ki.assistent;

import java.util.Locale;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.dto.Kunde.KundeAnsprechpartnerSpeichernDto;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.service.KundeAnsprechpartnerService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

/**
 * Änderung (nur als Vorschlag mit Bestätigung): legt zu einem Kunden einen weiteren Ansprechpartner an, zum Beispiel aus einer E-Mail oder einem Dokument im Chat.
 * Er wird nicht zum Hauptansprechpartner; das lässt sich am PC ändern. Dieselbe Prüfung wie am PC (höchstens so viele je Kunde wie dort erlaubt). Gibt es zum Kunden schon
 * einen Ansprechpartner mit diesem Namen, wird nichts angelegt.
 */
@Component
@RequiredArgsConstructor
public class AnsprechpartnerAnlegenWerkzeug implements KiWerkzeug {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]++@[^@\\s.]++(?:\\.[^@\\s.]++)++$");

    private final KundeRepository kunden;
    private final KundeAnsprechpartnerService service;

    @Override
    public String name() {
        return "ansprechpartner_anlegen";
    }

    @Override
    public String beschreibung() {
        return "Legt zu einem Kunden einen weiteren Ansprechpartner an (nur nach Bestätigung des Benutzers). Vorher mit kunden_suchen die ID des Kunden holen. Pflicht: kundeId und Name; "
                + "Funktion, Telefon, Mobil und E-Mail nur angeben, wenn sie im Dokument oder in der Nachricht stehen. Nichts erfinden.";
    }

    @Override
    public JsonNode parameter() {
        ObjectNode o = WerkzeugHilfe.JSON.createObjectNode().put("type", "object");
        ObjectNode p = o.putObject("properties");
        p.putObject("kundeId").put("type", "integer").put("description", "ID des Kunden (aus kunden_suchen)");
        p.putObject("name").put("type", "string").put("description", "Name der Person, zum Beispiel \"Erika Mustermann\"");
        p.putObject("funktion").put("type", "string").put("description", "Funktion oder Abteilung, zum Beispiel \"Einkauf\" (optional)");
        p.putObject("telefon").put("type", "string").put("description", "Telefonnummer (optional)");
        p.putObject("mobil").put("type", "string").put("description", "Mobilnummer (optional)");
        p.putObject("email").put("type", "string").put("description", "E-Mail-Adresse (optional)");
        o.putArray("required").add("kundeId").add("name");
        return o;
    }

    @Override
    public boolean aendert() {
        return true;
    }

    @Override
    public boolean verfuegbar(KiKontext kontext) {
        return true;
    }

    private record Eingabe(Kunde kunde, String name, String funktion, String telefon, String mobil, String email) { }

    private Eingabe lies(JsonNode a) {
        Long kundeId = WerkzeugHilfe.nummer(a, "kundeId", "die Kunden-ID");
        if (kundeId == null) {
            throw new KiWerkzeugFehler("Bitte die Kunden-ID angeben (zum Beispiel über die Kunden-Auskunft).");
        }
        Kunde kunde = kunden.findById(kundeId).orElseThrow(() -> new KiWerkzeugFehler("Den Kunden mit dieser ID gibt es nicht."));
        String name = WerkzeugHilfe.text(a, "name", 200, true, "den Namen");
        if (name.length() < 2) {
            throw new KiWerkzeugFehler("Der Name ist zu kurz.");
        }
        String email = WerkzeugHilfe.text(a, "email", 200, false, "die E-Mail-Adresse");
        if (email != null && !EMAIL.matcher(email).matches()) {
            throw new KiWerkzeugFehler("Die E-Mail-Adresse \"" + email + "\" ist nicht gültig.");
        }
        return new Eingabe(kunde, name, WerkzeugHilfe.text(a, "funktion", 120, false, "die Funktion"), WerkzeugHilfe.text(a, "telefon", 60, false, "die Telefonnummer"),
                WerkzeugHilfe.text(a, "mobil", 60, false, "die Mobilnummer"), email == null ? null : email.toLowerCase(Locale.ROOT));
    }

    private void pruefeDoppelt(Eingabe e) {
        boolean schonDa = service.liste(e.kunde().getId()).stream().anyMatch(v -> v.name() != null && v.name().strip().equalsIgnoreCase(e.name()));
        if (schonDa) {
            throw new KiWerkzeugFehler("Bei " + e.kunde().getName() + " gibt es schon einen Ansprechpartner \"" + e.name() + "\". Es wird nichts angelegt.");
        }
    }

    @Override
    @Transactional
    public String vorschau(JsonNode angaben) {
        Eingabe e = lies(angaben);
        pruefeDoppelt(e);
        StringBuilder sb = new StringBuilder("Ansprechpartner anlegen bei ").append(e.kunde().getName()).append(": \"").append(e.name()).append('"');
        if (e.funktion() != null) {
            sb.append(", ").append(e.funktion());
        }
        if (e.telefon() != null) {
            sb.append(", Tel. ").append(e.telefon());
        }
        if (e.mobil() != null) {
            sb.append(", Mobil ").append(e.mobil());
        }
        if (e.email() != null) {
            sb.append(", E-Mail ").append(e.email());
        }
        return sb.toString();
    }

    @Override
    @Transactional
    public String ausfuehren(JsonNode angaben, KiKontext kontext) {
        Eingabe e = lies(angaben);
        pruefeDoppelt(e);
        try {
            service.anlegen(e.kunde().getId(), new KundeAnsprechpartnerSpeichernDto(e.name(), e.funktion(), e.telefon(), e.mobil(), e.email(), null, Boolean.FALSE));
        } catch (ResponseStatusException ex) {
            throw new KiWerkzeugFehler(ex.getStatusCode() == HttpStatus.CONFLICT && ex.getReason() != null ? ex.getReason() : "Der Ansprechpartner konnte nicht angelegt werden.");
        }
        return "Der Ansprechpartner \"" + e.name() + "\" wurde bei " + e.kunde().getName() + " angelegt.";
    }
}
