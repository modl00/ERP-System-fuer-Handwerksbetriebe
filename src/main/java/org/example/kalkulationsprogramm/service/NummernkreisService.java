package org.example.kalkulationsprogramm.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;
import org.example.kalkulationsprogramm.domain.Nummernkreis;
import org.example.kalkulationsprogramm.domain.Nummernkreis.Datumsteil;
import org.example.kalkulationsprogramm.domain.Nummernkreis.Ruecksetzung;
import org.example.kalkulationsprogramm.domain.NummernkreisZaehler;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.repository.NummernkreisRepository;
import org.example.kalkulationsprogramm.repository.NummernkreisZaehlerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/**
 * Einstellbare Nummernkreise (Issue #62): je Dokumentart Präfix, Datumsteil, Rückstellung, Stellenzahl und Startnummer. Ohne eingestelltes Format gilt für die Art
 * das bisherige ({@code RE-2026/10/00001} mit gemeinsamem Monatszähler), damit sich nach dem Update nichts ändert.
 *
 * <p>Regeln: Eine vergebene Nummer ändert sich nie (nur neue Dokumente folgen dem neuen Format). Der Zähler wird unter Sperre erhöht, damit auch bei gleichzeitigem
 * Speichern keine Nummer doppelt vergeben wird; ist eine Nummer schon vergeben (zum Beispiel aus einem früheren Format oder von Hand), wird sie übersprungen.
 * Die Startnummer ist die kleinste, die der Zähler vergibt.
 */
@Service
@RequiredArgsConstructor
public class NummernkreisService {

    /** Der Lieferschein hat kein bisheriges Format: ohne Einstellung gilt {@code LS-JJJJ-NNNN}, jedes Jahr wieder ab 1. */
    public static final String LIEFERSCHEIN = "LIEFERSCHEIN";

    /** Die Dokumentarten mit ihrem bisherigen Präfix. */
    private static final Map<String, String[]> ARTEN = new LinkedHashMap<>();

    static {
        ARTEN.put("ANGEBOT", new String[] { "Angebot", "AG" });
        ARTEN.put("NACHTRAGSANGEBOT", new String[] { "Nachtragsangebot", "NA" });
        ARTEN.put("AUFTRAGSBESTAETIGUNG", new String[] { "Auftragsbestätigung", "AB" });
        ARTEN.put("RECHNUNG", new String[] { "Rechnung", "RE" });
        ARTEN.put("TEILRECHNUNG", new String[] { "Teilrechnung", "TR" });
        ARTEN.put("ABSCHLAGSRECHNUNG", new String[] { "Abschlagsrechnung", "AR" });
        ARTEN.put("SCHLUSSRECHNUNG", new String[] { "Schlussrechnung", "SR" });
        ARTEN.put("GUTSCHRIFT", new String[] { "Gutschrift", "GU" });
        ARTEN.put("STORNO", new String[] { "Storno", "ST" });
        ARTEN.put(LIEFERSCHEIN, new String[] { "Lieferschein", "LS" });
    }

    private static final Pattern PRAEFIX = Pattern.compile("[A-Za-z0-9]{1,10}");
    private static final Pattern ENDZIFFERN = Pattern.compile("(\\d+)$");

    private final NummernkreisRepository kreise;
    private final NummernkreisZaehlerRepository zaehler;
    private final AusgangsGeschaeftsDokumentRepository dokumente;

    public record Eingabe(String praefix, Datumsteil datumsteil, Ruecksetzung ruecksetzung, Integer stellen, Long startnummer) { }

    /** Eine Dokumentart mit ihrem Format. {@code eigenesFormat} false = das bisherige Format; die übrigen Felder zeigen dann den Vorschlag fürs Umstellen. */
    public record Zeile(String art, String bezeichnung, boolean eigenesFormat, String praefix, Datumsteil datumsteil, Ruecksetzung ruecksetzung, int stellen,
            long startnummer, String naechsteNummer, String warnung) { }

    @Transactional(readOnly = true)
    public List<Zeile> alle() {
        List<Zeile> zeilen = new ArrayList<>();
        for (String art : ARTEN.keySet()) {
            zeilen.add(zeile(art, null));
        }
        return zeilen;
    }

