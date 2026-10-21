package org.example.kalkulationsprogramm.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelFertigungsunterlage;
import org.example.kalkulationsprogramm.dto.Bestellwesen.FertigungsunterlageDto;
import org.example.kalkulationsprogramm.exception.NotFoundException;
import org.example.kalkulationsprogramm.repository.ArtikelFertigungsunterlageRepository;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fertigungsunterlagen (DXF, STEP, DWG, STP, DXB, PDF) am Artikelstamm.
 * Ablage unter {@code uploads/artikel-fertigungsunterlagen/{artikelId}/}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArtikelFertigungsunterlageService {

    private static final long MAX_DATEIGROESSE_BYTES = 25L * 1024 * 1024;

    private static final Set<String> ERLAUBTE_ENDUNGEN = Set.of("dxf", "step", "stp", "dwg", "dxb", "pdf");

    private final ArtikelFertigungsunterlageRepository repo;
    private final ArtikelRepository artikelRepo;

    @Value("${upload.path:uploads}")
    private String uploadPath;

    @Transactional(readOnly = true)
    public List<FertigungsunterlageDto> liste(Long artikelId) {
        pruefeArtikelExistiert(artikelId);
        return repo.findByArtikelIdOrderByErstelltAmAscIdAsc(artikelId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public FertigungsunterlageDto ladeHoch(Long artikelId, MultipartFile datei) throws IOException {
        Artikel artikel = artikelRepo.findById(artikelId)
                .orElseThrow(() -> new NotFoundException("Diesen Artikel gibt es nicht."));

        if (datei == null || datei.isEmpty()) {
            throw new IllegalArgumentException("Es wurde keine Datei ausgewählt.");
        }
        if (datei.getSize() > MAX_DATEIGROESSE_BYTES) {
            throw new IllegalArgumentException("Die Datei ist zu groß. Erlaubt sind höchstens 25 MB.");
        }

        String originalDateiname = bereinigeUndPruefeDateiname(datei.getOriginalFilename());
        String storedFilename = UUID.randomUUID() + "_" + originalDateiname;

        Path verzeichnis = verzeichnis(artikelId);
        Files.createDirectories(verzeichnis);
        Path targetPath = verzeichnis.resolve(storedFilename).normalize();
        if (!targetPath.startsWith(verzeichnis)) {
            throw new IllegalArgumentException("Der Dateiname ist nicht zulässig.");
        }

        try (var in = datei.getInputStream()) {
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }

        ArtikelFertigungsunterlage u = new ArtikelFertigungsunterlage();
        u.setArtikel(artikel);
        u.setOriginalDateiname(originalDateiname);
        u.setGespeicherterDateiname(storedFilename);
        u.setErstelltAm(LocalDateTime.now());
        u.setDateigroesseBytes(datei.getSize());
        return toDto(repo.save(u));
    }

    @Transactional(readOnly = true)
    public DateiErgebnis ladeDatei(Long id) {
        ArtikelFertigungsunterlage u = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Diese Fertigungsunterlage gibt es nicht."));

        Path verzeichnis = verzeichnis(u.getArtikel().getId());
        Path filePath = verzeichnis.resolve(u.getGespeicherterDateiname()).normalize();
        if (!filePath.startsWith(verzeichnis) || !Files.exists(filePath)) {
            throw new NotFoundException("Diese Datei gibt es nicht mehr.");
        }

        String endung = ermittleEndung(u.getOriginalDateiname());
        if (!ERLAUBTE_ENDUNGEN.contains(endung)) {
            throw new NotFoundException("Diese Datei gibt es nicht mehr.");
        }

        try {
            Resource resource = new UrlResource(filePath.toUri());
            String contentType = "pdf".equals(endung) ? MediaType.APPLICATION_PDF_VALUE : MediaType.APPLICATION_OCTET_STREAM_VALUE;
            return new DateiErgebnis(resource, u.getOriginalDateiname(), contentType, "pdf".equals(endung));
        } catch (java.net.MalformedURLException e) {
            throw new NotFoundException("Diese Datei gibt es nicht mehr.");
        }
    }

    /** Gibt die physische Datei-Bytes fuer den E-Mail-Versand zurueck. */
    @Transactional(readOnly = true)
    public byte[] ladeDateiBytes(Long id) throws IOException {
        ArtikelFertigungsunterlage u = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Diese Fertigungsunterlage gibt es nicht."));
        Path verzeichnis = verzeichnis(u.getArtikel().getId());
        Path filePath = verzeichnis.resolve(u.getGespeicherterDateiname()).normalize();
        if (!filePath.startsWith(verzeichnis) || !Files.exists(filePath)) {
            throw new NotFoundException("Diese Datei gibt es nicht mehr.");
        }
        return Files.readAllBytes(filePath);
    }

    @Transactional
    public void loeschen(Long id) {
        ArtikelFertigungsunterlage u = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Diese Fertigungsunterlage gibt es nicht."));
        Path verzeichnis = verzeichnis(u.getArtikel().getId());
        Path filePath = verzeichnis.resolve(u.getGespeicherterDateiname()).normalize();
        repo.delete(u);
        if (filePath.startsWith(verzeichnis)) {
            try {
                Files.deleteIfExists(filePath);
            } catch (IOException e) {
                log.warn("Datei konnte nicht geloescht werden: {}", filePath, e);
            }
        }
    }

    // -----------------------------------------------------------------------

    private void pruefeArtikelExistiert(Long artikelId) {
        if (artikelId == null || !artikelRepo.existsById(artikelId)) {
            throw new NotFoundException("Diesen Artikel gibt es nicht.");
        }
    }

    private Path verzeichnis(Long artikelId) {
        return Path.of(uploadPath, "artikel-fertigungsunterlagen", artikelId.toString()).toAbsolutePath().normalize();
    }

    private String baueDateiUrl(ArtikelFertigungsunterlage u) {
        return "/api/artikel/fertigungsunterlagen/" + u.getId() + "/datei";
    }

    FertigungsunterlageDto toDto(ArtikelFertigungsunterlage u) {
        return new FertigungsunterlageDto(u.getId(), u.getOriginalDateiname(), u.getErstelltAm(),
                u.getDateigroesseBytes(), baueDateiUrl(u), null);
    }

    private String bereinigeUndPruefeDateiname(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            throw new IllegalArgumentException("Der Datei fehlt ein Name.");
        }
        String bereinigt = StringUtils.cleanPath(originalFilename.trim());
        if (bereinigt.contains("..") || bereinigt.contains("/") || bereinigt.contains("\\")) {
            throw new IllegalArgumentException("Dieser Dateiname ist nicht zulässig.");
        }
        if (!ERLAUBTE_ENDUNGEN.contains(ermittleEndung(bereinigt))) {
            throw new IllegalArgumentException(
                    "Dieser Dateityp wird nicht unterstützt. Erlaubt sind DXF, STEP, STP, DWG, DXB und PDF.");
        }
        return bereinigt;
    }

    private String ermittleEndung(String dateiname) {
        int punkt = dateiname.lastIndexOf('.');
        if (punkt < 0 || punkt == dateiname.length() - 1) return "";
        return dateiname.substring(punkt + 1).toLowerCase(Locale.ROOT);
    }

    public record DateiErgebnis(Resource resource, String originalDateiname, String contentType, boolean istPdf) {}
}
