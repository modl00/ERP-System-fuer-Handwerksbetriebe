/** Schnittstelle der Plantafel (/api/plantafel). Jede Änderung liefert den ganzen Auftrag mit den neu gerechneten Terminen zurück. */

export type Ampel = 'OK' | 'KNAPP' | 'VERSPAETET' | 'OHNE_ZIEL';
export type Herkunft = 'KALKULATION' | 'GESCHAETZT' | 'VON_HAND';
export type VorgangStatus = 'GEPLANT' | 'LAEUFT' | 'FERTIG';
export type Prioritaet = 'DRINGEND' | 'NORMAL' | 'NICHT_DRINGEND';

export interface Abhaengigkeit { vorgaengerId: number; versatzTage: number }

export interface Vorgang {
    id: number;
    typId: number;
    typName: string;
    farbe: string;
    bezeichnung: string;
    dauerTage: number;
    dauerHerkunft: Herkunft;
    festerStart: string | null;
    start: string | null;
    ende: string | null;
    konflikt: boolean;
    status: VorgangStatus;
    notiz: string | null;
    vorgaenger: Abhaengigkeit[];
    /** Wer den Vorgang macht: Mitarbeiter (Nummern) und/oder eine Ressource; leer = nicht zugewiesen. */
    mitarbeiterIds: number[];
    ressourceId: number | null;
    prioritaet: Prioritaet;
    /** Aufwand in Stunden (Arbeitsvorgang); bei Wartevorgängen leer. Die Dauer in Tagen ergibt sich aus Aufwand, Personen und freier Zeit. */
    aufwandStunden: number | null;
    aufwandHerkunft: Herkunft;
    /** Der Vorgang beginnt nicht früher, darf sich aber mit der Kette nach hinten bewegen (Ziehen). Kein Fixtermin. */
    fruehestensAb: string | null;
    /** Reihenfolge, die der Planer unter gleich wichtigen Vorgängen festgelegt hat (kleiner = zuerst); leer = nicht entschieden. */
    rang: number | null;
    /** Warum der Vorgang später endet (fehlende Personen, andere Aufträge). */
    hinweis: string | null;
    /** Wartevorgang (Material, Fremdleistung): braucht keine Personen, nur Zeit. */
    wartet: boolean;
    buero: boolean;
    /** Noch niemand zugewiesen: Das Programm hat mit dem Standardwert (eine Person, voller Arbeitstag) gerechnet. */
    ohneZuweisungGeschaetzt: boolean;
    /** Zählt Änderungen von Hand. Wer mit einem älteren Stand speichert, bekommt eine Meldung statt die Änderung eines anderen zu überschreiben. */
    stand: number;
    /** Wartevorgang: der Lieferant, seit wann bestellt ist und der bestätigte Liefertermin (von Hand). */
    lieferantId: number | null;
    lieferantName: string | null;
    bestelltAm: string | null;
    lieferterminLieferant: string | null;
    /** Fremdleistung (Verzinken, Pulvern …) mit Menge in kg oder m². */
    fremdleistungId: number | null;
    menge: number | null;
    mengenEinheit: 'KG' | 'M2' | null;
    /** Schon gestempelte Stunden (Zeiterfassung); sie zählen vom Aufwand ab, nur der Rest wird verplant. */
    istStunden: number | null;
}

export interface Auftrag {
    projektId: number;
    bezeichnung: string;
    auftragsnummer: string | null;
    startDatum: string | null;
    zielDatum: string | null;
    liefertermin: string | null;
    ampel: Ampel;
    /** Um wie viele Arbeitstage die Lieferzeit nach dem Zieltermin liegt; sonst leer. */
    verspaetungTage?: number | null;
    vorgaenge: Vorgang[];
    /** Der Kunde des Auftrags (für den Sprung dorthin). */
    kundeId?: number | null;
    kundeName?: string | null;
}

/** {@code abteilungId} und {@code stundenProTag}: Woher die Arbeitszeit für den Dauer-Vorschlag kommt und wie viele Stunden pro Arbeitstag geleistet werden; beides leer = kein Vorschlag. */
export interface Vorgangstyp { id: number; name: string; farbe: string; sortierung: number; standardDauerTage: number; aktiv: boolean; abteilungId: number | null; stundenProTag: number | null; wartet: boolean; buero: boolean }

