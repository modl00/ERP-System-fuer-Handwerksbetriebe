package org.example.kalkulationsprogramm.controller.wartung;

import java.util.List;

import org.example.kalkulationsprogramm.dto.wartung.WartungDatenfeldWertDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungInhaltAenderungDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungProjektWartungDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungProtokollDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungPruefpunktErgebnisDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungStatusWechselDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungUnterschriftDto;
import org.example.kalkulationsprogramm.service.wartung.AppWartungService;
import org.example.kalkulationsprogramm.service.wartung.WartungProtokollPdfService;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Der Feldeinsatz für die Handy-App. Anmeldung über den Zugangslink im Kopf {@code X-Zugang}; Rechte und Modulschalter prüft der {@link AppWartungService}
 * bei jedem Aufruf (401 ohne gültigen Zugang, 403 ohne Recht).
 */
@RestController
@RequestMapping("/api/zeiterfassung/wartung")
@RequiredArgsConstructor
public class ZeiterfassungWartungController {

    static final String ZUGANG = "X-Zugang";

    private final AppWartungService service;

    @GetMapping("/projekte/{projektId}/anlagen")
    public List<WartungProjektWartungDto> anlagen(@RequestHeader(ZUGANG) String token, @PathVariable Long projektId) {
        return service.anlagenDesProjekts(token, projektId);
    }

    /** Startet den Feldeinsatz der Wartung (Tür/Anlage) oder gibt den laufenden zurück. */
    @PostMapping("/anlagen/{tuerId}/feldeinsatz")
    public ResponseEntity<WartungProtokollDto> starten(@RequestHeader(ZUGANG) String token, @PathVariable Long tuerId) {
        return ResponseEntity.status(HttpStatus.OK).body(service.feldeinsatzStarten(token, tuerId));
    }

    @GetMapping("/protokolle/{id}")
    public WartungProtokollDto protokoll(@RequestHeader(ZUGANG) String token, @PathVariable Long id) {
        return service.protokoll(token, id);
    }

    @PutMapping("/protokolle/{id}/pruefpunkte/{pruefpunktId}")
    public WartungInhaltAenderungDto pruefpunkt(@RequestHeader(ZUGANG) String token, @PathVariable Long id, @PathVariable Long pruefpunktId,
            @Valid @RequestBody WartungPruefpunktErgebnisDto dto) {
        return service.pruefpunktBewerten(token, id, pruefpunktId, dto);
    }

    @PutMapping("/protokolle/{id}/datenfelder/{datenfeldId}")
    public WartungInhaltAenderungDto datenfeld(@RequestHeader(ZUGANG) String token, @PathVariable Long id, @PathVariable Long datenfeldId,
            @Valid @RequestBody WartungDatenfeldWertDto dto) {
        return service.datenfeldSetzen(token, id, datenfeldId, dto);
    }

    @PutMapping("/protokolle/{id}/unterschrift")
    public WartungProtokollDto unterschrift(@RequestHeader(ZUGANG) String token, @PathVariable Long id, @Valid @RequestBody WartungUnterschriftDto dto) {
        return service.unterschreiben(token, id, dto);
    }

    @PostMapping("/protokolle/{id}/unterschrift/gespeichert")
    public WartungProtokollDto gemerkteUnterschrift(@RequestHeader(ZUGANG) String token, @PathVariable Long id) {
        return service.gemerkteUnterschrift(token, id);
    }

    @PutMapping("/protokolle/{id}/status")
    public WartungProtokollDto status(@RequestHeader(ZUGANG) String token, @PathVariable Long id, @Valid @RequestBody WartungStatusWechselDto dto) {
        return service.status(token, id, dto.status());
    }

    /** Das Protokoll als PDF (für die Vorschau am Gerät). */
    @GetMapping("/protokolle/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@RequestHeader(ZUGANG) String token, @PathVariable Long id) {
        WartungProtokollPdfService.PdfDatei datei = service.pdf(token, id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(datei.dateiname()).build().toString())
                .header("X-Content-Type-Options", "nosniff").cacheControl(CacheControl.noStore()).body(datei.inhalt());
    }
}
