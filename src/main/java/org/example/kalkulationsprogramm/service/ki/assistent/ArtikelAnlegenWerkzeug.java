package org.example.kalkulationsprogramm.service.ki.assistent;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelCreateDto;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.service.ArtikelService;
import org.example.kalkulationsprogramm.service.EanPruefung;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

/**
 * Änderung (nur als Vorschlag mit Bestätigung): legt einen Artikel im Artikelstamm an, optional mit Lieferant, Lieferanten-Artikelnummer und Preis. Die Vorschau
 * weist auf ähnliche Artikel hin, damit nichts doppelt angelegt wird. Es gibt kein eigenes Recht für den Artikelstamm; wie am PC darf es jeder angemeldete Benutzer.
 */
@Component
@RequiredArgsConstructor
public class ArtikelAnlegenWerkzeug implements KiWerkzeug {

    static final int MAX_NAME = 200;
    static final BigDecimal MAX_PREIS = new BigDecimal("1000000");

    private final ArtikelService artikelService;
    private final ArtikelRepository artikel;
    private final LieferantenRepository lieferanten;

    @Override
    public String name() {
        return "artikel_anlegen";
    }

    @Override
    public String beschreibung() {
        return "Legt einen neuen Artikel im Artikelstamm an (nur nach Bestätigung des Benutzers). Vorher mit artikel_suchen prüfen, ob es den Artikel schon gibt. Angaben: Bezeichnung und Einheit sind Pflicht, optional Lieferant, dessen Artikelnummer, EAN und Preis (netto je Einheit).";
    }