/** Eingabe für Anlegen und Ändern. {@code festerStart} ersetzt immer den bisherigen (leer = nicht fixiert). */
export interface VorgangEingabe { typId?: number | null; bezeichnung?: string | null; dauerTage?: number | null; festerStart?: string | null; status?: VorgangStatus | null; notiz?: string | null; aufwandStunden?: number | null; /** Der Stand, den der Anwender gesehen hat (leer = nicht prüfen). */ stand?: number | null }

/** Wird ausgelöst, wenn der Server einen Stand-Konflikt meldet (HTTP 409). */
export const KONFLIKT_EREIGNIS = 'plantafel-konflikt';

async function anfrage<T>(url: string, fallback: string, init?: RequestInit): Promise<T> {
    let res: Response;
    try {
        res = await fetch(url, init && { headers: { 'Content-Type': 'application/json' }, ...init });
    } catch {
        throw new Error('Der Server ist nicht erreichbar. Bitte später noch einmal versuchen.');
    }
    if (res.status === 409) {
        // Jemand anderes hat inzwischen geändert: Die Plantafel lädt neu (siehe Seite)
        try { window.dispatchEvent(new CustomEvent(KONFLIKT_EREIGNIS)); } catch { /* ohne Fenster (Test) */ }
    }
    if (!res.ok) {
        let meldung = fallback;
        try {
            const body = await res.json() as { message?: string };
            if (body?.message) meldung = body.message;
        } catch { /* keine Meldung vom Server */ }
        throw new Error(meldung);
    }
    return res.json() as Promise<T>;
}

const json = (method: string, body?: unknown): RequestInit => ({ method, body: body === undefined ? undefined : JSON.stringify(body) });
const BASIS = '/api/plantafel';

export const ladeAuftraege = () => anfrage<Auftrag[]>(`${BASIS}/auftraege`, 'Die Plantafel konnte nicht geladen werden.');
export const ladeTypen = () => anfrage<Vorgangstyp[]>(`${BASIS}/vorgangstypen`, 'Die Vorgangsarten konnten nicht geladen werden.');
export const nimmAuftragAuf = (projektId: number) => anfrage<Auftrag>(`${BASIS}/auftraege/${projektId}/standardkette`, 'Der Auftrag konnte nicht aufgenommen werden.', json('POST'));
export const setzeTermine = (projektId: number, start: string | null, ziel: string | null) =>
    anfrage<Auftrag>(`${BASIS}/auftraege/${projektId}/termine`, 'Die Termine konnten nicht gespeichert werden.', json('PUT', { start, ziel }));
export const legeVorgangAn = (projektId: number, e: VorgangEingabe) =>
    anfrage<Auftrag>(`${BASIS}/auftraege/${projektId}/vorgaenge`, 'Der Vorgang konnte nicht angelegt werden.', json('POST', e));
export const aendereVorgang = (id: number, e: VorgangEingabe) =>
    anfrage<Auftrag>(`${BASIS}/vorgaenge/${id}`, 'Der Vorgang konnte nicht gespeichert werden.', json('PUT', e));
/** Das Ziehen mit der Maus: Der Vorgang beginnt nicht früher als dieser Tag (leer = aufheben), darf sich aber mit der Kette nach hinten bewegen. */
export const setzeFruehestens = (id: number, datum: string | null, stand?: number | null) =>
    anfrage<Auftrag>(`${BASIS}/vorgaenge/${id}/fruehestens`, 'Der Beginn konnte nicht gespeichert werden.', json('PUT', { datum, stand: stand ?? null }));
/** Die Reihenfolge unter gleich wichtigen Vorgängen (kleiner = zuerst, leer = nicht entschieden). */
export const setzeRang = (id: number, rang: number | null, stand?: number | null) =>
    anfrage<Auftrag>(`${BASIS}/vorgaenge/${id}/rang`, 'Die Reihenfolge konnte nicht gespeichert werden.', json('PUT', { rang, stand: stand ?? null }));

