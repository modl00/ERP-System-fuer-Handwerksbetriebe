package org.example.kalkulationsprogramm.domain.planung;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Eine Art von Vorgang in der Plantafel (Planung, Materialbeschaffung, Fertigung, Veredelung, Montage … frei einstellbar). */
@Getter
@Setter
@Entity
@Table(name = "plan_vorgangstyp")
public class PlanVorgangstyp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100, unique = true)
    private String name;

    /** Farbe des Balkens als {@code #rrggbb}. */
    @Column(nullable = false, length = 9)
    private String farbe = "#be123c";

    @Column(nullable = false)
    private int sortierung = 0;

    /** Schätzwert in Arbeitstagen, wenn sonst nichts bekannt ist. */
    @Column(name = "standard_dauer_tage", nullable = false)
    private int standardDauerTage = 1;

    @Column(nullable = false)
    private boolean aktiv = true;

    /** Die Abteilung, deren Arbeitszeit aus dem Angebot für diese Art zählt (zum Beispiel Werkstatt für die Fertigung); leer = kein Vorschlag aus dem Angebot. */
    @Column(name = "abteilung_id")
    private Long abteilungId;

    /** Wie viele Stunden dieser Art pro Arbeitstag geleistet werden (zum Beispiel 3 Mitarbeiter mal 8 = 24); leer = kein Vorschlag aus dem Angebot. */
    @Column(name = "stunden_pro_tag", precision = 7, scale = 2)
    private java.math.BigDecimal stundenProTag;

    /** Wartevorgang (Material bestellen, Fremdleistung): braucht keine Personen, nur Zeit in Arbeitstagen. */
    @Column(nullable = false)
    private boolean wartet = false;

    /** Büroarbeit: zählt auch an Bürotagen einer Person (alle anderen Arten nicht). */
    @Column(nullable = false)
    private boolean buero = false;
}