    @Override
    public JsonNode parameter() {
        ObjectNode o = WerkzeugHilfe.JSON.createObjectNode().put("type", "object");
        ObjectNode p = o.putObject("properties");
        p.putObject("bezeichnung").put("type", "string").put("description", "Name des Artikels, zum Beispiel \"Rundrohr 42,4 x 2,0 S235\"");
        p.putObject("einheit").put("type", "string").put("description", "Einheit, in der der Artikel geführt und abgerechnet wird")
                .putArray("enum").add("STUECK").add("LAUFENDE_METER").add("QUADRATMETER").add("KILOGRAMM").add("LITER");
        p.putObject("artikelnummerLieferant").put("type", "string").put("description", "Artikelnummer des Lieferanten (optional)");
        p.putObject("ean").put("type", "string").put("description", "EAN/Barcode mit 8, 12, 13 oder 14 Ziffern (optional)");
        p.putObject("lieferantId").put("type", "integer").put("description", "ID des Lieferanten, falls ein Preis oder eine Lieferantennummer angegeben wird (optional)");
        p.putObject("preis").put("type", "number").put("description", "Preis netto je Einheit in Euro (optional)");
        o.putArray("required").add("bezeichnung").add("einheit");
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

    private record Eingabe(String bezeichnung, Verrechnungseinheit einheit, String lieferantenNummer, String ean, Lieferanten lieferant, BigDecimal preis) { }

    private Eingabe lies(JsonNode a) {
        String name = WerkzeugHilfe.text(a, "bezeichnung", MAX_NAME, true, "die Bezeichnung");
        if (name.length() < 2) {
            throw new KiWerkzeugFehler("Die Bezeichnung ist zu kurz.");
        }
        Verrechnungseinheit einheit = einheit(WerkzeugHilfe.text(a, "einheit", 40, true, "die Einheit"));
        String nummer = WerkzeugHilfe.text(a, "artikelnummerLieferant", 100, false, "die Lieferantennummer");
        String ean = WerkzeugHilfe.text(a, "ean", 20, false, "die EAN");
        if (ean != null) {
            ean = EanPruefung.normalisiert(ean);
            String fehler = EanPruefung.fehler(ean);
            if (fehler != null) {
                throw new KiWerkzeugFehler(fehler);
            }
        }
        BigDecimal preis = WerkzeugHilfe.zahl(a, "preis", "der Preis");
        if (preis != null && (preis.signum() < 0 || preis.compareTo(MAX_PREIS) > 0)) {
            throw new KiWerkzeugFehler("Der Preis muss zwischen 0 und 1.000.000 Euro liegen.");
        }
        Long lieferantId = WerkzeugHilfe.nummer(a, "lieferantId", "die Lieferanten-ID");
        Lieferanten lieferant = null;
        if (lieferantId != null) {
            lieferant = lieferanten.findById(lieferantId).orElseThrow(() -> new KiWerkzeugFehler("Den Lieferanten mit dieser ID gibt es nicht."));
        } else if (preis != null || nummer != null) {
            throw new KiWerkzeugFehler("Für Preis oder Lieferantennummer bitte auch den Lieferanten angeben (lieferantId, zum Beispiel über die Lieferanten-Auskunft).");
        }
        return new Eingabe(name, einheit, nummer, ean, lieferant, preis);
    }

    @Override
    @Transactional(readOnly = true)
    public String vorschau(JsonNode angaben) {
        Eingabe e = lies(angaben);
        StringBuilder sb = new StringBuilder("Artikel anlegen: \"").append(e.bezeichnung()).append("\", Einheit ").append(e.einheit().getAnzeigename());
        if (e.ean() != null) {
            sb.append(", EAN ").append(e.ean());
        }
        if (e.lieferant() != null) {
            sb.append(", Lieferant ").append(e.lieferant().getLieferantenname());
            if (e.lieferantenNummer() != null) {
                sb.append(" (dessen Nummer ").append(e.lieferantenNummer()).append(')');
            }
            if (e.preis() != null) {
                sb.append(", Preis ").append(WerkzeugHilfe.mengeText(e.preis())).append(" € netto je ").append(e.einheit().getAnzeigename());
            }
        }
        List<Artikel> aehnlich = aehnliche(e.bezeichnung());
        if (!aehnlich.isEmpty()) {
            sb.append(". ACHTUNG, ähnliche Artikel gibt es schon: ");
            sb.append(String.join("; ", aehnlich.stream().map(a -> "ID " + a.getId() + " " + (a.getProduktname() == null ? "(ohne Name)" : a.getProduktname())).toList()));
        }
        return sb.toString();
    }

    @Override
    public String ausfuehren(JsonNode angaben, KiKontext kontext) {
        Eingabe e = lies(angaben);
        ArtikelCreateDto dto = new ArtikelCreateDto();
        dto.setProduktname(e.bezeichnung());
        dto.setVerrechnungseinheit(e.einheit());
        dto.setEan(e.ean());
        dto.setExterneArtikelnummer(e.lieferantenNummer());
        dto.setPreis(e.preis());
        dto.setLieferantId(e.lieferant() == null ? null : e.lieferant().getId());
        try {
            Artikel neu = artikelService.erstelleArtikel(dto);
            return "Der Artikel \"" + neu.getProduktname() + "\" wurde angelegt (ID " + neu.getId() + ", " + e.einheit().getAnzeigename() + ").";
        } catch (ResponseStatusException ex) {
            throw new KiWerkzeugFehler(ex.getStatusCode() == HttpStatus.CONFLICT || ex.getReason() != null ? ex.getReason() : "Der Artikel konnte nicht angelegt werden.");
        }
    }

    private List<Artikel> aehnliche(String bezeichnung) {
        String klein = bezeichnung.toLowerCase(Locale.GERMAN);
        String[] w = klein.split("[\\s×x]+");
        return artikel.sucheFuerAssistent(like(w, 0), like(w, 1), like(w, 2), "%" + klein + "%", bezeichnung.replaceAll("\\s+", ""), PageRequest.of(0, 3));
    }

    private static String like(String[] woerter, int i) {
        return i < woerter.length && !woerter[i].isBlank() ? "%" + woerter[i] + "%" : "%";
    }

    /** Einheit aus dem Namen der Aufzählung oder aus gebräuchlichen Wörtern ("Meter", "kg", "Stück" ...). */
    static Verrechnungseinheit einheit(String text) {
        String t = text.strip().toUpperCase(Locale.GERMAN).replace(' ', '_');
        try {
            return Verrechnungseinheit.valueOf(t);
        } catch (IllegalArgumentException e) {
            // weiter mit gebräuchlichen Schreibweisen
        }
        return switch (t) {
            case "STÜCK", "STK", "ST" -> Verrechnungseinheit.STUECK;
            case "METER", "M", "LFM", "LFD._METER", "LAUFENDER_METER" -> Verrechnungseinheit.LAUFENDE_METER;
            case "M²", "M2", "QM" -> Verrechnungseinheit.QUADRATMETER;
            case "KG", "KILO" -> Verrechnungseinheit.KILOGRAMM;
            case "L", "LTR" -> Verrechnungseinheit.LITER;
            default -> throw new KiWerkzeugFehler("Die Einheit \"" + text + "\" kenne ich nicht. Erlaubt: Stück, Laufende Meter, Quadratmeter, Kilogramm, Liter.");
        };
    }
}