/** Eine mögliche Änderung zum Ausprobieren („Vorschau"). Leere Felder bleiben unverändert; „…Setzen" heißt: Auch ein leerer Wert gilt (Termin aufheben). */
export interface Probe {
    aufwandStunden?: number | null;
    dauerTage?: number | null;
    fruehestensAb?: string | null;
    fruehestensAbSetzen?: boolean;
    festerStart?: string | null;
    festerStartSetzen?: boolean;
    mitarbeiterIds?: number[] | null;
    prioritaet?: Prioritaet | null;
}
export interface AuftragAenderung {
    projektId: number; bezeichnung: string; altLiefertermin: string | null; neuLiefertermin: string; tage: number | null;
    altAmpel: Ampel; neuAmpel: Ampel; neuVerspaetungTage: number | null; eigener: boolean;
}
export interface VorgangAenderung { vorgangId: number; projektId: number; bezeichnung: string; auftrag: string; altStart: string | null; altEnde: string | null; neuStart: string; neuEnde: string }
export interface Vorschau { auftraege: AuftragAenderung[]; vorgaenge: VorgangAenderung[]; warnungen: string[] }
export const ladeVorschau = (vorgangId: number, probe: Probe) =>
    anfrage<Vorschau>(`${BASIS}/vorgaenge/${vorgangId}/vorschau`, 'Die Vorschau konnte nicht berechnet werden.', json('POST', probe));

/** Die letzten Änderungen von Hand; nur die jüngste, noch nicht zurückgenommene lässt sich zurücknehmen. */
export interface Aenderung { id: number; zeitpunkt: string; benutzer: string | null; beschreibung: string; vorgangId: number | null; projektId: number | null; zurueckgenommen: boolean; rueckgaengigMoeglich: boolean }
export const ladeAenderungen = (limit = 10) => anfrage<Aenderung[]>(`${BASIS}/aenderungen?limit=${limit}`, 'Die Änderungsliste konnte nicht geladen werden.');
export const nimmZurueck = (id: number) => anfrage<Auftrag>(`${BASIS}/aenderungen/${id}/rueckgaengig`, 'Die Änderung konnte nicht zurückgenommen werden.', json('POST'));
/** Rechnet die ganze Plantafel neu: Urlaub, Termine und der neue Tag ändern die Termine, ohne dass jemand sie anfasst. */
export const rechneNeu = () => anfrage<{ ok: boolean }>(`${BASIS}/neu-berechnen`, 'Die Plantafel konnte nicht neu gerechnet werden.', json('POST'));

/** Arbeitstag und Wochenrhythmus: Stunden eines Arbeitstags (Ersatz ohne Zeitkonto, Standard ohne Zuweisung) und die Bürotage je Mitarbeiter (ISO-Wochentage 1 bis 7). */
export interface Rhythmus { arbeitstagStunden: number; buerotage: Record<string, number[]> }
export const ladeRhythmus = () => anfrage<Rhythmus>(`${BASIS}/rhythmus`, 'Der Wochenrhythmus konnte nicht geladen werden.');
export const speichereRhythmus = (r: Rhythmus) =>
    anfrage<Rhythmus>('/api/settings/planung/rhythmus', 'Der Wochenrhythmus konnte nicht gespeichert werden.', json('PUT', r));

/** Wartevorgang: Lieferant, „bestellt am" und bestätigter Liefertermin; alles leer = aufheben. */
export const setzeLieferung = (id: number, l: { lieferantId: number | null; bestelltAm: string | null; lieferterminLieferant: string | null }, stand?: number | null) =>
    anfrage<Auftrag>(`${BASIS}/vorgaenge/${id}/lieferung`, 'Die Lieferung konnte nicht gespeichert werden.', json('PUT', { ...l, stand: stand ?? null }));

/** Die Lieferanten für die Auswahl (der Server liefert sie seitenweise). */
export const ladeLieferanten = async (): Promise<{ id: number; name: string; standardLieferzeitTage: number | null }[]> => {
    const res = await fetch('/api/lieferanten?size=1000');
    if (!res.ok) throw new Error('Die Lieferanten konnten nicht geladen werden.');
    const data = await res.json() as { lieferanten?: { id: number; lieferantenname?: string; standardLieferzeitTage?: number | null }[] };
    return (data.lieferanten ?? []).map(l => ({ id: l.id, name: l.lieferantenname ?? `Lieferant ${l.id}`, standardLieferzeitTage: l.standardLieferzeitTage ?? null }));
};

