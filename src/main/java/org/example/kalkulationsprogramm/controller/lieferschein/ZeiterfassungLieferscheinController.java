package org.example.kalkulationsprogramm.controller.lieferschein;

import java.util.List;

import org.example.kalkulationsprogramm.dto.lieferschein.LieferscheinAnlegenDto;
import org.example.kalkulationsprogramm.dto.lieferschein.LieferscheinDto;
import org.example.kalkulationsprogramm.dto.lieferschein.LieferscheinKopieDto;
import org.example.kalkulationsprogramm.dto.lieferschein.LieferscheinUnterschriftDto;
import org.example.kalkulationsprogramm.dto.lieferschein.NummernbereichDto;
import org.example.kalkulationsprogramm.service.lieferschein.AppLieferscheinService;
import org.example.kalkulationsprogramm.service.lieferschein.LieferscheinPdfService;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Lieferscheine für die Handy-App. Anmeldung über den Zugangslink im Kopf {@code X-Zugang} (401 ohne gültigen Zugang, 403 ohne Recht). */
@RestController
@RequestMapping("/api/zeiterfassung/lieferscheine")
@RequiredArgsConstructor
public class ZeiterfassungLieferscheinController {

    static final String ZUGANG = "X-Zugang";

    private final AppLieferscheinService service;

    @GetMapping("/projekt/{projektId}")
    public List<LieferscheinDto> imProjekt(@RequestHeader(ZUGANG) String token, @PathVariable Long projektId) {
        return service.imProjekt(token, projektId);
    }

    @GetMapping("/{id}")
    public LieferscheinDto holen(@RequestHeader(ZUGANG) String token, @PathVariable Long id) {
        return service.holen(token, id);
    }

    @PostMapping
    public LieferscheinDto anlegen(@RequestHeader(ZUGANG) String token, @Valid @RequestBody LieferscheinAnlegenDto dto) {
        return service.anlegen(token, dto);
    }

    @PutMapping("/{id}/unterschrift")
    public LieferscheinDto unterschreiben(@RequestHeader(ZUGANG) String token, @PathVariable Long id, @Valid @RequestBody LieferscheinUnterschriftDto dto) {
        return service.unterschreiben(token, id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> loeschen(@RequestHeader(ZUGANG) String token, @PathVariable Long id) {
        service.loeschen(token, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/kopie")
    public LieferscheinDto kopie(@RequestHeader(ZUGANG) String token, @PathVariable Long id, @Valid @RequestBody LieferscheinKopieDto dto) {
        return service.kopieSenden(token, id, dto);
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@RequestHeader(ZUGANG) String token, @PathVariable Long id) {
        LieferscheinPdfService.PdfDatei datei = service.pdf(token, id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(datei.dateiname()).build().toString())
                .header("X-Content-Type-Options", "nosniff").cacheControl(CacheControl.noStore()).body(datei.inhalt());
    }

    /** Reserviert Nummern für das Arbeiten ohne Netz und gibt alle noch freien dieses Geräts zurück. */
    @PostMapping("/nummernbereich")
    public NummernbereichDto nummernbereich(@RequestHeader(ZUGANG) String token, @RequestParam(defaultValue = "20") int soll) {
        return service.nummernbereich(token, soll);
    }
}
