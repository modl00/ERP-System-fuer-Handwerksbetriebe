package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.service.formel.ArbeitsgangFormeln;
import org.example.kalkulationsprogramm.service.formel.ArtikelFormeln;
import org.example.kalkulationsprogramm.service.formel.FormelException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.Arbeitsgang;
import org.example.kalkulationsprogramm.domain.ArbeitsgangStundensatz;
import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.domain.Fremdleistung;
import org.example.kalkulationsprogramm.domain.Leistung;
import org.example.kalkulationsprogramm.domain.LeistungPosition;
import org.example.kalkulationsprogramm.dto.Leistung.LeistungKalkulationDto;
import org.example.kalkulationsprogramm.dto.Leistung.LeistungKalkulationSpeichernDto;
import org.example.kalkulationsprogramm.repository.ArbeitsgangRepository;
import org.example.kalkulationsprogramm.repository.ArbeitsgangStundensatzRepository;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.FremdleistungRepository;
import org.example.kalkulationsprogramm.repository.LeistungRepository;
import org.hibernate.Hibernate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/**
 * Kalkuliert den Preis einer Leistung aus Material (Artikel) und Arbeitszeit (Arbeitsgang) mit einem Zuschlag.
 *
 * <ul>
 *   <li>Material: Verkaufspreis des Artikels (günstigster aktueller Lieferantenpreis plus Verkaufsaufschlag des Artikels, wie im Dokumenteditor)
 *       mal Menge.</li>
 *   <li>Arbeit: Stundensatz des Arbeitsgangs (aktuelles Jahr, sonst der jüngste vorhandene) mal Stunden.</li>
 *   <li>Preis = (Material + Arbeit) plus Zuschlag in Prozent.</li>
 * </ul>
 *
 * <p>Der Preis wird nur in {@link Leistung#getPreis()} geschrieben, wenn die Leistung "kalkuliert" ist und gespeichert oder ausdrücklich
 * übernommen wird. Eine Preisänderung bei einem Artikel verändert also nie von selbst eine Leistung oder ein Dokument; die Oberfläche zeigt
 * nur an, dass der gespeicherte Preis veraltet ist.</p>
 */
@Service
@RequiredArgsConstructor
public class LeistungKalkulationService {

    static final int MAX_POSITIONEN = 100;
    /** Wie viele Ebenen Baugruppen ineinander stecken dürfen (Geländer → Feld → Pfosten → Fußplatte ...). */
    static final int MAX_BAUGRUPPEN_TIEFE = 8;
    private static final BigDecimal MAX_MENGE = new BigDecimal("1000000");
    private static final BigDecimal MAX_ZUSCHLAG = new BigDecimal("1000");
    private static final BigDecimal HUNDERT = new BigDecimal("100");
    private static final BigDecimal SECHZIG = new BigDecimal("60");
    private static final BigDecimal MAX_MINUTEN = new BigDecimal("100000");

    private final LeistungRepository leistungen;
    private final ArtikelRepository artikel;
    private final ArbeitsgangRepository arbeitsgaenge;
    private final ArbeitsgangStundensatzRepository stundensaetze;
    private final ArtikelPositionsPreisService artikelPreise;
    private final FremdleistungRepository fremdleistungen;
    private final Clock uhr;

    @Transactional(readOnly = true)
    public LeistungKalkulationDto laden(Long leistungId) {
        return aufstellung(finde(leistungId));
    }

    /** Rechnet die übergebenen Zeilen durch, ohne etwas zu speichern (für die Anzeige beim Bearbeiten). */
    @Transactional(readOnly = true)
    public LeistungKalkulationDto vorschau(Long leistungId, LeistungKalkulationSpeichernDto eingabe) {
        Leistung leistung = finde(leistungId);
        BigDecimal los = losgroesse(eingabe);
        List<LeistungPosition> zeilen = pruefeUndBaueZeilen(leistung, eingabe, los);
        return aufstellung(leistung.getId(), eingabe.kalkuliert(), zuschlagOderNull(eingabe), los, zeilen, leistung.getPreis());
    }

    /** Der Preis bei einer bestimmten Angebotsmenge: Eintrag der Staffel. */
    public record StaffelZeile(BigDecimal menge, BigDecimal materialJeEinheit, BigDecimal arbeitJeEinheit, BigDecimal preisJeEinheit, BigDecimal gesamtpreis, boolean vollstaendig,
            BigDecimal maschineJeEinheit, BigDecimal fremdleistungJeEinheit) { }

    /**
     * Rechnet die gespeicherte Kalkulation für mehrere Angebotsmengen durch (zum Beispiel 10, 25, 50 lfm). Die Rüstzeit verteilt sich dabei auf die ganze
     * Menge, bei Baugruppen auf die Menge der Baugruppe (Menge je Einheit × Angebotsmenge): 5 lfm sind je Meter teurer als 50 lfm. Die gespeicherte
     * Losgröße gilt hier nicht. Es wird nichts gespeichert.
     */
    @Transactional(readOnly = true)
    public List<StaffelZeile> staffel(Long leistungId, List<BigDecimal> mengen) {
        Leistung leistung = finde(leistungId);
        if (mengen == null || mengen.isEmpty()) {
            throw fehler("Bitte mindestens eine Menge angeben.");
        }
        if (mengen.size() > 10) {
            throw fehler("Es sind höchstens 10 Mengen auf einmal möglich.");
        }
        int jahr = LocalDate.now(uhr).getYear();
        BigDecimal prozent = leistung.getZuschlagProzent() == null ? BigDecimal.ZERO : leistung.getZuschlagProzent();
        List<StaffelZeile> zeilen = new ArrayList<>();
        for (BigDecimal m : mengen) {
            if (m == null || m.signum() <= 0 || m.compareTo(MAX_MENGE) > 0) {
                throw fehler("Jede Menge muss größer als 0 sein (höchstens 1.000.000).");
            }
            Ergebnis e = rechne(leistung.getPositionen(), m, jahr, 0, true);
            BigDecimal zwischen = e.material().add(e.arbeit()).add(e.maschine()).add(e.fremd());
            BigDecimal preis = zwischen.add(zwischen.multiply(prozent).divide(HUNDERT, 2, RoundingMode.HALF_UP)).setScale(2, RoundingMode.HALF_UP);
            zeilen.add(new StaffelZeile(m, e.material(), e.arbeit(), preis, preis.multiply(m).setScale(2, RoundingMode.HALF_UP), e.vollstaendig(), e.maschine(), e.fremd()));
        }
        return zeilen;
    }

