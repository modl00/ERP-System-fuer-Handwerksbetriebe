package org.example.kalkulationsprogramm.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Ein Angebot eines Veredlers oder Fremdbetriebs (Verzinken, Pulverbeschichten, Fräsen …) mit seinen Kostenarten (Stufe 4 von #135). Jede Kostenart ist
 * einzeln ein- oder ausgeschaltet: Ist der Wert leer, gibt es sie bei diesem Veredler nicht. Die Berechnung steht im {@code FremdleistungsRechner}.
 */
@Entity
@Getter
@Setter
@Table(name = "fremdleistung")
public class Fremdleistung {

    /** Wonach der Veredler abrechnet. */
    public enum Einheit {
        KG("kg"), QUADRATMETER("m²"), METER("m"), STUECK("Stk");

        private final String kurz;

        Einheit(String kurz) {
            this.kurz = kurz;
        }

        public String kurz() {
            return kurz;
        }
    }

    /** Wie die Transportkosten entstehen. */
    public enum TransportArt { KEINE, PAUSCHAL, JE_KG, JE_FAHRT, JE_KM }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lieferant_id", nullable = false)
    private Lieferanten lieferant;

    /** Was der Veredler macht, zum Beispiel „Feuerverzinken“. Gleiche Bezeichnungen bei verschiedenen Veredlern lassen sich vergleichen. */
    @Column(name = "bezeichnung", nullable = false, length = 200)
    private String bezeichnung;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "einheit", nullable = false, length = 20)
    private Einheit einheit;

    /** Preis je Einheit (kg, m², m, Stück), zusätzlich zu den anderen Kosten. */
    @Column(name = "preis_je_einheit", precision = 12, scale = 4)
    private BigDecimal preisJeEinheit;

    /** Mindestpreis je Auftrag: Der Einheitspreis-Teil kostet mindestens so viel. */
    @Column(name = "mindestpreis", precision = 12, scale = 2)
    private BigDecimal mindestpreis;

    /** Einmalige Kosten je Auftrag (Rüsten, Aufhängen, Anfahrt). */
    @Column(name = "einmal_betrag", precision = 12, scale = 2)
    private BigDecimal einmalBetrag;

    /** Kosten je Charge, die auf die Menge dieses Auftrags verteilt werden (zum Beispiel Fahrt je Charge geteilt durch die Teile der Charge). */
    @Column(name = "anteil_betrag", precision = 12, scale = 2)
    private BigDecimal anteilBetrag;

    /** Wie viele Einheiten eine Charge fasst (zu {@link #anteilBetrag}). */
    @Column(name = "anteil_chargenmenge", precision = 12, scale = 3)
    private BigDecimal anteilChargenmenge;

    /** Prozent auf die Summe aller übrigen Kosten dieses Veredlers (zum Beispiel Energie- oder Umweltzuschlag). */
    @Column(name = "prozent_zuschlag", precision = 6, scale = 2)
    private BigDecimal prozentZuschlag;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "transport_art", nullable = false, length = 20)
    private TransportArt transportArt = TransportArt.KEINE;

    /** Pauschal: der Betrag; je kg: Euro je kg; je Fahrt: Euro je Fahrt; je km: Euro je km. */
    @Column(name = "transport_betrag", precision = 12, scale = 4)
    private BigDecimal transportBetrag;

    /** Nur bei Transport je km: Strecke einfach in km (hin und zurück wird mitgerechnet). */
    @Column(name = "transport_km", precision = 8, scale = 2)
    private BigDecimal transportKm;

    /** Anzahl der Fahrten je Auftrag (bei Transport je Fahrt und je km). */
    @Column(name = "transport_fahrten", nullable = false)
    private int transportFahrten = 1;

    @Column(name = "sonstige_bezeichnung", length = 100)
    private String sonstigeBezeichnung;

    @Column(name = "sonstige_betrag", precision = 12, scale = 2)
    private BigDecimal sonstigeBetrag;

    /** Preisstand: seit wann die Preise gelten. */
    @Column(name = "gueltig_ab", nullable = false)
    private LocalDate gueltigAb;

    @Column(name = "aktiv", nullable = false)
    private boolean aktiv = true;
}
