package org.example.kalkulationsprogramm.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.domain.Dokumenttyp;
import org.example.kalkulationsprogramm.domain.Firmeninformation;
import org.example.kalkulationsprogramm.domain.Textbaustein;
import org.example.kalkulationsprogramm.dto.Formular.FormularTextbausteinDefaultsDto;
import org.example.kalkulationsprogramm.dto.Formular.VorlagenEinstellungen;
import org.example.kalkulationsprogramm.dto.Textbaustein.TextbausteinDto;
import org.example.kalkulationsprogramm.repository.FirmeninformationRepository;
import org.example.kalkulationsprogramm.repository.FormularTemplateAssignmentRepository;
import org.example.kalkulationsprogramm.repository.TextbausteinRepository;
import org.example.kalkulationsprogramm.service.StandardVorlagenInhalt.Familie;
import org.example.kalkulationsprogramm.service.StandardVorlagenInhalt.Texte;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Legt die mitgelieferten Standardvorlagen an: je eine Vorlage für Angebot und Auftrag, Rechnung und Mahnung, dazu Vortext und
 * Nachtext für jeden Dokumenttyp. Nichts Vorhandenes wird überschrieben oder geändert:
 * <ul>
 *   <li>Eine Vorlage mit gleichem Namen bleibt unangetastet (samt ihrer Textbausteine).</li>
 *   <li>Ein Dokumenttyp, dem schon eine Vorlage zugeordnet ist, behält diese Zuordnung.</li>
 *   <li>Ein Textbaustein mit gleichem Namen wird weiterverwendet statt doppelt angelegt.</li>
 * </ul>
 * Dadurch ist der Aufruf beliebig oft möglich, zum Beispiel über den Knopf "Standardvorlagen anlegen".
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StandardVorlagenService {

    private final FormularTemplateService vorlagenService;
    private final FormularTemplateAssignmentRepository zuordnungen;
    private final TextbausteinService textbausteinService;
    private final TextbausteinRepository textbausteine;
    private final FormularTextbausteinDefaultService standardtexte;
    private final FirmeninformationRepository firmen;

    /** Ergebnis eines Aufrufs: was neu angelegt wurde und was schon da war. */
    public record Ergebnis(List<String> angelegteVorlagen, List<String> vorhandeneVorlagen, int neueTextbausteine) {
    }

    @Transactional
    public Ergebnis anlegen() {
        List<String> angelegt = new ArrayList<>();
        List<String> vorhanden = new ArrayList<>();
        int neueTexte = 0;
        List<String> bekannte = vorlagenService.listNamedTemplates().stream()
                .map(v -> v.getName() == null ? "" : v.getName().toLowerCase())
                .toList();

        for (Familie familie : StandardVorlagenInhalt.FAMILIEN) {
            if (bekannte.contains(familie.vorlagenName().toLowerCase())) {
                vorhanden.add(familie.vorlagenName());
                continue;
            }
            // Nur Dokumenttypen zuordnen, die noch keine Vorlage haben, sonst würde die neue die gewählte verdrängen
            List<String> freieTypen = familie.typen().stream()
                    .filter(t -> zuordnungen.findByDokumenttyp(t).isEmpty())
                    .map(Dokumenttyp::getLabel)
                    .toList();
            vorlagenService.saveNamedTemplate(familie.vorlagenName(), StandardVorlagenInhalt.layoutHtml(familie.einstellungen()), freieTypen);

            FormularTextbausteinDefaultsDto defaults = new FormularTextbausteinDefaultsDto();
            for (Dokumenttyp typ : familie.typen()) {
                Texte texte = StandardVorlagenInhalt.texte(typ);
                if (texte == null) {
                    continue;
                }
                boolean vorNeu = !textbausteine.findFirstByNameIgnoreCase(name("Vortext", typ)).isPresent();
                boolean nachNeu = !textbausteine.findFirstByNameIgnoreCase(name("Nachtext", typ)).isPresent();
                Textbaustein vor = holeOderLegeAn(typ, "VORTEXT", "Vortext", texte.vortext());
                Textbaustein nach = holeOderLegeAn(typ, "NACHTEXT", "Nachtext", texte.nachtext());
                neueTexte += (vorNeu ? 1 : 0) + (nachNeu ? 1 : 0);

                FormularTextbausteinDefaultsDto.Entry eintrag = new FormularTextbausteinDefaultsDto.Entry();
                eintrag.setDokumenttyp(typ.getLabel());
                eintrag.setVortextIds(List.of(vor.getId()));
                eintrag.setNachtextIds(List.of(nach.getId()));
                defaults.getEntries().add(eintrag);
            }
            standardtexte.replaceDefaults(familie.vorlagenName(), defaults);
            angelegt.add(familie.vorlagenName());
        }
        log.info("Standardvorlagen: angelegt {}, schon vorhanden {}, neue Textbausteine {}", angelegt, vorhanden, neueTexte);
        return new Ergebnis(angelegt, vorhanden, neueTexte);
    }

    /** Die Werte der Firmen-Platzhalter (Namen in Großbuchstaben ohne Klammern), für die Vorschau im Editor. */
    @Transactional(readOnly = true)
    public Map<String, String> firmenPlatzhalter() {
        Firmeninformation firma = firmen.findById(1L).orElse(null);
        return FirmenPlatzhalter.werte(firma);
    }

    /** Das Seitenlayout (Vorlagen-HTML mit Einstellungen) nach den übergebenen Einstellungen, zum Beispiel für den Knopf "Layout übernehmen" im Editor. */
    public String layoutAusEinstellungen(VorlagenEinstellungen einstellungen) {
        return StandardVorlagenInhalt.layoutHtml(einstellungen);
    }

    /** Die Standardeinstellungen: normal oder für einen Lieferschein (ohne Preise, mit Artikelnummer). */
    public VorlagenEinstellungen standardEinstellungen(boolean lieferschein) {
        return lieferschein ? VorlagenEinstellungen.lieferschein() : VorlagenEinstellungen.standard();
    }

    /** Wahr, wenn es noch weder Vorlagen noch Textbausteine gibt (frische Installation). */
    @Transactional(readOnly = true)
    public boolean nochNichtsVorhanden() {
        return vorlagenService.listNamedTemplates().isEmpty() && textbausteine.count() == 0;
    }

    private static String name(String art, Dokumenttyp typ) {
        return "Standard " + art + " " + typ.getLabel();
    }

    private Textbaustein holeOderLegeAn(Dokumenttyp typ, String textTyp, String art, String html) {
        String name = name(art, typ);
        return textbausteine.findFirstByNameIgnoreCase(name).orElseGet(() -> {
            TextbausteinDto dto = new TextbausteinDto();
            dto.setName(name);
            dto.setTyp(textTyp);
            dto.setBeschreibung("Mitgelieferter Standardtext, frei änderbar");
            dto.setHtml(html);
            dto.setDokumenttypen(List.of(typ.getLabel()));
            return textbausteinService.create(dto);
        });
    }
}
