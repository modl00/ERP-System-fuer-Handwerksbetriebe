package org.example.kalkulationsprogramm.service.ki.assistent;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import org.example.kalkulationsprogramm.domain.Funktion;
import org.example.kalkulationsprogramm.domain.FunktionsAktion;
import org.example.kalkulationsprogramm.dto.Lager.LagerBestandDto;
import org.example.kalkulationsprogramm.dto.Lager.LagerBuchungEingabe;
import org.example.kalkulationsprogramm.dto.Lager.LagerortDto;
import org.example.kalkulationsprogramm.service.FunktionsRechteService;
import org.example.kalkulationsprogramm.service.LagerModulSchalterService;
import org.example.kalkulationsprogramm.service.LagerService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

/**
 * Änderung (nur als Vorschlag mit Bestätigung): bucht einen Lagerzugang oder eine Entnahme. Inventur-Korrekturen gehören bewusst nicht dazu. Gleiche Regeln und
 * Rechte wie die Lagerseite: Recht „Lager anlegen“, Lagermodul eingeschaltet; die Buchung selbst macht der {@link LagerService}.
 */
@Component
@RequiredArgsConstructor
public class LagerBuchenWerkzeug implements KiWerkzeug {

    private final LagerService lager;
    private final FunktionsRechteService rechte;
    private final ObjectProvider<LagerModulSchalterService> modul;

    @Override
    public String name() {
        return "lager_buchen";
    }

    @Override
    public String beschreibung() {
        return "Bucht einen Lagerzugang (Ware ist eingetroffen) oder eine Entnahme (Ware wurde genommen) für einen Artikel (nur nach Bestätigung des Benutzers). Die Artikel-ID vorher mit artikel_suchen oder lagerbestand ermitteln; Lagerort mit Namen angeben.";
    }

    @Override
    public JsonNode parameter() {
        ObjectNode o = WerkzeugHilfe.JSON.createObjectNode().put("type", "object");
        ObjectNode p = o.putObject("properties");
        p.putObject("artikelId").put("type", "integer").put("description", "ID des Artikels (aus artikel_suchen oder lagerbestand)");
        p.putObject("art").put("type", "string").put("description", "ZUGANG = Ware kommt ins Lager, ABGANG = Ware wird entnommen").putArray("enum").add("ZUGANG").add("ABGANG");
        p.putObject("menge").put("type", "number").put("description", "Menge in der Einheit des Artikels, größer als 0");
        p.putObject("lagerort").put("type", "string").put("description", "Name des Lagerorts, zum Beispiel \"Hauptlager\"");
        p.putObject("projektId").put("type", "integer").put("description", "Nur bei ABGANG: ID des Projekts, für das entnommen wird (optional)");
        p.putObject("notiz").put("type", "string").put("description", "Kurze Notiz zur Buchung (optional)");
        o.putArray("required").add("artikelId").add("art").add("menge").add("lagerort");
        return o;
    }

    @Override
    public boolean aendert() {
        return true;
    }

    @Override
    public boolean verfuegbar(KiKontext k) {
        LagerModulSchalterService m = modul.getIfAvailable();
        if (m != null && !m.istAktiv()) {
            return false;
        }
        return k.admin() || rechte.darfProfil(k.profilId(), Funktion.LAGER, FunktionsAktion.ANLEGEN);
    }

    private record Eingabe(LagerBuchungEingabe buchung, LagerBestandDto artikel, LagerortDto ort) { }

