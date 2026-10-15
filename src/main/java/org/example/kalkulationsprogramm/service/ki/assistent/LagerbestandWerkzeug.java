package org.example.kalkulationsprogramm.service.ki.assistent;

import java.util.List;

import org.example.kalkulationsprogramm.domain.Funktion;
import org.example.kalkulationsprogramm.domain.FunktionsAktion;
import org.example.kalkulationsprogramm.dto.Lager.LagerBestandDto;
import org.example.kalkulationsprogramm.service.FunktionsRechteService;
import org.example.kalkulationsprogramm.service.LagerModulSchalterService;
import org.example.kalkulationsprogramm.service.LagerService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

/** Auskunft: Lagerbestand eines Artikels nach Name oder Artikelnummer, mit Lagerorten und Mindestbestand. Nur mit Recht „Lager ansehen“ und eingeschaltetem Lagermodul. */
@Component
@RequiredArgsConstructor
public class LagerbestandWerkzeug implements KiWerkzeug {

    static final int MAX_TREFFER = 10;

    private final LagerService lager;
    private final FunktionsRechteService rechte;
    private final ObjectProvider<LagerModulSchalterService> modul;

    @Override
    public String name() {
        return "lagerbestand";
    }

    @Override
    public String beschreibung() {
        return "Zeigt den Lagerbestand von Artikeln, die im Lager geführt werden (Suche nach Name oder Artikelnummer): Bestand, Einheit, Lagerorte, Mindestbestand und Warnungen.";
    }

    @Override
    public JsonNode parameter() {
        return WerkzeugHilfe.sucheSchema("Name oder Artikelnummer des Artikels im Lager");
    }

    @Override
    public boolean aendert() {
        return false;
    }

    @Override
    public boolean verfuegbar(KiKontext k) {
        LagerModulSchalterService m = modul.getIfAvailable();
        if (m != null && !m.istAktiv()) {
            return false;
        }
        return k.admin() || rechte.darfProfil(k.profilId(), Funktion.LAGER, FunktionsAktion.ANSEHEN);
    }

    @Override
    public String ausfuehren(JsonNode angaben, KiKontext kontext) {
        if (!verfuegbar(kontext)) {
            throw new KiWerkzeugFehler(FunktionsRechteService.fehlermeldung(Funktion.LAGER, FunktionsAktion.ANSEHEN));
        }
        String suche = WerkzeugHilfe.suchbegriff(angaben);
        List<LagerBestandDto> zeilen = lager.bestand(suche, false);
        if (zeilen.isEmpty()) {
            return "Im Lager gibt es keinen Artikel zu \"" + suche + "\". (Nur Artikel mit Buchung oder Mindestbestand werden im Lager geführt.)";
        }
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (LagerBestandDto z : zeilen) {
            if (++n > MAX_TREFFER) {
                sb.append("… weitere Treffer, bitte genauer suchen.");
                break;
            }
            sb.append("- ID ").append(z.artikelId()).append(" | ").append(z.bezeichnung());
            if (z.artikelnummer() != null) {
                sb.append(" | Nr. ").append(z.artikelnummer());
            }
            sb.append(" | Bestand ").append(z.bestand().stripTrailingZeros().toPlainString()).append(' ').append(z.einheit());
            if (z.mindestbestand() != null) {
                sb.append(" | Mindestbestand ").append(z.mindestbestand().stripTrailingZeros().toPlainString());
            }
            if (z.negativ()) {
                sb.append(" | ACHTUNG: negativ, es wurde mehr entnommen als eingebucht");
            } else if (z.unterMindestbestand()) {
                sb.append(" | unter dem Mindestbestand");
            }
            if (!z.orte().isEmpty()) {
                sb.append(" | Orte: ");
                sb.append(String.join(", ", z.orte().stream().map(o -> o.lagerortName() + " " + o.bestand().stripTrailingZeros().toPlainString()).toList()));
            }
            sb.append('\n');
        }
        return sb.toString().stripTrailing();
    }
}
