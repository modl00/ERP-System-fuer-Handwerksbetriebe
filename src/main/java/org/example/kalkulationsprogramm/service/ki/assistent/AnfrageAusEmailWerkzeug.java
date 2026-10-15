package org.example.kalkulationsprogramm.service.ki.assistent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.EmailZuordnungTyp;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.dto.Anfrage.AnfrageErstellenDto;
import org.example.kalkulationsprogramm.event.EmailAddressChangedEvent;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.service.AnfrageService;
import org.example.kalkulationsprogramm.service.KundennummerService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

/**
 * Änderung (nur als Vorschlag mit Bestätigung): legt aus einer E-Mail im Chat eine <b>Anfrage</b> an, bei Bedarf mit einem <b>neuen Kunden</b>, und verknüpft die Mail damit.
 * Alles geschieht in einem Schritt und in einer Transaktion: Entweder ist am Ende alles da, oder nichts. Gibt es zur Absender-Adresse schon einen Kunden, wird nichts angelegt;
 * die KI muss dann mit {@code kundeId} den vorhandenen Kunden nehmen.
 */
@Component
@RequiredArgsConstructor
public class AnfrageAusEmailWerkzeug implements KiWerkzeug {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]++@[^@\\s.]++(?:\\.[^@\\s.]++)++$");

    private final EmailRepository emails;
    private final KundeRepository kunden;
    private final AnfrageRepository anfragen;
    private final AnfrageService anfrageService;
    private final KundennummerService kundennummern;
    private final ApplicationEventPublisher events;

    /** Optional: markiert die Mail im Postfach, wenn der Administrator das eingeschaltet hat. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.example.kalkulationsprogramm.service.PostfachSpiegelService postfachSpiegel;

    @Override
    public String name() {
        return "anfrage_aus_email_anlegen";
    }

    @Override
    public String beschreibung() {
        return "Legt aus einer E-Mail (im Chat angehängt, mit \"E-Mail-Nr. im Programm\") eine Anfrage an und verknüpft die E-Mail damit (nur nach Bestätigung des Benutzers). "
                + "Vorher IMMER mit kunden_suchen nach dem Absender (E-Mail-Adresse) und der Firma suchen. Gibt es den Kunden: kundeId angeben. Gibt es ihn nicht: kunde_name und, "
                + "soweit in der E-Mail zu finden, die Anschrift, Telefon und Ansprechpartner angeben; dann wird der Kunde mit angelegt. Pflicht sind emailId und bauvorhaben. "
                + "Nichts erfinden: Was nicht in der E-Mail steht, weglassen.";
    }

    @Override
    public JsonNode parameter() {
        ObjectNode o = WerkzeugHilfe.JSON.createObjectNode().put("type", "object");
        ObjectNode p = o.putObject("properties");
        p.putObject("emailId").put("type", "integer").put("description", "Nummer der E-Mail (\"E-Mail-Nr. im Programm\" im angehängten Text)");
        p.putObject("kundeId").put("type", "integer").put("description", "ID eines vorhandenen Kunden (aus kunden_suchen); weglassen, wenn ein neuer Kunde angelegt werden soll");
        p.putObject("kunde_name").put("type", "string").put("description", "Name des neuen Kunden (Firma oder Person); nur wenn kundeId fehlt");
        p.putObject("kunde_strasse").put("type", "string").put("description", "Straße und Hausnummer des neuen Kunden (optional)");
        p.putObject("kunde_plz").put("type", "string").put("description", "Postleitzahl des neuen Kunden (optional)");
        p.putObject("kunde_ort").put("type", "string").put("description", "Ort des neuen Kunden (optional)");
        p.putObject("kunde_telefon").put("type", "string").put("description", "Telefon des neuen Kunden (optional)");
        p.putObject("kunde_ansprechpartner").put("type", "string").put("description", "Ansprechpartner beim neuen Kunden (optional)");
        p.putObject("bauvorhaben").put("type", "string").put("description", "Kurzer Titel der Anfrage, zum Beispiel \"Geländer Mehrfamilienhaus Musterstraße\"");
        p.putObject("kurzbeschreibung").put("type", "string").put("description", "Was angefragt wird, in ein bis drei Sätzen (optional)");
        p.putObject("projektStrasse").put("type", "string").put("description", "Straße des Bauvorhabens (optional)");
        p.putObject("projektPlz").put("type", "string").put("description", "Postleitzahl des Bauvorhabens (optional)");
        p.putObject("projektOrt").put("type", "string").put("description", "Ort des Bauvorhabens (optional)");
        o.putArray("required").add("emailId").add("bauvorhaben");
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

    private record Eingabe(Long emailId, Long kundeId, String kundeName, String kundeStrasse, String kundePlz, String kundeOrt, String kundeTelefon, String kundeAnsprechpartner,
            String bauvorhaben, String kurzbeschreibung, String projektStrasse, String projektPlz, String projektOrt) { }

    private Eingabe lies(JsonNode a) {
        Long emailId = WerkzeugHilfe.nummer(a, "emailId", "die E-Mail-Nummer");
        if (emailId == null) {
            throw new KiWerkzeugFehler("Bitte die Nummer der E-Mail angeben (emailId, steht im angehängten Text).");
        }
        Long kundeId = a.hasNonNull("kundeId") ? WerkzeugHilfe.nummer(a, "kundeId", "die Kunden-ID") : null;
        String name = WerkzeugHilfe.text(a, "kunde_name", 200, false, "der Kundenname");
        if (kundeId == null && (name == null || name.length() < 2)) {
            throw new KiWerkzeugFehler("Bitte entweder kundeId (vorhandener Kunde) oder kunde_name (neuer Kunde) angeben.");
        }
        String bauvorhaben = WerkzeugHilfe.text(a, "bauvorhaben", 200, true, "das Bauvorhaben");
        return new Eingabe(emailId, kundeId, name, WerkzeugHilfe.text(a, "kunde_strasse", 200, false, "die Straße"), WerkzeugHilfe.text(a, "kunde_plz", 10, false, "die Postleitzahl"),
                WerkzeugHilfe.text(a, "kunde_ort", 100, false, "der Ort"), WerkzeugHilfe.text(a, "kunde_telefon", 50, false, "die Telefonnummer"),
                WerkzeugHilfe.text(a, "kunde_ansprechpartner", 200, false, "der Ansprechpartner"), bauvorhaben,
                WerkzeugHilfe.text(a, "kurzbeschreibung", 2000, false, "die Kurzbeschreibung"), WerkzeugHilfe.text(a, "projektStrasse", 200, false, "die Bauvorhaben-Straße"),
                WerkzeugHilfe.text(a, "projektPlz", 10, false, "die Bauvorhaben-Postleitzahl"), WerkzeugHilfe.text(a, "projektOrt", 100, false, "der Bauvorhaben-Ort"));
    }

    /** Prüft alles, was vor dem Anlegen feststehen muss, und liefert Mail und (falls vorhanden) Kunden. */
    private record Pruefung(Email mail, Kunde kunde, String absender) { }

    private Pruefung pruefe(Eingabe e) {
        Email mail = emails.findById(e.emailId()).orElseThrow(() -> new KiWerkzeugFehler("Die E-Mail " + e.emailId() + " gibt es nicht."));
        if (mail.getDirection() != EmailDirection.IN) {
            throw new KiWerkzeugFehler("Nur eingehende E-Mails lassen sich zu einer Anfrage machen.");
        }
        if (mail.getZuordnungTyp() != EmailZuordnungTyp.KEINE) {
            throw new KiWerkzeugFehler("Diese E-Mail ist schon zugeordnet (" + mail.getZuordnungTyp() + "). Es wird nichts angelegt.");
        }
        String absender = mail.getFromAddress() == null ? null : mail.getFromAddress().strip().toLowerCase(Locale.ROOT);
        if (absender != null && !EMAIL.matcher(absender).matches()) {
            absender = null;
        }
        Kunde kunde = null;
        if (e.kundeId() != null) {
            kunde = kunden.findById(e.kundeId()).orElseThrow(() -> new KiWerkzeugFehler("Den Kunden " + e.kundeId() + " gibt es nicht."));
        } else if (absender != null) {
            List<Kunde> vorhanden = kunden.findByKundenEmailIgnoreCase(absender);
            if (!vorhanden.isEmpty()) {
                Kunde k = vorhanden.get(0);
                throw new KiWerkzeugFehler("Zu " + absender + " gibt es schon den Kunden \"" + k.getName() + "\" (ID " + k.getId() + "). Bitte kundeId " + k.getId() + " angeben, es wird kein zweiter Kunde angelegt.");
            }
        }
        return new Pruefung(mail, kunde, absender);
    }

    @Override
    @Transactional(readOnly = true)
    public String vorschau(JsonNode angaben) {
        Eingabe e = lies(angaben);
        Pruefung p = pruefe(e);
        StringBuilder sb = new StringBuilder();
        if (p.kunde() != null) {
            sb.append("Vorhandenen Kunden verwenden: ").append(p.kunde().getName()).append(" (Kundennr. ").append(p.kunde().getKundennummer()).append(")");
        } else {
            sb.append("NEUEN Kunden anlegen: \"").append(e.kundeName()).append('"');
            List<String> teile = new ArrayList<>();
            String ort = ((e.kundePlz() == null ? "" : e.kundePlz() + " ") + (e.kundeOrt() == null ? "" : e.kundeOrt())).strip();
            if (e.kundeStrasse() != null) teile.add(e.kundeStrasse());
            if (!ort.isEmpty()) teile.add(ort);
            if (e.kundeTelefon() != null) teile.add("Tel. " + e.kundeTelefon());
            if (e.kundeAnsprechpartner() != null) teile.add("Ansprechpartner " + e.kundeAnsprechpartner());
            if (p.absender() != null) teile.add("E-Mail " + p.absender());
            if (!teile.isEmpty()) sb.append(", ").append(String.join(", ", teile));
        }
        sb.append("\nAnfrage anlegen: \"").append(e.bauvorhaben()).append('"');
        String adresse = ((e.projektStrasse() == null ? "" : e.projektStrasse() + ", ") + (e.projektPlz() == null ? "" : e.projektPlz() + " ") + (e.projektOrt() == null ? "" : e.projektOrt())).strip();
        if (!adresse.isEmpty()) sb.append(", Bauvorhaben in ").append(adresse);
        if (e.kurzbeschreibung() != null) sb.append("\nBeschreibung: ").append(e.kurzbeschreibung());
        sb.append("\nDie E-Mail \"").append(p.mail().getSubject() == null ? "(ohne Betreff)" : p.mail().getSubject()).append("\" wird mit der Anfrage verknüpft.");
        return sb.toString();
    }

    @Override
    @Transactional
    public String ausfuehren(JsonNode angaben, KiKontext kontext) {
        Eingabe e = lies(angaben);
        Pruefung p = pruefe(e);
        Kunde kunde = p.kunde();
        boolean neuerKunde = false;
        if (kunde == null) {
            Kunde neu = new Kunde();
            neu.setKundennummer(kundennummern.reserviereNaechsteKundennummer());
            neu.setName(e.kundeName());
            neu.setAnsprechspartner(e.kundeAnsprechpartner());
            neu.setStrasse(e.kundeStrasse());
            neu.setPlz(e.kundePlz());
            neu.setOrt(e.kundeOrt());
            neu.setTelefon(e.kundeTelefon());
            neu.setKundenEmails(p.absender() == null ? new ArrayList<>() : new ArrayList<>(List.of(p.absender())));
            kunde = kunden.save(neu);
            neuerKunde = true;
            if (!kunde.getKundenEmails().isEmpty()) {
                events.publishEvent(EmailAddressChangedEvent.forNewEntity(EmailAddressChangedEvent.EntityType.KUNDE, kunde.getId(), new ArrayList<>(kunde.getKundenEmails())));
            }
        }
        AnfrageErstellenDto dto = new AnfrageErstellenDto();
        dto.setKundenId(kunde.getId());
        dto.setBauvorhaben(e.bauvorhaben());
        dto.setKurzbeschreibung(e.kurzbeschreibung());
        dto.setProjektStrasse(e.projektStrasse());
        dto.setProjektPlz(e.projektPlz());
        dto.setProjektOrt(e.projektOrt());
        if (p.absender() != null) {
            dto.setKundenEmails(new ArrayList<>(List.of(p.absender())));
        }
        var erstellt = anfrageService.erstelleAnfrage(dto);
        Anfrage anfrage = anfragen.findById(erstellt.getId()).orElseThrow(() -> new KiWerkzeugFehler("Die Anfrage wurde nicht gefunden."));
        Email mail = emails.findById(e.emailId()).orElseThrow();
        mail.assignToAnfrage(anfrage);
        emails.save(mail);
        if (postfachSpiegel != null) {
            postfachSpiegel.markierenVormerken(mail, "Anfrage_" + anfrage.getId());
        }
        return (neuerKunde ? "Der Kunde \"" + kunde.getName() + "\" (Kundennr. " + kunde.getKundennummer() + ") wurde angelegt. " : "Kunde: \"" + kunde.getName() + "\". ")
                + "Die Anfrage \"" + anfrage.getBauvorhaben() + "\" wurde angelegt (ID " + anfrage.getId() + ") und die E-Mail damit verknüpft.";
    }
}
