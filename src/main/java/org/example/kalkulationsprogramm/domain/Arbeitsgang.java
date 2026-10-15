package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@Entity
public class Arbeitsgang {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Original: ArbeitsgangID

    /**
     * Optimistisches Sperren: schuetzt gegen paralleles Speichern (auch aus
     * der Mobile-App, die keine Sperr-Oberflaeche hat).
     */
    @Version
    private Long version;

    @Column(nullable = false, unique = true)
    private String beschreibung;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "abteilung_id", nullable = false)
    private Abteilung abteilung;

    @Column(name = "archiviert", nullable = false)
    private boolean archiviert = false;

    @Column(name = "satz_quelle", nullable = false, length = 20)
    private String satzQuelle = SATZ_EIGEN;

    @Column(name = "satz_gruppe", length = 30)
    private String satzGruppe;

    public static final String SATZ_EIGEN = "EIGEN";
    public static final String SATZ_VERRECHNUNG = "VERRECHNUNG";

    public boolean folgtVerrechnungssatz() {
        return SATZ_VERRECHNUNG.equals(satzQuelle);
    }

    @Column(name = "formel_felder_json", length = 4000)
    private String formelFelderJson;

    @Column(name = "formel_bearbeitung", length = 500)
    private String formelBearbeitung;

    @Column(name = "formel_ruest", length = 500)
    private String formelRuest;

    @Column(name = "ruestzeit_minuten", precision = 8, scale = 2)
    private java.math.BigDecimal ruestzeitMinuten;

    @Column(name = "bearbeitungszeit_minuten", precision = 8, scale = 2)
    private java.math.BigDecimal bearbeitungszeitMinuten;

    @Column(name = "maschinen_stundensatz", precision = 10, scale = 2)
    private java.math.BigDecimal maschinenStundensatz;

    @Column(name = "maschinen_bezeichnung", length = 100)
    private String maschinenBezeichnung;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maschine_id")
    private Maschine maschine;

    @OneToMany(mappedBy = "arbeitsgang", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Zeitbuchung> ZeitbuchungList = new ArrayList<>();

    @OneToMany(mappedBy = "arbeitsgang", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ArbeitsgangStundensatz> stundensaetze = new ArrayList<>();

    public java.math.BigDecimal effektiverMaschinenSatz() {
        return maschine != null ? maschine.getKostensatz() : maschinenStundensatz;
    }

    public String effektiveMaschinenBezeichnung() {
        return maschine != null ? maschine.getName() : maschinenBezeichnung;
    }
}