/** Das Fenster bei der Auftragsbestätigung (#331, Abschnitt 5): vorausgefüllt aus dem Auftrag. */
export interface FremdleistungInfo { fremdleistungId: number; bezeichnung: string; lieferantId: number | null; lieferantName: string | null; einheit: string; menge: number; lieferzeitTage: number | null }
export interface AufnahmeArt { id: number; name: string; wartet: boolean; buero: boolean }
export interface Aufnahme {
    projektId: number; bezeichnung: string; auftragsnummer: string | null; bereitsGeplant: boolean; offen: boolean; quelle: 'ANGEBOT' | 'AUFTRAGSBESTAETIGUNG' | null;
    fremdleistungen: FremdleistungInfo[]; arten: AufnahmeArt[]; hinweise: string[]; arbeitstagStunden: number; hatArbeitszeit: boolean;
}
export interface AufnahmeEingabe {
    spaeter: boolean; zielDatum: string | null; prioritaet: Prioritaet; fremdleistungen: { fremdleistungId: number | null; bezeichnung: string; lieferantId: number | null; menge: number | null; einheit: string | null }[];
    montageVorOrt: boolean; montageTage: number | null; mitarbeiterIds: number[];
}
export const ladeAufnahme = (projektId: number) => anfrage<Aufnahme>(`${BASIS}/auftraege/${projektId}/aufnahme`, 'Die Angaben zum Auftrag konnten nicht geladen werden.');
export const planeAuftrag = (projektId: number, e: AufnahmeEingabe) =>
    anfrage<Auftrag>(`${BASIS}/auftraege/${projektId}/planen`, 'Der Auftrag konnte nicht geplant werden.', json('POST', e));
export interface OffenerAuftrag { projektId: number; bezeichnung: string; auftragsnummer: string | null }
export const ladeOffene = () => anfrage<OffenerAuftrag[]>(`${BASIS}/offene-planung`, 'Die Liste der neuen Aufträge konnte nicht geladen werden.');
export const ladeAbFenster = () => anfrage<{ aktiv: boolean }>(`${BASIS}/ab-fenster`, 'Die Einstellung konnte nicht geladen werden.');
export const setzeAbFenster = (aktiv: boolean) => anfrage<{ aktiv: boolean }>(`${BASIS}/ab-fenster`, 'Die Einstellung konnte nicht gespeichert werden.', json('PUT', { aktiv }));

/** Wird ausgelöst, wenn eine Auftragsbestätigung erstellt wurde: Das Fenster zur Planung fragt dann nach (wenn es der Benutzer nicht abgeschaltet hat). */
export const AB_ERSTELLT_EREIGNIS = 'plantafel-ab-erstellt';
export function meldeAuftragsbestaetigung(projektId: number): void {
    try { window.dispatchEvent(new CustomEvent(AB_ERSTELLT_EREIGNIS, { detail: { projektId } })); } catch { /* ohne Fenster (Test) */ }
}

/** Zu welcher Vorgangsart die gestempelte Zeit eines Arbeitsgangs zählt (leer = automatisch über die Abteilung). {@code mehrdeutig}: Mehrere Arten teilen die Abteilung. */
export interface ArbeitsgangZuordnung { arbeitsgangId: number; arbeitsgang: string; abteilungId: number | null; abteilung: string | null; typId: number | null; mehrdeutig: boolean }
export const ladeZuordnungen = () => anfrage<ArbeitsgangZuordnung[]>(`${BASIS}/arbeitsgang-zuordnung`, 'Die Zuordnung der Arbeitsgänge konnte nicht geladen werden.');
export const speichereZuordnung = (arbeitsgangId: number, typId: number | null) =>
    anfrage<ArbeitsgangZuordnung[]>(`${BASIS}/arbeitsgang-zuordnung`, 'Die Zuordnung konnte nicht gespeichert werden.', json('PUT', { arbeitsgangId, typId }));

/** Nimmt den ganzen Auftrag aus der Plantafel (alle Vorgänge); der Auftrag selbst bleibt bestehen. */
export const entferneAuftrag = (projektId: number) => anfrage<{ ok: boolean }>(`${BASIS}/auftraege/${projektId}`, 'Der Auftrag konnte nicht entfernt werden.', json('DELETE'));
/** Wichtigkeit für alle noch nicht erledigten Vorgänge eines Auftrags. */
export const setzeAuftragPrioritaet = (projektId: number, prioritaet: Prioritaet) =>
    anfrage<Auftrag>(`${BASIS}/auftraege/${projektId}/prioritaet`, 'Die Wichtigkeit konnte nicht gespeichert werden.', json('PUT', { prioritaet }));

export const loescheVorgang = (id: number) => anfrage<Auftrag>(`${BASIS}/vorgaenge/${id}`, 'Der Vorgang konnte nicht gelöscht werden.', json('DELETE'));
export const setzeVorgaenger = (id: number, vorgaengerId: number, versatzTage: number) =>
    anfrage<Auftrag>(`${BASIS}/vorgaenge/${id}/vorgaenger/${vorgaengerId}`, 'Die Abhängigkeit konnte nicht gespeichert werden.', json('PUT', { versatzTage }));