    @Transactional
    public LeistungKalkulationDto speichern(Long leistungId, LeistungKalkulationSpeichernDto eingabe) {
        Leistung leistung = finde(leistungId);
        BigDecimal los = losgroesse(eingabe);
        List<LeistungPosition> neu = pruefeUndBaueZeilen(leistung, eingabe, los);
        leistung.getPositionen().clear();
        leistung.getPositionen().addAll(neu);
        leistung.setLosgroesse(los);
        leistung.setZuschlagProzent(zuschlagOderNull(eingabe));
        leistung.setKalkuliert(eingabe.kalkuliert());

        LeistungKalkulationDto stand = aufstellung(leistung);
        if (eingabe.kalkuliert()) {
            pruefeBerechenbar(stand);
            leistung.setPreis(stand.preisBerechnet());
        }
        leistungen.save(leistung);
        return aufstellung(leistung);
    }

    /**
     * Die Aufstellung der gespeicherten Kalkulation für eine Angebotsmenge (Issue #55/#58, intern im Angebot): Rüstzeit, Mindestpreis und einmalige Kosten
     * verteilen sich auf diese Menge, bei Baugruppen auf deren Menge; die gespeicherte Losgröße gilt dafür nicht. {@code preisGespeichert} ist der Preis an der Leistung,
     * {@code preisVeraltet} sagt, ob er vom berechneten abweicht. Es wird nichts gespeichert.
     */
    @Transactional(readOnly = true)
    public LeistungKalkulationDto fuerAngebotsmenge(Long leistungId, BigDecimal menge) {
        Leistung leistung = finde(leistungId);
        if (menge == null || menge.signum() <= 0 || menge.compareTo(MAX_MENGE) > 0) {
            throw fehler("Die Menge muss größer als 0 sein (höchstens 1.000.000).");
        }
        return aufstellung(leistung.getId(), leistung.isKalkuliert(), leistung.getZuschlagProzent(), menge.setScale(3, RoundingMode.HALF_UP), leistung.getPositionen(), leistung.getPreis(), true);
    }

    /** Setzt den gespeicherten Preis auf das Ergebnis der Kalkulation, zum Beispiel nachdem ein Artikel teurer wurde. */
    @Transactional
    public LeistungKalkulationDto preisUebernehmen(Long leistungId) {
        Leistung leistung = finde(leistungId);
        if (leistung.getPositionen().isEmpty()) {
            throw fehler("Diese Leistung hat noch keine Kalkulation.");
        }
        LeistungKalkulationDto stand = aufstellung(leistung);
        pruefeBerechenbar(stand);
        leistung.setPreis(stand.preisBerechnet());
        leistungen.save(leistung);
        return aufstellung(leistung);
    }

    /** Die Losgröße der Eingabe: leer = 1, sonst größer 0 bis 1.000.000. */
    private static BigDecimal losgroesse(LeistungKalkulationSpeichernDto eingabe) {
        if (eingabe == null || eingabe.losgroesse() == null) {
            return BigDecimal.ONE.setScale(3);
        }
        BigDecimal l = eingabe.losgroesse();
        if (l.signum() <= 0 || l.compareTo(MAX_MENGE) > 0) {
            throw fehler("Die Losgröße muss größer als 0 sein (höchstens 1.000.000).");
        }
        return l.setScale(3, RoundingMode.HALF_UP);
    }

    private List<LeistungPosition> pruefeUndBaueZeilen(Leistung leistung, LeistungKalkulationSpeichernDto eingabe, BigDecimal losgroesse) {
        if (eingabe == null) {
            throw fehler("Die Kalkulation fehlt.");
        }
        List<LeistungKalkulationSpeichernDto.Position> zeilen = eingabe.positionen() == null ? List.of() : eingabe.positionen();
        if (zeilen.size() > MAX_POSITIONEN) {
            throw fehler("Eine Leistung kann höchstens " + MAX_POSITIONEN + " Zeilen haben.");
        }
        BigDecimal zuschlag = eingabe.zuschlagProzent();
        if (zuschlag != null && (zuschlag.signum() < 0 || zuschlag.compareTo(MAX_ZUSCHLAG) > 0)) {
            throw fehler("Der Zuschlag muss zwischen 0 und 1000 Prozent liegen.");
        }
        List<LeistungPosition> neu = new ArrayList<>();
        int reihenfolge = 0;
        for (LeistungKalkulationSpeichernDto.Position z : zeilen) {
            neu.add(zeile(leistung, z, reihenfolge++, losgroesse));
        }
        pruefeBaugruppen(leistung, neu);
        return neu;
    }

    private static BigDecimal zuschlagOderNull(LeistungKalkulationSpeichernDto eingabe) {
        BigDecimal z = eingabe.zuschlagProzent();
        return z == null || z.signum() == 0 ? null : z.setScale(2, RoundingMode.HALF_UP);
    }

