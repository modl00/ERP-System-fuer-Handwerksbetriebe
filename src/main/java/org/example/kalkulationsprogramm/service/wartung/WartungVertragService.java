package org.example.kalkulationsprogramm.service.wartung;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.wartung.WartungVertrag;
import org.example.kalkulationsprogramm.dto.wartung.WartungVertragDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungVertragSpeichernDto;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungVertragRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/** Wartungsvertrag je Projekt: anlegen oder ändern, lesen, löschen. Prüft Laufzeit, Intervall und Preis. */
@Service
@RequiredArgsConstructor
public class WartungVertragService {

    static final int MAX_INTERVALL_MONATE = 120;
    private static final BigDecimal MAX_PREIS = new BigDecimal("10000000");
    private static final int BALD_TAGE = 90;

    private final WartungVertragRepository vertraege;
    private final ProjektRepository projekte;
    private final Clock uhr;

    @Transactional(readOnly = true)
    public Optional<WartungVertragDto> finde(Long projektId) {
        pruefeProjekt(projektId);
        return vertraege.findByProjektId(projektId).map(this::dto);
    }

    /** Legt den Vertrag des Projekts an oder ersetzt dessen Angaben. */
    @Transactional
    public WartungVertragDto speichern(Long projektId, WartungVertragSpeichernDto eingabe) {
        Projekt projekt = pruefeProjekt(projektId);
        if (eingabe == null || eingabe.beginn() == null) {
            throw fehler("Bitte den Beginn des Vertrags angeben.");
        }
        if (eingabe.ende() != null && eingabe.ende().isBefore(eingabe.beginn())) {
            throw fehler("Das Ende darf nicht vor dem Beginn liegen.");
        }
        int intervall = eingabe.intervallMonate() == null ? 12 : eingabe.intervallMonate();
        if (intervall < 1 || intervall > MAX_INTERVALL_MONATE) {
            throw fehler("Das Intervall muss zwischen 1 und " + MAX_INTERVALL_MONATE + " Monaten liegen.");
        }
        BigDecimal preis = eingabe.preisNetto();
        if (preis != null && (preis.signum() < 0 || preis.compareTo(MAX_PREIS) > 0)) {
            throw fehler("Der Preis muss zwischen 0 und 10.000.000 Euro liegen.");
        }
        String nummer = text(eingabe.vertragsnummer(), 60, "Die Vertragsnummer ist zu lang (höchstens 60 Zeichen).");
        String notiz = text(eingabe.notiz(), 1000, "Die Notiz ist zu lang (höchstens 1000 Zeichen).");

        WartungVertrag v = vertraege.findByProjektId(projektId).orElseGet(() -> {
            WartungVertrag neu = new WartungVertrag();
            neu.setProjekt(projekt);
            return neu;
        });
        v.setVertragsnummer(nummer);
        v.setBeginn(eingabe.beginn());
        v.setEnde(eingabe.ende());
        v.setIntervallMonate(intervall);
        v.setPreisNetto(preis == null ? null : preis.setScale(2, RoundingMode.HALF_UP));
        v.setNotiz(notiz);
        return dto(vertraege.save(v));
    }

    @Transactional
    public void loeschen(Long projektId) {
        pruefeProjekt(projektId);
        vertraege.findByProjektId(projektId).ifPresent(vertraege::delete);
    }

    private Projekt pruefeProjekt(Long projektId) {
        if (projektId == null || projektId <= 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Das Projekt wurde nicht gefunden.");
        }
        return projekte.findById(projektId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Das Projekt wurde nicht gefunden."));
    }

    WartungVertragDto dto(WartungVertrag v) {
        LocalDate heute = LocalDate.now(uhr);
        String status = v.getBeginn().isAfter(heute) ? "NOCH_NICHT_GESTARTET"
                : (v.getEnde() != null && v.getEnde().isBefore(heute) ? "ABGELAUFEN" : "LAEUFT");
        boolean endetBald = "LAEUFT".equals(status) && v.getEnde() != null && !v.getEnde().isAfter(heute.plusDays(BALD_TAGE));
        return new WartungVertragDto(v.getId(), v.getProjekt().getId(), v.getVertragsnummer(), v.getBeginn(), v.getEnde(),
                v.getIntervallMonate(), v.getPreisNetto(), v.getNotiz(), status, endetBald);
    }

    private static String text(String s, int max, String meldung) {
        if (s == null || s.isBlank()) {
            return null;
        }
        if (s.length() > max) {
            throw fehler(meldung);
        }
        return s.strip();
    }

    private static ResponseStatusException fehler(String text) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, text);
    }
}
