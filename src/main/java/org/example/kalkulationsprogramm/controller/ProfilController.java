package org.example.kalkulationsprogramm.controller;

import java.math.BigDecimal;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Profilform;
import org.example.kalkulationsprogramm.repository.WerkstoffRepository;
import org.example.kalkulationsprogramm.service.ProfilRechner;
import org.example.kalkulationsprogramm.service.ProfilRechner.Ergebnis;
import org.example.kalkulationsprogramm.service.ProfilTabelle;
import org.example.kalkulationsprogramm.service.ProfilTabelle.Eintrag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/** Hilfen für Gewicht und Oberfläche am Artikel (#168): die Profiltabelle und die Rechnung aus den Maßen. */
@RestController
@RequestMapping("/api/artikel/profile")
@RequiredArgsConstructor
public class ProfilController {

    private final ProfilTabelle tabelle;
    private final WerkstoffRepository werkstoffe;

    /** Die Formen, für die es Tabellenwerte gibt (damit die Oberfläche das Auswahlfeld nur dort zeigt). */
    @GetMapping("/formen")
    public List<Profilform> formen() {
        return tabelle.formen();
    }

    /** Die Profile einer Form mit Maßen, Gewicht und Oberfläche je Meter. */
    @GetMapping("/tabelle")
    public List<Eintrag> tabelle(@RequestParam Profilform profilform) {
        return tabelle.fuer(profilform);
    }

    /** Die Angaben für die Rechnung aus den Maßen (mm); der Werkstoff liefert die Dichte, ohne ihn gilt Stahl. */
    public record RechenAnfrage(Profilform profilform, BigDecimal durchmesser, BigDecimal hoehe, BigDecimal breite, BigDecimal wandstaerke, Long werkstoffId) { }

    @PostMapping("/rechnen")
    public Ergebnis rechnen(@RequestBody RechenAnfrage a) {
        BigDecimal dichte = a.werkstoffId() == null ? null : werkstoffe.findById(a.werkstoffId()).map(w -> w.getDichte()).orElse(null);
        try {
            return ProfilRechner.rechne(a.profilform(), a.durchmesser(), a.hoehe(), a.breite(), a.wandstaerke(), dichte);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * Testrechnung für die Zuschnitt-Formel eines Artikels (#228): Felder und Formeln wie im Formular, dazu die Werte der Felder. Der Werkstoff liefert die Dichte
     * ({@code dichte} in der Formel), {@code kgJeMeter} und {@code m2JeMeter} die gleichnamigen Werte. Speichert nichts.
     */
    public record FormelAnfrage(java.util.List<org.example.kalkulationsprogramm.service.formel.ArbeitsgangFormeln.Feld> felder, String gewicht, String flaeche,
            java.util.Map<String, BigDecimal> werte, Long werkstoffId, BigDecimal kgJeMeter, BigDecimal m2JeMeter) { }

    public record FormelErgebnis(BigDecimal kg, BigDecimal m2) { }

    @PostMapping("/formel")
    public FormelErgebnis formel(@RequestBody FormelAnfrage a) {
        try {
            var formel = org.example.kalkulationsprogramm.service.formel.ArtikelFormeln.pruefe(
                    new org.example.kalkulationsprogramm.service.formel.ArtikelFormeln.Formel(a.felder(), a.gewicht(), a.flaeche()));
            if (formel.gewicht() == null && formel.flaeche() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte eine Formel für das Gewicht oder die Fläche eintragen.");
            }
            BigDecimal dichte = a.werkstoffId() == null ? null : werkstoffe.findById(a.werkstoffId()).map(w -> w.getDichte()).orElse(null);
            var r = org.example.kalkulationsprogramm.service.formel.ArtikelFormeln.berechne(formel, a.werte(),
                    org.example.kalkulationsprogramm.service.formel.ArtikelFormeln.eingebaut(dichte, a.kgJeMeter(), a.m2JeMeter()));
            return new FormelErgebnis(r.kg(), r.m2());
        } catch (org.example.kalkulationsprogramm.service.formel.FormelException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
