package org.example.kalkulationsprogramm.domain.wartung;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** Merkt, welcher ERP-Datensatz aus welchem Altdatensatz einer Übernahme entstanden ist (Kunde, Objekt, Tür, Protokoll), damit ein zweiter Lauf nichts doppelt anlegt. */
@Entity
@Table(name = "wartung_import_zuordnung", uniqueConstraints = @UniqueConstraint(name = "uk_wartung_import_zuordnung", columnNames = { "quelle", "typ", "alt_id" }))
@Getter
@Setter
public class WartungImportZuordnung {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String quelle;

    /** KUNDE, OBJEKT, TUER oder PROTOKOLL. */
    @Column(nullable = false, length = 20)
    private String typ;

    @Column(name = "alt_id", nullable = false)
    private Long altId;

    @Column(name = "erp_id", nullable = false)
    private Long erpId;

    @Column(name = "importiert_am", nullable = false)
    private LocalDateTime importiertAm = LocalDateTime.now();
}