    private Eingabe lies(JsonNode a) {
        Long artikelId = WerkzeugHilfe.nummer(a, "artikelId", "die Artikel-ID");
        if (artikelId == null) {
            throw new KiWerkzeugFehler("Bitte die Artikel-ID angeben (zuerst mit artikel_suchen suchen).");
        }
        String art = WerkzeugHilfe.text(a, "art", 20, true, "die Art der Buchung (ZUGANG oder ABGANG)").toUpperCase(Locale.ROOT);
        if (!art.equals("ZUGANG") && !art.equals("ABGANG")) {
            throw new KiWerkzeugFehler("Die Art muss ZUGANG oder ABGANG sein (Inventur-Korrekturen macht das Programm nicht über die KI).");
        }
        BigDecimal menge = WerkzeugHilfe.zahl(a, "menge", "die Menge");
        if (menge == null || menge.signum() <= 0) {
            throw new KiWerkzeugFehler("Bitte eine Menge größer als 0 angeben.");
        }
        String ortName = WerkzeugHilfe.text(a, "lagerort", 100, true, "den Lagerort");
        List<LagerortDto> passend = lager.orte().stream().filter(LagerortDto::aktiv).filter(o -> o.name().strip().equalsIgnoreCase(ortName)).toList();
        if (passend.size() != 1) {
            String verfuegbar = String.join(", ", lager.orte().stream().filter(LagerortDto::aktiv).map(LagerortDto::name).toList());
            throw new KiWerkzeugFehler(passend.isEmpty()
                    ? "Den Lagerort \"" + ortName + "\" gibt es nicht." + (verfuegbar.isEmpty() ? " Es ist noch kein Lagerort angelegt." : " Vorhanden: " + verfuegbar + ".")
                    : "Den Lagerort \"" + ortName + "\" gibt es mehrfach. Bitte den Namen genauer angeben.");
        }
        Long projektId = WerkzeugHilfe.nummer(a, "projektId", "die Projekt-ID");
        if (projektId != null && !art.equals("ABGANG")) {
            throw new KiWerkzeugFehler("Ein Projekt lässt sich nur bei einer Entnahme angeben.");
        }
        String notiz = WerkzeugHilfe.text(a, "notiz", 250, false, "die Notiz");
        LagerBestandDto bestand;
        try {
            bestand = lager.bestandVon(artikelId);
        } catch (ResponseStatusException e) {
            throw new KiWerkzeugFehler(e.getStatusCode() == HttpStatus.NOT_FOUND ? "Den Artikel mit dieser ID gibt es nicht." : e.getReason());
        }
        return new Eingabe(new LagerBuchungEingabe(art, artikelId, passend.get(0).id(), menge, projektId, notiz, null, null), bestand, passend.get(0));
    }

    @Override
    public String vorschau(JsonNode angaben) {
        Eingabe e = lies(angaben);
        StringBuilder sb = new StringBuilder(beschreibe(e));
        if (e.buchung().projektId() != null) {
            sb.append(" für Projekt ").append(e.buchung().projektId());
        }
        if (e.buchung().notiz() != null) {
            sb.append(" (Notiz: ").append(e.buchung().notiz()).append(')');
        }
        if (e.buchung().art().equals("ABGANG")) {
            BigDecimal dort = e.artikel().orte().stream().filter(o -> o.lagerortId().equals(e.ort().id())).map(LagerBestandDto.OrtBestand::bestand).findFirst().orElse(BigDecimal.ZERO);
            if (e.buchung().menge().compareTo(dort) > 0) {
                sb.append(". ACHTUNG: dort sind nur ").append(WerkzeugHilfe.mengeText(dort)).append(' ').append(e.artikel().einheit()).append(" vorhanden, der Bestand würde negativ");
            }
        }
        return sb.toString();
    }

    @Override
    public String ausfuehren(JsonNode angaben, KiKontext kontext) {
        if (!verfuegbar(kontext)) {
            throw new KiWerkzeugFehler(FunktionsRechteService.fehlermeldung(Funktion.LAGER, FunktionsAktion.ANLEGEN));
        }
        Eingabe e = lies(angaben);
        try {
            LagerBestandDto neu = lager.buche(e.buchung());
            return "Gebucht: " + beschreibe(e) + ". Neuer Bestand von \"" + neu.bezeichnung() + "\": " + WerkzeugHilfe.mengeText(neu.bestand()) + " " + neu.einheit() + ".";
        } catch (ResponseStatusException ex) {
            throw new KiWerkzeugFehler(ex.getReason() == null ? "Die Buchung ist nicht möglich." : ex.getReason());
        }
    }

    private static String beschreibe(Eingabe e) {
        String menge = WerkzeugHilfe.mengeText(e.buchung().menge()) + " " + e.artikel().einheit();
        return e.buchung().art().equals("ZUGANG")
                ? "Lagerzugang " + menge + " \"" + e.artikel().bezeichnung() + "\" ins Lager \"" + e.ort().name() + "\""
                : "Entnahme " + menge + " \"" + e.artikel().bezeichnung() + "\" aus dem Lager \"" + e.ort().name() + "\"";
    }
}
