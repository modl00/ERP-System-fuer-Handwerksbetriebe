package org.example.kalkulationsprogramm.domain.planung;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.Projekt;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Ein Vorgang im Ablauf eines Auftrags. Start und Ende rechnet das Programm ({@code PlanungsRechner}); von Hand gesetzt wird nur der feste Start. */
@Getter
@Setter
@Entity
@Table(name = "plan_vorgang")
public class PlanVorgang {

    public static final String HERKUNFT_KALKULATION = "KALKULATION";
    public static final String HERKUNFT_GESCHAETZT = "GESCHAETZT";
    public static final String HERKUNFT_VON_HAND = "VON_HAND";
    public static final String STATUS_GEPLANT = "GEPLANT";
    public static final String STATUS_LAEUFT = "LAEUFT";
    public static final String STATUS_FERTIG = "FERTIG";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "projekt_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Projekt projekt;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "typ_id", nullable = false)
    private PlanVorgangstyp typ;

    @Column(nullable = false, length = 200)
    private String bezeichnung;

    /** Arbeitstage; 0 = Meilenstein. */
    @Column(name = "dauer_tage", nullable = false)
    private int dauerTage = 1;

    /** Woher die Dauer kommt: KALKULATION, GESCHAETZT oder VON_HAND. */
    @Column(name = "dauer_herkunft", nullable = false, length = 20)
    private String dauerHerkunft = HERKUNFT_GESCHAETZT;

    /** Gesetzt = fixiert: Der Vorgang beginnt nicht früher und wird vom Programm nie verschoben. */
    @Column(name = "fester_start")
    private LocalDate festerStart;

    @Column(name = "start_datum")
    private LocalDate startDatum;

    @Column(name = "ende_datum")
    private LocalDate endeDatum;

    /** Der feste Start liegt vor dem Ende der Vorgänger. */
    @Column(nullable = false)
    private boolean konflikt = false;

    @Column(nullable = false, length = 20)
    private String status = STATUS_GEPLANT;

    @Column(nullable = false)
    private int sortierung = 0;

    @Column(length = 1000)
    private String notiz;

    public static final String PRIO_DRINGEND = "DRINGEND";
    public static final String PRIO_NORMAL = "NORMAL";
    public static final String PRIO_NICHT_DRINGEND = "NICHT_DRINGEND";

    /** Die Ressource (Werkstatt, Büro …), bei der der Vorgang läuft; leer = keine. */
    @Column(name = "ressource_id")
    private Long ressourceId;

    /** DRINGEND, NORMAL (laufende Aufträge) oder NICHT_DRINGEND. */
    @Column(nullable = false, length = 20)
    private String prioritaet = PRIO_NORMAL;

    /** Aufwand in Stunden (Arbeitsvorgang); leer bei Wartevorgängen. Die Dauer in Tagen ergibt sich dann aus Aufwand, Personen und freier Zeit. */
    @Column(name = "aufwand_stunden", precision = 9, scale = 2)
    private java.math.BigDecimal aufwandStunden;

    /** Woher der Aufwand kommt: KALKULATION, GESCHAETZT oder VON_HAND. */
    @Column(name = "aufwand_herkunft", nullable = false, length = 20)
    private String aufwandHerkunft = HERKUNFT_GESCHAETZT;

    /** Der Vorgang beginnt nicht früher, darf sich aber mit der Kette nach hinten bewegen (Ziehen mit der Maus). Getrennt vom festen Start. */
    @Column(name = "fruehestens_ab")
    private LocalDate fruehestensAb;

    /** Reihenfolge, die der Planer festgelegt hat (kleiner = zuerst); leer = noch nicht entschieden, dann gilt der frühere Zieltermin. */
    @Column(name = "rang")
    private Integer rang;

    /** Warum der Vorgang später endet (fehlende Personen, andere Aufträge); vom Programm gerechnet. */
    @Column(name = "hinweis", length = 1000)
    private String hinweis;

    /** Bei einem Wartevorgang: der Lieferant. Dessen übliche Lieferzeit gilt, solange keine Dauer von Hand eingetragen ist. */
    @Column(name = "lieferant_id")
    private Long lieferantId;

    /** Bei einer Fremdleistung (Verzinken, Pulvern …): das Angebot des Veredlers, aus der Kalkulation der Leistung. */
    @Column(name = "fremdleistung_id")
    private Long fremdleistungId;

    /** Menge der Fremdleistung in {@code mengenEinheit} (Gewicht in kg oder Fläche in m²); Grundlage der Sammelcharge. */
    @Column(name = "menge", precision = 12, scale = 3)
    private java.math.BigDecimal menge;

    /** KG oder M2. */
    @Column(name = "mengen_einheit", length = 10)
    private String mengenEinheit;

    /** Wann bestellt wurde: Die Lieferzeit zählt ab dann (Entscheidung H). */
    @Column(name = "bestellt_am")
    private LocalDate bestelltAm;

    /** Liefertermin laut Auftragsbestätigung des Lieferanten (von Hand); ohne Eintrag liest das Programm ihn aus dem Dokument. */
    @Column(name = "liefertermin_lieferant")
    private LocalDate lieferterminLieferant;

    /** Zählt Änderungen von Hand (nicht die automatische Neuberechnung): Wer mit einem älteren Stand speichert, bekommt eine Meldung statt die Änderung eines anderen zu überschreiben. */
    @Column(name = "bearbeitung", nullable = false)
    private int bearbeitung = 0;

    /** Noch niemand zugewiesen: Das Programm hat mit dem Standardwert gerechnet. */
    @Column(name = "ohne_zuweisung_geschaetzt", nullable = false)
    private boolean ohneZuweisungGeschaetzt = false;

    /** Die Mitarbeiter, die den Vorgang machen (Nummern); leer = niemand zugewiesen. */
    @ElementCollection
    @CollectionTable(name = "plan_vorgang_mitarbeiter", joinColumns = @JoinColumn(name = "vorgang_id"))
    @Column(name = "mitarbeiter_id")
    private Set<Long> mitarbeiterIds = new HashSet<>();
}
