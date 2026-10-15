package org.example.kalkulationsprogramm.controller.wartung;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.example.kalkulationsprogramm.domain.wartung.WartungStatus;
import org.example.kalkulationsprogramm.dto.wartung.WartungDatenfeldDefinitionDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungDatenfeldNeuDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungDatenfeldWertDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungInhaltAenderungDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungProtokollAnlegenDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungProtokollDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungPruefpunktNeuDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungPruefpunktTextDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungProtokollKopfDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungProtokollartSammelDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungProtokollListeneintragDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungPruefpunktErgebnisDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungStatusWechselDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungUnterschriftDto;
import org.example.kalkulationsprogramm.service.wartung.WartungBenutzerService;
import org.example.kalkulationsprogramm.service.wartung.WartungProtokollPdfService;
import org.example.kalkulationsprogramm.service.wartung.WartungProtokollService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
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

import java.time.LocalDate;

/** Wartungsprotokolle: Liste, anlegen, ausfüllen, unterschreiben, Status wechseln. */
@RestController
@RequestMapping("/api/wartung/protokolle")
@AllArgsConstructor
public class WartungProtokollController {

    static final int MAX_SEITENGROESSE = 100;

    private final WartungProtokollService protokollService;
    private final WartungProtokollPdfService pdfService;
    private final WartungBenutzerService benutzerService;

    @GetMapping
    public ResponseEntity<Page<WartungProtokollListeneintragDto>> suche(
            @RequestParam(required = false) Long kundeId,
            @RequestParam(required = false) Long objektId,
            @RequestParam(required = false) Long tuerId,
            @RequestParam(required = false) WartungStatus status,
            @RequestParam(name = "mitArchiv", defaultValue = "false") boolean mitArchiv,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate datumVon,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate datumBis,
            @RequestParam(required = false) String suche,
            @RequestParam(defaultValue = "0") int seite,
            @RequestParam(defaultValue = "20") int groesse) {
        PageRequest seitenwahl = PageRequest.of(Math.max(0, seite), Math.min(Math.max(1, groesse), MAX_SEITENGROESSE));
        return ResponseEntity.ok(protokollService.suche(kundeId, objektId, tuerId, status, mitArchiv,
                datumVon, datumBis, suche, seitenwahl));
    }

