package org.example.kalkulationsprogramm.domain.wartung;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * Ein Baustein des Vorlagenkatalogs, zum Beispiel "Brandschutztür" oder "Feststellanlage".
 * Prüfpunkte, Datenfelder, Abschnitte und Bauarten hängen über {@code modul_id} daran.
 */
@Entity
@Getter
@Setter
public class WartungModul {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "modul_key", nullable = false, unique = true, length = 60)
    private String modulKey;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String beschreibung;

    /** Name eines lucide-react-Icons. */
    @Column(length = 40)
    private String icon;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WartungModulKategorie kategorie = WartungModulKategorie.BASIS;

    /** Mitgelieferte Vorlage. Systemmodule lassen sich nicht löschen. */
    @Column(name = "system_modul", nullable = false)
    private boolean systemModul;

    @Column(nullable = false)
    private boolean aktiv = true;

    @Column(nullable = false)
    private int sortierung;

    @Column(name = "standard_intervall_monate", nullable = false)
    private int standardIntervallMonate = 12;

    /** Nur bei Zusatzmodulen: an welche Basis-Module es andocken darf. Leer = an jedes. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "wartung_modul_andockbar", joinColumns = @JoinColumn(name = "modul_id"))
    @Column(name = "basis_modul_id", nullable = false)
    private Set<Long> andockbarAnBasisModulIds = new HashSet<>();

    public boolean istBasis() {
        return kategorie == WartungModulKategorie.BASIS;
    }
}
