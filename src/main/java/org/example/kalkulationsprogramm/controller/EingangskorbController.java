package org.example.kalkulationsprogramm.controller;

import java.util.List;

import org.example.kalkulationsprogramm.domain.EingangStatus;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentProjektAnteil;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.dto.eingangskorb.EingangskorbEintragDto;
import org.example.kalkulationsprogramm.repository.LieferantDokumentProjektAnteilRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/eingangskorb")
@RequiredArgsConstructor
public class EingangskorbController {

    private final LieferantDokumentRepository dokumentRepository;
    private final ProjektRepository projektRepository;
    private final LieferantDokumentProjektAnteilRepository anteilRepository;

    @GetMapping
    public ResponseEntity<List<EingangskorbEintragDto>> liste() {
        List<LieferantDokument> dokumente = dokumentRepository
                .findByEingangStatusOrderByUploadDatumDesc(EingangStatus.WARTE_AUF_ZUORDNUNG);
        List<EingangskorbEintragDto> dtos = dokumente.stream().map(this::toDto).toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/anzahl")
    public ResponseEntity<Long> anzahl() {
        return ResponseEntity.ok(dokumentRepository.countByEingangStatus(EingangStatus.WARTE_AUF_ZUORDNUNG));
    }

    public record ZuordnungRequest(Long projektId, Integer prozent) {}

    @PostMapping("/{id}/zuordnen")
    public ResponseEntity<Void> zuordnen(@PathVariable Long id, @RequestBody ZuordnungRequest req) {
        LieferantDokument dok = dokumentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dokument nicht gefunden: " + id));
        Projekt projekt = projektRepository.findById(req.projektId())
                .orElseThrow(() -> new IllegalArgumentException("Projekt nicht gefunden: " + req.projektId()));

        LieferantDokumentProjektAnteil anteil = new LieferantDokumentProjektAnteil();
        anteil.setDokument(dok);
        anteil.setProjekt(projekt);
        anteil.setProzent(req.prozent() != null ? req.prozent() : 100);
        anteilRepository.save(anteil);

        dok.setEingangStatus(EingangStatus.ZUGEORDNET);
        dokumentRepository.save(dok);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/papierkorb")
    public ResponseEntity<Void> inPapierkorb(@PathVariable Long id) {
        LieferantDokument dok = dokumentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dokument nicht gefunden: " + id));
        dok.setEingangStatus(EingangStatus.PAPIERKORB);
        dokumentRepository.save(dok);
        return ResponseEntity.ok().build();
    }

    private EingangskorbEintragDto toDto(LieferantDokument d) {
        String emailBetreff = null;
        String emailAbsender = null;
        if (d.getAttachment() != null && d.getAttachment().getEmail() != null) {
            var email = d.getAttachment().getEmail();
            emailBetreff = email.getSubject();
            emailAbsender = email.getFromAddress();
        }
        return new EingangskorbEintragDto(
                d.getId(),
                d.getEffektiverDateiname(),
                d.getTyp() != null ? d.getTyp().name() : null,
                d.getUploadDatum(),
                d.getLieferant().getId(),
                d.getLieferant().getLieferantenname(),
                emailBetreff,
                emailAbsender,
                d.getEingangStatus().name()
        );
    }
}
