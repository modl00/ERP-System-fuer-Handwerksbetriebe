package org.example.kalkulationsprogramm.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Zählt, was an einem Mitarbeiter hängt und aufbewahrt werden muss (#284). So kann das Programm beim Löschen genau sagen, <b>was</b> das Löschen verhindert,
 * statt nur "geht nicht" zu melden. Lohnabrechnungen zählen mit: Sie müssen aufbewahrt werden und dürfen nicht mit dem Mitarbeiter verschwinden.
 */
@Component
public class MitarbeiterBezuege {

    /** Anzeige (Einzahl, Mehrzahl) und Entität; jede Entität hat das Feld {@code mitarbeiter}. */
    private static final String[][] ARTEN = {
            {"Zeitbuchung", "Zeitbuchungen", "Zeitbuchung"},
            {"Abwesenheit (Urlaub, Krankheit)", "Abwesenheiten (Urlaub, Krankheit)", "Abwesenheit"},
            {"Urlaubsantrag", "Urlaubsanträge", "Urlaubsantrag"},
            {"Langzeitkrankmeldung", "Langzeitkrankmeldungen", "Langzeitkrankmeldung"},
            {"Zeitkonto-Korrektur", "Zeitkonto-Korrekturen", "ZeitkontoKorrektur"},
            {"Lohnabrechnung", "Lohnabrechnungen", "Lohnabrechnung"},
    };

    @PersistenceContext
    private EntityManager em;

    /** Eine Zeile je Art mit Einträgen, zum Beispiel "12 Zeitbuchungen"; leer, wenn nichts am Mitarbeiter hängt. */
    @Transactional(readOnly = true)
    public List<String> beschreibe(Long mitarbeiterId) {
        List<String> zeilen = new ArrayList<>();
        for (String[] art : ARTEN) {
            long n = em.createQuery("select count(e) from " + art[2] + " e where e.mitarbeiter.id = :id", Long.class)
                    .setParameter("id", mitarbeiterId).getSingleResult();
            if (n > 0) {
                zeilen.add(n + " " + (n == 1 ? art[0] : art[1]));
            }
        }
        return zeilen;
    }
}
