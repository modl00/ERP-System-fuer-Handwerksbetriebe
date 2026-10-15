package org.example.kalkulationsprogramm.controller;

import java.util.List;

import org.example.kalkulationsprogramm.dto.Bestellwesen.BestellVorschlagDto;
import org.example.kalkulationsprogramm.dto.Bestellwesen.BestellungDto;
import org.example.kalkulationsprogramm.dto.Bestellwesen.BestellungEingabe;
import org.example.kalkulationsprogramm.service.LieferantenBestellungPdfService;
import org.example.kalkulationsprogramm.service.LieferantenBestellungService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Bestellungen an Lieferanten (Issue #86). Das Funktionsrecht "Bestellungen" prüft der {@code FunktionsrechtSperre}-Zugriffsfilter nach der HTTP-Methode
 * (Lesen = ansehen, POST = anlegen, PUT = ändern, DELETE = löschen); die Statuswechsel (bestellen, liefern, stornieren) sind POST-Aufrufe und zählen
 * deshalb als "anlegen".
 */
@RestController
@RequestMapping("/api/lieferanten-bestellungen")
@RequiredArgsConstructor
public class LieferantenBestellungController {

    private final LieferantenBestellungService service;
    private final LieferantenBestellungPdfService pdfService;

    public record BestellenDto(String per) { }

    public record StornoDto(String grund) { }

    public record LieferungDto(boolean geliefert) { }

    @GetMapping
    public List<BestellungDto> liste(@RequestParam(required = false) String stand, @RequestParam(required = false) Long lieferantId,
            @RequestParam(required = false) Long projektId, @RequestParam(defaultValue = "false") boolean nurUeberfaellig) {
        return service.liste(stand, lieferantId, projektId, nurUeberfaellig);
    }

    @GetMapping("/{id}")
    public BestellungDto lade(@PathVariable Long id) {
        return service.lade(id);
    }

    @GetMapping("/vorschlaege/mindestbestand")
    public List<BestellVorschlagDto> mindestbestand() {
        return service.vorschlaegeMindestbestand();
    }

    @GetMapping("/vorschlaege/projekt/{projektId}")
    public List<BestellVorschlagDto> projekt(@PathVariable Long projektId) {
        return service.vorschlaegeProjekt(projektId);
    }

    @PostMapping
    public ResponseEntity<BestellungDto> anlegen(@RequestBody BestellungEingabe eingabe) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.anlegen(eingabe));
    }

    @PutMapping("/{id}")
    public BestellungDto aendern(@PathVariable Long id, @RequestBody BestellungEingabe eingabe) {
        return service.aendern(id, eingabe);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> loeschen(@PathVariable Long id) {
        service.loeschen(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/bestellen")
    public BestellungDto bestellen(@PathVariable Long id, @RequestBody(required = false) BestellenDto body) {
        return service.bestellen(id, body == null ? null : body.per());
    }

    @PostMapping("/{id}/positionen/{positionId}/geliefert")
    public BestellungDto liefern(@PathVariable Long id, @PathVariable Long positionId, @RequestBody LieferungDto body) {
        return service.liefern(id, positionId, body.geliefert());
    }

    @PostMapping("/{id}/alles-geliefert")
    public BestellungDto alleGeliefert(@PathVariable Long id) {
        return service.alleGeliefert(id);
    }

    @PostMapping("/{id}/stornieren")
    public BestellungDto stornieren(@PathVariable Long id, @RequestBody StornoDto body) {
        return service.stornieren(id, body == null ? null : body.grund());
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        BestellungDto b = service.lade(id);
        byte[] bytes = pdfService.pdf(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + LieferantenBestellungPdfService.dateiname(b) + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }
}
