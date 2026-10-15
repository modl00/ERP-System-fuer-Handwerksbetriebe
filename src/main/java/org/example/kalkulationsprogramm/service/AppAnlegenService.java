package org.example.kalkulationsprogramm.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import org.example.kalkulationsprogramm.controller.KundeDuplikatException;
import org.example.kalkulationsprogramm.domain.Anrede;
import org.example.kalkulationsprogramm.domain.Funktion;
import org.example.kalkulationsprogramm.domain.FunktionsAktion;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.dto.Anfrage.AnfrageErstellenDto;
import org.example.kalkulationsprogramm.dto.Anfrage.AnfrageResponseDto;
import org.example.kalkulationsprogramm.dto.Kunde.KundeCreateRequestDto;
import org.example.kalkulationsprogramm.dto.Kunde.KundeDuplikatResponseDto;
import org.example.kalkulationsprogramm.event.EmailAddressChangedEvent;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/**
 * Kunden und Anfragen in der Handy-App anlegen (Issue #117). Der Mitarbeiter kommt über den Zugangslink (Token); geprüft wird das Recht
 * „Anlegen“ an der Funktion {@link Funktion#KUNDEN} beziehungsweise {@link Funktion#ANFRAGEN} (Rechteverwaltung, je Abteilung). Die App darf nur das
 * Nötige senden: Alles Weitere (Kundennummer, Datum, Status) bestimmt der Server. Die Abfolge beim Kunden entspricht dem Anlegen am PC (Duplikat-Prüfung,
 * Kundennummer, Ansprechpartner, E-Mail-Abgleich), die Anfrage entsteht über denselben {@link AnfrageService}.
 */
@Service
@RequiredArgsConstructor
public class AppAnlegenService {

    private final FunktionsRechteService rechte;
    private final KundeRepository kundeRepository;
    private final KundennummerService kundennummerService;
    private final KundeDuplikatService duplikatService;
    private final KundeAnsprechpartnerService ansprechpartnerService;
    private final AnfrageService anfrageService;
    private final ApplicationEventPublisher eventPublisher;

    /** Der neu angelegte Kunde in der Kurzform, die die App braucht. */
    public record KundeKurz(Long id, String name, String kundennummer, String ort, String hinweis) { }

    /**
     * @param bestaetigt der Mitarbeiter hat die möglichen Duplikate gesehen und will den Kunden trotzdem anlegen
     * @throws KundeDuplikatException bei möglichen Duplikaten ohne Bestätigung
     */
    @Transactional
    public KundeKurz legeKundeAn(String token, KundeCreateRequestDto dto, boolean bestaetigt) {
        rechte.verlangeFuerToken(token, Funktion.KUNDEN, FunktionsAktion.ANLEGEN);
        if (dto == null || !StringUtils.hasText(dto.getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte einen Namen angeben.");
        }
        Set<String> emails = normalisiere(dto.getKundenEmails());
        Set<String> vergeben = new LinkedHashSet<>();
        if (!bestaetigt) {
            KundeDuplikatResponseDto duplikate = duplikatService.findeDuplikate(emails.stream().findFirst().orElse(null), dto.getTelefon(), dto.getMobiltelefon(),
                    dto.getName(), dto.getPlz(), dto.getStrasse());
            if (duplikate.getDuplikate() != null && !duplikate.getDuplikate().isEmpty()) {
                throw new KundeDuplikatException(duplikate);
            }
        }
        // Eine E-Mail-Adresse gehört nur zu einem Kunden. Wird trotz Duplikat-Hinweis angelegt, bleibt die schon vergebene Adresse beim anderen Kunden
        for (String e : emails) {
            if (kundeRepository.existsByKundenEmail(e)) {
                vergeben.add(e);
            }
        }
        emails.removeAll(vergeben);
        Kunde kunde = new Kunde();
        kunde.setKundennummer(kundennummerService.reserviereNaechsteKundennummer());
        kunde.setName(dto.getName().trim());
        kunde.setAnrede(Anrede.fromString(dto.getAnrede()));
        kunde.setAnsprechspartner(leerZuNull(dto.getAnsprechspartner()));
        kunde.setStrasse(leerZuNull(dto.getStrasse()));
        kunde.setPlz(leerZuNull(dto.getPlz()));
        kunde.setOrt(leerZuNull(dto.getOrt()));
        kunde.setTelefon(leerZuNull(dto.getTelefon()));
        kunde.setMobiltelefon(leerZuNull(dto.getMobiltelefon()));
        kunde.setKundenEmails(new ArrayList<>(emails));
        Kunde gespeichert = kundeRepository.save(kunde);
        ansprechpartnerService.ausKundenformular(gespeichert, dto.getAnsprechspartner());
        if (!emails.isEmpty()) {
            eventPublisher.publishEvent(EmailAddressChangedEvent.forNewEntity(EmailAddressChangedEvent.EntityType.KUNDE, gespeichert.getId(), new ArrayList<>(emails)));
        }
        String hinweis = vergeben.isEmpty() ? null : (vergeben.size() == 1 ? "Die E-Mail-Adresse " : "Die E-Mail-Adressen ") + String.join(", ", vergeben)
                + (vergeben.size() == 1 ? " gehört schon zu einem anderen Kunden und wurde nicht übernommen." : " gehören schon zu anderen Kunden und wurden nicht übernommen.");
        return new KundeKurz(gespeichert.getId(), gespeichert.getName(), gespeichert.getKundennummer(), gespeichert.getOrt(), hinweis);
    }

    /** Die Angaben einer neuen Anfrage aus der App: nur diese Felder werden übernommen. */
    public record AnfrageEingabe(String bauvorhaben, Long kundenId, String projektStrasse, String projektPlz, String projektOrt, String kurzbeschreibung,
            java.math.BigDecimal betrag) { }

    @Transactional
    public AnfrageResponseDto legeAnfrageAn(String token, AnfrageEingabe e) {
        rechte.verlangeFuerToken(token, Funktion.ANFRAGEN, FunktionsAktion.ANLEGEN);
        if (e == null || !StringUtils.hasText(e.bauvorhaben())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte das Bauvorhaben angeben.");
        }
        if (e.bauvorhaben().length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Das Bauvorhaben ist zu lang (höchstens 255 Zeichen).");
        }
        if (e.betrag() != null && e.betrag().signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Der Betrag darf nicht negativ sein.");
        }
        Kunde kunde = e.kundenId() == null ? null : kundeRepository.findById(e.kundenId()).orElse(null);
        if (kunde == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte einen Kunden wählen.");
        }
        AnfrageErstellenDto dto = new AnfrageErstellenDto();
        dto.setBauvorhaben(e.bauvorhaben().trim());
        dto.setKundenId(kunde.getId());
        dto.setProjektStrasse(leerZuNull(e.projektStrasse()));
        dto.setProjektPlz(leerZuNull(e.projektPlz()));
        dto.setProjektOrt(leerZuNull(e.projektOrt()));
        dto.setKurzbeschreibung(leerZuNull(e.kurzbeschreibung()));
        dto.setBetrag(e.betrag());
        return anfrageService.erstelleAnfrage(dto);
    }

    private static String leerZuNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private static Set<String> normalisiere(java.util.List<String> emails) {
        Set<String> r = new LinkedHashSet<>();
        if (emails != null) {
            for (String e : emails) {
                if (StringUtils.hasText(e)) {
                    r.add(e.trim().toLowerCase(Locale.GERMAN));
                }
            }
        }
        return r;
    }
}
