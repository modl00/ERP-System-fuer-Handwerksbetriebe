package org.example.kalkulationsprogramm.service.ki.assistent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.dto.LieferantDokumentDto.AnalyzeResponse;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.service.GeminiDokumentAnalyseService;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterFehler;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterService;
import org.example.kalkulationsprogramm.service.ki.KiFunktion;
import org.example.kalkulationsprogramm.service.ki.KiObergrenze;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/**
 * Ein Beleg im KI-Chat (Issue #127, Teil A): Die Datei wird ausgewertet (ZUGFeRD, XML, sonst KI) und mit einem <b>Vorschlag zum Ablegen</b> beantwortet:
 * erkannte Art, Nummer, Beträge, dazu der passende Lieferant oder Kandidaten zur Auswahl und Hinweise (Nummer gibt es schon, KI unsicher). Hier wird
 * <b>nichts gespeichert und nichts angelegt</b>: Abgelegt wird erst nach dem Klick des Benutzers über den gewohnten Import des Lieferanten.
 * Die zentrale Obergrenze gilt: Ist die KI aus oder darf nichts an Gemini gehen, wird der Beleg gar nicht erst ausgewertet.
 */
@Service
@RequiredArgsConstructor
public class KiBelegService {

    static final long MAX_BYTES = 15L * 1024 * 1024;
    private static final Set<String> RECHTSFORMEN = Set.of("gmbh", "ag", "kg", "ohg", "ug", "co", "mbh", "ek", "gbr", "se", "und", "haftungsbeschraenkt", "e", "k");
    private static final Pattern KEIN_BUCHSTABE = Pattern.compile("[^\\p{L}\\p{N}]+");

    private final GeminiDokumentAnalyseService analyse;
    private final LieferantenRepository lieferanten;
    private final LieferantGeschaeftsdokumentRepository geschaeftsdokumente;
    private final KiAnbieterService anbieterService;

    public record Kandidat(Long id, String name, double aehnlichkeit) { }

    /** {@code lieferantId} ist nur gesetzt, wenn der Lieferant eindeutig erkannt ist; sonst steht die Auswahl in {@code kandidaten}. */
    public record Vorschlag(AnalyzeResponse beleg, Long lieferantId, String lieferantName, List<Kandidat> kandidaten, List<String> hinweise) { }