export const loescheVorgaenger = (id: number, vorgaengerId: number) =>
    anfrage<Auftrag>(`${BASIS}/vorgaenge/${id}/vorgaenger/${vorgaengerId}`, 'Die Abhängigkeit konnte nicht entfernt werden.', json('DELETE'));
/** Eine Zeile des Dauer-Vorschlags aus der Arbeitszeit des Auftrags. {@code vorgeschlageneDauer} ist leer, wenn es keinen Vorschlag gibt ({@code hinweis} sagt warum). */
export interface DauerVorschlag {
    vorgangId: number;
    bezeichnung: string;
    art: string;
    aktuelleDauer: number;
    aktuelleHerkunft: Herkunft;
    vorgeschlageneDauer: number | null;
    stunden: number | null;
    stundenProTag: number | null;
    abteilung: string | null;
    hinweis: string | null;
}
export interface DauerVorschlagAntwort { quelle: 'ANGEBOT' | 'AUFTRAGSBESTAETIGUNG' | null; zeilen: DauerVorschlag[]; hinweise: string[] }

export const ladeDauerVorschlag = (projektId: number) =>
    anfrage<DauerVorschlagAntwort>(`${BASIS}/auftraege/${projektId}/vorschlag`, 'Der Vorschlag konnte nicht berechnet werden.');
export const uebernehmeDauerVorschlag = (projektId: number, vorgangIds: number[]) =>
    anfrage<Auftrag>(`${BASIS}/auftraege/${projektId}/vorschlag/uebernehmen`, 'Der Vorschlag konnte nicht übernommen werden.', json('POST', { vorgangIds }));

/** Vorschlag der KI: welche Vorgänge ein Auftrag braucht. {@code hinweise}: was die KI nannte, das aber nicht übernommen werden konnte. */
export interface KiSchritt { typId: number; typName: string; farbe: string; dauerTage: number; begruendung: string }
export interface KiKettenVorschlag { quelle: 'ANGEBOT' | 'AUFTRAGSBESTAETIGUNG' | null; schritte: KiSchritt[]; hinweise: string[] }

export const ladeKiVorschlag = (projektId: number) =>
    anfrage<KiKettenVorschlag>(`${BASIS}/ki/auftraege/${projektId}/vorgaenge`, 'Die KI konnte keinen Vorschlag machen.');
export const uebernehmeKiVorschlag = (projektId: number, schritte: { typId: number; dauerTage: number }[]) =>
    anfrage<Auftrag>(`${BASIS}/ki/auftraege/${projektId}/vorgaenge/uebernehmen`, 'Der Vorschlag konnte nicht übernommen werden.', json('POST', { schritte }));
export const ladeKiErklaerung = (projektId: number) =>
    anfrage<{ text: string }>(`${BASIS}/ki/auftraege/${projektId}/erklaerung`, 'Die KI konnte den Termin nicht erklären.');

export const speichereTyp = (id: number | null, t: Partial<Omit<Vorgangstyp, 'id'>>) =>
    anfrage<Vorgangstyp>(id == null ? `${BASIS}/vorgangstypen` : `${BASIS}/vorgangstypen/${id}`, 'Die Vorgangsart konnte nicht gespeichert werden.', json(id == null ? 'POST' : 'PUT', t));

/** Etwas, das Arbeit aufnimmt, aber kein Mitarbeiter ist: Werkstatt, Büro, Fahrzeug … */
export interface Ressource { id: number; name: string; art: string; sortierung: number; aktiv: boolean }
export interface Person { id: number; name: string }
export interface Zuweisung { mitarbeiterIds: number[]; ressourceId: number | null; /** Leer = die Wichtigkeit bleibt, wie sie ist. */ prioritaet: Prioritaet | null }

export const ladeRessourcen = () => anfrage<Ressource[]>(`${BASIS}/ressourcen`, 'Die Ressourcen konnten nicht geladen werden.');
export const ladePersonen = () => anfrage<Person[]>(`${BASIS}/personen`, 'Die Mitarbeiter konnten nicht geladen werden.');
export const weiseZu = (vorgangId: number, z: Zuweisung, stand?: number | null) =>
    anfrage<Auftrag>(`${BASIS}/vorgaenge/${vorgangId}/zuweisung`, 'Die Zuweisung konnte nicht gespeichert werden.', json('PUT', { ...z, stand: stand ?? null }));

