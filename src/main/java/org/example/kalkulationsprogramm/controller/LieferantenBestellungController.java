package org.example.kalkulationsprogramm.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.dto.Bestellwesen.BestellVorschlagDto;
import org.example.kalkulationsprogramm.dto.Bestellwesen.BestellungDto;
import org.example.kalkulationsprogramm.dto.Bestellwesen.BestellungEingabe;
import org.example.kalkulationsprogramm.dto.Bestellwesen.FertigungsunterlageDto;
import org.example.kalkulationsprogramm.service.ArtikelFertigungsunterlageService;
import org.example.kalkulationsprogramm.service.BestellPositionAnhangService;
import org.example.kalkulationsprogramm.service.LieferantenBestellungPdfService;
import org.example.kalkulationsprogramm.service.LieferantenBestellungService;
import org.springframework.http.ContentDisposition;
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
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Bestellungen an Lieferanten (Issue #86). Das Funktionsrecht "Bestellungen" prüft der {@code FunktionsrechtSperre}-Zugriffsfilter nach der HTTP-Methode
 * (Lesen = ansehen, POST = anlegen, PUT = ändern, DELETE = löschen); die Statuswechsel (bestellen, liefern, stornieren) sind POST-Aufrufe und zählen
 * deshalb als "anlegen".
 */
@Slf4j
@RestController
@RequestMapping("/api/lieferanten-bestellungen")
@RequiredArgsConstructor
public class LieferantenBestellungController {

    private final LieferantenBestellungService service;
    private final LieferantenBestellungPdfService pdfService;
    private final BestellPositionAnhangService anhangService;
    private final ArtikelFertigungsunterlageService fertigungsunterlageService;

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

    // ---- Positions-Anhänge (Issue #510) ----

    /** Alle Anhänge einer Bestellposition. */
    @GetMapping("/{id}/positionen/{positionId}/anhaenge")
    public List<FertigungsunterlageDto> listeAnhaenge(@PathVariable Long id, @PathVariable Long positionId) {
        return anhangService.liste(positionId);
    }

    /** Fertigungsunterlagen des Artikels einer Position als Vorschläge zum Übernehmen. */
    @GetMapping("/{id}/positionen/{positionId}/anhaenge/vorschlaege")
    public List<FertigungsunterlageDto> vorschlaege(@PathVariable Long id, @PathVariable Long positionId) {
        return service.fertigungsunterlagenVorschlaege(positionId);
    }

    /** Datei direkt für eine Bestellposition hochladen (projektspezifisch). */
    @PostMapping(value = "/{id}/positionen/{positionId}/anhaenge", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> hochladen(@PathVariable Long id, @PathVariable Long positionId,
            @org.springframework.web.bind.annotation.RequestPart("datei") MultipartFile datei) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(anhangService.ladeHoch(positionId, datei));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IOException e) {
            log.error("Anhang-Upload fuer Position {} fehlgeschlagen", positionId, e);
            return ResponseEntity.internalServerError().body(Map.of("message", "Die Datei konnte nicht gespeichert werden."));
        }
    }

    /** Fertigungsunterlage aus dem Artikelstamm in eine Bestellposition übernehmen. */
    @PostMapping("/{id}/positionen/{positionId}/anhaenge/vom-artikel/{fertigungsunterlageId}")
    public ResponseEntity<?> uebernehmeVomArtikel(@PathVariable Long id, @PathVariable Long positionId,
            @PathVariable Long fertigungsunterlageId) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(anhangService.uebernehmeVomArtikel(positionId, fertigungsunterlageId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IOException e) {
            log.error("Fehler beim Uebernehmen der Fertigungsunterlage {} in Position {}", fertigungsunterlageId, positionId, e);
            return ResponseEntity.internalServerError().body(Map.of("message", "Die Datei konnte nicht übernommen werden."));
        }
    }

    /** Datei eines Anhangs herunterladen. */
    @GetMapping("/position-anhaenge/{id}/datei")
    public ResponseEntity<org.springframework.core.io.Resource> ladeDatei(@PathVariable Long id) {
        BestellPositionAnhangService.DateiErgebnis datei = anhangService.ladeDatei(id);
        ContentDisposition disposition = (datei.istPdf()
                ? ContentDisposition.inline()
                : ContentDisposition.attachment())
                .filename(datei.originalDateiname(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(datei.contentType()))
                .header("Content-Disposition", disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(datei.resource());
    }

    /** Anhang einer Bestellposition löschen. */
    @DeleteMapping("/position-anhaenge/{id}")
    public ResponseEntity<Void> loescheAnhang(@PathVariable Long id) {
        anhangService.loeschen(id);
        return ResponseEntity.noContent().build();
    }
}
