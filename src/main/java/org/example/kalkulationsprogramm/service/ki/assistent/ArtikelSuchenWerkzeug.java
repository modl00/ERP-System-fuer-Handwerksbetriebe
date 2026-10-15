package org.example.kalkulationsprogramm.service.ki.assistent;

import java.util.List;
import java.util.Locale;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

/** Auskunft: Artikel nach Name, Artikelnummer, EAN oder Maßen suchen (höchstens 10 Treffer). */
@Component
@RequiredArgsConstructor
public class ArtikelSuchenWerkzeug implements KiWerkzeug {

    static final int MAX_TREFFER = 10;

    private final ArtikelRepository artikel;

    @Override
    public String name() {
        return "artikel_suchen";
    }

    @Override
    public String beschreibung() {
        return "Sucht Artikel im Artikelstamm nach Name, Artikelnummer, EAN oder Maßen (zum Beispiel \"rundrohr 42,4\"). Liefert bis zu 10 Treffer mit Nummer, Einheit und günstigstem Preis.";
    }

    @Override
    public JsonNode parameter() {
        return WerkzeugHilfe.sucheSchema("Suchbegriffe, zum Beispiel Name, Artikelnummer oder EAN");
    }

    @Override
    public boolean aendert() {
        return false;
    }

    @Override
    public boolean verfuegbar(KiKontext kontext) {
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public String ausfuehren(JsonNode angaben, KiKontext kontext) {
        String suche = WerkzeugHilfe.suchbegriff(angaben);
        String klein = suche.toLowerCase(Locale.GERMAN);
        String[] woerter = klein.split("[\\s×x]+");
        String w1 = like(woerter, 0);
        String w2 = like(woerter, 1);
        String w3 = like(woerter, 2);
        List<Artikel> treffer = artikel.sucheFuerAssistent(w1, w2, w3, "%" + klein + "%", suche.replaceAll("\\s+", ""), PageRequest.of(0, MAX_TREFFER));
        if (treffer.isEmpty()) {
            return "Kein Artikel gefunden für \"" + suche + "\".";
        }
        StringBuilder sb = new StringBuilder(treffer.size() + (treffer.size() == 1 ? " Treffer" : " Treffer") + " für \"" + suche + "\":\n");
        for (Artikel a : treffer) {
            sb.append("- ID ").append(a.getId()).append(" | ").append(nvl(a.getProduktname()));
            if (a.getArtikelnummer() != null) {
                sb.append(" | Nr. ").append(a.getArtikelnummer());
            }
            if (a.getEan() != null) {
                sb.append(" | EAN ").append(a.getEan());
            }
            if (a.getVerrechnungseinheit() != null) {
                sb.append(" | ").append(a.getVerrechnungseinheit().getAnzeigename());
            }
            a.getGuenstigsterPreis().ifPresent(p -> sb.append(" | ab ").append(p.getPreis().toPlainString()).append(" €"));
            sb.append('\n');
        }
        return sb.toString().stripTrailing();
    }

    private static String like(String[] woerter, int i) {
        return i < woerter.length && !woerter[i].isBlank() ? "%" + woerter[i] + "%" : "%";
    }

    private static String nvl(String s) {
        return s == null ? "(ohne Name)" : s;
    }
}