/** Ein Eintrag in der Ansicht nach Mitarbeitern und Ressourcen: Vorgang der Plantafel, Kalendertermin oder Abwesenheit. {@code hinweis} gesetzt = doppelt belegt oder in einer Abwesenheit eingeplant. */
export interface BelegungEintrag {
    id: string;
    /** TERMIN_SAMMEL: nur in der Ansicht, bei großen Zeitstufen fasst sie die Termine einer Woche zusammen. */
    quelle: 'VORGANG' | 'TERMIN' | 'ABWESENHEIT' | 'WARTUNG' | 'TERMIN_SAMMEL';
    titel: string;
    untertitel: string | null;
    von: string;
    bis: string;
    vonZeit: string | null;
    bisZeit: string | null;
    farbe: string;
    projektId: number | null;
    prioritaet: Prioritaet | null;
    fest: boolean;
    status: VorgangStatus | null;
    hinweis: string | null;
    /** Nur bei Arbeitsvorgängen: Aufwand und schon gestempelte Stunden (Fortschritt). */
    aufwandStunden?: number | null;
    istStunden?: number | null;
    /** Nur in der Auftragszeile: die Vorgänge (Nummern), nach deren Ende dieser beginnt; daraus zeichnet die Ansicht die Pfeile. */
    vorgaengerIds?: number[];
}
export interface BelegungZeile { schluessel: string; gruppe: 'MITARBEITER' | 'RESSOURCE' | 'ALLGEMEIN'; name: string; eintraege: BelegungEintrag[] }
/** Ein Tag, an dem nicht gearbeitet wird. */
export interface FreierTag { datum: string; art: 'FEIERTAG' | 'BETRIEBSURLAUB'; name: string }
export interface Belegung { von: string; bis: string; zeilen: BelegungZeile[]; freieTage?: FreierTag[] }

export const ladeBelegung = (von: string, bis: string) =>
    anfrage<Belegung>(`${BASIS}/belegung?von=${encodeURIComponent(von)}&bis=${encodeURIComponent(bis)}`, 'Die Belegung konnte nicht geladen werden.');

/** Einen Kalendertermin verschieben: nur Tag, Uhrzeit und (beim Wechsel in die Zeile eines anderen Mitarbeiters) der Teilnehmer ändern sich. */
export interface TerminVerschiebung { datum: string; von: string | null; bis: string | null; vonMitarbeiterId: number | null; nachMitarbeiterId: number | null }
export const verschiebeTermin = (id: number, v: TerminVerschiebung) =>
    anfrage<{ id: number }>(`${BASIS}/termine/${id}/verschieben`, 'Der Termin konnte nicht verschoben werden.', json('PUT', v));

/** Ein Kalendertermin, wie ihn die Plantafel anlegt und ändert ({@code datumBis} gesetzt = mehrtägig und ganztägig). */
export interface Termin {
    id: number;
    titel: string;
    beschreibung: string | null;
    datum: string;
    datumBis: string | null;
    startZeit: string | null;
    endeZeit: string | null;
    ganztaegig: boolean;
    farbe: string | null;
    projektId: number | null;
    kundeId: number | null;
    lieferantId: number | null;
    anfrageId: number | null;
    teilnehmer: { id: number; name: string }[];
}
export interface TerminEingabe {
    titel: string;
    beschreibung: string | null;
    datum: string;
    datumBis: string | null;
    startZeit: string | null;
    endeZeit: string | null;
    ganztaegig: boolean;
    farbe: string | null;
    projektId: number | null;
    kundeId: number | null;
    lieferantId: number | null;
    anfrageId: number | null;
    teilnehmerIds: number[];
}

export const ladeTermin = (id: number) => anfrage<Termin>(`/api/kalender/${id}`, 'Der Termin konnte nicht geladen werden.');
export const speichereTermin = (id: number | null, t: TerminEingabe) =>
    anfrage<Termin>(id == null ? '/api/kalender' : `/api/kalender/${id}`, 'Der Termin konnte nicht gespeichert werden.', json(id == null ? 'POST' : 'PUT', t));
export const loescheTermin = (id: number) => anfrage<{ deleted: boolean }>(`/api/kalender/${id}`, 'Der Termin konnte nicht gelöscht werden.', json('DELETE'));
