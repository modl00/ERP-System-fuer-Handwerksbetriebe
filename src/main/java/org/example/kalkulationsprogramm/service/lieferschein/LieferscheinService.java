package org.example.kalkulationsprogramm.service.lieferschein;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.lieferschein.Lieferschein;
import org.example.kalkulationsprogramm.domain.lieferschein.LieferscheinPosition;
import org.example.kalkulationsprogramm.dto.lieferschein.LieferscheinAnlegenDto;
import org.example.kalkulationsprogramm.dto.lieferschein.LieferscheinDto;
import org.example.kalkulationsprogramm.dto.lieferschein.LieferscheinKopieDto;
import org.example.kalkulationsprogramm.dto.lieferschein.LieferscheinUnterschriftDto;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.repository.lieferschein.LieferscheinRepository;
import org.example.kalkulationsprogramm.service.NummernkreisService;
import org.example.kalkulationsprogramm.service.wartung.WartungUnterschriftPruefer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/**
 * Lieferscheine an Kunden (Issue #128): erstellen, vom Empfänger unterschreiben lassen, als PDF ansehen und als Kopie per E-Mail senden. Die Rechte prüft der
 * {@link AppLieferscheinService}; hier steht die Fachlogik.
 *
 * <p>Regeln: Ein unterschriebener Lieferschein ist fertig und wird nicht mehr geändert oder gelöscht. Dieselbe {@code clientId} ergibt immer denselben
 * Lieferschein (sicheres Wiederholen nach Verbindungsabbruch). Eine mitgeschickte Nummer muss eine für den Mitarbeiter reservierte sein, sonst vergibt der
 * Server die nächste. Die E-Mail geht nur nach ausdrücklicher Bestätigung.</p>
 */
@Service
@RequiredArgsConstructor
public class LieferscheinService {

    private final LieferscheinRepository lieferscheine;
    private final KundeRepository kunden;
    private final ProjektRepository projekte;
    private final NummernkreisService nummernkreise;
    private final NummernbereichService nummernbereiche;
    private final LieferscheinMailVersand mailVersand;
    private final LieferscheinPdfService pdfService;

