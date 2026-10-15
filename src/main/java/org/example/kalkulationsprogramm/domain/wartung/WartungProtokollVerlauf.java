package org.example.kalkulationsprogramm.domain.wartung;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Ein Statuswechsel eines Wartungsprotokolls: wer, wann, von wo nach wo und warum (#238). Wird nur angehängt, nie geändert. */
@Entity
@Table(name = "wartung_protokoll_verlauf")
@Getter
@Setter
@NoArgsConstructor
public class WartungProtokollVerlauf {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "protokoll_id", nullable = false)
    private Long protokollId;

    @Column(nullable = false)
    private LocalDateTime zeit;

    @Column(length = 120)
    private String benutzer;

    @Enumerated(EnumType.STRING)
    @Column(name = "von_status", nullable = false, length = 20)
    private WartungStatus vonStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "nach_status", nullable = false, length = 20)
    private WartungStatus nachStatus;

    @Column(length = 500)
    private String grund;
}
