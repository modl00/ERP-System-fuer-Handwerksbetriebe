package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.KundeAnsprechpartner;
import org.example.kalkulationsprogramm.dto.Kunde.KundeAnsprechpartnerDto;
import org.example.kalkulationsprogramm.dto.Kunde.KundeAnsprechpartnerSpeichernDto;
import org.example.kalkulationsprogramm.repository.KundeAnsprechpartnerRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Ansprechpartner eines Kunden. Die Liste ist die Quelle der Wahrheit; der Name des Hauptansprechpartners
 * wird in {@code kunde.ansprechspartner} mitgeführt, weil E-Mails, Mahnungen, Dokumente und die Telefonsuche
 * dieses Feld lesen.
 */
@Service
@RequiredArgsConstructor
public class KundeAnsprechpartnerService {

    static final int MAX_JE_KUNDE = 50;

    private final KundeAnsprechpartnerRepository repository;
    private final KundeRepository kundeRepository;

    /**
     * Die Ansprechpartner des Kunden, Hauptansprechpartner zuerst. Hat der Kunde noch keine, aber ein altes Textfeld
     * "Ansprechpartner" (Neuanlage über Anfrage-Formular o. Ä.), wird daraus der Hauptansprechpartner angelegt.
     */
    @Transactional
    public List<KundeAnsprechpartnerDto> liste(Long kundeId) {
        Kunde kunde = ladeKunde(kundeId);
        altbestandUebernehmen(kunde);
        return repository.findByKundeIdOrderByHauptDescNameAscIdAsc(kundeId).stream().map(KundeAnsprechpartnerService::alsDto).toList();
    }

    /** Anzahl für die Kundenansicht. Ein Kunde nur mit altem Textfeld zählt als einer (die Zeile entsteht beim ersten Öffnen der Liste). */
    @Transactional(readOnly = true)
    public long anzahl(Kunde kunde) {
        long n = repository.countByKundeId(kunde.getId());
        return n == 0 && hatText(kunde.getAnsprechspartner()) ? 1 : n;
    }

    @Transactional
    public KundeAnsprechpartnerDto anlegen(Long kundeId, KundeAnsprechpartnerSpeichernDto dto) {
        Kunde kunde = ladeKunde(kundeId);
        // Ein altes Textfeld wird zuerst zur Zeile, sonst überschriebe der neue Hauptansprechpartner den alten Namen
        altbestandUebernehmen(kunde);
        if (repository.countByKundeId(kundeId) >= MAX_JE_KUNDE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Pro Kunde sind höchstens " + MAX_JE_KUNDE + " Ansprechpartner möglich.");
        }
        KundeAnsprechpartner neu = new KundeAnsprechpartner();
        neu.setKunde(kunde);
        uebernehmen(neu, dto);
        // Der erste Ansprechpartner eines Kunden ist automatisch der Hauptansprechpartner
        boolean haupt = Boolean.TRUE.equals(dto.haupt()) || repository.countByKundeId(kundeId) == 0;
        if (haupt) {
            hauptEntziehen(kundeId);
        }
        neu.setHaupt(haupt);
        repository.saveAndFlush(neu);
        hauptNamenSpiegeln(kunde);
        return alsDto(neu);
    }

    @Transactional
    public KundeAnsprechpartnerDto aendern(Long kundeId, Long id, KundeAnsprechpartnerSpeichernDto dto) {
        Kunde kunde = ladeKunde(kundeId);
        KundeAnsprechpartner a = lade(kundeId, id);
        uebernehmen(a, dto);
        if (Boolean.TRUE.equals(dto.haupt()) && !a.isHaupt()) {
            hauptEntziehen(kundeId);
            a.setHaupt(true);
        }
        repository.saveAndFlush(a);
        hauptNamenSpiegeln(kunde);
        return alsDto(a);
    }

    @Transactional
    public KundeAnsprechpartnerDto hauptSetzen(Long kundeId, Long id) {
        Kunde kunde = ladeKunde(kundeId);
        KundeAnsprechpartner a = lade(kundeId, id);
        if (!a.isHaupt()) {
            hauptEntziehen(kundeId);
            a.setHaupt(true);
            repository.saveAndFlush(a);
        }
        hauptNamenSpiegeln(kunde);
        return alsDto(a);
    }

