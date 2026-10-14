package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelInProjekt;
import org.example.kalkulationsprogramm.repository.ArtikelDokumentRepository;
import org.example.kalkulationsprogramm.repository.ArtikelInProjektRepository;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.LieferantenArtikelPreiseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/**
 * Archiv für Artikel. <b>Archivieren</b> nimmt einen Artikel aus Suche und Auswahl, ohne etwas zu verlieren; er lässt sich wiederherstellen.
 * <b>Endgültig löschen</b> geht nur für archivierte Artikel und (im Controller) nur für Administratoren. Wird der Artikel in keinem Projekt
 * verwendet, entfällt er samt Preisen und Dokumenten. Steckt er in Projekten, bleibt er als unsichtbarer Platzhalter bestehen: Die Projekte
 * (Stücklisten, Bestellungen, Nachkalkulation) hängen an ihm und zeigen weiter Namen und Preis.
 */
@Service
@RequiredArgsConstructor
public class ArtikelArchivService {

    private static final Logger LOG = LoggerFactory.getLogger(ArtikelArchivService.class);

    private final ArtikelRepository artikel;
    private final ArtikelInProjektRepository projektZeilen;
    private final LieferantenArtikelPreiseRepository preise;
    private final ArtikelDokumentRepository dokumente;
    private final ArtikelDokumentService dokumentService;

    /** Ein Projekt, in dem der Artikel steckt, mit der verwendeten Menge als Text. */
    public record ProjektNutzung(Long projektId, String bauvorhaben, String menge) { }

    /** Wo ein Artikel überall verwendet wird: Grundlage für die Frage „wirklich löschen?“. */
    public record Verwendungen(Long id, String name, boolean archiviert, List<ProjektNutzung> projekte, long lieferantenpreise, long dokumente, long varianten,
            String grundartikelName) { }

    /** Ergebnis des endgültigen Löschens. */
    public record Geloescht(boolean alsPlatzhalter, int projekte) { }

    /** Archiviert den Artikel; bei einem Grundartikel auf Wunsch auch seine Varianten. Gibt die Zahl der neu archivierten Artikel zurück. */
    @Transactional
    public int archiviere(Long id, boolean mitVarianten, String von) {
        Artikel a = aktiver(id);
        int anzahl = archiviereEinen(a, von) ? 1 : 0;
        if (mitVarianten && a.isGrundartikel()) {
            for (Artikel v : artikel.findByGrundartikelRef(a)) {
                if (archiviereEinen(v, von)) {
                    anzahl++;
                }
            }
        }
        return anzahl;
    }

    @Transactional
    public void stelleWiederHer(Long id) {
        Artikel a = artikel.findById(id).orElseThrow(ArtikelArchivService::unbekannt);
        if (a.getGeloeschtAm() != null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Den Artikel gibt es nicht mehr.");
        }
        a.setArchiviertAm(null);
        a.setArchiviertVon(null);
        artikel.save(a);
    }

    @Transactional(readOnly = true)
    public Verwendungen verwendungen(Long id) {
        Artikel a = aktiver(id);
        List<ProjektNutzung> projekte = new ArrayList<>();
        for (ArtikelInProjekt z : projektZeilen.findByArtikel_Id(id)) {
            projekte.add(new ProjektNutzung(z.getProjekt().getId(), z.getProjekt().getBauvorhaben(), menge(z)));
        }
        return new Verwendungen(a.getId(), a.getProduktname(), a.getArchiviertAm() != null, projekte, preise.countByArtikel_Id(id), dokumente.countByArtikelId(id),
                a.isGrundartikel() ? artikel.countByGrundartikelRef(a) : 0, a.getGrundartikelRef() == null ? null : a.getGrundartikelRef().getProduktname());
    }