    @Transactional
    public Zeile speichern(String art, Eingabe e) {
        pruefeArt(art);
        if (e == null) {
            throw fehler(HttpStatus.BAD_REQUEST, "Die Angaben fehlen.");
        }
        String praefix = e.praefix() == null ? "" : e.praefix().strip();
        if (!PRAEFIX.matcher(praefix).matches()) {
            throw fehler(HttpStatus.BAD_REQUEST, "Das Präfix besteht aus 1 bis 10 Buchstaben oder Ziffern (zum Beispiel RE).");
        }
        if (e.datumsteil() == null || e.ruecksetzung() == null) {
            throw fehler(HttpStatus.BAD_REQUEST, "Bitte Datumsteil und Rückstellung wählen.");
        }
        int stellen = e.stellen() == null ? 4 : e.stellen();
        if (stellen < 3 || stellen > 8) {
            throw fehler(HttpStatus.BAD_REQUEST, "Die Zahl der Stellen liegt zwischen 3 und 8.");
        }
        long start = e.startnummer() == null ? 1 : e.startnummer();
        if (start < 1 || start >= Math.pow(10, stellen)) {
            throw fehler(HttpStatus.BAD_REQUEST, "Die Startnummer muss mit " + stellen + " Stellen darstellbar sein (mindestens 1).");
        }
        Nummernkreis k = kreise.findByArt(art).orElseGet(Nummernkreis::new);
        k.setArt(art);
        k.setPraefix(praefix);
        k.setDatumsteil(e.datumsteil());
        k.setRuecksetzung(e.ruecksetzung());
        k.setStellen(stellen);
        k.setStartnummer(start);
        kreise.save(k);
        return zeile(art, k);
    }

    /** Zurück zum bisherigen Format; schon vergebene Nummern bleiben unverändert. */
    @Transactional
    public Zeile zuruecksetzen(String art) {
        pruefeArt(art);
        kreise.findByArt(art).ifPresent(kreise::delete);
        return zeile(art, null);
    }

    /**
     * Die nächste Nummer der Art, oder leer, wenn für sie kein eigenes Format eingestellt ist (dann gilt das bisherige). {@code bereitsVergeben} prüft, ob eine
     * Nummer schon existiert; solche werden übersprungen.
     */
    @Transactional
    public Optional<String> naechsteNummer(String art, Predicate<String> bereitsVergeben) {
        Optional<Nummernkreis> gefunden = kreisFuer(art);
        if (gefunden.isEmpty()) {
            return Optional.empty();
        }
        Nummernkreis k = gefunden.get();
        LocalDate heute = LocalDate.now();
        String periode = periode(k.getRuecksetzung(), heute);
        NummernkreisZaehler z = zaehler.findeMitSperre(art, periode).orElseGet(() -> {
            NummernkreisZaehler neu = new NummernkreisZaehler();
            neu.setArt(art);
            neu.setPeriode(periode);
            neu.setLetzte(k.getStartnummer() - 1);
            return neu;
        });
        // Die Startnummer ist die kleinste: Wurde sie später erhöht, springt der Zähler dorthin
        z.setLetzte(Math.max(z.getLetzte(), k.getStartnummer() - 1));
        String nummer;
        int versuche = 0;
        do {
            z.setLetzte(z.getLetzte() + 1);
            nummer = formatiere(k, heute, z.getLetzte());
            if (++versuche > 10_000) {
                throw fehler(HttpStatus.CONFLICT, "Es ließ sich keine freie Nummer finden. Bitte die Startnummer im Nummernkreis erhöhen.");
            }
        } while (bereitsVergeben != null && bereitsVergeben.test(nummer));
        zaehler.save(z);
        return Optional.of(nummer);
    }

