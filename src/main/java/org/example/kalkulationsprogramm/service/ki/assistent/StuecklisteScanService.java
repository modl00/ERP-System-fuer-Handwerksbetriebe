package org.example.kalkulationsprogramm.service.ki.assistent;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelInProjekt;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelCreateDto;
import org.example.kalkulationsprogramm.repository.ArtikelInProjektRepository;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.service.ArtikelService;
import org.example.kalkulationsprogramm.service.GeminiDokumentAnalyseService;
import org.example.kalkulationsprogramm.service.ProjektManagementService;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterFehler;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterService;
import org.example.kalkulationsprogramm.service.ki.KiFunktion;
import org.example.kalkulationsprogramm.service.ki.KiObergrenze;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Stückliste aus einem Dokument per KI auslesen und als Bedarfsliste im Projekt anlegen.
 * Issue #536.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StuecklisteScanService {

    static final long MAX_BYTES = 15L * 1024 * 1024;

    private static final String PROMPT =
            "Du analysierst ein Dokument (z.B. Stueckliste, Materialliste, Aufmass, Planung oder Leistungsverzeichnis). "
            + "Extrahiere alle Materialpositionen und Stucklisten-Eintrage vollstaendig. "
            + "Antworte NUR mit einem validen JSON-Objekt, kein Markdown, keine Erklaerungen.\n"
            + "{\n"
            + "  \"positionen\": [\n"
            + "    {\n"
            + "      \"bezeichnung\": \"Produktbezeichnung oder Materialname\",\n"
            + "      \"menge\": 1.0,\n"
            + "      \"einheit\": \"Stk\"\n"
            + "    }\n"
            + "  ]\n"
            + "}\n"
            + "Wenn keine Menge angegeben ist, setze 1.0. "
            + "Einheiten normalisieren: Stueck/St/Stk -> \"Stk\", Meter/m -> \"m\", Kilogramm/kg -> \"kg\", "
            + "Liter/l -> \"l\", Pakete/Pkg -> \"Pkg\", Quadratmeter/m2 -> \"m2\". "
            + "Ignoriere Ueberschriften, Summenzeilen und reine Texte ohne Materialbezug.";

    private final GeminiDokumentAnalyseService geminiService;
    private final KiAnbieterService anbieterService;
    private final ArtikelRepository artikelRepository;
    private final ArtikelService artikelService;
    private final ProjektManagementService projektManagementService;
    private final ArtikelInProjektRepository artikelInProjektRepository;
    private final ObjectMapper objectMapper;

    public record ScanPosition(
            String bezeichnung,
            BigDecimal menge,
            String einheit,
            Long artikelId,
            String artikelName,
            boolean gefunden) {
    }

    public record ScanErgebnis(
            List<ScanPosition> positionen,
            List<String> hinweise) {
    }

    public record UebernahmePosition(
            Long artikelId,
            String bezeichnung,
            double menge,
            String einheit,
            boolean neuAnlegen) {
    }

    public record UebernahmeErgebnis(
            int uebernommen,
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
                    "Die Stueckliste konnte nicht ausgelesen werden. Bitte ein lesbares PDF oder Foto hochladen.");
        }

        return parseUndAbgleichen(json);
    }

    @Transactional
    public UebernahmeErgebnis uebernehmen(Long projektId, List<UebernahmePosition> positionen) {
        Projekt projekt = projektManagementService.findeProjektEntity(projektId);
        int uebernommen = 0;
        int neuAngelegt = 0;
        List<String> meldungen = new ArrayList<>();

        for (UebernahmePosition pos : positionen) {
            if (pos.menge() <= 0) {
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
                Artikel artikel = artikelRepository.findById(artikelId).orElse(null);
                if (artikel == null) {
                    continue;
                }

                ArtikelInProjekt aip = new ArtikelInProjekt();
                aip.setProjekt(projekt);
                aip.setArtikel(artikel);
                aip.setHinzugefuegtAm(LocalDate.now());
                aip.setAusLager(false);
                aip.setBestellt(false);

                Verrechnungseinheit ve = verrechnungseinheitAus(pos.einheit());
                BigDecimal menge = BigDecimal.valueOf(pos.menge());
                if (ve == Verrechnungseinheit.LAUFENDE_METER) {
                    aip.setMeter(menge);
                } else if (ve == Verrechnungseinheit.KILOGRAMM) {
                    aip.setKilogramm(menge);
                } else {
                    aip.setStueckzahl(menge.intValue());
                }

                artikelInProjektRepository.save(aip);
                uebernommen++;
            } catch (Exception e) {
                log.warn("[StuecklisteScan] Uebernehmen fuer \"{}\" fehlgeschlagen: {}", pos.bezeichnung(), e.getMessage());
                meldungen.add("\"" + pos.bezeichnung() + "\" konnte nicht uebernommen werden: " + e.getMessage());
            }
        }
        return new UebernahmeErgebnis(uebernommen, neuAngelegt, meldungen);
    }

    private ScanErgebnis parseUndAbgleichen(String json) {
        List<ScanPosition> positionen = new ArrayList<>();
        List<String> hinweise = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode posArr = root.path("positionen");
            if (posArr.isMissingNode() || !posArr.isArray()) {
                hinweise.add("Die KI hat keine Positionen erkannt. Bitte pruefen Sie, ob das Dokument Materialpositionen enthaelt.");
                return new ScanErgebnis(positionen, hinweise);
            }

            for (JsonNode p : posArr) {
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

                Optional<Artikel> treffer = artikelRepository.findFirstByProduktnameIgnoreCase(bezeichnung);

                if (treffer.isPresent()) {
                    Artikel a = treffer.get();
                    positionen.add(new ScanPosition(bezeichnung, menge, einheit, a.getId(), a.getProduktname(), true));
                } else {
                    positionen.add(new ScanPosition(bezeichnung, menge, einheit, null, null, false));
                }
            }
        } catch (Exception e) {
            log.warn("[StuecklisteScan] JSON-Parsing fehlgeschlagen: {}", e.getMessage());
            hinweise.add("Die Antwort der KI konnte nicht ausgelesen werden. Bitte erneut versuchen.");
        }

        if (positionen.isEmpty()) {
            hinweise.add("Keine Positionen erkannt. Bitte pruefen Sie, ob das Dokument eine Stueckliste oder Materialliste ist.");
        }
        return new ScanErgebnis(positionen, hinweise);
    }

    private Artikel legeArtikelAn(UebernahmePosition pos) {
        ArtikelCreateDto dto = new ArtikelCreateDto();
        dto.setProduktname(pos.bezeichnung());
        dto.setVerrechnungseinheit(verrechnungseinheitAus(pos.einheit()));
        return artikelService.erstelleArtikel(dto);
    }

    private Verrechnungseinheit verrechnungseinheitAus(String einheit) {
        if (einheit == null) {
            return Verrechnungseinheit.STUECK;
        }
        return switch (einheit.toLowerCase().trim()) {
            case "m", "meter", "lm" -> Verrechnungseinheit.LAUFENDE_METER;
            case "m2", "qm" -> Verrechnungseinheit.QUADRATMETER;
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
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode n = node.path(field);
        if (n.isNull() || n.isMissingNode()) return null;
        String v = n.asText(null);
        return (v == null || v.equalsIgnoreCase("null") || v.isBlank()) ? null : v.strip();
    }

    private static BigDecimal mengeOrNull(JsonNode node, String field) {
        JsonNode n = node.path(field);
        if (n.isNull() || n.isMissingNode()) return null;
        try {
            return new BigDecimal(n.asText("0"));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
