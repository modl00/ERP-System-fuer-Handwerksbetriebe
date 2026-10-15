package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.Funktion;
import org.example.kalkulationsprogramm.domain.FunktionsAktion;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.dto.Lager.LagerBestandDto;
import org.example.kalkulationsprogramm.dto.Lager.LagerBuchungEingabe;
import org.example.kalkulationsprogramm.dto.Lager.LagerortDto;
import org.example.kalkulationsprogramm.dto.Lager.UmbuchungEingabe;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/**
 * Das Lager in der Handy-App (Issue #69, Stufe 2): suchen, per Strichcode finden, Entnahme, Zugang, Inventur, Umbuchung und einen Strichcode einem
 * Artikel zuordnen. Die App arbeitet ohne Benutzer-Anmeldung mit dem Zugangslink des Mitarbeiters; jede Aktion prüft deshalb auf dem Server Modul und
 * Funktionsrecht ({@link FunktionsRechteService#verlangeFuerToken}) und bucht unter dem Namen des Mitarbeiters.
 *
 * <p>Rechte: ansehen für Suche und Anzeige, anlegen für Zugang und Entnahme, ändern für Inventur, Umbuchung und das Zuordnen eines Strichcodes.</p>
 */
@Service
@RequiredArgsConstructor
public class LagerAppService {

    static final int MAX_TREFFER = 30;

    /** Ergebnis der Suche per Strichcode: {@code gefunden} false heißt, die Nummer ist noch keinem Artikel zugeordnet. */
    public record EanTreffer(boolean gefunden, String ean, LagerBestandDto artikel) { }

    private final FunktionsRechteService rechte;
    private final LagerService lager;
    private final ArtikelRepository artikel;
    private final ObjectProvider<LagerModulSchalterService> schalter;

    private Mitarbeiter pruefe(String token, FunktionsAktion aktion) {
        LagerModulSchalterService s = schalter.getIfAvailable();
        if (s != null && !s.istAktiv()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Das Lager ist ausgeschaltet.");
        }
        return rechte.verlangeFuerToken(token, Funktion.LAGER, aktion);
    }

    @Transactional(readOnly = true)
    public List<LagerortDto> orte(String token) {
        pruefe(token, FunktionsAktion.ANSEHEN);
        return lager.orte().stream().filter(LagerortDto::aktiv).toList();
    }

    @Transactional(readOnly = true)
    public List<LagerBestandDto> suche(String token, String suchbegriff) {
        pruefe(token, FunktionsAktion.ANSEHEN);
        String q = suchbegriff == null ? "" : suchbegriff.strip();
        if (q.length() < 2) {
            return List.of();
        }
        // Gesucht wird im ganzen Artikelstamm (Name, Artikelnummer, Strichcode), nicht nur unter dem, was schon im Lager liegt: Ein Artikel, der
        // noch nie gebucht wurde, muss für den ersten Zugang auffindbar sein.
        String klein = q.toLowerCase(Locale.GERMAN);
        String[] woerter = klein.split("[\\s×x]+");
        List<Long> ids = artikel.sucheFuerAssistent(like(woerter, 0), like(woerter, 1), like(woerter, 2), "%" + klein + "%", q.replaceAll("\\s+", ""),
                PageRequest.of(0, MAX_TREFFER)).stream().map(Artikel::getId).toList();
        return ids.isEmpty() ? List.of() : lager.bestandFuer(ids);
    }

    /** Wie viele „häufig benutzte“ Artikel die App zeigt, bevor man etwas sucht. */
    static final int MAX_HAEUFIG = 8;

    /** Die meistgebuchten Artikel (zuerst die eigenen), damit man sie ohne Suche findet. */
    @Transactional(readOnly = true)
    public List<LagerBestandDto> haeufig(String token) {
        Mitarbeiter m = pruefe(token, FunktionsAktion.ANSEHEN);
        return lager.bestandFuer(lager.haeufigeArtikelIds(benutzer(m), MAX_HAEUFIG));
    }

    @Transactional(readOnly = true)
    public EanTreffer nachEan(String token, String eingabe) {
        pruefe(token, FunktionsAktion.ANSEHEN);
        String ean = EanPruefung.normalisiert(eingabe);
        if (ean == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte einen Strichcode angeben.");
        }
        return artikel.findByEan(ean)
                .map(a -> new EanTreffer(true, ean, lager.bestandVon(a.getId())))
                .orElse(new EanTreffer(false, ean, null));
    }

    @Transactional
    public LagerBestandDto buche(String token, LagerBuchungEingabe e) {
        boolean inventur = e != null && e.art() != null && "KORREKTUR".equals(e.art().strip().toUpperCase(Locale.ROOT));
        Mitarbeiter m = pruefe(token, inventur ? FunktionsAktion.AENDERN : FunktionsAktion.ANLEGEN);
        return lager.buche(e, benutzer(m));
    }

    @Transactional
    public LagerBestandDto umbuche(String token, UmbuchungEingabe e) {
        Mitarbeiter m = pruefe(token, FunktionsAktion.AENDERN);
        return lager.umbuche(e, benutzer(m));
    }

    /** Ordnet einen Strichcode einem Artikel zu (wenn die Ware beim Scannen noch unbekannt war). Prüft Prüfziffer und Eindeutigkeit. */
    @Transactional
    public LagerBestandDto eanZuordnen(String token, Long artikelId, String eingabe) {
        pruefe(token, FunktionsAktion.AENDERN);
        String ean = EanPruefung.normalisiert(eingabe);
        if (ean == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte einen Strichcode angeben.");
        }
        String fehler = EanPruefung.fehler(ean);
        if (fehler != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fehler);
        }
        Artikel a = artikel.findById(artikelId == null ? -1L : artikelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Den Artikel gibt es nicht."));
        if (artikel.findByEan(ean).filter(x -> !x.getId().equals(a.getId())).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dieser Strichcode gehört schon zu einem anderen Artikel.");
        }
        a.setEan(ean);
        a.setSuchtext(ArtikelSuchtext.aufbauen(a));
        artikel.save(a);
        return lager.bestandVon(a.getId());
    }

    /** Das n-te Suchwort als Muster mit % davor und dahinter; fehlt es, passt jedes. */
    private static String like(String[] woerter, int index) {
        return index < woerter.length && !woerter[index].isBlank() ? "%" + woerter[index] + "%" : "%";
    }

    private static String benutzer(Mitarbeiter m) {
        String name = ((m.getVorname() == null ? "" : m.getVorname()) + " " + (m.getNachname() == null ? "" : m.getNachname())).strip();
        return (name.isEmpty() ? "Mitarbeiter " + m.getId() : name) + " (App)";
    }

    /** Für Tests: die Menge ist größer als 0? */
    static boolean positiv(BigDecimal menge) {
        return menge != null && menge.signum() > 0;
    }
}
