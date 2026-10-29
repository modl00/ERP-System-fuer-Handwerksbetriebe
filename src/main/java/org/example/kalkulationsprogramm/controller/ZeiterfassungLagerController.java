package org.example.kalkulationsprogramm.controller;

import java.math.BigDecimal;
import java.util.List;

import org.example.kalkulationsprogramm.dto.Lager.LagerBestandDto;
import org.example.kalkulationsprogramm.dto.Lager.LagerBuchungEingabe;
import org.example.kalkulationsprogramm.dto.Lager.LagerortDto;
import org.example.kalkulationsprogramm.dto.Lager.UmbuchungEingabe;
import org.example.kalkulationsprogramm.service.LagerAppService;
import org.example.kalkulationsprogramm.service.LagerAppService.EanTreffer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Lager für die Handy-App (Issue #69, Stufe 2). Die App kennt nur den Zugangslink des Mitarbeiters ({@code token}); er steht bei Abfragen im Pfad
 * (wie bei den Rechten der App), bei Buchungen im Text der Anfrage, damit er nicht in Protokollen von Adressen landet. Die Rechte prüft der
 * {@link LagerAppService} bei jedem Aufruf.
 */
@RestController
@RequestMapping("/api/zeiterfassung/lager")
@RequiredArgsConstructor
public class ZeiterfassungLagerController {

    private final LagerAppService service;

    /** Buchung aus der App: {@code art} ist ZUGANG, ABGANG (Entnahme) oder KORREKTUR (Inventur, {@code menge} ist der gezählte Bestand). */
    public record BuchungDto(String token, String art, Long artikelId, Long lagerortId, BigDecimal menge, Long projektId, String notiz) { }

    public record UmbuchungDto(String token, Long artikelId, Long vonLagerortId, Long nachLagerortId, BigDecimal menge, String notiz) { }

    public record EanZuordnungDto(String token, Long artikelId, String ean) { }

    @GetMapping("/{token}/orte")
    public List<LagerortDto> orte(@PathVariable String token) {
        return service.orte(token);
    }

    @GetMapping("/{token}/suche")
    public List<LagerBestandDto> suche(@PathVariable String token, @RequestParam(name = "q", defaultValue = "") String suchbegriff) {
        return service.suche(token, suchbegriff);
    }

    @GetMapping("/{token}/haeufig")
    public List<LagerBestandDto> haeufig(@PathVariable String token) {
        return service.haeufig(token);
    }

    @GetMapping("/{token}/ean/{ean}")
    public EanTreffer nachEan(@PathVariable String token, @PathVariable String ean) {
        return service.nachEan(token, ean);
    }

    @PostMapping("/buchung")
    public LagerBestandDto buche(@RequestBody BuchungDto b) {
        return service.buche(b.token(), new LagerBuchungEingabe(b.art(), b.artikelId(), b.lagerortId(), b.menge(), b.projektId(), b.notiz(), null, null));
    }

    @PostMapping("/umbuchung")
    public LagerBestandDto umbuche(@RequestBody UmbuchungDto u) {
        return service.umbuche(u.token(), new UmbuchungEingabe(u.artikelId(), u.vonLagerortId(), u.nachLagerortId(), u.menge(), u.notiz()));
    }

    @PostMapping("/ean-zuordnen")
    public LagerBestandDto eanZuordnen(@RequestBody EanZuordnungDto z) {
        return service.eanZuordnen(z.token(), z.artikelId(), z.ean());
    }
}
