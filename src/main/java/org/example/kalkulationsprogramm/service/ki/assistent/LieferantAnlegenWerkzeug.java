package org.example.kalkulationsprogramm.service.ki.assistent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.event.EmailAddressChangedEvent;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.service.LieferantEmailResolver;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

/**
 * Änderung (nur als Vorschlag mit Bestätigung): legt einen Lieferanten mit Anschrift, Telefon und E-Mail an, zum Beispiel aus einem Dokument im Chat. Legt dasselbe an wie
 * „Neuer Lieferant“ am PC (aktiv, ohne Rollen). Gibt es den Namen schon, wird nichts angelegt; ähnliche Namen stehen als Warnung in der Vorschau.
 */
@Component
@RequiredArgsConstructor
public class LieferantAnlegenWerkzeug implements KiWerkzeug {

    static final int MAX_NAME = 200;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]++@[^@\\s.]++(?:\\.[^@\\s.]++)++$");

    private final LieferantenRepository lieferanten;
    private final LieferantEmailResolver emailResolver;
    private final ApplicationEventPublisher events;

    @Override
    public String name() {
        return "lieferant_anlegen";
    }

    @Override
    public String beschreibung() {
        return "Legt einen neuen Lieferanten an (nur nach Bestätigung des Benutzers). Vorher mit lieferanten_suchen prüfen, ob es ihn schon gibt. Pflicht ist nur der Name; "
                + "Straße, Postleitzahl, Ort, Telefon, E-Mail und Ansprechpartner sind optional und nur anzugeben, wenn sie im Dokument oder in der Nachricht stehen. Nichts erfinden.";
    }

    @Override
    public JsonNode parameter() {
        ObjectNode o = WerkzeugHilfe.JSON.createObjectNode().put("type", "object");
        ObjectNode p = o.putObject("properties");
        p.putObject("name").put("type", "string").put("description", "Name der Firma, zum Beispiel \"Meier Pulverbeschichtung\"");
        p.putObject("strasse").put("type", "string").put("description", "Straße und Hausnummer (optional)");
        p.putObject("plz").put("type", "string").put("description", "Postleitzahl (optional)");
        p.putObject("ort").put("type", "string").put("description", "Ort (optional)");
        p.putObject("telefon").put("type", "string").put("description", "Telefonnummer (optional)");
        p.putObject("email").put("type", "string").put("description", "E-Mail-Adresse (optional)");
        p.putObject("ansprechpartner").put("type", "string").put("description", "Name des Ansprechpartners (optional)");
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

    private record Eingabe(String name, String strasse, String plz, String ort, String telefon, String email, String ansprechpartner) { }

    private Eingabe lies(JsonNode a) {
        String name = WerkzeugHilfe.text(a, "name", MAX_NAME, true, "den Namen");
        if (name.length() < 2) {
            throw new KiWerkzeugFehler("Der Name ist zu kurz.");
        }
        String email = WerkzeugHilfe.text(a, "email", 200, false, "die E-Mail-Adresse");
        if (email != null && !EMAIL.matcher(email).matches()) {
            throw new KiWerkzeugFehler("Die E-Mail-Adresse \"" + email + "\" ist nicht gültig.");
        }
        return new Eingabe(name, WerkzeugHilfe.text(a, "strasse", 200, false, "die Straße"), WerkzeugHilfe.text(a, "plz", 10, false, "die Postleitzahl"),
                WerkzeugHilfe.text(a, "ort", 100, false, "der Ort"), WerkzeugHilfe.text(a, "telefon", 50, false, "die Telefonnummer"), email == null ? null : email.toLowerCase(Locale.ROOT),
                WerkzeugHilfe.text(a, "ansprechpartner", 200, false, "der Ansprechpartner"));
    }

    @Override
    @Transactional(readOnly = true)
    public String vorschau(JsonNode angaben) {
        Eingabe e = lies(angaben);
        lieferanten.findByLieferantennameIgnoreCase(e.name()).ifPresent(vorhanden -> {
            throw new KiWerkzeugFehler("Den Lieferanten \"" + vorhanden.getLieferantenname() + "\" gibt es schon (ID " + vorhanden.getId() + "). Es wird nichts angelegt.");
        });
        StringBuilder sb = new StringBuilder("Lieferant anlegen: \"").append(e.name()).append('"');
        List<String> anschrift = new ArrayList<>();
        if (e.strasse() != null) {
            anschrift.add(e.strasse());
        }
        String ortszeile = ((e.plz() == null ? "" : e.plz() + " ") + (e.ort() == null ? "" : e.ort())).strip();
        if (!ortszeile.isEmpty()) {
            anschrift.add(ortszeile);
        }
        if (!anschrift.isEmpty()) {
            sb.append(", ").append(String.join(", ", anschrift));
        }
        if (e.telefon() != null) {
            sb.append(", Tel. ").append(e.telefon());
        }
        if (e.email() != null) {
            sb.append(", E-Mail ").append(e.email());
        }
        if (e.ansprechpartner() != null) {
            sb.append(", Ansprechpartner ").append(e.ansprechpartner());
        }
        String gesucht = KiBelegService.normal(e.name());
        List<String> aehnlich = new ArrayList<>();
        for (Lieferanten l : lieferanten.findAll()) {
            if (KiBelegService.aehnlichkeit(gesucht, KiBelegService.normal(l.getLieferantenname())) >= 0.5) {
                aehnlich.add("ID " + l.getId() + " " + l.getLieferantenname());
            }
            if (aehnlich.size() >= 3) {
                break;
            }
        }
        if (!aehnlich.isEmpty()) {
            sb.append(". ACHTUNG, ähnliche Lieferanten gibt es schon: ").append(String.join("; ", aehnlich));
        }
        return sb.toString();
    }

    @Override
    @Transactional
    public String ausfuehren(JsonNode angaben, KiKontext kontext) {
        Eingabe e = lies(angaben);
        if (lieferanten.findByLieferantennameIgnoreCase(e.name()).isPresent()) {
            throw new KiWerkzeugFehler("Den Lieferanten \"" + e.name() + "\" gibt es schon. Es wurde nichts angelegt.");
        }
        Lieferanten l = new Lieferanten();
        l.setLieferantenname(e.name());
        l.setStrasse(e.strasse());
        l.setPlz(e.plz());
        l.setOrt(e.ort());
        l.setTelefon(e.telefon());
        l.setVertreter(e.ansprechpartner());
        l.setIstAktiv(Boolean.TRUE);
        l.setRollen(new HashSet<>());
        l.setKundenEmails(e.email() == null ? new ArrayList<>() : new ArrayList<>(List.of(e.email())));
        Lieferanten gespeichert = lieferanten.save(l);
        try {
            emailResolver.refresh();
            events.publishEvent(EmailAddressChangedEvent.forNewEntity(EmailAddressChangedEvent.EntityType.LIEFERANT, gespeichert.getId(), gespeichert.getKundenEmails()));
        } catch (Exception ignoriert) {
            // Wie am PC: Das Zuordnen alter E-Mails ist Beiwerk und darf das Anlegen nicht zurückrollen
        }
        return "Der Lieferant \"" + gespeichert.getLieferantenname() + "\" wurde angelegt (ID " + gespeichert.getId() + ").";
    }
}