    @Transactional
    public void loeschen(Long kundeId, Long id) {
        Kunde kunde = ladeKunde(kundeId);
        KundeAnsprechpartner a = lade(kundeId, id);
        boolean warHaupt = a.isHaupt();
        repository.delete(a);
        repository.flush();
        if (warHaupt) {
            // Der nächste in der Reihenfolge rückt nach, damit der Kunde nie ohne Hauptansprechpartner dasteht
            repository.findByKundeIdOrderByHauptDescNameAscIdAsc(kundeId).stream().findFirst().ifPresent(n -> {
                n.setHaupt(true);
                repository.saveAndFlush(n);
            });
        }
        hauptNamenSpiegeln(kunde);
    }

    /**
     * Für das Kundenformular: Das Textfeld "Ansprechpartner" des Formulars gilt nur, solange der Kunde noch keine
     * Ansprechpartner in der Liste hat. Danach ist die Liste maßgebend und das Formular ändert sie nicht.
     */
    @Transactional
    public void ausKundenformular(Kunde kunde, String name) {
        if (kunde.getId() == null || repository.countByKundeId(kunde.getId()) > 0) {
            return;
        }
        if (hatText(name)) {
            KundeAnsprechpartner a = new KundeAnsprechpartner();
            a.setKunde(kunde);
            a.setName(kuerze(name.trim(), 200));
            a.setHaupt(true);
            repository.save(a);
        }
    }

    /** Gibt es schon eine Liste, bleibt der Name aus dem Formular außer Acht und das Feld zeigt den Hauptansprechpartner. */
    @Transactional(readOnly = true)
    public boolean hatListe(Long kundeId) {
        return kundeId != null && repository.countByKundeId(kundeId) > 0;
    }

    // ── intern ──────────────────────────────────────────────────────────────

    private void hauptEntziehen(Long kundeId) {
        repository.findByKundeIdOrderByHauptDescNameAscIdAsc(kundeId).stream()
                .filter(KundeAnsprechpartner::isHaupt)
                .forEach(h -> {
                    h.setHaupt(false);
                    repository.saveAndFlush(h);
                });
    }

    /** Hat der Kunde noch keine Zeile, aber ein altes Textfeld "Ansprechpartner", wird daraus der Hauptansprechpartner. */
    private void altbestandUebernehmen(Kunde kunde) {
        if (repository.countByKundeId(kunde.getId()) == 0 && hatText(kunde.getAnsprechspartner())) {
            KundeAnsprechpartner alt = new KundeAnsprechpartner();
            alt.setKunde(kunde);
            alt.setName(kuerze(kunde.getAnsprechspartner().trim(), 200));
            alt.setHaupt(true);
            repository.saveAndFlush(alt);
        }
    }

    private void hauptNamenSpiegeln(Kunde kunde) {
        String name = repository.findByKundeIdOrderByHauptDescNameAscIdAsc(kunde.getId()).stream()
                .filter(KundeAnsprechpartner::isHaupt)
                .map(KundeAnsprechpartner::getName)
                .findFirst()
                .orElse(null);
        kundeRepository.setzeAnsprechspartnerName(kunde.getId(), name);
    }

    /** Lädt den Kunden mit Schreibsperre: Zwei gleichzeitige Änderungen laufen nacheinander (kein doppelter Hauptansprechpartner, Obergrenze gilt). */
    private Kunde ladeKunde(Long kundeId) {
        return kundeRepository.findMitSperre(kundeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Der Kunde wurde nicht gefunden."));
    }

    private KundeAnsprechpartner lade(Long kundeId, Long id) {
        // Über den Kunden suchen: Ein Ansprechpartner eines anderen Kunden ist hier "nicht gefunden"
        return repository.findByIdAndKundeId(id, kundeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Der Ansprechpartner wurde nicht gefunden."));
    }

    private static void uebernehmen(KundeAnsprechpartner a, KundeAnsprechpartnerSpeichernDto dto) {
        a.setName(dto.name().trim());
        a.setFunktion(leerZuNull(dto.funktion()));
        a.setTelefon(leerZuNull(dto.telefon()));
        a.setMobil(leerZuNull(dto.mobil()));
        a.setEmail(leerZuNull(dto.email()));
        a.setNotizen(leerZuNull(dto.notizen()));
    }

    private static KundeAnsprechpartnerDto alsDto(KundeAnsprechpartner a) {
        return new KundeAnsprechpartnerDto(a.getId(), a.getName(), a.getFunktion(), a.getTelefon(), a.getMobil(),
                a.getEmail(), a.getNotizen(), a.isHaupt());
    }

    private static String leerZuNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static boolean hatText(String s) {
        return s != null && !s.isBlank();
    }

    private static String kuerze(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
