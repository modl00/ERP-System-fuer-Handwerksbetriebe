package org.example.kalkulationsprogramm.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.dto.Aktivitaet.AktivitaetDto.Einstellungen;
import org.example.kalkulationsprogramm.dto.Aktivitaet.AktivitaetDto.EinstellungenEingabe;
import org.example.kalkulationsprogramm.dto.Aktivitaet.AktivitaetDto.Ereignis;
import org.example.kalkulationsprogramm.dto.Aktivitaet.AktivitaetDto.MeinTag;
import org.example.kalkulationsprogramm.service.AktivitaetService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/**
 * Aktivitätsprotokoll (Issue #93). "Mein Tagesverlauf" zeigt jedem Benutzer nur die eigenen Ereignisse; alles unter {@code /api/admin/**} ist in der
 * Sicherheitskonfiguration auf Administratoren beschränkt.
 */
@RestController
@RequiredArgsConstructor
public class AktivitaetController {

    private final AktivitaetService service;

    @GetMapping("/api/aktivitaet/mein")
    public MeinTag mein(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate datum, Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof FrontendUserPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bitte anmelden.");
        }
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return service.mein(principal.getId(), admin, datum);
    }

    @GetMapping("/api/admin/aktivitaet")
    public List<Ereignis> imSystem(@RequestParam(required = false) String objektTyp, @RequestParam(required = false) Long objektId, @RequestParam(required = false) String art,
            @RequestParam(required = false) Integer tage, @RequestParam(required = false) Integer limit) {
        return service.imSystem(objektTyp, objektId, art, tage, limit);
    }

    @GetMapping("/api/admin/aktivitaet/einstellungen")
    public Einstellungen einstellungen() {
        return service.einstellungen();
    }

    @PutMapping("/api/admin/aktivitaet/einstellungen")
    public Einstellungen speichern(@RequestBody EinstellungenEingabe eingabe) {
        return service.speichereEinstellungen(eingabe == null ? null : eingabe.mitarbeiterAktiv(), eingabe == null ? null : eingabe.aufbewahrungTage());
    }

    /** Auskunft über eine Person (Art 15 DSGVO). */
    @GetMapping("/api/admin/aktivitaet/person/{profilId}")
    public List<Ereignis> auskunft(@PathVariable Long profilId) {
        return service.auskunft(profilId);
    }

    /** Löschung auf Wunsch einer Person (Art 17 DSGVO). */
    @DeleteMapping("/api/admin/aktivitaet/person/{profilId}")
    public Map<String, Integer> loeschen(@PathVariable Long profilId) {
        return Map.of("geloescht", service.loescheVon(profilId));
    }
}