    /** Löscht einen archivierten Artikel endgültig; steckt er in Projekten, bleibt er als unsichtbarer Platzhalter bestehen. */
    @Transactional
    public Geloescht loesche(Long id) {
        // Zeile sperren: Eine Projektzeile, die gleichzeitig auf den Artikel zeigt, wartet, bis hier entschieden ist (sonst würde sie mitgelöscht)
        Artikel a = id == null ? null : artikel.findByIdForUpdate(id).filter(x -> x.getGeloeschtAm() == null).orElse(null);
        if (a == null) {
            throw unbekannt();
        }
        if (a.getArchiviertAm() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bitte den Artikel zuerst archivieren. Endgültig gelöscht wird nur aus dem Archiv.");
        }
        if (a.isGrundartikel()) {
            long n = artikel.countByGrundartikelRef(a);
            if (n > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Dieser Grundartikel hat noch " + n + (n == 1 ? " Variante" : " Varianten")
                        + ". Bitte zuerst die Varianten löschen.");
            }
        }
        // Datenbankzeilen jetzt, die Dateien erst nach dem erfolgreichen Abschluss (sonst wären sie bei einem Rollback verloren)
        dokumentService.loescheAlleDokumenteNachCommit(id);
        int inProjekten = projektZeilen.findByArtikel_Id(id).size();
        if (inProjekten == 0) {
            artikel.delete(a);
            LOG.info("Artikel {} endgültig gelöscht", id);
            return new Geloescht(false, 0);
        }
        // Platzhalter: Name, Maße und Preise bleiben für die Projekte; alles, was den Artikel auffindbar oder wiederverwendbar macht, entfällt
        a.setGeloeschtAm(LocalDateTime.now());
        a.setArtikelnummer(null);
        a.setEan(null);
        a.setSuchtext(null);
        a.setGrundartikelRef(null);
        a.setKurzbezeichnung(null);
        // Vom Lieferantenabgleich abkoppeln: Weder CSV-Import noch Preisübernahme aus Angeboten und Rechnungen dürfen den Platzhalter wiederfinden.
        // Die Preisstände selbst bleiben, die Projektzeilen verweisen auf sie.
        a.getArtikelpreis().forEach(p -> {
            p.setExterneArtikelnummer(null);
            p.setAktuell(false);
        });
        artikel.save(a);
        LOG.info("Artikel {} endgültig gelöscht, bleibt als Platzhalter für {} Projektzeilen", id, inProjekten);
        return new Geloescht(true, inProjekten);
    }

    private boolean archiviereEinen(Artikel a, String von) {
        if (a.getArchiviertAm() != null) {
            return false;
        }
        a.setArchiviertAm(LocalDateTime.now());
        a.setArchiviertVon(von == null || von.isBlank() ? null : von.strip().substring(0, Math.min(100, von.strip().length())));
        artikel.save(a);
        return true;
    }

    /** Der Artikel zur ID; unbekannte und endgültig gelöschte Artikel gibt es für den Aufrufer nicht. */
    private Artikel aktiver(Long id) {
        Artikel a = id == null ? null : artikel.findById(id).orElse(null);
        if (a == null || a.getGeloeschtAm() != null) {
            throw unbekannt();
        }
        return a;
    }

    private static ResponseStatusException unbekannt() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Den Artikel gibt es nicht.");
    }

    private static String menge(ArtikelInProjekt z) {
        List<String> teile = new ArrayList<>();
        if (z.getStueckzahl() != null && z.getStueckzahl() > 0) {
            teile.add(z.getStueckzahl() + " Stück");
        }
        if (z.getMeter() != null && z.getMeter().signum() > 0) {
            teile.add(zahl(z.getMeter()) + " m");
        }
        if (z.getKilogramm() != null && z.getKilogramm().signum() > 0) {
            teile.add(zahl(z.getKilogramm()) + " kg");
        }
        return teile.isEmpty() ? "ohne Menge" : String.join(", ", teile);
    }

    private static String zahl(BigDecimal d) {
        return new java.text.DecimalFormat("#,##0.##", java.text.DecimalFormatSymbols.getInstance(Locale.GERMANY)).format(d);
    }
}