    private Leistung finde(Long id) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Leistung nicht gefunden.");
        }
        return leistungen.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Leistung nicht gefunden."));
    }

    private LeistungPosition zeile(Leistung leistung, LeistungKalkulationSpeichernDto.Position z, int reihenfolge, BigDecimal losgroesse) {
        if (z == null || z.typ() == null) {
            throw fehler("Eine Zeile der Kalkulation ist unvollständig.");
        }
        LeistungPosition p = new LeistungPosition();
        p.setLeistung(leistung);
        p.setSortOrder(reihenfolge);
        boolean baugruppe = "BAUGRUPPE".equalsIgnoreCase(z.typ());
        // Hat der Arbeitsgang eine Formel, entstehen die Zeiten aus den eingegebenen Werten (#216); die Zeiten aus der Anfrage zählen dann nicht
        ArbeitsgangFormeln.Zeiten formelZeiten = null;
        if (!baugruppe && "ARBEIT".equalsIgnoreCase(z.typ()) && z.arbeitsgangId() != null) {
            Arbeitsgang gang = arbeitsgaenge.findById(z.arbeitsgangId()).orElse(null);
            if (z.variablen() != null && ArbeitsgangFormeln.hat(gang)) {
                ArbeitsgangFormeln.Formel formel = ArbeitsgangFormeln.lese(gang);
                try {
                    formelZeiten = ArbeitsgangFormeln.berechne(formel, z.variablen());
                    p.setVariablenJson(ArbeitsgangFormeln.werteAlsJson(ArbeitsgangFormeln.wirksameWerte(formel, z.variablen())));
                } catch (FormelException e) {
                    throw fehler("Arbeitsgang „" + gang.getBeschreibung() + "“: " + e.getMessage());
                }
            }
        }
        boolean mitZeiten = !baugruppe && "ARBEIT".equalsIgnoreCase(z.typ()) && (formelZeiten != null || z.ruestzeitMinuten() != null || z.bearbeitungszeitMinuten() != null);
        if (mitZeiten) {
            BigDecimal ruest = formelZeiten != null ? (formelZeiten.ruestMinuten() == null ? BigDecimal.ZERO.setScale(2) : formelZeiten.ruestMinuten()) : minuten(z.ruestzeitMinuten(), "Die Rüstzeit");
            BigDecimal bearbeitung = formelZeiten != null ? formelZeiten.bearbeitungMinuten() : minuten(z.bearbeitungszeitMinuten(), "Die Bearbeitungszeit");
            if (ruest.signum() == 0 && bearbeitung.signum() == 0) {
                throw fehler(formelZeiten != null ? "Die Formel ergibt 0 Minuten. Bitte die Werte der Eingabefelder prüfen." : "Bitte eine Rüstzeit oder eine Bearbeitungszeit größer als 0 angeben.");
            }
            BigDecimal vorgaenge = vorgaenge(z.anzahlVorgaenge());
            p.setRuestzeitMinuten(ruest);
            p.setBearbeitungszeitMinuten(bearbeitung);
            p.setAnzahlVorgaenge(vorgaenge);
            p.setMenge(stunden(ruest, bearbeitung.multiply(vorgaenge), losgroesse).setScale(3, RoundingMode.HALF_UP));
        } else if ("FREMDLEISTUNG".equalsIgnoreCase(z.typ()) && herkunft(z.mengeAus()) != null) {
            // Die Menge kommt aus dem berechneten Gewicht oder der Oberfläche; gespeichert wird ein Platzhalter
            p.setMenge(BigDecimal.ONE.setScale(3));
        } else {
            if (z.menge() == null || z.menge().signum() <= 0 || z.menge().compareTo(MAX_MENGE) > 0) {
                throw fehler("Die Menge muss größer als 0 sein.");
            }
            p.setMenge(z.menge().setScale(3, RoundingMode.HALF_UP));
        }
        if (z.notiz() != null && z.notiz().length() > 255) {
            throw fehler("Die Notiz ist zu lang (höchstens 255 Zeichen).");
        }
        p.setNotiz(z.notiz() == null || z.notiz().isBlank() ? null : z.notiz().strip());
        switch (z.typ().toUpperCase()) {
            case "ARTIKEL" -> {
                p.setTyp(LeistungPosition.Typ.ARTIKEL);
                p.setVerschnittProzent(verschnitt(z.verschnittProzent()));
                if (z.artikelId() == null) {
                    throw fehler("Bitte einen Artikel wählen.");
                }
                p.setArtikel(artikel.findById(z.artikelId()).orElseThrow(() -> fehler("Der Artikel existiert nicht.")));
                // Hat der Artikel eine Zuschnitt-Formel (#228), werden die eingegebenen Maße geprüft und mit der Position gemerkt
                if (z.variablen() != null && Hibernate.unproxy(p.getArtikel()) instanceof ArtikelWerkstoffe w && ArtikelFormeln.hat(w)) {
                    ArtikelFormeln.Formel formel = ArtikelFormeln.lese(w);
                    try {
                        ArtikelFormeln.berechne(formel, z.variablen(), eingebaut(w));
                        p.setVariablenJson(ArbeitsgangFormeln.werteAlsJson(ArbeitsgangFormeln.wirksameWerte(new ArbeitsgangFormeln.Formel(formel.felder(), formel.gewicht(), null), z.variablen())));
                    } catch (FormelException e) {
                        throw fehler("Artikel „" + artikelName(p.getArtikel()) + "“: " + e.getMessage());
                    }
                }
            }
            case "ARBEIT" -> {
                p.setTyp(LeistungPosition.Typ.ARBEIT);
                if (z.arbeitsgangId() == null) {
                    throw fehler("Bitte einen Arbeitsgang wählen.");
                }
                p.setArbeitsgang(arbeitsgaenge.findById(z.arbeitsgangId()).orElseThrow(() -> fehler("Der Arbeitsgang existiert nicht.")));
                p.setMaschinenanteilProzent(maschinenanteil(z.maschinenanteilProzent()));
            }
            case "BAUGRUPPE" -> {
                p.setTyp(LeistungPosition.Typ.BAUGRUPPE);
                if (z.baugruppeId() == null) {
                    throw fehler("Bitte eine Baugruppe wählen.");
                }
                Leistung b = leistungen.findById(z.baugruppeId()).orElseThrow(() -> fehler("Die Baugruppe existiert nicht."));
                if (!b.isBaugruppe()) {
                    throw fehler("„" + b.getBezeichnung() + "“ ist keine Baugruppe. Bitte dort zuerst „Als Baugruppe nutzbar“ einschalten.");
                }
                p.setBaugruppe(b);
            }
            case "FREMDLEISTUNG" -> {
                p.setTyp(LeistungPosition.Typ.FREMDLEISTUNG);
                if (z.fremdleistungId() == null) {
                    throw fehler("Bitte eine Fremdleistung wählen.");
                }
                p.setFremdleistung(fremdleistungen.findById(z.fremdleistungId()).orElseThrow(() -> fehler("Die Fremdleistung existiert nicht.")));
                String herkunft = herkunft(z.mengeAus());
                if (herkunft != null) {
                    boolean passt = "GEWICHT".equals(herkunft) ? p.getFremdleistung().getEinheit() == Fremdleistung.Einheit.KG : p.getFremdleistung().getEinheit() == Fremdleistung.Einheit.QUADRATMETER;
                    if (!passt) {
                        throw fehler("\"" + p.getFremdleistung().getBezeichnung() + "\" rechnet nicht " + ("GEWICHT".equals(herkunft) ? "nach kg" : "nach m²") + " ab. Die Menge lässt sich deshalb nicht aus dem " + ("GEWICHT".equals(herkunft) ? "Gewicht" : "der Oberfläche") + " nehmen.");
                    }
                    p.setMengeAus(herkunft);
                }
                BigDecimal gewicht = z.gewichtKgJeEinheit();
                if (gewicht != null && (gewicht.signum() < 0 || gewicht.compareTo(MAX_MENGE) > 0)) {
                    throw fehler("Das Gewicht je Einheit muss zwischen 0 und 1.000.000 kg liegen.");
                }
                p.setGewichtKgJeEinheit(gewicht == null || gewicht.signum() == 0 ? null : gewicht.setScale(3, RoundingMode.HALF_UP));
            }
            default -> throw fehler("Unbekannte Art der Zeile: " + z.typ());
        }
        return p;
    }

    /** Woher die Menge einer Fremdleistung kommt: {@code null} = von Hand, sonst {@code GEWICHT} oder {@code OBERFLAECHE}. */
    private static String herkunft(String wert) {
        if (wert == null || wert.isBlank() || "HAND".equalsIgnoreCase(wert.strip())) {
            return null;
        }
        String w = wert.strip().toUpperCase();
        if (!w.equals("GEWICHT") && !w.equals("OBERFLAECHE")) {
            throw fehler("Die Herkunft der Menge ist unbekannt: " + wert);
        }
        return w;
    }

    /** Eine Zeitangabe in Minuten: leer = 0, sonst 0 bis 100.000, auf zwei Stellen. */
    private static BigDecimal minuten(BigDecimal wert, String name) {
        if (wert == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        if (wert.signum() < 0 || wert.compareTo(MAX_MINUTEN) > 0) {
            throw fehler(name + " muss zwischen 0 und 100.000 Minuten liegen.");
        }
        return wert.setScale(2, RoundingMode.HALF_UP);
    }

    /** Stunden je Einheit der Leistung: (Rüstzeit verteilt auf die Losgröße + Bearbeitungszeit) in Minuten durch 60. */
    static BigDecimal stunden(BigDecimal ruestMinuten, BigDecimal bearbeitungMinuten, BigDecimal losgroesse) {
        return minutenJeEinheit(ruestMinuten, bearbeitungMinuten, losgroesse).divide(SECHZIG, 6, RoundingMode.HALF_UP);
    }

    private static BigDecimal minutenJeEinheit(BigDecimal ruestMinuten, BigDecimal bearbeitungMinuten, BigDecimal losgroesse) {
        BigDecimal los = losgroesse == null || losgroesse.signum() <= 0 ? BigDecimal.ONE : losgroesse;
        return ruestMinuten.divide(los, 6, RoundingMode.HALF_UP).add(bearbeitungMinuten);
    }

    private void pruefeBerechenbar(LeistungKalkulationDto stand) {
        if (stand.positionen().isEmpty()) {
            throw fehler("Für einen berechneten Preis braucht die Leistung mindestens eine Zeile.");
        }
        stand.positionen().stream().filter(p -> p.einzelpreis() == null || "KEIN_MASCHINENSATZ".equals(p.hinweis()) || ("FREMDLEISTUNG".equals(p.typ()) && !"OK".equals(p.hinweis()))).findFirst().ifPresent(p -> {
            if ("KEIN_MASCHINENSATZ".equals(p.hinweis())) {
                throw fehler("Für \"" + p.bezeichnung() + "\" ist eine Maschinenzeit angegeben, aber am Arbeitsgang fehlt der Maschinenstundensatz. Bitte zuerst pflegen, sonst wäre der Preis zu niedrig.");
            }
            if ("BAUGRUPPE_UNVOLLSTAENDIG".equals(p.hinweis())) {
                throw fehler("Die Baugruppe \"" + p.bezeichnung() + "\" ist unvollständig (keine Zeilen, oder es fehlt ein Preis oder Stundensatz). "
                        + "Bitte zuerst pflegen, sonst wäre der Preis zu niedrig.");
            }
            throw fehler("Für \"" + p.bezeichnung() + "\" fehlt "
                    + ("KEIN_STUNDENSATZ".equals(p.hinweis()) ? "ein Stundensatz" : "KEIN_GEWICHT".equals(p.hinweis()) ? "das Gewicht" : "ein Preis")
                    + ". Bitte zuerst pflegen, sonst wäre der Preis zu niedrig.");
        });
    }

    private LeistungKalkulationDto aufstellung(Leistung leistung) {
        return aufstellung(leistung.getId(), leistung.isKalkuliert(), leistung.getZuschlagProzent(), leistung.getLosgroesse(),
                leistung.getPositionen(), leistung.getPreis());
    }

    /** Die gerechneten Zeilen einer Leistung oder Baugruppe mit Material- und Arbeitssumme je Einheit, ohne Zuschlag. */
    private record Ergebnis(List<LeistungKalkulationDto.Position> zeilen, BigDecimal material, BigDecimal arbeit, BigDecimal maschine, BigDecimal fremd, boolean vollstaendig) { }

    private LeistungKalkulationDto aufstellung(Long leistungId, boolean kalkuliert, BigDecimal zuschlagProzent, BigDecimal losgroesse,
            List<LeistungPosition> positionen, BigDecimal gespeichert) {
        return aufstellung(leistungId, kalkuliert, zuschlagProzent, losgroesse, positionen, gespeichert, false);
    }

    private LeistungKalkulationDto aufstellung(Long leistungId, boolean kalkuliert, BigDecimal zuschlagProzent, BigDecimal losgroesse,
            List<LeistungPosition> positionen, BigDecimal gespeichert, boolean ausAngebotsmenge) {
        Ergebnis e = rechne(positionen, losgroesse, LocalDate.now(uhr).getYear(), 0, ausAngebotsmenge);
        BigDecimal material = e.material();
        BigDecimal arbeit = e.arbeit();
        BigDecimal maschine = e.maschine();
        BigDecimal fremd = e.fremd();
        BigDecimal zwischen = material.add(arbeit).add(maschine).add(fremd);
        BigDecimal prozent = zuschlagProzent == null ? BigDecimal.ZERO : zuschlagProzent;
        BigDecimal zuschlag = zwischen.multiply(prozent).divide(HUNDERT, 2, RoundingMode.HALF_UP);
        BigDecimal preis = zwischen.add(zuschlag).setScale(2, RoundingMode.HALF_UP);
        boolean veraltet = !e.zeilen().isEmpty() && (gespeichert == null || gespeichert.compareTo(preis) != 0);
        Gew gew = gewichtVon(positionen, 0);
        return new LeistungKalkulationDto(leistungId, kalkuliert, zuschlagProzent, losgroesse == null ? BigDecimal.ONE : losgroesse, e.zeilen(),
                material, arbeit, zwischen, zuschlag, preis, gespeichert, veraltet, e.vollstaendig(), maschine, fremd,
                gew.kg().setScale(3, RoundingMode.HALF_UP), gew.m2().setScale(3, RoundingMode.HALF_UP), gew.vollstaendig());
    }

    /**
     * Rechnet die Zeilen einer Leistung (oder Baugruppe) durch. {@code tiefe} ist die Ebene dieser Leistung (0 = die Leistung selbst); Baugruppen darin
     * liegen eine Ebene tiefer. Eine Baugruppe zählt mit ihrem Material und ihrer Arbeit (ohne eigenen Zuschlag): Den Zuschlag setzt nur die oberste
     * Leistung, einmal auf alles.
     */
    private Ergebnis rechne(List<LeistungPosition> positionen, BigDecimal losgroesse, int jahr, int tiefe, boolean ausAngebotsmenge) {
        List<LeistungKalkulationDto.Position> zeilen = new ArrayList<>();
        BigDecimal material = BigDecimal.ZERO;
        BigDecimal arbeit = BigDecimal.ZERO;
        BigDecimal maschine = BigDecimal.ZERO.setScale(2);
        BigDecimal fremd = BigDecimal.ZERO.setScale(2);
        boolean vollstaendig = true;

        for (LeistungPosition p : positionen) {
            if (p.getTyp() == LeistungPosition.Typ.BAUGRUPPE) {
                Leistung b = p.getBaugruppe();
                // Die Losgröße einer Baugruppe ergibt sich durch alle Ebenen aus der Losgröße darüber mal der Menge je Einheit (Geländer 50 lfm × 0,5 Felder × 2 Pfosten = 50 Pfosten):
                // Die Rüstzeit verteilt sich auf alle Einheiten, die im Auftrag wirklich vorkommen. Die eigene Losgröße einer Baugruppe gilt nur, wenn sie für sich allein kalkuliert wird.
                BigDecimal elternLos = losgroesse == null || losgroesse.signum() <= 0 ? BigDecimal.ONE : losgroesse;
                Ergebnis kind = tiefe + 1 > MAX_BAUGRUPPEN_TIEFE ? null : rechne(b.getPositionen(), elternLos.multiply(p.getMenge()), jahr, tiefe + 1, ausAngebotsmenge);
                boolean vollstaendigeBaugruppe = kind != null && kind.vollstaendig() && !b.getPositionen().isEmpty();
                BigDecimal je = kind == null ? BigDecimal.ZERO : kind.material().add(kind.arbeit()).add(kind.maschine()).add(kind.fremd());
                BigDecimal teilMaterial = kind == null ? BigDecimal.ZERO : kind.material().multiply(p.getMenge()).setScale(2, RoundingMode.HALF_UP);
                BigDecimal teilArbeit = kind == null ? BigDecimal.ZERO : kind.arbeit().multiply(p.getMenge()).setScale(2, RoundingMode.HALF_UP);
                BigDecimal teilMaschine = kind == null ? BigDecimal.ZERO : kind.maschine().multiply(p.getMenge()).setScale(2, RoundingMode.HALF_UP);
                BigDecimal teilFremd = kind == null ? BigDecimal.ZERO : kind.fremd().multiply(p.getMenge()).setScale(2, RoundingMode.HALF_UP);
                material = material.add(teilMaterial);
                arbeit = arbeit.add(teilArbeit);
                maschine = maschine.add(teilMaschine);
                fremd = fremd.add(teilFremd);
                if (!vollstaendigeBaugruppe) {
                    vollstaendig = false;
                }
                zeilen.add(new LeistungKalkulationDto.Position(p.getId(), "BAUGRUPPE", null, null, b.getBezeichnung(), p.getMenge(), einheitText(b),
                        vollstaendigeBaugruppe ? je : null, teilMaterial.add(teilArbeit).add(teilMaschine).add(teilFremd), vollstaendigeBaugruppe ? "OK" : "BAUGRUPPE_UNVOLLSTAENDIG", p.getNotiz(),
                        null, null, b.getId(), null));
                continue;
            }
            if (p.getTyp() == LeistungPosition.Typ.FREMDLEISTUNG) {
                Fremdleistung f = p.getFremdleistung();
                // Die Auftragsmenge dieser Ebene: Menge je Einheit der Leistung mal Zahl der Einheiten (Losgröße, bei der Staffel die Angebotsmenge)
                BigDecimal einheiten = losgroesse == null || losgroesse.signum() <= 0 ? BigDecimal.ONE : losgroesse;
                // Menge je Einheit der Leistung: von Hand, oder berechnet aus Gewicht bzw. Oberfläche der übrigen Zeilen (aus Artikel: kg je m, Mantelfläche je m)
                String herkunft = p.getMengeAus();
                Gew gew = herkunft == null ? null : gewichtVon(positionen, tiefe);
                // Verzinken rechnet immer nach Gewicht, Pulverbeschichten immer nach Fläche: Es zählt nur, was für diese Abrechnung nötig ist
                boolean mengeUnbekannt = gew != null && !("OBERFLAECHE".equals(herkunft) ? gew.m2Bekannt() : gew.kgBekannt());
                BigDecimal mengeJe = gew == null ? p.getMenge() : "OBERFLAECHE".equals(herkunft) ? gew.m2() : gew.kg();
                BigDecimal auftragsMenge = mengeJe.multiply(einheiten);
                // Gewicht für Transport je kg: von Hand je Einheit, sonst das berechnete Gesamtgewicht
                BigDecimal gewichtJe = p.getGewichtKgJeEinheit() != null ? p.getGewichtKgJeEinheit() : (gew == null ? gewichtVon(positionen, tiefe) : gew) instanceof Gew g && g.kgBekannt() && g.kg().signum() > 0 ? g.kg() : null;
                BigDecimal gewicht = gewichtJe == null ? null : gewichtJe.multiply(einheiten);
                FremdleistungsRechner.Ergebnis r = FremdleistungsRechner.rechne(f, auftragsMenge, gewicht);
                BigDecimal jeEinheit = r.summe().divide(einheiten, 2, RoundingMode.HALF_UP);
                fremd = fremd.add(jeEinheit);
                String hinweis = r.gewichtFehlt() || mengeUnbekannt ? "KEIN_GEWICHT" : "OK";
                if (r.gewichtFehlt() || mengeUnbekannt) {
                    vollstaendig = false;
                }
                zeilen.add(new LeistungKalkulationDto.Position(p.getId(), "FREMDLEISTUNG", null, null, f.getBezeichnung(), mengeJe.setScale(3, RoundingMode.HALF_UP), f.getEinheit().kurz(),
                        mengeJe.signum() == 0 ? null : jeEinheit.divide(mengeJe, 4, RoundingMode.HALF_UP), jeEinheit, hinweis, p.getNotiz(),
                        null, null, null, null, null, null, null, null, null, f.getId(), f.getLieferant().getLieferantenname(),
                        r.kostenarten().stream().map(k -> new LeistungKalkulationDto.FremdKostenart(k.art(), k.text(), k.betrag())).toList(), p.getGewichtKgJeEinheit(), null, null, herkunft));
                continue;
            }
            String bezeichnung;
            String einheit;
            BigDecimal einzel;
            String hinweis;
            if (p.getTyp() == LeistungPosition.Typ.ARTIKEL) {
                Artikel a = p.getArtikel();
                ArtikelFormeln.Ergebnis zuschnitt = zuschnitt(p);
                ArtikelPositionsPreisService.ArtikelPositionsVorschlag v = zuschnitt != null ? artikelPreise.berechneZuschnitt(a, zuschnitt.kg(), zuschnitt.m2()) : artikelPreise.berechne(a);
                bezeichnung = artikelName(a);
                einheit = v.einheit();
                einzel = v.einzelpreis();
                hinweis = v.hinweis() == null ? "OK" : v.hinweis().name();
            } else {
                Arbeitsgang g = p.getArbeitsgang();
                bezeichnung = g.getBeschreibung();
                einheit = "Std.";
                einzel = stundensatz(g, jahr);
                hinweis = einzel == null ? "KEIN_STUNDENSATZ" : "OK";
            }
            boolean mitZeiten = p.getTyp() == LeistungPosition.Typ.ARBEIT && (p.getRuestzeitMinuten() != null || p.getBearbeitungszeitMinuten() != null);
            BigDecimal menge = p.getMenge();
            BigDecimal summe;
            // Stunden je Einheit, ungerundet: Grundlage für Lohn und Maschine dieser Zeile
            BigDecimal stundenJeEinheit = null;
            if (p.getTyp() == LeistungPosition.Typ.ARBEIT) {
                stundenJeEinheit = mitZeiten
                        ? minutenJeEinheit(alsZahl(p.getRuestzeitMinuten()), alsZahl(p.getBearbeitungszeitMinuten()).multiply(vorgaenge(p.getAnzahlVorgaenge())), losgroesse)
                                .divide(SECHZIG, 8, RoundingMode.HALF_UP)
                        : p.getMenge();
            }
            if (einzel == null) {
                summe = BigDecimal.ZERO;
            } else if (mitZeiten) {
                // Aus den Minuten gerechnet, nicht aus der gerundeten Stundenzahl, damit kein Rundungsfehler im Preis steckt
                BigDecimal bearbeitung = alsZahl(p.getBearbeitungszeitMinuten()).multiply(vorgaenge(p.getAnzahlVorgaenge()));
                BigDecimal min = minutenJeEinheit(alsZahl(p.getRuestzeitMinuten()), bearbeitung, losgroesse);
                summe = einzel.multiply(min).divide(SECHZIG, 2, RoundingMode.HALF_UP);
                menge = stunden(alsZahl(p.getRuestzeitMinuten()), bearbeitung, losgroesse).setScale(3, RoundingMode.HALF_UP);
            } else {
                summe = einzel.multiply(mengeMitVerschnittUngerundet(p)).setScale(2, RoundingMode.HALF_UP);
            }
            if (einzel == null) {
                vollstaendig = false;
            }
            // Maschine des Arbeitsgangs: Kosten je Stunde × Stunden × Maschinenanteil (leer = 100 %). Ohne Satz am Arbeitsgang, aber mit ausdrücklichem Anteil: nicht berechenbar
            BigDecimal maschinenSatz = null;
            BigDecimal maschinenSumme = null;
            BigDecimal anteil = null;
            if (p.getTyp() == LeistungPosition.Typ.ARBEIT) {
                maschinenSatz = p.getArbeitsgang().effektiverMaschinenSatz();
                anteil = p.getMaschinenanteilProzent();
                if (maschinenSatz == null && anteil != null && anteil.signum() > 0) {
                    hinweis = "KEIN_MASCHINENSATZ";
                    vollstaendig = false;
                } else if (maschinenSatz != null && (anteil == null || anteil.signum() > 0)) {
                    maschinenSumme = maschinenSatz.multiply(stundenJeEinheit).multiply(anteil == null ? HUNDERT : anteil).divide(HUNDERT, 2, RoundingMode.HALF_UP);
                }
            }
            if (p.getTyp() == LeistungPosition.Typ.ARTIKEL) {
                material = material.add(summe);
            } else {
                arbeit = arbeit.add(summe);
                if (maschinenSumme != null) {
                    maschine = maschine.add(maschinenSumme);
                    summe = summe.add(maschinenSumme);
                }
            }
            zeilen.add(new LeistungKalkulationDto.Position(p.getId(), p.getTyp().name(),
                    p.getArtikel() == null ? null : p.getArtikel().getId(),
                    p.getArbeitsgang() == null ? null : p.getArbeitsgang().getId(),
                    bezeichnung, menge, einheit, einzel, summe, hinweis, p.getNotiz(),
                    mitZeiten ? p.getRuestzeitMinuten() : null, mitZeiten ? p.getBearbeitungszeitMinuten() : null, null,
                    mitZeiten ? vorgaenge(p.getAnzahlVorgaenge()) : null,
                    hatVerschnitt(p) ? p.getVerschnittProzent() : null,
                    hatVerschnitt(p) ? mengeMitVerschnittUngerundet(p).setScale(3, RoundingMode.HALF_UP) : null,
                    maschinenSatz, maschinenSumme, maschinenSatz == null ? null : (anteil == null ? HUNDERT.setScale(2) : anteil),
                    null, null, null, null,
                    p.getTyp() == LeistungPosition.Typ.ARTIKEL && gewichtDerZeile(p).kgBekannt() ? gewichtDerZeile(p).kg().setScale(3, RoundingMode.HALF_UP) : null,
                    p.getTyp() == LeistungPosition.Typ.ARTIKEL && gewichtDerZeile(p).m2Bekannt() ? gewichtDerZeile(p).m2().setScale(3, RoundingMode.HALF_UP) : null, null,
                    p.getTyp() == LeistungPosition.Typ.ARBEIT || p.getTyp() == LeistungPosition.Typ.ARTIKEL ? ArbeitsgangFormeln.werteAusJson(p.getVariablenJson()) : null,
                    formelFelderDerZeile(p)));
        }
        return new Ergebnis(zeilen, material, arbeit, maschine, fremd, vollstaendig);
    }

    /**
     * Gewicht (kg) und Oberfläche (m²) je Einheit der Leistung. Beides ist getrennt bekannt oder nicht: Das Verzinken rechnet immer nach Gewicht, das Pulverbeschichten
     * immer nach Fläche; fehlt nur die Fläche, bleibt das Verzinken berechenbar (und umgekehrt).
     */
    private record Gew(BigDecimal kg, BigDecimal m2, boolean kgBekannt, boolean m2Bekannt) {
        /** Von jedem Material sind Gewicht und Fläche bekannt. */
        boolean vollstaendig() {
            return kgBekannt && m2Bekannt;
        }
    }

    private static final Gew KEIN_GEWICHT = new Gew(BigDecimal.ZERO, BigDecimal.ZERO, true, true);

    /**
     * Gewicht und Oberfläche aller Material-Zeilen (ohne Verschnitt: verzinkt und beschichtet wird, was gebaut wird), Baugruppen mit ihrer Menge je Einheit.
     * Material mit unbekanntem Gewicht (Stück ohne Längenangabe, Artikel ohne Profilmasse) macht das Ergebnis unvollständig.
     */
    private Gew gewichtVon(List<LeistungPosition> positionen, int tiefe) {
        BigDecimal kg = BigDecimal.ZERO;
        BigDecimal m2 = BigDecimal.ZERO;
        boolean kgOk = true;
        boolean m2Ok = true;
        for (LeistungPosition p : positionen) {
            Gew z = null;
            if (p.getTyp() == LeistungPosition.Typ.ARTIKEL) {
                z = gewichtDerZeile(p);
            } else if (p.getTyp() == LeistungPosition.Typ.BAUGRUPPE && p.getBaugruppe() != null && tiefe + 1 <= MAX_BAUGRUPPEN_TIEFE) {
                Gew kind = gewichtVon(p.getBaugruppe().getPositionen(), tiefe + 1);
                z = new Gew(kind.kg().multiply(p.getMenge()), kind.m2().multiply(p.getMenge()), kind.kgBekannt(), kind.m2Bekannt());
            }
            if (z == null) {
                continue;
            }
            kg = kg.add(z.kg());
            m2 = m2.add(z.m2());
            kgOk &= z.kgBekannt();
            m2Ok &= z.m2Bekannt();
        }
        return new Gew(kg, m2, kgOk, m2Ok);
    }

    /**
     * Gewicht und Oberfläche einer Material-Zeile aus den Stammdaten des Artikels: in lfm Menge × kg je m und × Außenfläche je m; ein Blech in m² Menge × kg je m²
     * und × Fläche je m² (ohne Angabe beide Seiten: 2); in Stück Menge × kg je Stück und × Fläche je Stück. Was am Artikel fehlt, ist unbekannt.
     */
    private Gew gewichtDerZeile(LeistungPosition p) {
        Artikel a = p.getArtikel();
        if (a == null) {
            return KEIN_GEWICHT;
        }
        Object echt = Hibernate.unproxy(a);
        if (!(echt instanceof ArtikelWerkstoffe w)) {
            return new Gew(BigDecimal.ZERO, BigDecimal.ZERO, false, false);
        }
        // Zuschnitt mit Formel: Gewicht und Fläche je Stück aus den eingegebenen Maßen (#228)
        ArtikelFormeln.Ergebnis zuschnitt = zuschnitt(p);
        if (zuschnitt != null) {
            return gew(p.getMenge(), zuschnitt.kg(), zuschnitt.m2());
        }
        String einheit = artikelPreise.berechne(a).einheit();
        BigDecimal menge = p.getMenge();
        if ("lfm".equals(einheit)) {
            return gew(menge, w.getMasse(), w.getMantelflaeche());
        }
        if ("m²".equals(einheit)) {
            BigDecimal flaeche = w.getFlaecheJeQm() != null && w.getFlaecheJeQm().signum() > 0 ? w.getFlaecheJeQm() : new BigDecimal("2");
            return gew(menge, w.getMassePerQm(), flaeche);
        }
        if ("Stk".equals(einheit)) {
            return gew(menge, w.getKgJeStueck(), w.getM2JeStueck());
        }
        return new Gew(BigDecimal.ZERO, BigDecimal.ZERO, false, false);
    }

    /** Gewicht und Fläche je Stück aus der Zuschnitt-Formel (alles {@code null}, wenn sie sich mit den Werten nicht rechnen lässt); {@code null} ohne passende Formel. */
    private ArtikelFormeln.Ergebnis zuschnitt(LeistungPosition p) {
        if (p.getTyp() != LeistungPosition.Typ.ARTIKEL || p.getArtikel() == null) {
            return null;
        }
        if (!(Hibernate.unproxy(p.getArtikel()) instanceof ArtikelWerkstoffe w) || !ArtikelFormeln.hat(w)) {
            return null;
        }
        Verrechnungseinheit e = w.getVerrechnungseinheit();
        if (e != null && e != Verrechnungseinheit.KILOGRAMM && e != Verrechnungseinheit.QUADRATMETER && e != Verrechnungseinheit.STUECK) {
            return null;
        }
        try {
            return ArtikelFormeln.berechne(ArtikelFormeln.lese(w), ArbeitsgangFormeln.werteAusJson(p.getVariablenJson()), eingebaut(w));
        } catch (FormelException ex) {
            return new ArtikelFormeln.Ergebnis(null, null);
        }
    }

    private static java.util.Map<String, BigDecimal> eingebaut(ArtikelWerkstoffe w) {
        return ArtikelFormeln.eingebaut(w.getWerkstoff() == null ? null : w.getWerkstoff().getDichte(), w.getMasse(), w.getMantelflaeche());
    }

    /** Die Eingabefelder der Zuschnitt-Formel des Artikels dieser Zeile, damit die Oberfläche die Maße abfragen kann; sonst {@code null}. */
    private List<ArbeitsgangFormeln.Feld> formelFelderDerZeile(LeistungPosition p) {
        if (p.getTyp() != LeistungPosition.Typ.ARTIKEL || p.getArtikel() == null) {
            return null;
        }
        return Hibernate.unproxy(p.getArtikel()) instanceof ArtikelWerkstoffe w && ArtikelFormeln.hat(w) ? ArtikelFormeln.lese(w).felder() : null;
    }

    /** Menge × Gewicht je Einheit und Menge × Fläche je Einheit; ein fehlender oder nicht positiver Wert ist unbekannt. */
    private static Gew gew(BigDecimal menge, BigDecimal kgJe, BigDecimal m2Je) {
        boolean kgBekannt = kgJe != null && kgJe.signum() > 0;
        boolean m2Bekannt = m2Je != null && m2Je.signum() > 0;
        return new Gew(kgBekannt ? menge.multiply(kgJe) : BigDecimal.ZERO, m2Bekannt ? menge.multiply(m2Je) : BigDecimal.ZERO, kgBekannt, m2Bekannt);
    }

    private static boolean hatVerschnitt(LeistungPosition p) {
        return p.getTyp() == LeistungPosition.Typ.ARTIKEL && p.getVerschnittProzent() != null && p.getVerschnittProzent().signum() > 0;
    }

    /** Menge × (1 + Verschnitt): Mit dieser Menge rechnet der Preis. Ohne Verschnitt die Menge selbst. */
    private static BigDecimal mengeMitVerschnittUngerundet(LeistungPosition p) {
        if (!hatVerschnitt(p)) {
            return p.getMenge();
        }
        return p.getMenge().multiply(BigDecimal.ONE.add(p.getVerschnittProzent().divide(HUNDERT, 6, RoundingMode.HALF_UP)));
    }

    /** Maschinenzeit in Prozent der Arbeitszeit: leer = 100 (nicht gespeichert), sonst 0 bis 1.000. */
    private static BigDecimal maschinenanteil(BigDecimal prozent) {
        if (prozent == null) {
            return null;
        }
        if (prozent.signum() < 0 || prozent.compareTo(new BigDecimal("1000")) > 0) {
            throw fehler("Der Maschinenanteil muss zwischen 0 und 1.000 Prozent liegen.");
        }
        return prozent.setScale(2, RoundingMode.HALF_UP);
    }

    /** Verschnitt in Prozent: leer oder 0 = keiner, sonst bis 100. */
    private static BigDecimal verschnitt(BigDecimal prozent) {
        if (prozent == null || prozent.signum() == 0) {
            return null;
        }
        if (prozent.signum() < 0 || prozent.compareTo(HUNDERT) > 0) {
            throw fehler("Der Verschnitt muss zwischen 0 und 100 Prozent liegen.");
        }
        return prozent.setScale(2, RoundingMode.HALF_UP);
    }

    /** Wie oft ein Vorgang je Einheit vorkommt: leer = 1, sonst größer 0 bis 1.000.000. */
    private static BigDecimal vorgaenge(BigDecimal anzahl) {
        if (anzahl == null) {
            return BigDecimal.ONE.setScale(3);
        }
        if (anzahl.signum() <= 0 || anzahl.compareTo(MAX_MENGE) > 0) {
            throw fehler("Die Anzahl der Vorgänge muss größer als 0 sein.");
        }
        return anzahl.setScale(3, RoundingMode.HALF_UP);
    }

    private static String einheitText(Leistung baugruppe) {
        if (baugruppe.getEinheit() == null) {
            return "Stk";
        }
        return switch (baugruppe.getEinheit()) {
            case LAUFENDE_METER -> "lfm";
            case QUADRATMETER -> "m²";
            case KILOGRAMM -> "kg";
            case LITER -> "l";
            case STUECK -> "Stk";
        };
    }

    /** Keine Baugruppe darf sich selbst enthalten, auch nicht über Umwege, und mehr als {@link #MAX_BAUGRUPPEN_TIEFE} Ebenen sind nicht erlaubt. */
    private void pruefeBaugruppen(Leistung leistung, List<LeistungPosition> neu) {
        for (LeistungPosition p : neu) {
            if (p.getTyp() != LeistungPosition.Typ.BAUGRUPPE) {
                continue;
            }
            Leistung b = p.getBaugruppe();
            if (leistung.getId() != null && leistung.getId().equals(b.getId())) {
                throw fehler("Eine Baugruppe kann sich nicht selbst enthalten: „" + b.getBezeichnung() + "“.");
            }
            List<String> weg = new ArrayList<>(List.of(leistung.getBezeichnung() == null ? "Diese Leistung" : leistung.getBezeichnung(), b.getBezeichnung()));
            pruefeKreisUndTiefe(leistung.getId(), b, weg, new HashSet<>(Set.of(b.getId())), 1);
        }
    }

    private void pruefeKreisUndTiefe(Long ziel, Leistung aktuell, List<String> weg, Set<Long> aufDemWeg, int tiefe) {
        if (tiefe > MAX_BAUGRUPPEN_TIEFE) {
            throw fehler("Die Baugruppen stecken zu tief ineinander (höchstens " + MAX_BAUGRUPPEN_TIEFE + " Ebenen): " + String.join(" → ", weg) + ".");
        }
        for (LeistungPosition kind : aktuell.getPositionen()) {
            if (kind.getTyp() != LeistungPosition.Typ.BAUGRUPPE || kind.getBaugruppe() == null) {
                continue;
            }
            Leistung c = kind.getBaugruppe();
            if (ziel != null && ziel.equals(c.getId())) {
                List<String> kreis = new ArrayList<>(weg);
                kreis.add(c.getBezeichnung());
                throw fehler("Das ergäbe einen Kreis, eine Baugruppe würde sich selbst enthalten: " + String.join(" → ", kreis) + ".");
            }
            if (!aufDemWeg.add(c.getId())) {
                continue; // ein schon vorhandener Kreis, der diese Leistung nicht betrifft: hier nicht weiter absteigen
            }
            List<String> weiter = new ArrayList<>(weg);
            weiter.add(c.getBezeichnung());
            pruefeKreisUndTiefe(ziel, c, weiter, aufDemWeg, tiefe + 1);
            aufDemWeg.remove(c.getId());
        }
    }

    private static BigDecimal alsZahl(BigDecimal wert) {
        return wert == null ? BigDecimal.ZERO : wert;
    }

    /** Stundensatz des Arbeitsgangs: das aktuelle Jahr, sonst das jüngste vorhandene Jahr. */
    private BigDecimal stundensatz(Arbeitsgang g, int jahr) {
        return stundensaetze.findTopByArbeitsgangIdAndJahrOrderByIdDesc(g.getId(), jahr)
                .or(() -> stundensaetze.findTopByArbeitsgangIdOrderByJahrDesc(g.getId()))
                .map(ArbeitsgangStundensatz::getSatz)
                .orElse(null);
    }

    private static String artikelName(Artikel a) {
        for (String s : new String[] { a.getKurzbeschreibung(), a.getProduktname(), a.getArtikelnummer() }) {
            if (s != null && !s.isBlank()) {
                return s.strip();
            }
        }
        return "Artikel " + a.getId();
    }

    private static ResponseStatusException fehler(String text) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, text);
    }
}
