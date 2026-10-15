package org.example.kalkulationsprogramm.service.ki.assistent;

import java.util.List;

import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

/** Auskunft: Lieferanten nach Name, zweitem Namen oder E-Mail suchen (höchstens 10 Treffer, ohne E-Mail-Adressen in der Antwort). */
@Component
@RequiredArgsConstructor
public class LieferantenSuchenWerkzeug implements KiWerkzeug {

    static final int MAX_TREFFER = 10;

    private final LieferantenRepository lieferanten;

    @Override
    public String name() {
        return "lieferanten_suchen";
    }

    @Override
    public String beschreibung() {
        return "Sucht Lieferanten nach Name, zweitem Namen oder E-Mail. Liefert bis zu 10 Treffer mit ID, Ort und Telefon. Immer zuerst benutzen, bevor ein Lieferant angelegt wird.";
    }

    @Override
    public JsonNode parameter() {
        return WerkzeugHilfe.sucheSchema("Name, zweiter Name oder E-Mail des Lieferanten");
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
        List<Lieferanten> treffer = lieferanten.searchByNameOrEmail(suche);
        if (treffer.isEmpty()) {
            return "Kein Lieferant gefunden für \"" + suche + "\".";
        }
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Lieferanten l : treffer) {
            if (++n > MAX_TREFFER) {
                sb.append("… weitere Treffer, bitte genauer suchen.");
                break;
            }
            sb.append("- ID ").append(l.getId()).append(" | ").append(l.getLieferantenname());
            if (l.getOrt() != null) {
                sb.append(" | ").append(l.getOrt());
            }
            if (l.getTelefon() != null) {
                sb.append(" | Tel. ").append(l.getTelefon());
            }
            sb.append('\n');
        }
        return sb.toString().stripTrailing();
    }
}