    public Vorschlag pruefe(MultipartFile datei) {
        if (datei == null || datei.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte eine Datei auswählen.");
        }
        if (datei.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Die Datei ist zu groß (höchstens 15 MB).");
        }
        sperreBeachten();
        String name = dateiname(datei.getOriginalFilename());
        AnalyzeResponse beleg;
        Path temp = null;
        try {
            temp = Files.createTempFile("ki-beleg-", "-" + name);
            datei.transferTo(temp);
            beleg = analyse.analyzeFile(temp, name);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Die Datei konnte nicht gelesen werden.");
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // Aufräumen ist nur Hygiene; die Datei liegt im Temp-Ordner
                }
            }
        }
        if (beleg == null) {
            try {
                anbieterService.waehle(KiFunktion.DOKUMENT_ERKENNEN);
            } catch (KiAnbieterFehler e) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
            }
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Den Beleg konnte ich nicht auslesen. Wenn das Modell unter Einstellungen → KI-Funktionen → „Dokumente und Belege erkennen\" kein multimodales Modell ist (kein Bild-/PDF-Lesen), bitte dort ein passendes wählen (z. B. Gemini Flash). Sonst den Beleg über den Lieferanten von Hand ablegen.");
        }
        return vorschlag(beleg);
    }

    /** Baut aus der Auswertung den Vorschlag; getrennt, damit sich Zuordnung und Hinweise ohne Datei testen lassen. */
    Vorschlag vorschlag(AnalyzeResponse beleg) {
        List<Kandidat> kandidaten = kandidaten(beleg.getLieferantName());
        Long id = null;
        String name = null;
        if (!kandidaten.isEmpty() && kandidaten.get(0).aehnlichkeit() >= 1.0 && (kandidaten.size() == 1 || kandidaten.get(1).aehnlichkeit() < 1.0)) {
            id = kandidaten.get(0).id();
            name = kandidaten.get(0).name();
        }
        List<String> hinweise = new ArrayList<>();
        if (beleg.getAiConfidence() != null && beleg.getAiConfidence() < 0.6) {
            hinweise.add("Die Auswertung ist unsicher (" + Math.round(beleg.getAiConfidence() * 100) + " %). Bitte alle Angaben prüfen.");
        }
        if (beleg.getLieferantName() == null || beleg.getLieferantName().isBlank()) {
            hinweise.add("Ich habe keinen Lieferanten erkannt. Bitte einen auswählen.");
        } else if (id == null && kandidaten.isEmpty()) {
            hinweise.add("Den Lieferanten „" + beleg.getLieferantName() + "“ gibt es noch nicht. Er wird nur angelegt, wenn Sie das ausdrücklich möchten.");
        } else if (id == null) {
            hinweise.add("Der Lieferant ist nicht eindeutig. Bitte einen der Vorschläge wählen.");
        }
        if (id != null && beleg.getDokumentNummer() != null && !beleg.getDokumentNummer().isBlank()
                && geschaeftsdokumente.existsByLieferantIdAndDokumentNummer(id, beleg.getDokumentNummer())) {
            hinweise.add("Ein Dokument mit der Nummer " + beleg.getDokumentNummer() + " gibt es bei " + name + " schon. Beim Ablegen würde es abgelehnt.");
        }
        return new Vorschlag(beleg, id, name, kandidaten, hinweise);
    }

    /** Bis zu drei Lieferanten, die zum erkannten Namen passen (beste zuerst). */
    List<Kandidat> kandidaten(String erkannt) {
        String gesucht = normal(erkannt);
        if (gesucht.isEmpty()) {
            return List.of();
        }
        List<Kandidat> treffer = new ArrayList<>();
        for (Lieferanten l : lieferanten.findAll()) {
            double a = aehnlichkeit(gesucht, normal(l.getLieferantenname()));
            if (a >= 0.5) {
                treffer.add(new Kandidat(l.getId(), l.getLieferantenname(), a));
            }
        }
        treffer.sort(Comparator.comparingDouble(Kandidat::aehnlichkeit).reversed().thenComparing(Kandidat::name, String.CASE_INSENSITIVE_ORDER));
        return treffer.size() > 3 ? List.copyOf(treffer.subList(0, 3)) : List.copyOf(treffer);
    }

    private void sperreBeachten() {
        if (anbieterService.eingriff() == KiObergrenze.Eingriff.AUS) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Die KI ist ausgeschaltet (Einstellungen → KI → Grenzen).");
        }
        try {
            anbieterService.pruefeDatenschutz(KiFunktion.DOKUMENT_ERKENNEN);
        } catch (KiAnbieterFehler e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    static String normal(String name) {
        if (name == null) {
            return "";
        }
        String s = name.toLowerCase(Locale.ROOT).replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
        List<String> teile = new ArrayList<>();
        for (String t : KEIN_BUCHSTABE.split(s)) {
            if (!t.isEmpty() && !RECHTSFORMEN.contains(t)) {
                teile.add(t);
            }
        }
        return String.join(" ", teile);
    }

    /** 1,0 = gleich; 0,8 = einer enthält den anderen (mindestens 4 Zeichen); sonst der Anteil gemeinsamer Wörter. */
    static double aehnlichkeit(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        if (a.equals(b)) {
            return 1.0;
        }
        if (Math.min(a.length(), b.length()) >= 4 && (a.contains(b) || b.contains(a))) {
            return 0.8;
        }
        Set<String> x = new HashSet<>(List.of(a.split(" ")));
        Set<String> y = new HashSet<>(List.of(b.split(" ")));
        Set<String> gemeinsam = new HashSet<>(x);
        gemeinsam.retainAll(y);
        Set<String> alle = new HashSet<>(x);
        alle.addAll(y);
        return alle.isEmpty() ? 0 : (double) gemeinsam.size() / alle.size();
    }

    private static String dateiname(String original) {
        String n = original == null || original.isBlank() ? "beleg.pdf" : Path.of(original).getFileName().toString();
        return n.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
