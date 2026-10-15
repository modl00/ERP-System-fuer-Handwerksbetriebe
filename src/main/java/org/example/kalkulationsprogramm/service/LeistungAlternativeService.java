package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.Leistung;
import org.example.kalkulationsprogramm.domain.LeistungPosition;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.dto.Leistung.LeistungDto;
import org.example.kalkulationsprogramm.mapper.LeistungMapper;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.LeistungRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/**
 * Alternative Kalkulation direkt an einer Position im Angebot: Aus einer Katalogleistung oder einem Artikel entsteht eine <b>temporäre Leistung</b> (nicht im Katalog),
 * die der Bearbeiter wie im Leistungseditor kalkulieren kann, um kurzfristig eine individuelle Leistung zu erstellen. Das Original bleibt unberührt.
 * Die temporäre Leistung lässt sich später samt Kalkulation in den Leistungsstamm übernehmen (siehe {@link LeistungService#uebernehmeTemporaere}).
 */
@Service
@RequiredArgsConstructor
public class LeistungAlternativeService {

    private static final int MAX_NAME = 200;

    private final LeistungRepository leistungen;
    private final ArtikelRepository artikel;
    private final LeistungMapper mapper;

    /** Kopiert eine Leistung samt ihrer Kalkulation (Material, Arbeit, Baugruppen, Fremdleistungen) als temporäre Leistung „… (Alternative)“. */
    @Transactional
    public LeistungDto alternativeZuLeistung(Long id) {
        Leistung o = leistungen.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Die Leistung wurde nicht gefunden."));
        Leistung k = new Leistung();
        k.setBezeichnung(kuerze(o.getBezeichnung() + " (Alternative)"));
        k.setBeschreibung(o.getBeschreibung());
        k.setEinheit(o.getEinheit());
        k.setPreis(o.getPreis() == null ? BigDecimal.ZERO : o.getPreis());
        k.setKalkuliert(o.isKalkuliert());
        k.setZuschlagProzent(o.getZuschlagProzent());
        k.setLosgroesse(o.getLosgroesse());
        k.setKategorie(null);
        k.setBaugruppe(false);
        k.setTemporaer(true);
        for (LeistungPosition p : o.getPositionen()) {
            LeistungPosition c = new LeistungPosition();
            c.setLeistung(k);
            c.setTyp(p.getTyp());
            c.setArtikel(p.getArtikel());
            c.setArbeitsgang(p.getArbeitsgang());
            c.setBaugruppe(p.getBaugruppe());
            c.setFremdleistung(p.getFremdleistung());
            c.setGewichtKgJeEinheit(p.getGewichtKgJeEinheit());
            c.setMengeAus(p.getMengeAus());
            c.setMenge(p.getMenge());
            c.setRuestzeitMinuten(p.getRuestzeitMinuten());
            c.setBearbeitungszeitMinuten(p.getBearbeitungszeitMinuten());
            c.setAnzahlVorgaenge(p.getAnzahlVorgaenge());
            c.setVerschnittProzent(p.getVerschnittProzent());
            c.setMaschinenanteilProzent(p.getMaschinenanteilProzent());
            c.setVariablenJson(p.getVariablenJson());
            c.setSortOrder(p.getSortOrder());
            c.setNotiz(p.getNotiz());
            k.getPositionen().add(c);
        }
        return mapper.toDto(leistungen.save(k));
    }

    /** Legt für einen Artikel eine temporäre Leistung an, die den Artikel als Material enthält (Menge 1 je Einheit); Arbeit und mehr lassen sich in der Kalkulation ergänzen. */
    @Transactional
    public LeistungDto alternativeZuArtikel(Long artikelId, String name, Verrechnungseinheit einheit, BigDecimal preis) {
        Artikel a = artikel.findById(artikelId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Der Artikel wurde nicht gefunden."));
        Leistung k = new Leistung();
        String titel = name == null || name.isBlank() ? a.getProduktname() : name.strip();
        k.setBezeichnung(kuerze(titel));
        k.setEinheit(einheit == null ? Verrechnungseinheit.STUECK : einheit);
        k.setPreis(preis == null || preis.signum() < 0 ? BigDecimal.ZERO : preis);
        k.setBaugruppe(false);
        k.setTemporaer(true);
        LeistungPosition p = new LeistungPosition();
        p.setLeistung(k);
        p.setTyp(LeistungPosition.Typ.ARTIKEL);
        p.setArtikel(a);
        p.setMenge(BigDecimal.ONE.setScale(3));
        p.setSortOrder(0);
        k.getPositionen().add(p);
        return mapper.toDto(leistungen.save(k));
    }

    private static String kuerze(String text) {
        return text.length() > MAX_NAME ? text.substring(0, MAX_NAME) : text;
    }
}
