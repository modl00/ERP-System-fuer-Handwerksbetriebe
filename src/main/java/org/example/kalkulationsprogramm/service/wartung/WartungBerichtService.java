package org.example.kalkulationsprogramm.service.wartung;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.DokumentGruppe;
import org.example.kalkulationsprogramm.domain.ProjektDokument;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokoll;
import org.example.kalkulationsprogramm.dto.wartung.WartungBerichtDto;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungProtokollRepository;
import org.example.kalkulationsprogramm.service.DateiSpeicherService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * Erzeugt aus einer <b>abgeschlossenen</b> Prüfung den PDF-Bericht und legt ihn im Projekt ab. Dort ist er bei den
 * Dokumenten wieder aufrufbar, und das Protokoll merkt sich ihn. Ein erneutes Erzeugen ersetzt den bisherigen Bericht
 * (sonst stünden im Projekt mehrere Fassungen derselben Prüfung).
 */
@Slf4j
@Service
@AllArgsConstructor
public class WartungBerichtService {

    private final WartungProtokollRepository protokollRepository;
    private final WartungProtokollPdfService pdfService;
    private final DateiSpeicherService dateiSpeicherService;
    private final ProjektDokumentRepository dokumentRepository;

    /**
     * Legt den Bericht nach einem Abschluss von selbst im Projekt ab; hat der Feldeinsatz kein Projekt, geschieht nichts (kein Fehler). Gibt es schon einen Bericht, wird er
     * ersetzt. Der Dateiname beginnt mit dem Datum der Prüfung, damit die Berichte im Projekt nach Datum geordnet stehen.
     */
    @Transactional
    public void erstelleAutomatischImProjekt(Long protokollId) {
        WartungProtokoll protokoll = protokollRepository.findById(protokollId).orElse(null);
        if (protokoll == null || protokoll.getProjekt() == null || protokoll.getAbgeschlossenAm() == null) {
            return;
        }
        erstelleImProjekt(protokollId);
    }

    /** „Wartung_2026-10-08_“ davor: Datum der Prüfung, sonst des Abschlusses. */
    static String datumsPraefix(WartungProtokoll protokoll) {
        java.time.LocalDate datum = protokoll.getDatum() != null ? protokoll.getDatum()
                : protokoll.getAbgeschlossenAm() != null ? protokoll.getAbgeschlossenAm().toLocalDate() : java.time.LocalDate.now();
        return "Wartung_" + datum + "_";
    }

    @Transactional
    public WartungBerichtDto erstelleImProjekt(Long protokollId) {
        WartungProtokoll protokoll = protokollRepository.findMitSperre(protokollId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Das Protokoll wurde nicht gefunden."));
        if (protokoll.getAbgeschlossenAm() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Der Bericht lässt sich erst nach dem Abschluss des Feldeinsatzes erstellen.");
        }
        if (protokoll.getProjekt() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Dieser Feldeinsatz gehört zu keinem Projekt. Bitte zuerst unter \"Angaben\" ein Projekt wählen.");
        }

        WartungProtokollPdfService.PdfDatei pdf = pdfService.erstelle(protokollId);
        ProjektDokument bisher = protokoll.getBerichtDokumentId() == null ? null
                : dokumentRepository.findById(protokoll.getBerichtDokumentId()).orElse(null);

        ProjektDokument neu = dateiSpeicherService.speichereErzeugteDatei(
                pdf.inhalt(), datumsPraefix(protokoll) + pdf.dateiname(), protokoll.getProjekt().getId(), DokumentGruppe.DIVERSE_DOKUMENTE, "application/pdf");
        protokoll.setBerichtDokumentId(neu.getId());
        protokoll.setBerichtErstelltAm(LocalDateTime.now());
        protokollRepository.save(protokoll);

        if (bisher != null) {
            try {
                dateiSpeicherService.loescheDatei(bisher.getId());
            } catch (RuntimeException e) {
                // Der neue Bericht ist gespeichert, eine übrig gebliebene alte Fassung ist nur unschön
                log.warn("Der bisherige Bericht {} konnte nicht entfernt werden", bisher.getId(), e);
            }
        }
        return new WartungBerichtDto(neu.getId(), neu.getOriginalDateiname(), dokumentUrl(neu), protokoll.getBerichtErstelltAm());
    }

    /** Adresse, unter der das Dokument geöffnet wird (dieselbe wie bei den Projekt-Dokumenten). */
    public static String dokumentUrl(ProjektDokument dokument) {
        return "/api/dokumente/" + UriUtils.encodePathSegment(dokument.getGespeicherterDateiname(), StandardCharsets.UTF_8);
    }
}