    @Transactional
    public LieferscheinDto anlegen(Mitarbeiter m, LieferscheinAnlegenDto dto) {
        if (dto.clientId() != null && !dto.clientId().isBlank()) {
            var vorhanden = lieferscheine.findByClientId(dto.clientId().strip());
            if (vorhanden.isPresent()) {
                return dto(vorhanden.get());
            }
        }
        if (dto.kundeId() == null && dto.projektId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte einen Kunden oder ein Projekt angeben.");
        }
        Projekt projekt = dto.projektId() == null ? null : projekte.findById(dto.projektId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Das Projekt wurde nicht gefunden."));
        Kunde kunde = dto.kundeId() != null ? kunden.findById(dto.kundeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Der Kunde wurde nicht gefunden.")) : projekt.getKundenId();
        if (kunde == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Das Projekt hat keinen Kunden. Bitte den Kunden angeben.");
        }
        if (dto.unterschriftBild() != null && !dto.unterschriftBild().isBlank()) {
            WartungUnterschriftPruefer.pruefe(dto.unterschriftBild());
        }

        Lieferschein l = new Lieferschein();
        l.setNummer(nummerVergeben(m, dto.nummer()));
        l.setClientId(dto.clientId() == null || dto.clientId().isBlank() ? null : dto.clientId().strip());
        l.setKunde(kunde);
        l.setProjekt(projekt);
        l.setEmpfaengerName(dto.empfaengerName().strip());
        l.setDatum(dto.datum() == null ? LocalDate.now() : dto.datum());
        l.setBemerkung(dto.bemerkung() == null || dto.bemerkung().isBlank() ? null : dto.bemerkung().strip());
        l.setErstelltVon(m);
        l.setErstelltAm(LocalDateTime.now());
        int sortierung = 0;
        for (LieferscheinAnlegenDto.Position p : dto.positionen()) {
            LieferscheinPosition pos = new LieferscheinPosition();
            pos.setLieferschein(l);
            pos.setSortierung(sortierung++);
            pos.setBezeichnung(p.bezeichnung().strip());
            pos.setMenge(p.menge());
            pos.setEinheit(p.einheit() == null || p.einheit().isBlank() ? null : p.einheit().strip());
            l.getPositionen().add(pos);
        }
        if (dto.unterschriftBild() != null && !dto.unterschriftBild().isBlank()) {
            l.setUnterschriftBild(dto.unterschriftBild());
            l.setUnterschriftName(l.getEmpfaengerName());
            l.setUnterschriebenAm(LocalDateTime.now());
        }
        return dto(lieferscheine.save(l));
    }

    private String nummerVergeben(Mitarbeiter m, String mitgeschickt) {
        if (mitgeschickt != null && !mitgeschickt.isBlank()) {
            String nummer = mitgeschickt.strip();
            if (lieferscheine.existsByNummer(nummer)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Die Nummer " + nummer + " ist schon einem Lieferschein zugeordnet.");
            }
            nummernbereiche.verwenden(m, NummernkreisService.LIEFERSCHEIN, nummer);
            return nummer;
        }
        return nummernkreise.naechsteNummer(NummernkreisService.LIEFERSCHEIN, nr -> lieferscheine.existsByNummer(nr))
                .orElseThrow(() -> new IllegalStateException("Der Nummernkreis für Lieferscheine fehlt."));
    }

    @Transactional
    public LieferscheinDto unterschreiben(Long id, LieferscheinUnterschriftDto dto) {
        Lieferschein l = laden(id);
        if (l.istUnterschrieben()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dieser Lieferschein ist schon unterschrieben und wird nicht mehr geändert.");
        }
        WartungUnterschriftPruefer.pruefe(dto.bild());
        l.setUnterschriftBild(dto.bild());
        l.setUnterschriftName(dto.name() == null || dto.name().isBlank() ? l.getEmpfaengerName() : dto.name().strip());
        l.setUnterschriebenAm(LocalDateTime.now());
        return dto(lieferscheine.save(l));
    }

    @Transactional
    public void loeschen(Long id) {
        Lieferschein l = laden(id);
        if (l.istUnterschrieben()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ein unterschriebener Lieferschein wird nicht gelöscht.");
        }
        lieferscheine.delete(l);
    }

    @Transactional(readOnly = true)
    public List<LieferscheinDto> imProjekt(Long projektId) {
        return lieferscheine.findByProjektIdOrderByDatumDescIdDesc(projektId).stream().map(this::dto).toList();
    }

    @Transactional(readOnly = true)
    public LieferscheinDto holen(Long id) {
        return dto(laden(id));
    }

    @Transactional(readOnly = true)
    public LieferscheinPdfService.PdfDatei pdf(Long id) {
        return pdfService.erstelle(laden(id));
    }

    /** Verschickt die Kopie, aber nur mit Bestätigung. Das PDF ist nur beim unterschriebenen Lieferschein eine „Kopie“, ein Entwurf wird nicht versendet. */
    @Transactional
    public LieferscheinDto kopieSenden(Long id, LieferscheinKopieDto dto) {
        if (!dto.bestaetigt()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte den Versand an " + dto.an() + " bestätigen.");
        }
        Lieferschein l = laden(id);
        if (!l.istUnterschrieben()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Der Lieferschein ist noch nicht unterschrieben. Bitte zuerst unterschreiben lassen.");
        }
        LieferscheinPdfService.PdfDatei pdf = pdfService.erstelle(l);
        mailVersand.sende(dto.an().strip(), "Lieferschein " + l.getNummer(),
                "<p>Guten Tag,</p><p>anbei erhalten Sie die Kopie Ihres Lieferscheins " + org.springframework.web.util.HtmlUtils.htmlEscape(l.getNummer()) + ".</p><p>Mit freundlichen Grüßen</p>",
                pdf.inhalt(), pdf.dateiname());
        l.setKopieAn(dto.an().strip());
        l.setKopieGesendetAm(LocalDateTime.now());
        return dto(lieferscheine.save(l));
    }

    private Lieferschein laden(Long id) {
        return lieferscheine.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Der Lieferschein wurde nicht gefunden."));
    }

    LieferscheinDto dto(Lieferschein l) {
        return new LieferscheinDto(l.getId(), l.getNummer(), l.getClientId(), l.getKunde() == null ? null : l.getKunde().getId(), l.getKunde() == null ? null : l.getKunde().getName(),
                l.getProjekt() == null ? null : l.getProjekt().getId(), l.getProjekt() == null ? null : l.getProjekt().getBauvorhaben(), l.getEmpfaengerName(), l.getDatum(),
                l.getBemerkung(), l.getPositionen().stream().map(p -> new LieferscheinDto.Position(p.getBezeichnung(), p.getMenge(), p.getEinheit())).toList(),
                l.istUnterschrieben(), l.getUnterschriftName(), l.getUnterschriebenAm(), l.getKopieAn(), l.getKopieGesendetAm());
    }
}
