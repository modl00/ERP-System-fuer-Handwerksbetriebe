package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Qualifikation;
import org.example.kalkulationsprogramm.dto.Verrechnungslohn.VerrechnungslohnErgebnisDto;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Kostensatz und Verrechnungssatz im Überblick (Stufe 3a von #166), <b>nur Anzeige, nichts wird verändert</b>. Aufgebaut auf dem Rechner „Was muss meine Stunde kosten?“
 * ({@link VerrechnungslohnService}): Lohnkosten und verkäufliche Stunden je Mitarbeiter, dazu die Gemeinkosten des Jahres.
 * <ul>
 *   <li><b>Personalkostensatz</b> = Lohnkosten ÷ verkäufliche Stunden (für den ganzen Betrieb, je Qualifikation oder je Person)</li>
 *   <li><b>Gemeinkostenumlage</b> = Gemeinkosten ÷ alle verkäuflichen Stunden des Betriebs (für alle gleich)</li>
 *   <li><b>Kostensatz</b> = Personalkostensatz + Umlage (die Selbstkosten einer Stunde)</li>
 *   <li><b>Verrechnungssatz</b> = Kostensatz × (1 + Wagnis %) × (1 + Gewinn %)</li>
 * </ul>
 * Die Zeile „Betrieb“ ist genau der Selbstkostensatz des bisherigen Rechners. Als Gruppe dient die vorhandene <b>Qualifikation</b> des Mitarbeiters (Auszubildender,
 * Facharbeiter, Meister), keine zweite Gruppenliste.
 */
@Service
@RequiredArgsConstructor
public class KostensatzService {

    static final BigDecimal MAX_WAGNIS = new BigDecimal("100");
    static final BigDecimal MAX_GEWINN = new BigDecimal("200");
    private static final BigDecimal HUNDERT = new BigDecimal("100");

    private final VerrechnungslohnService verrechnungslohn;
    private final MitarbeiterRepository mitarbeiter;

    /** Eine Zeile der Übersicht; die Sätze sind {@code null}, wenn es keine verkäuflichen Stunden gibt ({@code hinweis} sagt dann warum). */
    public record Zeile(String art, String name, int anzahl, BigDecimal lohnkosten, BigDecimal stunden, BigDecimal personalkostensatz, BigDecimal umlage, BigDecimal kostensatz,
            BigDecimal wagnisBetrag, BigDecimal gewinnBetrag, BigDecimal verrechnungssatz, String hinweis) { }

    /** Eine Fixkosten-Kostenstelle in der Gemeinkosten-Übersicht (#237): {@code soll} ist die Schätzung (leer = keine), {@code ist} sind die echten Kosten, {@code verwendet} der Rechenwert. */
    public record KostenstelleZeile(Long id, String name, BigDecimal soll, BigDecimal ist, BigDecimal verwendet, boolean sollGilt) { }

    /** Woraus die Gemeinkosten bestehen. {@code gesamtbetrag} ist die Ersatz-Schätzung für den ganzen Betrieb; sie gilt nur, wenn bei keiner Kostenstelle ein Soll steht. */
    public record Gemeinkosten(List<KostenstelleZeile> kostenstellen, BigDecimal gesamtbetrag, boolean gesamtbetragGilt, boolean enthaeltSchaetzwerte, boolean sollVorhanden) { }

    public record Ergebnis(int jahr, String modus, BigDecimal lohnkostenGesamt, BigDecimal stundenGesamt, BigDecimal gemeinkostenGesamt, BigDecimal umlage, BigDecimal wagnisProzent,
            BigDecimal gewinnProzent, Zeile betrieb, List<Zeile> gruppen, List<Zeile> personen, List<String> hinweise, Gemeinkosten gemeinkosten) { }

    @Transactional(readOnly = true)
    public Ergebnis berechne(int jahr, Integer interneQuoteProzent, BigDecimal wagnisProzent, BigDecimal gewinnProzent) {
        BigDecimal wagnis = wagnisProzent == null ? BigDecimal.ZERO : wagnisProzent;
        BigDecimal gewinn = gewinnProzent == null ? BigDecimal.ZERO : gewinnProzent;
        if (jahr < 2000 || jahr > 2100) {
            throw new IllegalArgumentException("Bitte ein Jahr zwischen 2000 und 2100 wählen.");
        }
        if (wagnis.signum() < 0 || wagnis.compareTo(MAX_WAGNIS) > 0) {
            throw new IllegalArgumentException("Das Wagnis muss zwischen 0 und 100 Prozent liegen.");
        }
        if (gewinn.signum() < 0 || gewinn.compareTo(MAX_GEWINN) > 0) {
            throw new IllegalArgumentException("Der Gewinn muss zwischen 0 und 200 Prozent liegen.");
        }
        VerrechnungslohnErgebnisDto e = verrechnungslohn.berechne(jahr, interneQuoteProzent);
        Map<Long, Mitarbeiter> personen = new LinkedHashMap<>();
        for (Mitarbeiter m : mitarbeiter.findMenschen()) {
            personen.put(m.getId(), m);
        }
        return rechne(e, personen, wagnis, gewinn);
    }

    /** Die reine Rechnung auf dem Ergebnis des Rechners; getrennt, damit sie sich ohne Datenbank prüfen lässt. */
    static Ergebnis rechne(VerrechnungslohnErgebnisDto e, Map<Long, Mitarbeiter> personen, BigDecimal wagnis, BigDecimal gewinn) {
        BigDecimal lohnGesamt = BigDecimal.ZERO;
        BigDecimal stundenGesamt = BigDecimal.ZERO;
        Map<Long, BigDecimal> lohn = new LinkedHashMap<>();
        Map<Long, BigDecimal> stunden = new LinkedHashMap<>();
        for (var z : e.getLohnzeilen()) {
            lohn.merge(z.getMitarbeiterId(), z.getGesamtkosten(), BigDecimal::add);
            lohnGesamt = lohnGesamt.add(z.getGesamtkosten());
        }
        for (var z : e.getStundenzeilen()) {
            stunden.merge(z.getMitarbeiterId(), z.getVerkaeuflicheStunden(), BigDecimal::add);
            stundenGesamt = stundenGesamt.add(z.getVerkaeuflicheStunden());
        }
        BigDecimal gemein = e.getGemeinkostenGesamt() == null ? BigDecimal.ZERO : e.getGemeinkostenGesamt();
        List<String> hinweise = new ArrayList<>();
        BigDecimal umlage = stundenGesamt.signum() > 0 ? gemein.divide(stundenGesamt, 8, RoundingMode.HALF_UP) : null;
        if (umlage == null) {
            hinweise.add("Es gibt keine verkäuflichen Stunden: Ohne sie lassen sich die Sätze nicht berechnen. Bitte die Zeiterfassung und die Arbeitszeiten prüfen.");
        }
        if (e.isEnthaeltSchaetzwerte()) {
            BigDecimal ist = e.getGemeinkostenIst() == null ? BigDecimal.ZERO : e.getGemeinkostenIst();
            hinweise.add("Die Gemeinkosten enthalten Schätzwerte: Gerechnet wird mit " + cent(gemein) + " € im Jahr, die echten Kosten aus den Belegen betragen erst " + cent(ist)
                    + " €. Sobald die echten Kosten höher sind, gelten diese.");
        }
        if (!e.getDatenLuecken().isEmpty()) {
            hinweise.add("Für dieses Jahr fehlen Angaben (" + e.getDatenLuecken().size() + "). Die Sätze sind dann nur eine Schätzung; Einzelheiten stehen im Rechner „Was muss meine Stunde kosten?“.");
        }

        Zeile betrieb = zeile("BETRIEB", "Ganzer Betrieb", lohn.size(), lohnGesamt, stundenGesamt, umlage, wagnis, gewinn);

        // Gruppen = Qualifikation der Mitarbeiter
        Map<String, List<Long>> nachGruppe = new LinkedHashMap<>();
        for (Qualifikation q : Qualifikation.values()) {
            nachGruppe.put(q.getBezeichnung(), new ArrayList<>());
        }
        nachGruppe.put("Ohne Angabe", new ArrayList<>());
        for (Long id : lohn.keySet()) {
            Mitarbeiter m = personen.get(id);
            String gruppe = m == null || m.getQualifikation() == null ? "Ohne Angabe" : m.getQualifikation().getBezeichnung();
            nachGruppe.get(gruppe).add(id);
        }
        List<Zeile> gruppen = new ArrayList<>();
        for (var eintrag : nachGruppe.entrySet()) {
            List<Long> ids = eintrag.getValue();
            if (ids.isEmpty()) {
                continue;
            }
            BigDecimal l = ids.stream().map(i -> lohn.getOrDefault(i, BigDecimal.ZERO)).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal s = ids.stream().map(i -> stunden.getOrDefault(i, BigDecimal.ZERO)).reduce(BigDecimal.ZERO, BigDecimal::add);
            gruppen.add(zeile("GRUPPE", eintrag.getKey(), ids.size(), l, s, umlage, wagnis, gewinn));
        }
        if (nachGruppe.get("Ohne Angabe").size() > 0) {
            hinweise.add("Bei " + nachGruppe.get("Ohne Angabe").size() + " Mitarbeitern ist keine Qualifikation eingetragen (Mitarbeiter → Stammdaten). Sie stehen unter „Ohne Angabe“.");
        }

        List<Zeile> einzelne = new ArrayList<>();
        for (Long id : lohn.keySet()) {
            Mitarbeiter m = personen.get(id);
            String name = m == null ? "Mitarbeiter " + id : (m.getVorname() + " " + m.getNachname()).strip();
            einzelne.add(zeile("PERSON", name, 1, lohn.get(id), stunden.getOrDefault(id, BigDecimal.ZERO), umlage, wagnis, gewinn));
        }
        einzelne.sort(Comparator.comparing(Zeile::name, String.CASE_INSENSITIVE_ORDER));

        return new Ergebnis(e.getJahr(), e.getModus() == null ? null : e.getModus().name(), cent(lohnGesamt), cent(stundenGesamt), cent(gemein), umlage == null ? null : cent(umlage),
                wagnis, gewinn, betrieb, gruppen, einzelne, hinweise, gemeinkosten(e));
    }

    /** Die Gemeinkosten je Fixkosten-Kostenstelle mit Soll, Ist und Rechenwert (#237). */
    static Gemeinkosten gemeinkosten(VerrechnungslohnErgebnisDto e) {
        List<KostenstelleZeile> zeilen = new ArrayList<>();
        boolean sollVorhanden = false;
        for (var k : e.getKostenstellen()) {
            sollVorhanden |= k.getSoll() != null;
            zeilen.add(new KostenstelleZeile(k.getKostenstelleId(), k.getBezeichnung(), k.getSoll(), k.getJahresbetrag(), k.getVerwendet(), k.isSollGilt()));
        }
        zeilen.sort(Comparator.comparing(KostenstelleZeile::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return new Gemeinkosten(zeilen, e.getGemeinkostenSchaetzung(), e.isSchaetzungGilt(), e.isEnthaeltSchaetzwerte(), sollVorhanden);
    }

    private static Zeile zeile(String art, String name, int anzahl, BigDecimal lohn, BigDecimal stunden, BigDecimal umlage, BigDecimal wagnis, BigDecimal gewinn) {
        if (stunden.signum() <= 0 || umlage == null) {
            String grund = umlage == null ? "Keine verkäuflichen Stunden im Betrieb." : "Keine verkäuflichen Stunden: Der Lohn dieser Zeile steckt im Betriebsdurchschnitt, ein eigener Satz lässt sich nicht berechnen.";
            return new Zeile(art, name, anzahl, cent(lohn), cent(stunden), null, umlage == null ? null : cent(umlage), null, null, null, null, grund);
        }
        BigDecimal personal = lohn.divide(stunden, 8, RoundingMode.HALF_UP);
        BigDecimal kosten = personal.add(umlage);
        BigDecimal mitWagnis = kosten.multiply(BigDecimal.ONE.add(wagnis.divide(HUNDERT, 8, RoundingMode.HALF_UP)));
        BigDecimal verrechnung = mitWagnis.multiply(BigDecimal.ONE.add(gewinn.divide(HUNDERT, 8, RoundingMode.HALF_UP)));
        return new Zeile(art, name, anzahl, cent(lohn), cent(stunden), cent(personal), cent(umlage), cent(kosten), cent(mitWagnis.subtract(kosten)), cent(verrechnung.subtract(mitWagnis)),
                cent(verrechnung), null);
    }

    private static BigDecimal cent(BigDecimal wert) {
        return wert.setScale(2, RoundingMode.HALF_UP);
    }
}
