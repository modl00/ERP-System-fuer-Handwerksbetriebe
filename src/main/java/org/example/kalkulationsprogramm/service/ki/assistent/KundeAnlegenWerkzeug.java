package org.example.kalkulationsprogramm.service.ki.assistent;

import java.util.List;

import org.example.kalkulationsprogramm.domain.Anrede;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.service.KundeDuplikatService;
import org.example.kalkulationsprogramm.service.KundennummerService;
import org.example.kalkulationsprogramm.util.Telefonnummer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

/**
 * Änderung (nur als Vorschlag mit Bestätigung): legt einen neuen Kunden an. Vor der Vorschau wird ein Duplikat-Check gemacht, damit keine doppelten Kunden entstehen.
 */
@Component
@RequiredArgsConstructor
public class KundeAnlegenWerkzeug implements KiWerkzeug {

    static final int MAX_NAME = 200;
    static final int MAX_TEXT = 200;

    private final KundeRepository kunden;
    private final KundennummerService kundennummerService;
    private final KundeDuplikatService duplikatService;

    @Override
    public String name() {
        return "kunde_anlegen";
    }

    @Override
    public String beschreibung() {
        return "Legt einen neuen Kunden an (nur nach Bestätigung des Benutzers). Vorher mit kunden_suchen prüfen, ob es den Kunden schon gibt. Pflicht: Firmen- oder Nachname. Optional: Anrede, Ansprechpartner, Adresse, Telefon, E-Mail.";
    }

    @Override
    public JsonNode parameter() {
        ObjectNode o = WerkzeugHilfe.JSON.createObjectNode().put("type", "object");
        ObjectNode p = o.putObject("properties");
        p.putObject("name").put("type", "string").put("description", "Firmenname oder Nachname des Kunden (Pflichtfeld)");
        p.putObject("anrede").put("type", "string").put("description", "Anrede des Kunden: Herr, Frau, Firma oder Damen und Herren (optional)");
        p.putObject("ansprechpartner").put("type", "string").put("description", "Ansprechpartner (Vorname Nachname, optional)");
        p.putObject("strasse").put("type", "string").put("description", "Straße und Hausnummer (optional)");
        p.putObject("plz").put("type", "string").put("description", "Postleitzahl (optional)");
        p.putObject("ort").put("type", "string").put("description", "Ort (optional)");
        p.putObject("telefon").put("type", "string").put("description", "Telefonnummer (optional)");
        p.putObject("email").put("type", "string").put("description", "E-Mail-Adresse (optional)");
        o.putArray("required").add("name");
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

    private record Eingabe(String name, Anrede anrede, String ansprechpartner, String strasse, String plz, String ort, String telefon, String email) { }

    private Eingabe lies(JsonNode a) {
        String name = WerkzeugHilfe.text(a, "name", MAX_NAME, true, "der Name");
        if (name.length() < 2) {
            throw new KiWerkzeugFehler("Der Name ist zu kurz.");
        }
        String anredeText = WerkzeugHilfe.text(a, "anrede", 40, false, "die Anrede");
        Anrede anrede = anredeText == null ? Anrede.FIRMA : anredeAus(anredeText);
        String ansprechpartner = WerkzeugHilfe.text(a, "ansprechpartner", MAX_TEXT, false, "der Ansprechpartner");
        String strasse = WerkzeugHilfe.text(a, "strasse", MAX_TEXT, false, "die Straße");
        String plz = WerkzeugHilfe.text(a, "plz", 10, false, "die PLZ");
        String ort = WerkzeugHilfe.text(a, "ort", MAX_TEXT, false, "der Ort");
        String telefon = WerkzeugHilfe.text(a, "telefon", 50, false, "die Telefonnummer");
        String email = WerkzeugHilfe.text(a, "email", 255, false, "die E-Mail");
        if (email != null && !email.contains("@")) {
            throw new KiWerkzeugFehler("Die E-Mail-Adresse ist ungültig.");
        }
        return new Eingabe(name, anrede, ansprechpartner, strasse, plz, ort, telefon, email);
    }

    @Override
    @Transactional(readOnly = true)
    public String vorschau(JsonNode angaben) {
        Eingabe e = lies(angaben);
        StringBuilder sb = new StringBuilder("Kunden anlegen: \"").append(e.name()).append('"');
        if (e.ansprechpartner() != null) {
            sb.append(", Ansprechpartner ").append(e.ansprechpartner());
        }
        if (e.strasse() != null || e.ort() != null) {
            sb.append(", ");
            if (e.strasse() != null) sb.append(e.strasse()).append(", ");
            if (e.plz() != null) sb.append(e.plz()).append(' ');
            if (e.ort() != null) sb.append(e.ort());
        }
        if (e.telefon() != null) sb.append(", Tel. ").append(e.telefon());
        if (e.email() != null) sb.append(", ").append(e.email());

        // Duplikat-Warnung
        var duplikate = duplikatService.findeDuplikate(e.email(), e.telefon(), null, e.name(), e.plz(), e.strasse());
        List<?> treffer = duplikate.getDuplikate();
        if (treffer != null && !treffer.isEmpty()) {
            sb.append(". ACHTUNG: ähnliche Kunden bereits vorhanden – bitte prüfen, ob dieser Kunde schon existiert.");
        }
        return sb.toString();
    }

    @Override
    @Transactional
    public String ausfuehren(JsonNode angaben, KiKontext kontext) {
        Eingabe e = lies(angaben);
        Kunde k = new Kunde();
        k.setKundennummer(kundennummerService.reserviereNaechsteKundennummer());
        k.setName(e.name());
        k.setAnrede(e.anrede());
        k.setAnsprechspartner(e.ansprechpartner());
        k.setStrasse(e.strasse());
        k.setPlz(e.plz());
        k.setOrt(e.ort());
        k.setTelefon(Telefonnummer.ohneReineVorwahl(e.telefon()));
        k.setZahlungsziel(8);
        if (e.email() != null) {
            k.getKundenEmails().add(e.email());
        }
        Kunde gespeichert = kunden.save(k);
        return "Kunde \"" + gespeichert.getName() + "\" wurde angelegt (Kundennummer " + gespeichert.getKundennummer() + ", ID " + gespeichert.getId() + ").";
    }

    static Anrede anredeAus(String text) {
        String t = text.strip().toUpperCase(java.util.Locale.GERMAN).replace(' ', '_').replace("UND", "").replace("__", "_");
        try {
            return Anrede.valueOf(t);
        } catch (IllegalArgumentException e) {
            // weiter mit gebräuchlichen Schreibweisen
        }
        return switch (t) {
            case "HERR", "MANN", "MÄNNLICH" -> Anrede.HERR;
            case "FRAU", "FRAU_", "WEIBLICH" -> Anrede.FRAU;
            case "DAMEN_HERREN", "DAMEN_HERREN_", "SEHR_GEEHRTE_DAMEN_HERREN" -> Anrede.DAMEN_HERREN;
            default -> Anrede.FIRMA;
        };
    }
}
