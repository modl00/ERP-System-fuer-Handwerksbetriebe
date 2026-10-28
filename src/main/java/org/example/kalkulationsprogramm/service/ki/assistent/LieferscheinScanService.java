package org.example.kalkulationsprogramm.service.ki.assistent;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.PreisQuelle;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelCreateDto;
import org.example.kalkulationsprogramm.dto.Lager.LagerBuchungEingabe;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentPositionRepository;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.example.kalkulationsprogramm.service.ArtikelService;
import org.example.kalkulationsprogramm.service.GeminiDokumentAnalyseService;
import org.example.kalkulationsprogramm.service.LagerService;
import org.example.kalkulationsprogramm.service.LieferantArtikelpreisService;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterFehler;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterService;
import org.example.kalkulationsprogramm.service.ki.KiFunktion;
import org.example.kalkulationsprogramm.service.ki.KiObergrenze;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Lieferschein per KI scannen: Artikel erkennen, mit Stammdaten abgleichen, ins Lager buchen oder neu anlegen.
 * Issue #526.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LieferscheinScanService {

    static final long MAX_BYTES = 15L * 1024 * 1024;

    private static final String PROMPT =
            "Du analysierst einen Lieferschein. Antworte NUR mit einem validen JSON-Objekt, kein Markdown, keine Erklaerungen.\n"
            + "{\n"
            + "  \"lieferantName\": \"Name des Lieferanten oder null\",\n"
            + "  \"lieferscheinNummer\": \"Lieferscheinnummer oder null\",\n"
            + "  \"positionen\": [\n"
            + "    {\n"
            + "      \"artikelnummer\": \"Artikelnummer oder Bestellnummer des Lieferanten, oder null\",\n"
            + "      \"bezeichnung\": \"Produktbezeichnung\",\n"
            + "      \"menge\": 10.0,\n"
            + "      \"einheit\": \"Stk\"\n"
            + "    }\n"
            + "  ]\n"
            + "}\n"
            + "Extrahiere alle Positionen vollstaendig. "
            + "Einheiten normalisieren: Stueck/St/Stk -> \"Stk\", Meter/m -> \"m\", Kilogramm/kg/KG -> \"kg\", Liter/l -> \"l\", Pakete/Pkg -> \"Pkg\", Quadratmeter/m2 -> \"m2\".";

    private final GeminiDokumentAnalyseService geminiService;
    private final KiAnbieterService anbieterService;
    private final ArtikelRepository artikelRepository;
    private final ArtikelService artikelService;
    private final LagerService lagerService;
    private final LieferantArtikelpreisService artikelpreisService;
    private final ObjectMapper objectMapper;
    private final LieferantDokumentPositionRepository positionRepository;
    private final LieferantGeschaeftsdokumentRepository geschaeftsdokumentRepository;

    public record ScanPosition(
            String artikelnummer,
            String bezeichnung,
            BigDecimal menge,
            String einheit,
            Long artikelId,
            String artikelName,
            boolean gefunden) {
    }

    /** Vorschau einer Dokument-Position für die Lager-Buchung. */
    public record LagerVorschauPosition(
            Long positionId,
            String bezeichnung,
            BigDecimal menge,
            String mengeneinheit,
            String externeArtikelnummer,
            Long artikelId,
            String artikelName,
            boolean abgeglichen,
            BigDecimal einzelpreis,
            Long lieferantId,
            String lieferantName) {
    }

    public record LagerVorschau(
            java.util.List<LagerVorschauPosition> positionen,
            java.time.LocalDateTime lagerGebuchtAm) {
    }

    public record ScanErgebnis(
            List<ScanPosition> positionen,
            String lieferantName,
            String lieferscheinNummer,
            List<String> hinweise) {
    }

    public record BuchenPosition(
            Long artikelId,
            BigDecimal menge,
            String artikelnummer,
            String bezeichnung,
            String einheit,
            boolean neuAnlegen) {
    }

    public record BuchenEingabe(
            List<BuchenPosition> positionen,
            Long lagerortId,
            String notiz) {
    }

    public record BuchenErgebnis(
            int gebucht,
            int neuAngelegt,
            List<String> meldungen) {
    }

    public ScanErgebnis scanDatei(MultipartFile datei) {
        if (datei == null || datei.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte eine Datei auswaehlen.");
        }
        if (datei.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Die Datei ist zu gross (hoechstens 15 MB).");
        }
        sperreBeachten();

        byte[] bytes;
        try {
            bytes = datei.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Die Datei konnte nicht gelesen werden.");
        }

        String originalName = datei.getOriginalFilename();
        String mimeType = ermittleMimeType(originalName);

        String json = geminiService.rufGeminiApiMitPrompt(bytes, mimeType, PROMPT);
        if (json == null || json.isBlank()) {
            try {
                anbieterService.waehle(KiFunktion.DOKUMENT_ERKENNEN);
            } catch (KiAnbieterFehler e) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
            }
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Der Lieferschein konnte nicht ausgelesen werden. Bitte ein lesbares PDF oder Foto hochladen.");
        }

        return parseUndAbgleichen(json);
    }

    @Transactional
    public BuchenErgebnis buchen(BuchenEingabe eingabe, String benutzer) {
        if (eingabe.lagerortId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte einen Lagerort auswaehlen.");
        }
        int gebucht = 0;
        int neuAngelegt = 0;
        List<String> meldungen = new ArrayList<>();

        for (BuchenPosition pos : eingabe.positionen()) {
            if (pos.menge() == null || pos.menge().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            try {
                Long artikelId = pos.artikelId();
                if (pos.neuAnlegen() && artikelId == null) {
                    Artikel neuer = legeArtikelAn(pos);
                    artikelId = neuer.getId();
                    neuAngelegt++;
                    meldungen.add("Artikel \"" + pos.bezeichnung() + "\" wurde neu angelegt.");
                }
                if (artikelId == null) {
                    continue;
                }
                String notiz = eingabe.notiz() != null && !eingabe.notiz().isBlank()
                        ? eingabe.notiz()
                        : "Lieferschein-Scan";
                lagerService.buche(
                        new LagerBuchungEingabe("ZUGANG", artikelId, eingabe.lagerortId(), pos.menge(), null, notiz),
                        benutzer);
                gebucht++;
            } catch (Exception e) {
                log.warn("[LieferscheinScan] Buchung fuer \"{}\" fehlgeschlagen: {}", pos.bezeichnung(), e.getMessage());
                meldungen.add("\"" + pos.bezeichnung() + "\" konnte nicht gebucht werden: " + e.getMessage());
            }
        }
        return new BuchenErgebnis(gebucht, neuAngelegt, meldungen);
    }

    private ScanErgebnis parseUndAbgleichen(String json) {
        List<ScanPosition> positionen = new ArrayList<>();
        String lieferantName = null;
        String lieferscheinNummer = null;
        List<String> hinweise = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(json);
            lieferantName = textOrNull(root, "lieferantName");
            lieferscheinNummer = textOrNull(root, "lieferscheinNummer");

            JsonNode posArr = root.path("positionen");
            if (posArr.isMissingNode() || !posArr.isArray()) {
                hinweise.add("Die KI hat keine Positionen erkannt. Bitte pruefen Sie, ob das Dokument ein Lieferschein ist.");
                return new ScanErgebnis(positionen, lieferantName, lieferscheinNummer, hinweise);
            }

            for (JsonNode p : posArr) {
                String artikelnummer = textOrNull(p, "artikelnummer");
                String bezeichnung = textOrNull(p, "bezeichnung");
                if (bezeichnung == null || bezeichnung.isBlank()) {
                    continue;
                }
                BigDecimal menge = mengeOrNull(p, "menge");
                if (menge == null) {
                    menge = BigDecimal.ONE;
                }
                String einheit = textOrNull(p, "einheit");
                if (einheit == null) {
                    einheit = "Stk";
                }

                // Artikel abgleichen: erst interne Nummer, dann externe Nummer, dann Name
                Optional<Artikel> treffer = Optional.empty();
                if (artikelnummer != null && !artikelnummer.isBlank()) {
                    treffer = artikelRepository.findByArtikelnummer(artikelnummer);
                    if (treffer.isEmpty()) {
                        treffer = artikelRepository.findByExterneArtikelnummer(artikelnummer);
                    }
                }
                if (treffer.isEmpty() && bezeichnung != null && !bezeichnung.isBlank()) {
                    treffer = artikelRepository.findFirstByProduktnameIgnoreCase(bezeichnung);
                }

                if (treffer.isPresent()) {
                    Artikel a = treffer.get();
                    positionen.add(new ScanPosition(artikelnummer, bezeichnung, menge, einheit,
                            a.getId(), a.getProduktname(), true));
                } else {
                    positionen.add(new ScanPosition(artikelnummer, bezeichnung, menge, einheit,
                            null, null, false));
                }
            }
        } catch (Exception e) {
            log.warn("[LieferscheinScan] JSON-Parsing fehlgeschlagen: {}", e.getMessage());
            hinweise.add("Die Antwort der KI konnte nicht ausgelesen werden. Bitte erneut versuchen.");
        }

        if (positionen.isEmpty()) {
            hinweise.add("Keine Positionen erkannt. Bitte pruefen Sie, ob das Dokument ein Lieferschein mit Artikelpositionen ist.");
        }
        return new ScanErgebnis(positionen, lieferantName, lieferscheinNummer, hinweise);
    }

    private Artikel legeArtikelAn(BuchenPosition pos) {
        ArtikelCreateDto dto = new ArtikelCreateDto();
        dto.setProduktname(pos.bezeichnung());
        if (pos.artikelnummer() != null && !pos.artikelnummer().isBlank()) {
            dto.setExterneArtikelnummer(pos.artikelnummer());
        }
        dto.setVerrechnungseinheit(verrechnungseinheitAus(pos.einheit()));
        return artikelService.erstelleArtikel(dto);
    }

    private Verrechnungseinheit verrechnungseinheitAus(String einheit) {
        if (einheit == null) {
            return Verrechnungseinheit.STUECK;
        }
        return switch (einheit.toLowerCase().trim()) {
            case "m", "meter", "lm" -> Verrechnungseinheit.LAUFENDE_METER;
            case "m2", "m²", "qm" -> Verrechnungseinheit.QUADRATMETER;
            case "kg", "kilogramm" -> Verrechnungseinheit.KILOGRAMM;
            case "l", "liter" -> Verrechnungseinheit.LITER;
            default -> Verrechnungseinheit.STUECK;
        };
    }

    private void sperreBeachten() {
        if (anbieterService.eingriff() == KiObergrenze.Eingriff.AUS) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Die KI ist ausgeschaltet (Einstellungen -> KI -> Grenzen).");
        }
        try {
            anbieterService.pruefeDatenschutz(KiFunktion.DOKUMENT_ERKENNEN);
        } catch (KiAnbieterFehler e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    private static String ermittleMimeType(String dateiname) {
        if (dateiname == null) {
            return "application/octet-stream";
        }
        String lower = dateiname.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        // jpg, jpeg und alles andere als image/jpeg
        return "image/jpeg";
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode n = node.path(field);
        if (n.isNull() || n.isMissingNode()) {
            return null;
        }
        String v = n.asText(null);
        return (v == null || v.equalsIgnoreCase("null") || v.isBlank()) ? null : v.strip();
    }

    private static BigDecimal mengeOrNull(JsonNode node, String field) {
        JsonNode n = node.path(field);
        if (n.isNull() || n.isMissingNode()) {
            return null;
        }
        try {
            return new BigDecimal(n.asText("0"));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Gibt eine Vorschau zurück: welche Positionen konnten einem Artikel zugeordnet werden?
     * Das Ergebnis zeigt dem Nutzer vor dem Buchen, welche Positionen noch einen neuen Artikel benötigen.
     */
    @Transactional(readOnly = true)
    public LagerVorschau vorschauFuerDokument(Long geschaeftsdokumentId) {
        var gd = geschaeftsdokumentRepository.findById(geschaeftsdokumentId).orElse(null);
        java.time.LocalDateTime lagerGebuchtAm = gd != null ? gd.getLagerGebuchtAm() : null;
        List<LagerVorschauPosition> positionen = positionRepository.findByGeschaeftsdokumentId(geschaeftsdokumentId).stream()
                .filter(p -> p.getMenge() != null && p.getMenge().compareTo(BigDecimal.ZERO) > 0)
                .map(p -> {
                    Optional<Artikel> treffer = sucheArtikel(p.getExterneArtikelnummer(), p.getBezeichnung());
                    var lieferant = p.getGeschaeftsdokument() != null && p.getGeschaeftsdokument().getDokument() != null
                            ? p.getGeschaeftsdokument().getDokument().getLieferant()
                            : null;
                    return new LagerVorschauPosition(
                            p.getId(),
                            p.getBezeichnung(),
                            p.getMenge(),
                            p.getMengeneinheit() != null ? p.getMengeneinheit() : "Stk",
                            p.getExterneArtikelnummer(),
                            treffer.map(Artikel::getId).orElse(null),
                            treffer.map(Artikel::getProduktname).orElse(null),
                            treffer.isPresent(),
                            p.getEinzelpreis(),
                            lieferant != null ? lieferant.getId() : null,
                            lieferant != null ? lieferant.getLieferantenname() : null);
                })
                .toList();
        return new LagerVorschau(positionen, lagerGebuchtAm);
    }

    /**
     * Bucht die bereits erkannten Positionen eines Lieferanten-Dokuments direkt
     * als Lagerzugang. Artikel werden nach externer Artikelnummer und Bezeichnung
     * abgeglichen. Für Positionen ohne automatischen Treffer kann der Aufrufer
     * explizit eine Artikel-ID mitgeben (z. B. aus einer zuvor geöffneten Anlegen-Maske).
     *
     * @param expliziteArtikelIds Positions-ID → Artikel-ID, überschreibt den automatischen Abgleich
     */
    @Transactional
    public BuchenErgebnis buchenAusDokument(Long geschaeftsdokumentId, Long lagerortId, String benutzer,
            Map<Long, Long> expliziteArtikelIds) {
        var positionen = positionRepository.findByGeschaeftsdokumentId(geschaeftsdokumentId).stream()
                .filter(p -> p.getMenge() != null && p.getMenge().compareTo(BigDecimal.ZERO) > 0)
                .toList();
        if (positionen.isEmpty()) {
            return new BuchenErgebnis(0, 0, List.of("Keine Positionen im Dokument gefunden."));
        }
        // Lieferant aus dem ersten Dokument des Geschäftsdokuments ermitteln (für Preishistorie)
        Long lieferantId = positionen.stream()
                .filter(p -> p.getGeschaeftsdokument() != null && p.getGeschaeftsdokument().getDokument() != null
                        && p.getGeschaeftsdokument().getDokument().getLieferant() != null)
                .findFirst()
                .map(p -> p.getGeschaeftsdokument().getDokument().getLieferant().getId())
                .orElse(null);

        List<BuchenPosition> buchenPositionen = positionen.stream().map(p -> {
            Long explizit = expliziteArtikelIds.get(p.getId());
            if (explizit != null) {
                Artikel artikel = artikelRepository.findById(explizit)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "Artikel mit ID " + explizit + " nicht gefunden."));
                return new BuchenPosition(
                        artikel.getId(), p.getMenge(),
                        p.getExterneArtikelnummer(), p.getBezeichnung(),
                        p.getMengeneinheit() != null ? p.getMengeneinheit() : "Stk", false);
            }
            Optional<Artikel> treffer = sucheArtikel(p.getExterneArtikelnummer(), p.getBezeichnung());
            return new BuchenPosition(
                    treffer.map(Artikel::getId).orElse(null),
                    p.getMenge(),
                    p.getExterneArtikelnummer(),
                    p.getBezeichnung(),
                    p.getMengeneinheit() != null ? p.getMengeneinheit() : "Stk",
                    treffer.isEmpty());
        }).toList();

        BuchenErgebnis ergebnis = buchen(new BuchenEingabe(buchenPositionen, lagerortId, "Wareneingang aus Lieferantenrechnung"), benutzer);

        // Einkaufspreis je Artikel aus der Rechnung in die Preishistorie übernehmen
        if (lieferantId != null) {
            for (int i = 0; i < positionen.size(); i++) {
                var pos = positionen.get(i);
                if (pos.getEinzelpreis() == null || pos.getEinzelpreis().compareTo(BigDecimal.ZERO) <= 0) continue;
                Long artikelId = buchenPositionen.get(i).artikelId();
                if (artikelId == null) continue;
                artikelpreisService.schreibePreisstand(
                        lieferantId, artikelId, pos.getEinzelpreis(),
                        pos.getExterneArtikelnummer(), PreisQuelle.RECHNUNG, null);
            }
        }

        if (ergebnis.gebucht() > 0) {
            geschaeftsdokumentRepository.findById(geschaeftsdokumentId).ifPresent(gd -> {
                gd.setLagerGebuchtAm(java.time.LocalDateTime.now());
                geschaeftsdokumentRepository.save(gd);
            });
        }

        return ergebnis;
    }

    private Optional<Artikel> sucheArtikel(String externeArtikelnummer, String bezeichnung) {
        Optional<Artikel> treffer = Optional.empty();
        if (externeArtikelnummer != null && !externeArtikelnummer.isBlank()) {
            treffer = artikelRepository.findByArtikelnummer(externeArtikelnummer);
            if (treffer.isEmpty()) {
                treffer = artikelRepository.findByExterneArtikelnummer(externeArtikelnummer);
            }
        }
        if (treffer.isEmpty() && bezeichnung != null && !bezeichnung.isBlank()) {
            treffer = artikelRepository.findFirstByProduktnameIgnoreCase(bezeichnung);
        }
        return treffer;
    }
}
