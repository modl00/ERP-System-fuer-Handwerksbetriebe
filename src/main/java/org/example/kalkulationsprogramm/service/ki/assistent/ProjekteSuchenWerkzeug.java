package org.example.kalkulationsprogramm.service.ki.assistent;

import java.util.List;

import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

/** Auskunft: Projekte nach Bauvorhaben, Kunde, Kundennummer oder Ort suchen (höchstens 10 Treffer). Liefert die Projekt-ID, die andere Werkzeuge (zum Beispiel Zeit buchen) brauchen. */
@Component
@RequiredArgsConstructor
public class ProjekteSuchenWerkzeug implements KiWerkzeug {

    static final int MAX_TREFFER = 10;

    private final ProjektRepository projekte;

    @Override
    public String name() {
        return "projekte_suchen";
    }

    @Override
    public String beschreibung() {
        return "Sucht Projekte nach Bauvorhaben, Kundenname, Kundennummer oder Ort. Liefert bis zu 10 Treffer mit Projekt-ID, Bauvorhaben, Auftragsnummer, Kunde und ob das Projekt abgeschlossen ist.";
    }

    @Override
    public JsonNode parameter() {
        return WerkzeugHilfe.sucheSchema("Bauvorhaben, Kundenname, Kundennummer oder Ort des Projekts");
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
        List<Projekt> treffer = projekte.searchByBauvorhabenOrKundeOrEmail(suche);
        if (treffer.isEmpty()) {
            return "Kein Projekt gefunden für \"" + suche + "\".";
        }
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Projekt p : treffer) {
            if (++n > MAX_TREFFER) {
                sb.append("… weitere Treffer, bitte genauer suchen.");
                break;
            }
            sb.append("- ID ").append(p.getId()).append(" | ").append(WerkzeugHilfe.kuerzen(p.getBauvorhaben(), 80));
            if (p.getAuftragsnummer() != null && !p.getAuftragsnummer().isBlank()) {
                sb.append(" | Auftrag ").append(p.getAuftragsnummer());
            }
            if (p.getKundenId() != null) {
                sb.append(" | Kunde ").append(WerkzeugHilfe.kuerzen(p.getKundenId().getName(), 60));
            }
            sb.append(p.isAbgeschlossen() ? " | abgeschlossen" : " | offen").append('\n');
        }
        return sb.toString().stripTrailing();
    }
}
