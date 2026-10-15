package org.example.kalkulationsprogramm.service.wartung;

import lombok.AllArgsConstructor;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.wartung.WartungBereich;
import org.example.kalkulationsprogramm.domain.wartung.WartungErgebnis;
import org.example.kalkulationsprogramm.domain.wartung.WartungFeldtyp;
import org.example.kalkulationsprogramm.domain.wartung.WartungMitarbeiterUnterschrift;
import org.example.kalkulationsprogramm.domain.wartung.WartungObjekt;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokoll;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokollAbschnitt;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokollDatenfeld;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokollModul;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokollPruefpunkt;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokollVerlauf;
import org.example.kalkulationsprogramm.dto.wartung.WartungVerlaufDto;
import org.example.kalkulationsprogramm.repository.wartung.WartungProtokollVerlaufRepository;
import org.example.kalkulationsprogramm.domain.wartung.WartungStatus;
import org.example.kalkulationsprogramm.domain.wartung.WartungTuer;
import org.example.kalkulationsprogramm.dto.wartung.WartungDatenfeldDefinitionDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungDatenfeldNeuDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungDatenfeldWertDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungInhaltAenderungDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungProtokollAnlegenDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungProtokollDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungProtokollKopfDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungProtokollartSammelDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungProtokollListeneintragDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungPruefpunktErgebnisDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungPruefpunktNeuDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungPruefpunktTextDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungUnterschriftDto;
import org.example.kalkulationsprogramm.mapper.wartung.WartungMapper;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungMitarbeiterUnterschriftRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungObjektRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungProtokollAbschnittRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungProtokollDatenfeldRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungProtokollPruefpunktRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungProtokollRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungTuerRepository;
import org.example.kalkulationsprogramm.service.wartung.WartungModulAuswahlPruefer.GewaehltesModul;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lebenslauf eines Wartungsprotokolls: anlegen, ausfüllen, unterschreiben, abschließen, archivieren.
 *
 * <p>Regeln: Inhalte lassen sich nur ändern, solange das Protokoll offen oder in Arbeit ist. Ein
 * Abschluss verlangt, dass alle Prüfpunkte bewertet sind und der Techniker unterschrieben hat. Beim
 * Abschluss wird der Wartungsplan der Tür fortgeschrieben.</p>
 */
@Service
@AllArgsConstructor
public class WartungProtokollService {

    private final WartungProtokollRepository protokollRepository;
    private final WartungProtokollAbschnittRepository abschnittRepository;
    private final WartungProtokollPruefpunktRepository pruefpunktRepository;
    private final WartungProtokollDatenfeldRepository datenfeldRepository;
    private final WartungObjektRepository objektRepository;
    private final WartungTuerRepository tuerRepository;
    private final KundeRepository kundeRepository;
    private final MitarbeiterRepository mitarbeiterRepository;
    private final ProjektRepository projektRepository;
    private final WartungModulAuswahlPruefer auswahlPruefer;
    private final WartungInhaltKopierer inhaltKopierer;
    private final WartungNummernService nummernService;
    private final WartungMapper mapper;
    private final WartungMitarbeiterUnterschriftRepository unterschriftRepository;
    private final ProjektDokumentRepository projektDokumentRepository;
    private final org.springframework.context.ApplicationEventPublisher ereignisse;
    private final WartungProtokollVerlaufRepository verlaufRepository;

