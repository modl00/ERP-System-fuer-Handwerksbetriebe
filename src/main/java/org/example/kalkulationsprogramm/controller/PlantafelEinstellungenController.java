package org.example.kalkulationsprogramm.controller;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.example.kalkulationsprogramm.service.planung.PlanungsEinstellungen;
import org.example.kalkulationsprogramm.service.planung.PlanungsEinstellungen.Urlaub;
import org.example.kalkulationsprogramm.service.planung.PlanungsEinstellungen.Werte;
import org.example.kalkulationsprogramm.service.planung.PlanungsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Einstellungen der Plantafel (Issue #331). {@code GET /api/plantafel/einstellungen} dürfen alle Angemeldeten lesen, weil die Ansicht
 * danach rechnet; {@code PUT /api/settings/planung} liegt unter {@code /api/settings/**} und ist damit nur für Admins.
 * Wochentage sind ISO-Zahlen: 1 = Montag bis 7 = Sonntag.
 */
@RestController
@RequiredArgsConstructor
public class PlantafelEinstellungenController {

    private final PlanungsEinstellungen einstellungen;
    private final PlanungsService planung;

    public record UrlaubDto(LocalDate von, LocalDate bis, String bezeichnung) { }

    public record EinstellungenDto(List<Integer> arbeitstage, int ampelSchwelleTage, List<UrlaubDto> betriebsurlaub) { }

    @GetMapping("/api/plantafel/einstellungen")
    public EinstellungenDto lesen() {
        return dto(einstellungen.lesen());
    }

    @PutMapping("/api/settings/planung")
    public EinstellungenDto speichern(@RequestBody EinstellungenDto eingabe) {
        Set<DayOfWeek> tage = EnumSet.noneOf(DayOfWeek.class);
        for (Integer n : eingabe.arbeitstage() == null ? List.<Integer>of() : eingabe.arbeitstage()) {
            if (n == null || n < 1 || n > 7) {
                throw new IllegalArgumentException("Unbekannter Wochentag: " + n);
            }
            tage.add(DayOfWeek.of(n));
        }
        List<Urlaub> urlaub = eingabe.betriebsurlaub() == null ? List.of()
                : eingabe.betriebsurlaub().stream().map(u -> new Urlaub(u.von(), u.bis(), u.bezeichnung())).toList();
        Werte neu = einstellungen.speichern(tage, eingabe.ampelSchwelleTage(), urlaub);
        planung.neuBerechnenAlle(); // Arbeitswoche und Betriebsurlaub verschieben die Termine
        return dto(neu);
    }

    /** Stunden eines Arbeitstags (Ersatz ohne Zeitkonto, Standardwert ohne Zuweisung) und die Bürotage je Mitarbeiter (ISO-Wochentage 1 bis 7, Schlüssel = Mitarbeiternummer). */
    public record RhythmusDto(java.math.BigDecimal arbeitstagStunden, Map<Long, List<Integer>> buerotage) { }

    @GetMapping("/api/plantafel/rhythmus")
    public RhythmusDto rhythmusLesen() {
        return rhythmusDto(einstellungen.rhythmusLesen());
    }

    @PutMapping("/api/settings/planung/rhythmus")
    public RhythmusDto rhythmusSpeichern(@RequestBody RhythmusDto eingabe) {
        Map<Long, Set<DayOfWeek>> tage = new java.util.LinkedHashMap<>();
        if (eingabe.buerotage() != null) {
            eingabe.buerotage().forEach((id, liste) -> {
                Set<DayOfWeek> menge = EnumSet.noneOf(DayOfWeek.class);
                for (Integer n : liste == null ? List.<Integer>of() : liste) {
                    if (n == null || n < 1 || n > 7) {
                        throw new IllegalArgumentException("Unbekannter Wochentag: " + n);
                    }
                    menge.add(DayOfWeek.of(n));
                }
                tage.put(id, menge);
            });
        }
        PlanungsEinstellungen.Rhythmus neu = einstellungen.rhythmusSpeichern(eingabe.arbeitstagStunden(), tage);
        planung.neuBerechnenAlle();
        return rhythmusDto(neu);
    }

    private static RhythmusDto rhythmusDto(PlanungsEinstellungen.Rhythmus r) {
        Map<Long, List<Integer>> tage = new java.util.TreeMap<>();
        r.buerotage().forEach((id, menge) -> tage.put(id, menge.stream().map(DayOfWeek::getValue).sorted().toList()));
        return new RhythmusDto(r.arbeitstagStunden(), tage);
    }

    private static EinstellungenDto dto(Werte w) {
        return new EinstellungenDto(
                w.arbeitstage().stream().map(DayOfWeek::getValue).sorted().toList(),
                w.ampelSchwelleTage(),
                w.betriebsurlaub().stream().map(u -> new UrlaubDto(u.von(), u.bis(), u.bezeichnung())).toList());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> ungueltig(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
    }
}
