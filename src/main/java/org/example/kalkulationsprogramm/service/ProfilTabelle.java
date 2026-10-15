package org.example.kalkulationsprogramm.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Profilform;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Die mitgelieferte Profiltabelle (#168): Gewicht und Oberfläche je Meter gängiger Walzprofile (IPE, HEA, HEB, UPN). Die Werte stehen in
 * {@code profile/profiltabelle.csv} und sind gerundete Normwerte für Stahl; im Artikel lassen sie sich überschreiben.
 */
@Component
public class ProfilTabelle {

    /** Eine Zeile der Tabelle. */
    public record Eintrag(Profilform form, String bezeichnung, BigDecimal hoeheMm, BigDecimal breiteMm, BigDecimal kgJeMeter, BigDecimal m2JeMeter) { }

    private final List<Eintrag> eintraege;

    public ProfilTabelle() {
        this.eintraege = Collections.unmodifiableList(lade());
    }

    /** Alle Profile dieser Form (leer, wenn die Form nicht in der Tabelle steht). */
    public List<Eintrag> fuer(Profilform form) {
        return eintraege.stream().filter(e -> e.form() == form).toList();
    }

    /** Die Formen, für die es Tabellenwerte gibt. */
    public List<Profilform> formen() {
        return eintraege.stream().map(Eintrag::form).distinct().toList();
    }

    public Optional<Eintrag> finde(Profilform form, String bezeichnung) {
        return eintraege.stream().filter(e -> e.form() == form && e.bezeichnung().equalsIgnoreCase(bezeichnung)).findFirst();
    }

    private static List<Eintrag> lade() {
        List<Eintrag> liste = new ArrayList<>();
        try (InputStream in = new ClassPathResource("profile/profiltabelle.csv").getInputStream();
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String zeile;
            while ((zeile = r.readLine()) != null) {
                if (zeile.isBlank() || zeile.startsWith("#")) {
                    continue;
                }
                String[] t = zeile.split(";");
                if (t.length != 6) {
                    throw new IllegalStateException("Profiltabelle: Zeile nicht lesbar: " + zeile);
                }
                liste.add(new Eintrag(Profilform.valueOf(t[0].trim()), t[1].trim(), new BigDecimal(t[2].trim()), new BigDecimal(t[3].trim()),
                        new BigDecimal(t[4].trim()), new BigDecimal(t[5].trim())));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Die Profiltabelle konnte nicht gelesen werden.", e);
        }
        return liste;
    }
}