    /** Das eingestellte Format der Art; beim Lieferschein ohne Einstellung das Standardformat, sonst leer (dann gilt das bisherige Format). */
    private Optional<Nummernkreis> kreisFuer(String art) {
        Optional<Nummernkreis> k = kreise.findByArt(art);
        if (k.isEmpty() && LIEFERSCHEIN.equals(art)) {
            Nummernkreis standard = new Nummernkreis();
            standard.setArt(art);
            standard.setPraefix("LS");
            standard.setDatumsteil(Datumsteil.JAHR);
            standard.setRuecksetzung(Ruecksetzung.JAEHRLICH);
            standard.setStellen(4);
            standard.setStartnummer(1);
            return Optional.of(standard);
        }
        return k;
    }

    private Zeile zeile(String art, Nummernkreis konfiguriert) {
        boolean eingestellt = konfiguriert != null || kreise.findByArt(art).isPresent();
        Nummernkreis k = konfiguriert != null ? konfiguriert : kreisFuer(art).orElse(null);
        String[] info = ARTEN.get(art);
        if (k == null) {
            // Das bisherige Format; die übrigen Felder sind der Vorschlag beim Umstellen (Präfix-JJJJ-NNNN, Rechnungen ohne Rückstellung)
            boolean rechnung = art.contains("RECHNUNG") || art.equals("GUTSCHRIFT") || art.equals("STORNO");
            String beispiel = info[1] + "-" + String.format("%tY/%<tm", LocalDate.now()) + "/00001";
            return new Zeile(art, info[0], false, info[1], Datumsteil.JAHR, rechnung ? Ruecksetzung.NIE : Ruecksetzung.JAEHRLICH, 4, 1, beispiel + " (bisheriges Format)", null);
        }
        LocalDate heute = LocalDate.now();
        long letzte = zaehler.findByArtAndPeriode(art, periode(k.getRuecksetzung(), heute)).map(NummernkreisZaehler::getLetzte).orElse(k.getStartnummer() - 1);
        String naechste = formatiere(k, heute, Math.max(letzte, k.getStartnummer() - 1) + 1);
        return new Zeile(art, info[0], eingestellt, k.getPraefix(), k.getDatumsteil(), k.getRuecksetzung(), k.getStellen(), k.getStartnummer(), naechste, warnung(art, k));
    }

    /** Gibt es schon Nummern dieser Art mit demselben Präfix, die weiter sind als die Startnummer? Dann überspringt der Zähler sie, das sagt die Warnung. */
    private String warnung(String art, Nummernkreis k) {
        AusgangsGeschaeftsDokumentTyp typ;
        try {
            typ = AusgangsGeschaeftsDokumentTyp.valueOf(art);
        } catch (IllegalArgumentException e) {
            return null;
        }
        long hoechste = 0;
        for (String n : dokumente.findeNummernDerArt(typ)) {
            if (n != null && n.startsWith(k.getPraefix() + "-")) {
                Matcher m = ENDZIFFERN.matcher(n);
                if (m.find() && m.group(1).length() <= 18) {
                    hoechste = Math.max(hoechste, Long.parseLong(m.group(1)));
                }
            }
        }
        return hoechste >= k.getStartnummer() ? "Es gibt schon Nummern bis " + hoechste + " mit diesem Präfix. Der Zähler überspringt vergebene Nummern; wer nahtlos anschließen will, wählt eine höhere Startnummer." : null;
    }

    static String periode(Ruecksetzung r, LocalDate d) {
        return switch (r) {
            case NIE -> "alle";
            case JAEHRLICH -> String.valueOf(d.getYear());
            case MONATLICH -> String.format("%d-%02d", d.getYear(), d.getMonthValue());
        };
    }

    static String formatiere(Nummernkreis k, LocalDate d, long wert) {
        String datum = switch (k.getDatumsteil()) {
            case KEINER -> "";
            case JAHR -> d.getYear() + "-";
            case JAHR_MONAT -> String.format("%d-%02d-", d.getYear(), d.getMonthValue());
        };
        return k.getPraefix() + "-" + datum + String.format("%0" + k.getStellen() + "d", wert);
    }

    private static void pruefeArt(String art) {
        if (art == null || !ARTEN.containsKey(art)) {
            throw fehler(HttpStatus.NOT_FOUND, "Diese Dokumentart gibt es nicht.");
        }
    }

    private static ResponseStatusException fehler(HttpStatus status, String text) {
        return new ResponseStatusException(status, text);
    }
}
