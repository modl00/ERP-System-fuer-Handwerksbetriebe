package org.example.kalkulationsprogramm.service.ki.assistent;

import java.util.List;

import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

/** Auskunft: Kunden nach Name, Ansprechpartner oder E-Mail suchen (höchstens 10 Treffer, ohne E-Mail-Adressen und Anschrift in der Antwort). */
@Component
@RequiredArgsConstructor
public class KundenSuchenWerkzeug implements KiWerkzeug {

    static final int MAX_TREFFER = 10;

    private final KundeRepository kunden;

    @Override
    public String name() {
        return "kunden_suchen";
    }

    @Override
    public String beschreibung() {
        return "Sucht Kunden nach Name, Ansprechpartner oder E-Mail. Liefert bis zu 10 Treffer mit Kundennummer, Ort und Telefon.";
    }

    @Override
    public JsonNode parameter() {
        return WerkzeugHilfe.sucheSchema("Name, Ansprechpartner oder E-Mail des Kunden");
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
        List<Kunde> treffer = kunden.searchByNameOrAnsprechpartnerOrEmail(suche);
        if (treffer.isEmpty()) {
            return "Kein Kunde gefunden für \"" + suche + "\".";
        }
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Kunde k : treffer) {
            if (++n > MAX_TREFFER) {
                sb.append("… weitere Treffer, bitte genauer suchen.");
                break;
            }
            sb.append("- ID ").append(k.getId()).append(" | ").append(k.getName());
            if (k.getKundennummer() != null) {
                sb.append(" | Kundennr. ").append(k.getKundennummer());
            }
            if (k.getOrt() != null) {
                sb.append(" | ").append(k.getOrt());
            }
            if (k.getTelefon() != null) {
                sb.append(" | Tel. ").append(k.getTelefon());
            }
            sb.append('\n');
        }
        return sb.toString().stripTrailing();
    }
}
