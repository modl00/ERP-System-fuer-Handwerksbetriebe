package org.example.kalkulationsprogramm.service.wartung;

import java.util.List;

import org.example.kalkulationsprogramm.domain.Funktion;
import org.example.kalkulationsprogramm.domain.FunktionsAktion;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.dto.wartung.AppAnlageAktualisierenDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungAnlageDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungDatenfeldWertDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungInhaltAenderungDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungModulAuswahlDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungProjektWartungDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungProtokollAnlegenDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungProtokollDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungPruefpunktErgebnisDto;
import org.example.kalkulationsprogramm.domain.wartung.WartungStatus;
import org.example.kalkulationsprogramm.dto.wartung.WartungUnterschriftDto;
import org.example.kalkulationsprogramm.service.FunktionsRechteService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/**
 * Der Feldeinsatz für die Handy-App (Issue #128, Außendienst): derselbe Weg wie am PC ({@link WartungProtokollService}), aber mit dem Zugangslink der App
 * statt der Anmeldung. Jeder Aufruf prüft <b>zuerst</b> den Zugang und das Recht an der Funktion „Wartung“ (Ansehen, Anlegen oder Ändern, wie in „Wer darf
 * was?“ eingestellt) und das Wartungsmodul; erst dann geschieht etwas. Der Zugangslink gilt wie ein Passwort und steht im Kopf der Anfrage, nie in der Adresse.
 */
@Service
@RequiredArgsConstructor
public class AppWartungService {

    private final FunktionsRechteService rechte;
    private final WartungAnlageService anlageService;
    private final WartungProtokollService protokollService;
    private final WartungProtokollPdfService pdfService;
    private final ObjectProvider<WartungModulSchalterService> modulSchalter;

    private Mitarbeiter pruefe(String token, FunktionsAktion aktion) {
        WartungModulSchalterService schalter = modulSchalter.getIfAvailable();
        if (schalter != null && !schalter.istAktiv()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Das Wartungsmodul ist ausgeschaltet.");
        }
        return rechte.verlangeFuerToken(token, Funktion.WARTUNG, aktion);
    }

    /** Stammdaten einer Anlage für die Bearbeitungsseite in der App. */
    public WartungAnlageDto anlage(String token, Long id) {
        pruefe(token, FunktionsAktion.ANSEHEN);
        return anlageService.laden(id);
    }

    /** Speichert Bezeichnung, Notizen und Intervall – die Felder, die der Techniker vor Ort anpassen kann. */
    public WartungAnlageDto anlageSpeichern(String token, Long id, AppAnlageAktualisierenDto dto) {
        pruefe(token, FunktionsAktion.AENDERN);
        return anlageService.aktualisieren(id, dto);
    }

    /** Die Wartungen eines Projekts mit offenem Feldeinsatz und den abgelegten Berichten. */
    public List<WartungProjektWartungDto> anlagenDesProjekts(String token, Long projektId) {
        pruefe(token, FunktionsAktion.ANSEHEN);
        return anlageService.projektWartungen(projektId);
    }

    /**
     * Startet den Feldeinsatz einer Wartung oder öffnet den laufenden: Läuft schon einer, wird er zurückgegeben (so entsteht bei einer Wiederholung nach einem
     * Verbindungsabbruch kein zweiter). Der Angemeldete ist der Techniker; Kunde, Objekt, Projekt und Module kommen von der Wartung, die letzte abgeschlossene
     * Prüfung ist die Vorlage (Folgeprüfung).
     */
    public WartungProtokollDto feldeinsatzStarten(String token, Long tuerId) {
        Mitarbeiter m = pruefe(token, FunktionsAktion.ANLEGEN);
        WartungAnlageDto a = anlageService.laden(tuerId);
        if (a.offenerFeldeinsatzId() != null) {
            return protokollService.laden(a.offenerFeldeinsatzId());
        }
        if (a.kunde() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Diese Wartung hat keinen Kunden. Bitte im Büro am PC vervollständigen.");
        }
        boolean vorlage = a.letztePruefungId() != null;
        List<WartungModulAuswahlDto> module = vorlage ? null : a.module().stream().map(x -> new WartungModulAuswahlDto(x.modulId(), x.bauartSchluessel())).toList();
        WartungProtokollAnlegenDto dto = new WartungProtokollAnlegenDto(a.kunde().id(), a.objekt() == null ? null : a.objekt().id(), a.id(),
                a.projekt() == null ? null : a.projekt().id(), m.getId(), null, null, null, module, null, null, a.letztePruefungId(), vorlage ? Boolean.TRUE : null);
        return protokollService.anlegen(dto, m.getId());
    }

    public WartungProtokollDto protokoll(String token, Long id) {
        pruefe(token, FunktionsAktion.ANSEHEN);
        return protokollService.laden(id);
    }

    public WartungInhaltAenderungDto pruefpunktBewerten(String token, Long id, Long pruefpunktId, WartungPruefpunktErgebnisDto dto) {
        pruefe(token, FunktionsAktion.AENDERN);
        return protokollService.pruefpunktBewerten(id, pruefpunktId, dto);
    }

    public WartungInhaltAenderungDto datenfeldSetzen(String token, Long id, Long datenfeldId, WartungDatenfeldWertDto dto) {
        pruefe(token, FunktionsAktion.AENDERN);
        return protokollService.datenfeldSetzen(id, datenfeldId, dto);
    }

    public WartungProtokollDto unterschreiben(String token, Long id, WartungUnterschriftDto dto) {
        pruefe(token, FunktionsAktion.AENDERN);
        return protokollService.unterschreiben(id, dto);
    }

    /** Die gemerkte Unterschrift des Mitarbeiters verwenden (nur wenn er selbst der Techniker ist, das prüft der Dienst). */
    public WartungProtokollDto gemerkteUnterschrift(String token, Long id) {
        Mitarbeiter m = pruefe(token, FunktionsAktion.AENDERN);
        return protokollService.gespeicherteUnterschriftVerwenden(id, m.getId());
    }

    public WartungProtokollDto status(String token, Long id, WartungStatus status) {
        pruefe(token, FunktionsAktion.AENDERN);
        return protokollService.statusWechseln(id, status);
    }

    public WartungProtokollPdfService.PdfDatei pdf(String token, Long id) {
        pruefe(token, FunktionsAktion.ANSEHEN);
        return pdfService.erstelle(id);
    }
}
