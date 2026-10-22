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

import org.example.kalkulationsprogramm.domain.ArtikelFertigungsunterlage;
import org.example.kalkulationsprogramm.domain.BestellPosition;
import org.example.kalkulationsprogramm.domain.BestellPositionAnhang;
import org.example.kalkulationsprogramm.domain.ProjektDokument;
import org.example.kalkulationsprogramm.dto.Bestellwesen.FertigungsunterlageDto;
import org.example.kalkulationsprogramm.exception.NotFoundException;
import org.example.kalkulationsprogramm.repository.ArtikelFertigungsunterlageRepository;
import org.example.kalkulationsprogramm.repository.BestellPositionAnhangRepository;
import org.example.kalkulationsprogramm.repository.BestellPositionRepository;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
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
 * Datei-Anhaenge an Bestellpositionen (Issue #510).
 * Ablage unter {@code uploads/bestell-position-anhaenge/{positionId}/}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BestellPositionAnhangService {

    private static final long MAX_DATEIGROESSE_BYTES = 25L * 1024 * 1024;
    private static final Set<String> ERLAUBTE_ENDUNGEN = Set.of("dxf", "step", "stp", "dwg", "dxb", "pdf");

    private final BestellPositionAnhangRepository repo;
    private final BestellPositionRepository positionRepo;
    private final ArtikelFertigungsunterlageRepository fertigungsunterlageRepo;
    private final ProjektDokumentRepository projektDokumentRepo;

    @Value("${upload.path:uploads}")
    private String uploadPath;

    @Value("${file.upload-dir}")
    private String projektDokumentUploadDir;

    @Transactional(readOnly = true)
    public List<FertigungsunterlageDto> liste(Long positionId) {
        pruefePositionExistiert(positionId);
        return repo.findByPositionIdOrderByErstelltAmAscIdAsc(positionId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FertigungsunterlageDto> vorschlaege(Long positionId) {
        BestellPosition position = positionRepo.findById(positionId)
                .orElseThrow(() -> new NotFoundException("Diese Bestellposition gibt es nicht."));
        if (position.getArtikel() == null) return List.of();
        return fertigungsunterlageRepo.findByArtikelIdOrderByErstelltAmAscIdAsc(position.getArtikel().getId())
                .stream()
                .map(u -> new FertigungsunterlageDto(u.getId(), u.getOriginalDateiname(), u.getErstelltAm(),
                        u.getDateigroesseBytes(), "/api/artikel/fertigungsunterlagen/" + u.getId() + "/datei", null))
                .toList();
    }

    /** Laedt eine Datei direkt fuer diese Position hoch (Quelle 2: projektspezifisch). */
    @Transactional
    public FertigungsunterlageDto ladeHoch(Long positionId, MultipartFile datei) throws IOException {
        BestellPosition position = positionRepo.findById(positionId)
                .orElseThrow(() -> new NotFoundException("Diese Bestellposition gibt es nicht."));

        if (datei == null || datei.isEmpty()) {
            throw new IllegalArgumentException("Es wurde keine Datei ausgewählt.");
        }
        if (datei.getSize() > MAX_DATEIGROESSE_BYTES) {
            throw new IllegalArgumentException("Die Datei ist zu groß. Erlaubt sind höchstens 25 MB.");
        }

        String originalDateiname = bereinigeUndPruefeDateiname(datei.getOriginalFilename());
        String storedFilename = UUID.randomUUID() + "_" + originalDateiname;

        Path verzeichnis = verzeichnis(positionId);
        Files.createDirectories(verzeichnis);
        Path targetPath = verzeichnis.resolve(storedFilename).normalize();
        if (!targetPath.startsWith(verzeichnis)) {
            throw new IllegalArgumentException("Der Dateiname ist nicht zulässig.");
        }

        try (var in = datei.getInputStream()) {
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }

        BestellPositionAnhang a = new BestellPositionAnhang();
        a.setPosition(position);
        a.setOriginalDateiname(originalDateiname);
        a.setGespeicherterDateiname(storedFilename);
        a.setErstelltAm(LocalDateTime.now());
        a.setDateigroesseBytes(datei.getSize());
        return toDto(repo.save(a));
    }

    /**
     * Uebernimmt eine ArtikelFertigungsunterlage als Kopie in die Bestellposition (Quelle 1).
     * Die Datei wird physisch kopiert, damit sie unabhaengig vom Artikelstamm bleibt.
     */
    @Transactional
    public FertigungsunterlageDto uebernehmeVomArtikel(Long positionId, Long fertigungsunterlageId) throws IOException {
        BestellPosition position = positionRepo.findById(positionId)
                .orElseThrow(() -> new NotFoundException("Diese Bestellposition gibt es nicht."));
        ArtikelFertigungsunterlage quelle = fertigungsunterlageRepo.findById(fertigungsunterlageId)
                .orElseThrow(() -> new NotFoundException("Diese Fertigungsunterlage gibt es nicht."));

        // Quelldatei ermitteln und kopieren
        Path quellVerz = Path.of(uploadPath, "artikel-fertigungsunterlagen",
                quelle.getArtikel().getId().toString()).toAbsolutePath().normalize();
        Path quellDatei = quellVerz.resolve(quelle.getGespeicherterDateiname()).normalize();
        if (!quellDatei.startsWith(quellVerz) || !Files.exists(quellDatei)) {
            throw new NotFoundException("Die Quelldatei gibt es nicht mehr.");
        }

        String storedFilename = UUID.randomUUID() + "_" + quelle.getOriginalDateiname();
        Path zielVerz = verzeichnis(positionId);
        Files.createDirectories(zielVerz);
        Path zielDatei = zielVerz.resolve(storedFilename).normalize();
        if (!zielDatei.startsWith(zielVerz)) {
            throw new IllegalArgumentException("Der Dateiname ist nicht zulässig.");
        }
        Files.copy(quellDatei, zielDatei, StandardCopyOption.REPLACE_EXISTING);

        BestellPositionAnhang a = new BestellPositionAnhang();
        a.setPosition(position);
        a.setOriginalDateiname(quelle.getOriginalDateiname());
        a.setGespeicherterDateiname(storedFilename);
        a.setErstelltAm(LocalDateTime.now());
        a.setDateigroesseBytes(quelle.getDateigroesseBytes());
        a.setQuellFertigungsunterlage(quelle);
        return toDto(repo.save(a));
    }

    /**
     * Uebernimmt ein ProjektDokument als Kopie in die Bestellposition (Quelle 3: aus dem Projektordner, Issue #520).
     */
    @Transactional
    public FertigungsunterlageDto uebernehmeVomProjektDokument(Long positionId, Long projektDokumentId) throws IOException {
        BestellPosition position = positionRepo.findById(positionId)
                .orElseThrow(() -> new NotFoundException("Diese Bestellposition gibt es nicht."));
        ProjektDokument quelle = projektDokumentRepo.findById(projektDokumentId)
                .orElseThrow(() -> new NotFoundException("Dieses Projektdokument gibt es nicht."));

        if (!ERLAUBTE_ENDUNGEN.contains(ermittleEndung(quelle.getOriginalDateiname()))) {
            throw new IllegalArgumentException(
                    "Dieser Dateityp wird nicht unterstützt. Erlaubt sind DXF, STEP, STP, DWG, DXB und PDF.");
        }

        Path quellBasis = Path.of(projektDokumentUploadDir).toAbsolutePath().normalize();
        Path quellDatei = quellBasis.resolve(quelle.getGespeicherterDateiname()).normalize();
        if (!quellDatei.startsWith(quellBasis) || !Files.exists(quellDatei)) {
            throw new NotFoundException("Die Quelldatei gibt es nicht mehr.");
        }

        String storedFilename = UUID.randomUUID() + "_" + quelle.getOriginalDateiname();
        Path zielVerz = verzeichnis(positionId);
        Files.createDirectories(zielVerz);
        Path zielDatei = zielVerz.resolve(storedFilename).normalize();
        if (!zielDatei.startsWith(zielVerz)) {
            throw new IllegalArgumentException("Der Dateiname ist nicht zulässig.");
        }
        Files.copy(quellDatei, zielDatei, StandardCopyOption.REPLACE_EXISTING);

        BestellPositionAnhang a = new BestellPositionAnhang();
        a.setPosition(position);
        a.setOriginalDateiname(quelle.getOriginalDateiname());
        a.setGespeicherterDateiname(storedFilename);
        a.setErstelltAm(LocalDateTime.now());
        try { a.setDateigroesseBytes(Files.size(zielDatei)); } catch (IOException ignored) { }
        return toDto(repo.save(a));
    }

    @Transactional(readOnly = true)
    public DateiErgebnis ladeDatei(Long id) {
        BestellPositionAnhang a = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Diesen Anhang gibt es nicht."));

        Path verzeichnis = verzeichnis(a.getPosition().getId());
        Path filePath = verzeichnis.resolve(a.getGespeicherterDateiname()).normalize();
        if (!filePath.startsWith(verzeichnis) || !Files.exists(filePath)) {
            throw new NotFoundException("Diese Datei gibt es nicht mehr.");
        }

        String endung = ermittleEndung(a.getOriginalDateiname());
        if (!ERLAUBTE_ENDUNGEN.contains(endung)) {
            throw new NotFoundException("Diese Datei gibt es nicht mehr.");
        }

        try {
            Resource resource = new UrlResource(filePath.toUri());
            String contentType = "pdf".equals(endung) ? MediaType.APPLICATION_PDF_VALUE : MediaType.APPLICATION_OCTET_STREAM_VALUE;
            return new DateiErgebnis(resource, a.getOriginalDateiname(), contentType, "pdf".equals(endung));
        } catch (java.net.MalformedURLException e) {
            throw new NotFoundException("Diese Datei gibt es nicht mehr.");
        }
    }

    /** Laedt alle Bytes aller Anhaenge einer Bestellung - fuer den E-Mail-Versand. */
    @Transactional(readOnly = true)
    public List<AnhangBytes> ladeAlleBytes(Long bestellungId) {
        return repo.findByBestellungId(bestellungId).stream()
                .map(a -> {
                    try {
                        Path verzeichnis = verzeichnis(a.getPosition().getId());
                        Path filePath = verzeichnis.resolve(a.getGespeicherterDateiname()).normalize();
                        if (!filePath.startsWith(verzeichnis) || !Files.exists(filePath)) {
                            log.warn("Anhang {} nicht gefunden beim E-Mail-Versand", a.getId());
                            return null;
                        }
                        String endung = ermittleEndung(a.getOriginalDateiname());
                        String mime = "pdf".equals(endung) ? "application/pdf" : "application/octet-stream";
                        return new AnhangBytes(Files.readAllBytes(filePath), a.getOriginalDateiname(), mime);
                    } catch (IOException e) {
                        log.warn("Fehler beim Lesen von Anhang {}: {}", a.getId(), e.getMessage());
                        return null;
                    }
                })
                .filter(b -> b != null)
                .toList();
    }

    @Transactional
    public void loeschen(Long id) {
        BestellPositionAnhang a = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Diesen Anhang gibt es nicht."));
        Path verzeichnis = verzeichnis(a.getPosition().getId());
        Path filePath = verzeichnis.resolve(a.getGespeicherterDateiname()).normalize();
        repo.delete(a);
        if (filePath.startsWith(verzeichnis)) {
            try {
                Files.deleteIfExists(filePath);
            } catch (IOException e) {
                log.warn("Datei konnte nicht geloescht werden: {}", filePath, e);
            }
        }
    }

    // -----------------------------------------------------------------------

    private void pruefePositionExistiert(Long positionId) {
        if (positionId == null || !positionRepo.existsById(positionId)) {
            throw new NotFoundException("Diese Bestellposition gibt es nicht.");
        }
    }

    private Path verzeichnis(Long positionId) {
        return Path.of(uploadPath, "bestell-position-anhaenge", positionId.toString()).toAbsolutePath().normalize();
    }

    private String baueDateiUrl(BestellPositionAnhang a) {
        return "/api/lieferanten-bestellungen/position-anhaenge/" + a.getId() + "/datei";
    }

    FertigungsunterlageDto toDto(BestellPositionAnhang a) {
        Long quellId = a.getQuellFertigungsunterlage() != null ? a.getQuellFertigungsunterlage().getId() : null;
        return new FertigungsunterlageDto(a.getId(), a.getOriginalDateiname(), a.getErstelltAm(),
                a.getDateigroesseBytes(), baueDateiUrl(a), quellId);
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
    public record AnhangBytes(byte[] data, String dateiname, String mimeType) {}
}