    @PostMapping
    public ResponseEntity<WartungProtokollDto> anlegen(@Valid @RequestBody WartungProtokollAnlegenDto dto, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(protokollService.anlegen(dto, benutzerService.mitarbeiterId(authentication)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WartungProtokollDto> laden(@PathVariable Long id) {
        return ResponseEntity.ok(protokollService.laden(id));
    }

    /** Das Protokoll als PDF, im Browser geöffnet (nicht heruntergeladen), damit man es gleich drucken kann. */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        WartungProtokollPdfService.PdfDatei datei = pdfService.erstelle(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(datei.dateiname()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(datei.inhalt());
    }

    /** Die Protokollart mehrerer Wartungen auf einmal ändern; die Antwort nennt, was nicht geändert werden konnte. */
    @PostMapping("/protokollart")
    public ResponseEntity<WartungProtokollartSammelDto.Ergebnis> protokollartSetzen(@Valid @RequestBody WartungProtokollartSammelDto dto) {
        return ResponseEntity.ok(protokollService.protokollartSetzen(dto.ids(), dto.protokollTyp()));
    }

    @PutMapping("/{id}/kopf")
    public ResponseEntity<WartungProtokollDto> kopfAendern(
            @PathVariable Long id, @Valid @RequestBody WartungProtokollKopfDto dto) {
        return ResponseEntity.ok(protokollService.kopfAendern(id, dto));
    }

    @PutMapping("/{id}/pruefpunkte/{pruefpunktId}")
    public ResponseEntity<WartungInhaltAenderungDto> pruefpunktBewerten(
            @PathVariable Long id, @PathVariable Long pruefpunktId,
            @Valid @RequestBody WartungPruefpunktErgebnisDto dto) {
        return ResponseEntity.ok(protokollService.pruefpunktBewerten(id, pruefpunktId, dto));
    }

    /** Ergänzt einen Prüfpunkt im laufenden Feldeinsatz (nur in diesem Protokoll). */
    @PostMapping("/{id}/pruefpunkte")
    public ResponseEntity<WartungProtokollDto> pruefpunktHinzufuegen(
            @PathVariable Long id, @Valid @RequestBody WartungPruefpunktNeuDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(protokollService.pruefpunktHinzufuegen(id, dto));
    }

    @PutMapping("/{id}/pruefpunkte/{pruefpunktId}/text")
    public ResponseEntity<WartungProtokollDto> pruefpunktText(
            @PathVariable Long id, @PathVariable Long pruefpunktId, @Valid @RequestBody WartungPruefpunktTextDto dto) {
        return ResponseEntity.ok(protokollService.pruefpunktText(id, pruefpunktId, dto));
    }

    @DeleteMapping("/{id}/pruefpunkte/{pruefpunktId}")
    public ResponseEntity<WartungProtokollDto> pruefpunktLoeschen(@PathVariable Long id, @PathVariable Long pruefpunktId) {
        return ResponseEntity.ok(protokollService.pruefpunktLoeschen(id, pruefpunktId));
    }

    /** Ergänzt ein Datenfeld im laufenden Feldeinsatz (nur in diesem Protokoll). */
    @PostMapping("/{id}/datenfelder")
    public ResponseEntity<WartungProtokollDto> datenfeldHinzufuegen(
            @PathVariable Long id, @Valid @RequestBody WartungDatenfeldNeuDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(protokollService.datenfeldHinzufuegen(id, dto));
    }

    @PutMapping("/{id}/datenfelder/{datenfeldId}/definition")
    public ResponseEntity<WartungProtokollDto> datenfeldDefinition(
            @PathVariable Long id, @PathVariable Long datenfeldId, @Valid @RequestBody WartungDatenfeldDefinitionDto dto) {
        return ResponseEntity.ok(protokollService.datenfeldDefinition(id, datenfeldId, dto));
    }

    @DeleteMapping("/{id}/datenfelder/{datenfeldId}")
    public ResponseEntity<WartungProtokollDto> datenfeldLoeschen(@PathVariable Long id, @PathVariable Long datenfeldId) {
        return ResponseEntity.ok(protokollService.datenfeldLoeschen(id, datenfeldId));
    }

    @PutMapping("/{id}/datenfelder/{datenfeldId}")
    public ResponseEntity<WartungInhaltAenderungDto> datenfeldSetzen(
            @PathVariable Long id, @PathVariable Long datenfeldId,
            @Valid @RequestBody WartungDatenfeldWertDto dto) {
        return ResponseEntity.ok(protokollService.datenfeldSetzen(id, datenfeldId, dto));
    }

    @PutMapping("/{id}/unterschrift")
    public ResponseEntity<WartungProtokollDto> unterschreiben(
            @PathVariable Long id, @Valid @RequestBody WartungUnterschriftDto dto) {
        return ResponseEntity.ok(protokollService.unterschreiben(id, dto));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<WartungProtokollDto> statusWechseln(
            @PathVariable Long id, @Valid @RequestBody WartungStatusWechselDto dto) {
        return ResponseEntity.ok(protokollService.statusWechseln(id, dto.status(), dto.grund()));
    }

    /** Wer das Protokoll wann abgeschlossen, wieder geöffnet oder archiviert hat (#238). */
    @org.springframework.web.bind.annotation.GetMapping("/{id}/verlauf")
    public ResponseEntity<java.util.List<org.example.kalkulationsprogramm.dto.wartung.WartungVerlaufDto>> verlauf(@PathVariable Long id) {
        return ResponseEntity.ok(protokollService.verlauf(id));
    }
}
