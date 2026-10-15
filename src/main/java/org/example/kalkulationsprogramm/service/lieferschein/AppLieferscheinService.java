package org.example.kalkulationsprogramm.service.lieferschein;

import java.util.List;

import org.example.kalkulationsprogramm.domain.Funktion;
import org.example.kalkulationsprogramm.domain.FunktionsAktion;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.dto.lieferschein.LieferscheinAnlegenDto;
import org.example.kalkulationsprogramm.dto.lieferschein.LieferscheinDto;
import org.example.kalkulationsprogramm.dto.lieferschein.LieferscheinKopieDto;
import org.example.kalkulationsprogramm.dto.lieferschein.LieferscheinUnterschriftDto;
import org.example.kalkulationsprogramm.dto.lieferschein.NummernbereichDto;
import org.example.kalkulationsprogramm.repository.lieferschein.LieferscheinRepository;
import org.example.kalkulationsprogramm.service.FunktionsRechteService;
import org.example.kalkulationsprogramm.service.NummernkreisService;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Lieferscheine für die Handy-App (Issue #128): Anmeldung über den Zugangslink, Rechte an der Funktion „Lieferscheine“ wie in „Wer darf was?“ eingestellt.
 * Jeder Aufruf prüft zuerst Zugang und Recht; erst dann geschieht etwas.
 */
@Service
@RequiredArgsConstructor
public class AppLieferscheinService {

    private final FunktionsRechteService rechte;
    private final LieferscheinService service;
    private final NummernbereichService nummernbereiche;
    private final LieferscheinRepository lieferscheine;

    private Mitarbeiter pruefe(String token, FunktionsAktion aktion) {
        return rechte.verlangeFuerToken(token, Funktion.LIEFERSCHEINE, aktion);
    }

    public List<LieferscheinDto> imProjekt(String token, Long projektId) {
        pruefe(token, FunktionsAktion.ANSEHEN);
        return service.imProjekt(projektId);
    }

    public LieferscheinDto holen(String token, Long id) {
        pruefe(token, FunktionsAktion.ANSEHEN);
        return service.holen(id);
    }

    public LieferscheinDto anlegen(String token, LieferscheinAnlegenDto dto) {
        return service.anlegen(pruefe(token, FunktionsAktion.ANLEGEN), dto);
    }

    public LieferscheinDto unterschreiben(String token, Long id, LieferscheinUnterschriftDto dto) {
        pruefe(token, FunktionsAktion.AENDERN);
        return service.unterschreiben(id, dto);
    }

    public void loeschen(String token, Long id) {
        pruefe(token, FunktionsAktion.LOESCHEN);
        service.loeschen(id);
    }

    public LieferscheinPdfService.PdfDatei pdf(String token, Long id) {
        pruefe(token, FunktionsAktion.ANSEHEN);
        return service.pdf(id);
    }

    /** Die Kopie per E-Mail: nur mit Recht zum Ändern und mit Bestätigung. */
    public LieferscheinDto kopieSenden(String token, Long id, LieferscheinKopieDto dto) {
        pruefe(token, FunktionsAktion.AENDERN);
        return service.kopieSenden(id, dto);
    }

    /** Füllt den Nummernbereich dieses Geräts auf {@code soll} Nummern auf (nur wer Lieferscheine anlegen darf). */
    public NummernbereichDto nummernbereich(String token, int soll) {
        Mitarbeiter m = pruefe(token, FunktionsAktion.ANLEGEN);
        return nummernbereiche.auffuellen(m, NummernkreisService.LIEFERSCHEIN, soll, lieferscheine::existsByNummer);
    }
}