    // ── Lesen ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public WartungProtokollDto laden(Long id) {
        return alsDto(lade(id));
    }

    /**
     * Sucht Protokolle für die Liste. Alle Filter sind optional. Ohne Statusfilter bleibt das Archiv
     * ausgeblendet, außer {@code mitArchiv} ist gesetzt.
     */
    @Transactional(readOnly = true)
    public Page<WartungProtokollListeneintragDto> suche(
            Long kundeId, Long objektId, Long tuerId, WartungStatus status, boolean mitArchiv,
            LocalDate datumVon, LocalDate datumBis, String text, Pageable pageable) {
        if (datumVon != null && datumBis != null && datumVon.isAfter(datumBis)) {
            throw falsch("Das Datum „von“ liegt nach dem Datum „bis“.");
        }
        String muster = (text == null || text.isBlank()) ? null : "%" + maskiereLike(text.trim().toLowerCase(Locale.ROOT)) + "%";
        WartungStatus ausschluss = (status == null && !mitArchiv) ? WartungStatus.ARCHIVIERT : null;
        return protokollRepository.suche(kundeId, objektId, tuerId, status, ausschluss, datumVon, datumBis, muster, pageable);
    }

    // ── Anlegen ─────────────────────────────────────────────────────────────

    /** Wie {@link #anlegen(WartungProtokollAnlegenDto, Long)}, ohne dass eine gemerkte Unterschrift übernommen wird. */
    @Transactional
    public WartungProtokollDto anlegen(WartungProtokollAnlegenDto dto) {
        return anlegen(dto, null);
    }

    /**
     * Legt einen Feldeinsatz an.
     *
     * @param aufruferMitarbeiterId der Mitarbeiter des angemeldeten Benutzers (oder {@code null}): Nur wenn er selbst der Techniker
     *                              des Feldeinsatzes ist, kommt seine gemerkte Unterschrift mit
     */
    @Transactional
    public WartungProtokollDto anlegen(WartungProtokollAnlegenDto dto, Long aufruferMitarbeiterId) {
        Kunde kunde = kundeRepository.findById(dto.kundeId())
                .orElseThrow(() -> nichtGefunden("Der Kunde wurde nicht gefunden."));

        WartungTuer tuer = null;
        WartungObjekt objekt = null;
        if (dto.tuerId() != null) {
            tuer = tuerRepository.findMitSperre(dto.tuerId()).orElseThrow(() -> nichtGefunden("Die Tür wurde nicht gefunden."));
            if (!tuer.isAktiv()) {
                throw falsch("Diese Tür ist nicht mehr aktiv.");
            }
            objekt = tuer.getObjekt();
            if (dto.objektId() != null && !objekt.getId().equals(dto.objektId())) {
                throw falsch("Die Tür gehört nicht zum gewählten Objekt.");
            }
        } else if (dto.objektId() != null) {
            objekt = objektRepository.findById(dto.objektId()).orElseThrow(() -> nichtGefunden("Das Objekt wurde nicht gefunden."));
        }
        if (objekt != null) {
            if (!objekt.getKunde().getId().equals(kunde.getId())) {
                throw falsch("Das Objekt gehört nicht zu diesem Kunden.");
            }
            if (!objekt.isAktiv()) {
                throw falsch("Dieses Objekt ist nicht mehr aktiv.");
            }
        }

        // Ein zweiter Feldeinsatz neben einem noch offenen derselben Wartung ist fast immer ein Versehen
        if (tuer != null) {
            protokollRepository.findFirstByTuerIdAndStatusInOrderByIdDesc(tuer.getId(), List.of(WartungStatus.OFFEN, WartungStatus.IN_BEARBEITUNG))
                    .ifPresent(offen -> {
                        throw konflikt("Für diese Wartung läuft schon ein Feldeinsatz (" + offen.getNummer()
                                + "). Bitte den öffnen oder abschließen.");
                    });
        }

        WartungProtokoll vorlage = ladeVorlage(dto.vorlageProtokollId(), tuer);
        if (vorlage == null && (dto.module() == null || dto.module().isEmpty())) {
            throw falsch("Bitte mindestens ein Modul wählen.");
        }

        // Das Projekt der Wartung gilt, solange nichts anderes gewählt wurde. Ein ausdrücklich gewähltes
        // Projekt hat Vorrang und ändert nur diesen Feldeinsatz, nicht die Wartung.
        Projekt projekt = dto.projektId() != null ? ladeProjekt(dto.projektId(), kunde)
                : (tuer != null && tuer.getProjekt() != null ? tuer.getProjekt() : (vorlage != null ? vorlage.getProjekt() : null));
        Mitarbeiter techniker = ladeTechniker(dto.technikerId());
        List<GewaehltesModul> module = vorlage == null ? auswahlPruefer.pruefe(dto.module(), true) : List.of();

        LocalDate datum = dto.datum() != null ? dto.datum() : LocalDate.now();
        int intervall = vorlage != null && dto.intervallMonate() == null && (tuer == null || tuer.getIntervallMonate() == null)
                ? vorlage.getIntervallMonate()
                : bestimmeIntervall(dto.intervallMonate(), tuer, module);

        WartungProtokoll protokoll = new WartungProtokoll();
        protokoll.setNummer(nummernService.naechsteNummer(LocalDate.now()));
        protokoll.setKunde(kunde);
        protokoll.setObjekt(objekt);
        protokoll.setTuer(tuer);
        protokoll.setProjekt(projekt);
        protokoll.setTechniker(techniker);
        protokoll.setTitel(leerZuNull(dto.titel()));
        protokoll.setDatum(datum);
        protokoll.setIntervallMonate(intervall);
        protokoll.setNaechsteWartung(datum.plusMonths(intervall));
        protokoll.setBemerkungen(leerZuNull(dto.bemerkungen()));
        protokoll.setAnsprechpartnerName(leerZuNull(dto.ansprechpartnerName()) != null ? leerZuNull(dto.ansprechpartnerName())
                : (vorlage != null ? vorlage.getAnsprechpartnerName() : null));
        if (vorlage != null) {
            protokoll.setVorlageProtokollId(vorlage.getId());
            vorlage.getModule().forEach(m -> protokoll.getModule().add(new WartungProtokollModul(
                    m.getModulId(), m.getModulKey(), m.getModulName(), m.getUntertypSchluessel(), m.getUntertypLabel())));
        }
        for (GewaehltesModul gm : module) {
            protokoll.getModule().add(new WartungProtokollModul(
                    gm.modul().getId(),
                    gm.modul().getModulKey(),
                    gm.modul().getName(),
                    gm.untertyp() == null ? null : gm.untertyp().getSchluessel(),
                    gm.untertyp() == null ? null : gm.untertyp().getLabel()));
        }
        uebernehmeGespeicherteUnterschrift(protokoll, aufruferMitarbeiterId);
        protokollRepository.saveAndFlush(protokoll);

        if (vorlage != null) {
            inhaltKopierer.kopiereAusProtokoll(vorlage.getId(), protokoll.getId(), !Boolean.FALSE.equals(dto.werteUebernehmen()));
        } else {
            Map<String, WartungErgebnis> vorherige = tuer == null ? Map.of() : vorherigeErgebnisse(tuer.getId());
            inhaltKopierer.kopiere(protokoll.getId(), module, vorherige);
        }
        return alsDto(protokoll);
    }

    /** Die Vorlage muss eine abgeschlossene Prüfung <b>derselben Wartung</b> sein. Sie wird nur gelesen. */
    private WartungProtokoll ladeVorlage(Long vorlageId, WartungTuer tuer) {
        if (vorlageId == null) {
            return null;
        }
        WartungProtokoll vorlage = protokollRepository.findById(vorlageId).orElseThrow(() -> nichtGefunden("Die Vorlage wurde nicht gefunden."));
        if (tuer == null || vorlage.getTuer() == null || !vorlage.getTuer().getId().equals(tuer.getId())) {
            throw falsch("Die Vorlage gehört zu einer anderen Wartung.");
        }
        if (vorlage.getAbgeschlossenAm() == null) {
            throw falsch("Nur eine abgeschlossene Prüfung lässt sich als Vorlage verwenden.");
        }
        return vorlage;
    }

    /**
     * Die einmal gespeicherte Unterschrift des Technikers kommt als <b>Kopie</b> ins Protokoll. Ändert er sie später,
     * bleiben bestehende und abgeschlossene Protokolle so, wie sie unterschrieben wurden. Das geschieht nur, wenn der
     * Techniker den Feldeinsatz selbst startet: Wählt das Büro einen Kollegen, unterschreibt dieser selbst.
     */
    private void uebernehmeGespeicherteUnterschrift(WartungProtokoll protokoll, Long aufruferMitarbeiterId) {
        if (protokoll.getTechniker() == null || protokoll.getTechniker().getId() == null
                || !protokoll.getTechniker().getId().equals(aufruferMitarbeiterId)) {
            return;
        }
        unterschriftRepository.findById(protokoll.getTechniker().getId()).ifPresent(u -> {
            protokoll.setUnterschriftTechniker(u.getBild());
            protokoll.setUnterschriftTechnikerName(u.getName());
        });
    }

    /** Die Protokollarten, die sich auf einmal setzen lassen (dieselben wie in der Auswahl am Protokoll). */
    public static final List<String> PROTOKOLLARTEN = List.of("Wartungsprotokoll", "Instandhaltungsprotokoll", "Prüfprotokoll", "Inspektionsprotokoll");

    /**
     * Setzt die Protokollart mehrerer Protokolle. Abgeschlossene oder archivierte Protokolle bleiben unverändert (erst wieder öffnen), unbekannte
     * Nummern werden gemeldet; jedes Protokoll wird einzeln gesperrt und gespeichert, ein Fehlschlag bei einem trifft die anderen nicht.
     */
    @Transactional
    public WartungProtokollartSammelDto.Ergebnis protokollartSetzen(List<Long> ids, String protokollTyp) {
        String typ = protokollTyp == null ? "" : protokollTyp.strip();
        if (!PROTOKOLLARTEN.contains(typ)) {
            throw falsch("Diese Protokollart gibt es nicht. Möglich: " + String.join(", ", PROTOKOLLARTEN) + ".");
        }
        if (ids == null || ids.isEmpty()) {
            throw falsch("Bitte mindestens eine Wartung auswählen.");
        }
        int geaendert = 0;
        List<WartungProtokollartSammelDto.Uebersprungen> uebersprungen = new java.util.ArrayList<>();
        for (Long id : new java.util.LinkedHashSet<>(ids)) {
            WartungProtokoll p = id == null ? null : protokollRepository.findMitSperre(id).orElse(null);
            if (p == null) {
                uebersprungen.add(new WartungProtokollartSammelDto.Uebersprungen(id, null, "Die Wartung gibt es nicht (mehr)."));
            } else if (!p.getStatus().istInhaltBearbeitbar()) {
                uebersprungen.add(new WartungProtokollartSammelDto.Uebersprungen(id, p.getNummer(),
                        "Ist „" + anzeige(p.getStatus()).toLowerCase(Locale.GERMAN) + "“ und lässt sich nicht ändern. Bitte zuerst wieder öffnen."));
            } else if (!typ.equals(p.getProtokollTyp())) {
                p.setProtokollTyp(typ);
                protokollRepository.save(p);
                geaendert++;
            }
        }
        return new WartungProtokollartSammelDto.Ergebnis(geaendert, uebersprungen);
    }

    // ── Bearbeiten ──────────────────────────────────────────────────────────

    @Transactional
    public WartungProtokollDto kopfAendern(Long id, WartungProtokollKopfDto dto) {
        WartungProtokoll protokoll = ladeZumSchreiben(id);
        pruefeBearbeitbar(protokoll);

        protokoll.setTitel(leerZuNull(dto.titel()));
        protokoll.setProtokollTyp(dto.protokollTyp().trim());
        protokoll.setDatum(dto.datum());
        protokoll.setIntervallMonate(dto.intervallMonate());
        protokoll.setNaechsteWartung(dto.datum().plusMonths(dto.intervallMonate()));
        Long bisherigerTechniker = protokoll.getTechniker() == null ? null : protokoll.getTechniker().getId();
        if (!java.util.Objects.equals(bisherigerTechniker, dto.technikerId())) {
            // Nur bei einem Wechsel prüfen: Ein später ausgeschiedener Techniker darf stehen bleiben.
            protokoll.setTechniker(ladeTechniker(dto.technikerId()));
        }
        protokoll.setProjekt(ladeProjekt(dto.projektId(), protokoll.getKunde()));
        objektZuordnen(protokoll, dto.objektId());
        protokoll.setBemerkungen(leerZuNull(dto.bemerkungen()));
        protokoll.setAnsprechpartnerName(leerZuNull(dto.ansprechpartnerName()));
        markiereInArbeit(protokoll);
        return alsDto(protokollRepository.save(protokoll));
    }

    /**
     * Ordnet den Feldeinsatz einem Objekt des Kunden zu (oder löst die Zuordnung). Gehört der Feldeinsatz zu einer
     * Wartung, ergibt sich das Objekt aus ihr und lässt sich hier nicht ändern. Ein inzwischen deaktiviertes Objekt
     * darf stehen bleiben, neu gewählt werden kann es nicht.
     */
    private void objektZuordnen(WartungProtokoll protokoll, Long objektId) {
        WartungTuer wartung = protokoll.getTuer();
        if (wartung != null) {
            WartungObjekt vonWartung = wartung.getObjekt();
            if (objektId != null && !objektId.equals(vonWartung.getId())) {
                throw konflikt("Das Objekt ergibt sich aus der Wartung „" + wartung.getBezeichnung() + "“ (Objekt „"
                        + vonWartung.getBezeichnung() + "“). Es lässt sich hier nicht ändern.");
            }
            protokoll.setObjekt(vonWartung);
            return;
        }
        Long bisher = protokoll.getObjekt() == null ? null : protokoll.getObjekt().getId();
        if (java.util.Objects.equals(bisher, objektId)) {
            return;
        }
        if (objektId == null) {
            protokoll.setObjekt(null);
            return;
        }
        WartungObjekt objekt = objektRepository.findById(objektId).orElseThrow(() -> nichtGefunden("Das Objekt wurde nicht gefunden."));
        if (!objekt.getKunde().getId().equals(protokoll.getKunde().getId())) {
            throw falsch("Das Objekt gehört nicht zu diesem Kunden.");
        }
        if (!objekt.isAktiv()) {
            throw falsch("Dieses Objekt ist nicht mehr aktiv.");
        }
        protokoll.setObjekt(objekt);
    }

    @Transactional
    public WartungInhaltAenderungDto pruefpunktBewerten(Long protokollId, Long pruefpunktId, WartungPruefpunktErgebnisDto dto) {
        WartungProtokoll protokoll = ladeZumSchreiben(protokollId);
        pruefeBearbeitbar(protokoll);
        WartungProtokollPruefpunkt punkt = pruefpunktRepository.findByIdAndProtokollId(pruefpunktId, protokollId)
                .orElseThrow(() -> nichtGefunden("Der Prüfpunkt wurde nicht gefunden."));

        punkt.setErgebnis(dto.ergebnis());
        punkt.setBemerkung(leerZuNull(dto.bemerkung()));
        pruefpunktRepository.save(punkt);
        markiereInArbeit(protokoll);

        WartungProtokollDto.Statistik statistik = mapper.statistik(
                pruefpunktRepository.findByProtokollIdOrderBySortierungAscIdAsc(protokollId));
        return new WartungInhaltAenderungDto(protokoll.getStatus(), statistik,
                new WartungProtokollDto.Pruefpunkt(punkt.getId(), punkt.getAbschnittId(), punkt.getModulKey(), punkt.getLabel(),
                        punkt.getErgebnis(), punkt.getBemerkung(), punkt.getVorherigesErgebnis(), punkt.getSortierung()),
                null);
    }

    @Transactional
    public WartungInhaltAenderungDto datenfeldSetzen(Long protokollId, Long datenfeldId, WartungDatenfeldWertDto dto) {
        WartungProtokoll protokoll = ladeZumSchreiben(protokollId);
        pruefeBearbeitbar(protokoll);
        WartungProtokollDatenfeld feld = datenfeldRepository.findByIdAndProtokollId(datenfeldId, protokollId)
                .orElseThrow(() -> nichtGefunden("Das Datenfeld wurde nicht gefunden."));

        feld.setWert(pruefeWert(feld, leerZuNull(dto.wert())));
        datenfeldRepository.save(feld);
        markiereInArbeit(protokoll);

        WartungProtokollDto.Statistik statistik = mapper.statistik(
                pruefpunktRepository.findByProtokollIdOrderBySortierungAscIdAsc(protokollId));
        return new WartungInhaltAenderungDto(protokoll.getStatus(), statistik, null,
                new WartungProtokollDto.Datenfeld(feld.getId(), feld.getAbschnittId(), feld.getModulKey(), feld.getLabel(),
                        feld.getFeldtyp(), WartungMapper.optionenListe(feld.getOptionen()), feld.getWert(), feld.getSortierung()));
    }

    @Transactional
    public WartungProtokollDto unterschreiben(Long id, WartungUnterschriftDto dto) {
        WartungProtokoll protokoll = ladeZumSchreiben(id);
        pruefeBearbeitbar(protokoll);

        boolean loeschen = dto.bild() == null || dto.bild().isBlank();
        String bild = null;
        if (!loeschen) {
            WartungUnterschriftPruefer.pruefe(dto.bild());
            bild = dto.bild();
            if (leerZuNull(dto.name()) == null) {
                throw falsch("Bitte den Namen unter der Unterschrift eintragen.");
            }
        }
        String name = loeschen ? null : dto.name().trim();
        if (dto.rolle() == WartungUnterschriftDto.Rolle.TECHNIKER) {
            protokoll.setUnterschriftTechniker(bild);
            protokoll.setUnterschriftTechnikerName(name);
        } else {
            protokoll.setUnterschriftKunde(bild);
            protokoll.setUnterschriftKundeName(name);
        }
        markiereInArbeit(protokoll);
        return alsDto(protokollRepository.save(protokoll));
    }

    /** Setzt die gespeicherte Unterschrift des angegebenen Mitarbeiters als Unterschrift des Technikers ein (als Kopie). */
    @Transactional
    public WartungProtokollDto gespeicherteUnterschriftVerwenden(Long id, Long mitarbeiterId) {
        WartungProtokoll protokoll = ladeZumSchreiben(id);
        pruefeBearbeitbar(protokoll);
        if (mitarbeiterId == null) {
            throw konflikt("Ihr Benutzer ist keinem Mitarbeiter zugeordnet. Bitte die Unterschrift neu zeichnen.");
        }
        Long techniker = protokoll.getTechniker() == null ? null : protokoll.getTechniker().getId();
        if (techniker != null && !techniker.equals(mitarbeiterId)) {
            throw konflikt("Die gespeicherte Unterschrift gilt nur für den Mitarbeiter selbst. Dieser Feldeinsatz gehört "
                    + protokoll.getTechniker().getVorname() + " " + protokoll.getTechniker().getNachname() + ".");
        }
        WartungMitarbeiterUnterschrift gespeichert = unterschriftRepository.findById(mitarbeiterId)
                .orElseThrow(() -> nichtGefunden("Es ist noch keine Unterschrift gespeichert. Bitte einmal unterschreiben und speichern."));
        protokoll.setUnterschriftTechniker(gespeichert.getBild());
        protokoll.setUnterschriftTechnikerName(gespeichert.getName());
        markiereInArbeit(protokoll);
        return alsDto(protokollRepository.save(protokoll));
    }

    // ── Prüfpunkte im laufenden Feldeinsatz ─────────────────────────────────

    private static final String ZUSAETZLICHE_PRUEFPUNKTE = "Zusätzliche Prüfpunkte";

    /** Ergänzt einen Prüfpunkt, nur in diesem Protokoll (der Katalog bleibt unverändert). */
    @Transactional
    public WartungProtokollDto pruefpunktHinzufuegen(Long protokollId, WartungPruefpunktNeuDto dto) {
        WartungProtokoll protokoll = ladeZumSchreiben(protokollId);
        pruefeBearbeitbar(protokoll);
        List<WartungProtokollAbschnitt> abschnitte = abschnittRepository.findByProtokollIdOrderBySortierungAscIdAsc(protokollId);
        List<WartungProtokollPruefpunkt> punkte = pruefpunktRepository.findByProtokollIdOrderBySortierungAscIdAsc(protokollId);
        if (punkte.size() >= MAX_PRUEFPUNKTE_JE_PROTOKOLL) {
            throw falsch("Ein Feldeinsatz kann höchstens " + MAX_PRUEFPUNKTE_JE_PROTOKOLL + " Prüfpunkte haben.");
        }

        WartungProtokollAbschnitt ziel;
        if (dto.abschnittId() != null) {
            ziel = abschnitte.stream().filter(a -> a.getId().equals(dto.abschnittId()) && a.getBereich() == WartungBereich.PRUEFPUNKTE)
                    .findFirst().orElseThrow(() -> falsch("Dieser Abschnitt gehört nicht zu den Prüfpunkten dieses Feldeinsatzes."));
        } else {
            ziel = abschnitte.stream()
                    .filter(a -> a.getBereich() == WartungBereich.PRUEFPUNKTE && ZUSAETZLICHE_PRUEFPUNKTE.equals(a.getLabel()))
                    .findFirst().orElseGet(() -> {
                        WartungProtokollAbschnitt neu = new WartungProtokollAbschnitt();
                        neu.setProtokollId(protokollId);
                        neu.setBereich(WartungBereich.PRUEFPUNKTE);
                        neu.setLabel(ZUSAETZLICHE_PRUEFPUNKTE);
                        neu.setSortierung(abschnitte.stream().mapToInt(WartungProtokollAbschnitt::getSortierung).max().orElse(0) + 1);
                        return abschnittRepository.save(neu);
                    });
        }

        WartungProtokollPruefpunkt punkt = new WartungProtokollPruefpunkt();
        punkt.setProtokollId(protokollId);
        punkt.setAbschnittId(ziel.getId());
        punkt.setLabel(dto.label().trim());
        // Am Ende: Die Oberfläche und das PDF gruppieren nach Abschnitt, innerhalb davon gilt die Sortierung
        punkt.setSortierung(punkte.stream().mapToInt(WartungProtokollPruefpunkt::getSortierung).max().orElse(0) + 1);
        pruefpunktRepository.save(punkt);
        markiereInArbeit(protokoll);
        return alsDto(protokollRepository.save(protokoll));
    }

    @Transactional
    public WartungProtokollDto pruefpunktText(Long protokollId, Long pruefpunktId, WartungPruefpunktTextDto dto) {
        WartungProtokoll protokoll = ladeZumSchreiben(protokollId);
        pruefeBearbeitbar(protokoll);
        WartungProtokollPruefpunkt punkt = pruefpunktRepository.findByIdAndProtokollId(pruefpunktId, protokollId)
                .orElseThrow(() -> nichtGefunden("Der Prüfpunkt wurde nicht gefunden."));
        punkt.setLabel(dto.label().trim());
        pruefpunktRepository.save(punkt);
        markiereInArbeit(protokoll);
        return alsDto(protokollRepository.save(protokoll));
    }

    /** Entfernt einen Prüfpunkt aus diesem Feldeinsatz (nicht aus dem Katalog). Ein leerer Abschnitt entfällt mit. */
    @Transactional
    public WartungProtokollDto pruefpunktLoeschen(Long protokollId, Long pruefpunktId) {
        WartungProtokoll protokoll = ladeZumSchreiben(protokollId);
        pruefeBearbeitbar(protokoll);
        WartungProtokollPruefpunkt punkt = pruefpunktRepository.findByIdAndProtokollId(pruefpunktId, protokollId)
                .orElseThrow(() -> nichtGefunden("Der Prüfpunkt wurde nicht gefunden."));
        Long abschnittId = punkt.getAbschnittId();
        pruefpunktRepository.delete(punkt);
        pruefpunktRepository.flush();
        if (abschnittId != null) {
            boolean leer = pruefpunktRepository.findByProtokollIdOrderBySortierungAscIdAsc(protokollId).stream()
                    .noneMatch(p -> abschnittId.equals(p.getAbschnittId()))
                    && datenfeldRepository.findByProtokollIdOrderBySortierungAscIdAsc(protokollId).stream()
                    .noneMatch(d -> abschnittId.equals(d.getAbschnittId()));
            if (leer) {
                abschnittRepository.deleteById(abschnittId);
            }
        }
        markiereInArbeit(protokoll);
        return alsDto(protokollRepository.save(protokoll));
    }

    // ── Datenfelder im laufenden Feldeinsatz ────────────────────────────────

    static final int MAX_DATENFELDER_JE_PROTOKOLL = 200;
    private static final int MAX_OPTIONEN = 50;

    /** Ergänzt ein Datenfeld, nur in diesem Protokoll (der Katalog bleibt unverändert). Es steht am Ende seiner Gruppe. */
    @Transactional
    public WartungProtokollDto datenfeldHinzufuegen(Long protokollId, WartungDatenfeldNeuDto dto) {
        WartungProtokoll protokoll = ladeZumSchreiben(protokollId);
        pruefeBearbeitbar(protokoll);
        List<WartungProtokollDatenfeld> felder = new ArrayList<>(datenfeldRepository.findByProtokollIdOrderBySortierungAscIdAsc(protokollId));
        if (felder.size() >= MAX_DATENFELDER_JE_PROTOKOLL) {
            throw falsch("Ein Feldeinsatz kann höchstens " + MAX_DATENFELDER_JE_PROTOKOLL + " Datenfelder haben.");
        }

        String modulKey = leerZuNull(dto.modulKey());
        if (modulKey != null && protokoll.getModule().stream().noneMatch(m -> modulKey.equals(m.getModulKey()))) {
            throw falsch("Dieses Modul gehört nicht zu diesem Feldeinsatz.");
        }
        WartungFeldtyp typ = dto.feldtyp() == null ? WartungFeldtyp.TEXT : dto.feldtyp();
        String optionen = typ == WartungFeldtyp.AUSWAHL ? pruefeOptionen(dto.optionen(), true) : keineOptionenErlaubt(dto.optionen());

        WartungProtokollDatenfeld neu = new WartungProtokollDatenfeld();
        neu.setProtokollId(protokollId);
        neu.setModulKey(modulKey);
        neu.setLabel(dto.label().trim());
        neu.setFeldtyp(typ);
        neu.setOptionen(optionen);
        // Gleicher Abschnitt wie die Felder derselben Gruppe, damit nichts verwaist
        felder.stream().filter(f -> java.util.Objects.equals(f.getModulKey(), modulKey) && f.getAbschnittId() != null)
                .findFirst().ifPresent(f -> neu.setAbschnittId(f.getAbschnittId()));
        datenfeldRepository.save(neu);

        // Hinter das letzte Feld der Gruppe einordnen und lückenlos neu durchzählen: Oberfläche und PDF sortieren nur nach der Nummer
        int letzterDerGruppe = -1;
        for (int i = 0; i < felder.size(); i++) {
            if (java.util.Objects.equals(felder.get(i).getModulKey(), modulKey)) {
                letzterDerGruppe = i;
            }
        }
        // Gibt es in der Gruppe noch kein Feld: "Allgemein" steht immer vorn (wie in der Oberfläche), jedes Modul hinten
        int einfuegePlatz = letzterDerGruppe >= 0 ? letzterDerGruppe + 1 : (modulKey == null ? 0 : felder.size());
        felder.add(einfuegePlatz, neu);
        for (int i = 0; i < felder.size(); i++) {
            felder.get(i).setSortierung(i);
        }
        datenfeldRepository.saveAll(felder);
        markiereInArbeit(protokoll);
        return alsDto(protokollRepository.save(protokoll));
    }

    /** Ändert die Bezeichnung eines Datenfelds, bei einer Auswahl auch die Möglichkeiten. Ein nicht mehr angebotener Wert wird geleert. */
    @Transactional
    public WartungProtokollDto datenfeldDefinition(Long protokollId, Long datenfeldId, WartungDatenfeldDefinitionDto dto) {
        WartungProtokoll protokoll = ladeZumSchreiben(protokollId);
        pruefeBearbeitbar(protokoll);
        WartungProtokollDatenfeld feld = datenfeldRepository.findByIdAndProtokollId(datenfeldId, protokollId)
                .orElseThrow(() -> nichtGefunden("Das Datenfeld wurde nicht gefunden."));
        if (feld.getFeldtyp() == WartungFeldtyp.AUSWAHL) {
            if (dto.optionen() != null) {
                feld.setOptionen(pruefeOptionen(dto.optionen(), true));
                if (feld.getWert() != null && !WartungMapper.optionenListe(feld.getOptionen()).contains(feld.getWert())) {
                    feld.setWert(null);
                }
            }
        } else {
            keineOptionenErlaubt(dto.optionen());
        }
        feld.setLabel(dto.label().trim());
        datenfeldRepository.save(feld);
        markiereInArbeit(protokoll);
        return alsDto(protokollRepository.save(protokoll));
    }

    /** Entfernt ein Datenfeld aus diesem Feldeinsatz (nicht aus dem Katalog). Ein dadurch leerer Abschnitt entfällt mit. */
    @Transactional
    public WartungProtokollDto datenfeldLoeschen(Long protokollId, Long datenfeldId) {
        WartungProtokoll protokoll = ladeZumSchreiben(protokollId);
        pruefeBearbeitbar(protokoll);
        WartungProtokollDatenfeld feld = datenfeldRepository.findByIdAndProtokollId(datenfeldId, protokollId)
                .orElseThrow(() -> nichtGefunden("Das Datenfeld wurde nicht gefunden."));
        Long abschnittId = feld.getAbschnittId();
        datenfeldRepository.delete(feld);
        datenfeldRepository.flush();
        if (abschnittId != null) {
            boolean leer = datenfeldRepository.findByProtokollIdOrderBySortierungAscIdAsc(protokollId).stream()
                    .noneMatch(d -> abschnittId.equals(d.getAbschnittId()))
                    && pruefpunktRepository.findByProtokollIdOrderBySortierungAscIdAsc(protokollId).stream()
                    .noneMatch(p -> abschnittId.equals(p.getAbschnittId()));
            if (leer) {
                abschnittRepository.deleteById(abschnittId);
            }
        }
        markiereInArbeit(protokoll);
        return alsDto(protokollRepository.save(protokoll));
    }

    /** Bereinigt die Möglichkeiten einer Auswahl (getrimmt, ohne Leere und Doppelte) und speichert sie zeilenweise. */
    private static String pruefeOptionen(List<String> optionen, boolean pflicht) {
        List<String> sauber = optionen == null ? List.<String>of() : optionen.stream()
                .filter(java.util.Objects::nonNull).flatMap(String::lines).map(String::trim).filter(o -> !o.isEmpty()).distinct().toList();
        if (sauber.size() > MAX_OPTIONEN) {
            throw falsch("Eine Auswahl kann höchstens " + MAX_OPTIONEN + " Möglichkeiten haben.");
        }
        if (pflicht && sauber.size() < 2) {
            throw falsch("Eine Auswahl braucht mindestens zwei Möglichkeiten.");
        }
        return String.join("\n", sauber);
    }

    private static String keineOptionenErlaubt(List<String> optionen) {
        if (optionen != null && optionen.stream().anyMatch(o -> o != null && !o.isBlank())) {
            throw falsch("Möglichkeiten gibt es nur bei einer Auswahl.");
        }
        return null;
    }

    // ── Status ──────────────────────────────────────────────────────────────

    /**
     * Wechselt den Status. Abschließen verlangt vollständige Bewertung und die Unterschrift des
     * Technikers; Wiederöffnen und Archivieren sind jederzeit möglich, solange die Reihenfolge stimmt.
     */
    @Transactional
    public WartungProtokollDto statusWechseln(Long id, WartungStatus ziel) {
        return statusWechseln(id, ziel, null);
    }

    /** Wie {@link #statusWechseln(Long, WartungStatus)}; jeder echte Wechsel steht danach mit Benutzer, Zeit und optionalem Grund im Verlauf (#238). */
    @Transactional
    public WartungProtokollDto statusWechseln(Long id, WartungStatus ziel, String grund) {
        WartungProtokoll protokoll = ladeZumSchreiben(id);
        WartungStatus alt = protokoll.getStatus();
        if (alt == ziel) {
            return alsDto(protokoll);
        }
        if (!alt.darfWechselnZu(ziel)) {
            throw konflikt("Ein Protokoll mit dem Status „" + anzeige(alt) + "“ lässt sich nicht auf „" + anzeige(ziel) + "“ stellen.");
        }

        switch (ziel) {
            case ABGESCHLOSSEN -> {
                if (alt == WartungStatus.ARCHIVIERT) {
                    if (protokoll.getAbgeschlossenAm() == null) {
                        throw konflikt("Dieses Protokoll wurde nie abgeschlossen. Bitte stattdessen als „Offen“ wiederherstellen.");
                    }
                } else {
                    pruefeAbschlussFaehig(protokoll);
                    protokoll.setAbgeschlossenAm(LocalDateTime.now());
                }
            }
            case OFFEN -> {
                if (alt == WartungStatus.ARCHIVIERT && protokoll.getAbgeschlossenAm() != null) {
                    throw konflikt("Dieses Protokoll war abgeschlossen. Bitte als „Abgeschlossen“ wiederherstellen.");
                }
            }
            case IN_BEARBEITUNG -> {
                if (alt == WartungStatus.ABGESCHLOSSEN) {
                    // Wieder öffnen: gilt nicht mehr als abgeschlossen, auch nicht für den Wartungsplan.
                    protokoll.setAbgeschlossenAm(null);
                }
            }
            case ARCHIVIERT -> { /* nur der Status ändert sich */ }
        }
        protokoll.setStatus(ziel);
        protokollRepository.saveAndFlush(protokoll);
        schreibeVerlauf(protokoll.getId(), alt, ziel, grund);
        if (protokoll.getTuer() != null && (ziel == WartungStatus.ABGESCHLOSSEN || alt == WartungStatus.ABGESCHLOSSEN)) {
            aktualisiereWartungsplan(protokoll.getTuer());
        }
        // Frisch abgeschlossen (nicht aus dem Archiv geholt): Der Bericht kommt nach dem Speichern von selbst ins Projekt
        if (ziel == WartungStatus.ABGESCHLOSSEN && alt != WartungStatus.ARCHIVIERT && ereignisse != null) {
            ereignisse.publishEvent(new WartungProtokollAbgeschlossen(protokoll.getId()));
        }
        return alsDto(protokoll);
    }

    private void schreibeVerlauf(Long protokollId, WartungStatus von, WartungStatus nach, String grund) {
        WartungProtokollVerlauf v = new WartungProtokollVerlauf();
        v.setProtokollId(protokollId);
        v.setZeit(LocalDateTime.now());
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        String name = auth == null || auth.getName() == null || auth.getName().isBlank() ? null : auth.getName();
        v.setBenutzer(name == null ? null : name.length() > 120 ? name.substring(0, 120) : name);
        v.setVonStatus(von);
        v.setNachStatus(nach);
        String g = leerZuNull(grund);
        v.setGrund(g == null ? null : g.length() > 500 ? g.substring(0, 500) : g);
        verlaufRepository.save(v);
    }

    /** Alle Statuswechsel des Protokolls, der neueste zuerst. */
    @Transactional(readOnly = true)
    public List<WartungVerlaufDto> verlauf(Long id) {
        if (!protokollRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Das Protokoll wurde nicht gefunden.");
        }
        return verlaufRepository.findByProtokollIdOrderByZeitDescIdDesc(id).stream()
                .map(v -> new WartungVerlaufDto(v.getZeit(), v.getBenutzer(), v.getVonStatus(), v.getNachStatus(), v.getGrund()))
                .toList();
    }

    // ── Hilfen ──────────────────────────────────────────────────────────────

    private void pruefeAbschlussFaehig(WartungProtokoll protokoll) {
        long offen = pruefpunktRepository.countByProtokollIdAndErgebnisIsNull(protokoll.getId());
        if (offen > 0) {
            throw konflikt(offen == 1
                    ? "Ein Prüfpunkt ist noch nicht bewertet. Bitte zuerst alle Punkte bewerten."
                    : offen + " Prüfpunkte sind noch nicht bewertet. Bitte zuerst alle Punkte bewerten.");
        }
        boolean unterschrift = protokoll.getUnterschriftTechniker() != null && !protokoll.getUnterschriftTechniker().isBlank();
        if (!unterschrift || leerZuNull(protokoll.getUnterschriftTechnikerName()) == null) {
            throw konflikt("Bitte zuerst als Techniker unterschreiben.");
        }
    }

    /**
     * Setzt Intervall und nächste Fälligkeit der Tür nach dem neuesten abgeschlossenen Protokoll.
     * Aus dem Verlauf berechnet statt blind hochgezählt: So stimmt der Plan auch, wenn ein altes
     * Protokoll nachträglich abgeschlossen oder ein Protokoll wieder geöffnet wird.
     */
    private void aktualisiereWartungsplan(WartungTuer tuer) {
        List<WartungProtokoll> neuestes = protokollRepository
                .findByTuerIdAndAbgeschlossenAmIsNotNullOrderByDatumDescIdDesc(tuer.getId(), PageRequest.of(0, 1));
        if (neuestes.isEmpty()) {
            // Kein abgeschlossenes Protokoll mehr (z. B. das einzige wurde wieder geöffnet): keine Fälligkeit behaupten.
            tuer.setNaechsteFaelligkeit(null);
            tuerRepository.save(tuer);
            return;
        }
        WartungProtokoll letztes = neuestes.get(0);
        tuer.setIntervallMonate(letztes.getIntervallMonate());
        tuer.setNaechsteFaelligkeit(letztes.getDatum().plusMonths(letztes.getIntervallMonate()));
        tuerRepository.save(tuer);
    }

    /** Ergebnisse des letzten abgeschlossenen Protokolls derselben Tür, für den Vergleich "Letztes Mal". */
    private Map<String, WartungErgebnis> vorherigeErgebnisse(Long tuerId) {
        List<WartungProtokoll> vorgaenger = protokollRepository
                .findByTuerIdAndAbgeschlossenAmIsNotNullOrderByDatumDescIdDesc(tuerId, PageRequest.of(0, 1));
        Map<String, WartungErgebnis> ergebnis = new HashMap<>();
        if (vorgaenger.isEmpty()) {
            return ergebnis;
        }
        for (WartungProtokollPruefpunkt punkt : pruefpunktRepository
                .findByProtokollIdOrderBySortierungAscIdAsc(vorgaenger.get(0).getId())) {
            if (punkt.getErgebnis() != null) {
                ergebnis.putIfAbsent(WartungInhaltKopierer.schluessel(punkt.getModulKey(), punkt.getLabel()), punkt.getErgebnis());
            }
        }
        return ergebnis;
    }

    private int bestimmeIntervall(Integer gewuenscht, WartungTuer tuer, List<GewaehltesModul> module) {
        if (gewuenscht != null) {
            return gewuenscht;
        }
        if (tuer != null && tuer.getIntervallMonate() != null) {
            return tuer.getIntervallMonate();
        }
        return module.stream().mapToInt(g -> g.modul().getStandardIntervallMonate()).min().orElse(12);
    }

    private static String pruefeWert(WartungProtokollDatenfeld feld, String wert) {
        if (wert == null) {
            return null;
        }
        if (feld.getFeldtyp() == WartungFeldtyp.JA_NEIN && !wert.equals("Ja") && !wert.equals("Nein")) {
            throw falsch("Bitte „Ja“ oder „Nein“ wählen.");
        }
        if (feld.getFeldtyp() == WartungFeldtyp.AUSWAHL) {
            List<String> optionen = WartungMapper.optionenListe(feld.getOptionen());
            if (!optionen.isEmpty() && !optionen.contains(wert)) {
                throw falsch("Bitte einen der angebotenen Werte wählen.");
            }
        }
        if (feld.getFeldtyp() == WartungFeldtyp.JAHR && !wert.isBlank()) {
            int jahr;
            try {
                jahr = wert.strip().length() == 4 ? Integer.parseInt(wert.strip()) : -1;
            } catch (NumberFormatException e) {
                jahr = -1;
            }
            if (jahr < 1900 || jahr > java.time.Year.now().getValue() + 1) {
                throw falsch("Bitte ein vierstelliges Jahr zwischen 1900 und " + (java.time.Year.now().getValue() + 1) + " eintragen.");
            }
            return String.valueOf(jahr);
        }
        return wert;
    }

    private void markiereInArbeit(WartungProtokoll protokoll) {
        if (protokoll.getStatus() == WartungStatus.OFFEN) {
            protokoll.setStatus(WartungStatus.IN_BEARBEITUNG);
            protokollRepository.save(protokoll);
        }
    }

    private void pruefeBearbeitbar(WartungProtokoll protokoll) {
        if (!protokoll.getStatus().istInhaltBearbeitbar()) {
            throw konflikt("Dieses Protokoll ist „" + anzeige(protokoll.getStatus()).toLowerCase(Locale.GERMAN)
                    + "“ und lässt sich nicht mehr ändern. Bitte zuerst wieder öffnen.");
        }
    }

    private Projekt ladeProjekt(Long projektId, Kunde kunde) {
        if (projektId == null) {
            return null;
        }
        Projekt projekt = projektRepository.findById(projektId).orElseThrow(() -> nichtGefunden("Das Projekt wurde nicht gefunden."));
        Kunde projektKunde = projekt.getKundenId();
        if (projektKunde != null && !projektKunde.getId().equals(kunde.getId())) {
            throw falsch("Das Projekt gehört zu einem anderen Kunden.");
        }
        return projekt;
    }

    private Mitarbeiter ladeTechniker(Long technikerId) {
        if (technikerId == null) {
            return null;
        }
        Mitarbeiter mitarbeiter = mitarbeiterRepository.findById(technikerId)
                .orElseThrow(() -> nichtGefunden("Der Mitarbeiter wurde nicht gefunden."));
        if (Boolean.FALSE.equals(mitarbeiter.getAktiv())) {
            throw falsch("Dieser Mitarbeiter ist nicht mehr aktiv.");
        }
        return mitarbeiter;
    }

    /**
     * Lädt das Protokoll mit Schreibsperre. Alle Änderungen an einem Protokoll laufen dadurch nacheinander:
     * Zwei schnelle Tipps auf dem Handy kollidieren nicht mehr, und ein Abschluss kann nicht an einer
     * gleichzeitig laufenden Bewertung vorbeiprüfen.
     */
    private WartungProtokoll ladeZumSchreiben(Long id) {
        return protokollRepository.findMitSperre(id).orElseThrow(() -> nichtGefunden("Das Protokoll wurde nicht gefunden."));
    }

    private WartungProtokoll lade(Long id) {
        return protokollRepository.findById(id).orElseThrow(() -> nichtGefunden("Das Protokoll wurde nicht gefunden."));
    }

    private WartungProtokollDto alsDto(WartungProtokoll protokoll) {
        Long id = protokoll.getId();
        return mapper.toDto(protokoll,
                abschnittRepository.findByProtokollIdOrderBySortierungAscIdAsc(id),
                pruefpunktRepository.findByProtokollIdOrderBySortierungAscIdAsc(id),
                datenfeldRepository.findByProtokollIdOrderBySortierungAscIdAsc(id),
                bericht(protokoll));
    }

    /** Der im Projekt abgelegte Bericht. Wurde das Dokument inzwischen im Projekt gelöscht, gibt es keinen. */
    private WartungProtokollDto.Bericht bericht(WartungProtokoll protokoll) {
        if (protokoll.getBerichtDokumentId() == null) {
            return null;
        }
        return projektDokumentRepository.findById(protokoll.getBerichtDokumentId())
                .map(d -> new WartungProtokollDto.Bericht(d.getId(), WartungBerichtService.dokumentUrl(d), protokoll.getBerichtErstelltAm()))
                .orElse(null);
    }

    private static String anzeige(WartungStatus status) {
        return switch (status) {
            case OFFEN -> "Offen";
            case IN_BEARBEITUNG -> "In Arbeit";
            case ABGESCHLOSSEN -> "Abgeschlossen";
            case ARCHIVIERT -> "Archiviert";
        };
    }

    /** Maskiert die Platzhalter von LIKE (% und _) und das Escape-Zeichen selbst mit "!". */
    static String maskiereLike(String text) {
        return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private static String leerZuNull(String text) {
        if (text == null) {
            return null;
        }
        String getrimmt = text.trim();
        return getrimmt.isEmpty() ? null : getrimmt;
    }

    private static final int MAX_PRUEFPUNKTE_JE_PROTOKOLL = 500;

    private static ResponseStatusException falsch(String meldung) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, meldung);
    }

    private static ResponseStatusException nichtGefunden(String meldung) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, meldung);
    }

    private static ResponseStatusException konflikt(String meldung) {
        return new ResponseStatusException(HttpStatus.CONFLICT, meldung);
    }
}
