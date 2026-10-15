import type {
    AnlegenEingabe,
    DatenfeldNeuEingabe,
    Bezug,
    GespeicherteUnterschrift,
    ProjektWartung,
    WartungAnlage,
    WartungBericht,
    GlobalesDatenfeld,
    InhaltAenderung,
    KopfEingabe,
    ListenFilter,
    ModulDetail,
    ModulUebersicht,
    SammelberichtEingabe,
    WartungVerlaufEintrag,
    Seite,
    WartungErgebnis,
    WartungListeneintrag,
    WartungModul,
    WartungObjekt,
    WartungObjektEingabe,
    WartungProtokoll,
    WartungStatus,
    WartungTuer,
    WartungTuerEingabe,
    WartungVertrag,
    WartungVertragEingabe,
} from './types';

/** Fehler mit einer Meldung, die der Nutzer lesen darf (kommt vom Server oder ist unser Fallback). */
export class WartungFehler extends Error {
    readonly status: number;

    constructor(message: string, status: number) {
        super(message);
        this.name = 'WartungFehler';
        this.status = status;
    }
}

/**
 * Das Backend lehnt fachlich mit einem ApiError ab ({@code message}). Ohne diese Meldung sähe der
 * Nutzer nur "Status 409" und wüsste nicht, was zu tun ist.
 */
async function fehlerAus(res: Response, fallback: string): Promise<WartungFehler> {
    try {
        const body = await res.json();
        const text = body?.message ?? body?.reason ?? body?.error;
        if (typeof text === 'string' && text.trim()) return new WartungFehler(text.trim(), res.status);
    } catch {
        // kein oder kaputtes JSON: dann der Fallback
    }
    return new WartungFehler(fallback, res.status);
}

async function anfrage<T>(methode: string, pfad: string, fallback: string, body?: unknown): Promise<T> {
    let res: Response;
    try {
        res = await fetch(pfad, {
            method: methode,
            headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
            body: body === undefined ? undefined : JSON.stringify(body),
        });
    } catch {
        throw new WartungFehler('Der Server ist nicht erreichbar. Bitte die Verbindung prüfen und noch einmal versuchen.', 0);
    }
    if (!res.ok) throw await fehlerAus(res, fallback);
    return (await res.json()) as T;
}

const leerZuNull = (text: string): string | null => (text.trim() === '' ? null : text.trim());

// ── Katalog ─────────────────────────────────────────────────────────────────

export const ladeModule = () =>
    anfrage<WartungModul[]>('GET', '/api/wartung/module', 'Die Module konnten nicht geladen werden.');

// ── Objekte und Türen ───────────────────────────────────────────────────────

export const ladeObjekte = (kundeId: number, mitInaktiven = false) =>
    anfrage<WartungObjekt[]>(
        'GET',
        `/api/wartung/kunden/${encodeURIComponent(String(kundeId))}/objekte${mitInaktiven ? '?inaktive=true' : ''}`,
        'Die Objekte konnten nicht geladen werden.');

const objektBody = (e: WartungObjektEingabe) => ({
    bezeichnung: e.bezeichnung.trim(),
    strasse: leerZuNull(e.strasse),
    plz: leerZuNull(e.plz),
    ort: leerZuNull(e.ort),
    ansprechpartnerName: leerZuNull(e.ansprechpartnerName),
    ansprechpartnerTelefon: leerZuNull(e.ansprechpartnerTelefon),
    notizen: leerZuNull(e.notizen),
});

export const legeObjektAn = (kundeId: number, e: WartungObjektEingabe) =>
    anfrage<WartungObjekt>('POST', `/api/wartung/kunden/${encodeURIComponent(String(kundeId))}/objekte`,
        'Das Objekt konnte nicht angelegt werden.', objektBody(e));

export const aendereObjekt = (objektId: number, e: WartungObjektEingabe) =>
    anfrage<WartungObjekt>('PUT', `/api/wartung/objekte/${encodeURIComponent(String(objektId))}`,
        'Das Objekt konnte nicht gespeichert werden.', objektBody(e));

export const setzeObjektAktiv = (objektId: number, aktiv: boolean) =>
    anfrage<WartungObjekt>('PUT', `/api/wartung/objekte/${encodeURIComponent(String(objektId))}/aktiv`,
        'Das Objekt konnte nicht geändert werden.', { aktiv });

const tuerBody = (e: WartungTuerEingabe) => ({
    bezeichnung: e.bezeichnung.trim(),
    notizen: leerZuNull(e.notizen),
    intervallMonate: e.intervallMonate,
    module: e.module,
});

export const legeTuerAn = (objektId: number, e: WartungTuerEingabe) =>
    anfrage<WartungTuer>('POST', `/api/wartung/objekte/${encodeURIComponent(String(objektId))}/tueren`,
        'Die Tür konnte nicht angelegt werden.', tuerBody(e));

export const aendereTuer = (tuerId: number, e: WartungTuerEingabe) =>
    anfrage<WartungTuer>('PUT', `/api/wartung/tueren/${encodeURIComponent(String(tuerId))}`,
        'Die Tür konnte nicht gespeichert werden.', tuerBody(e));

export const setzeTuerAktiv = (tuerId: number, aktiv: boolean) =>
    anfrage<WartungTuer>('PUT', `/api/wartung/tueren/${encodeURIComponent(String(tuerId))}/aktiv`,
        'Die Tür konnte nicht geändert werden.', { aktiv });

export const verschiebeTuer = (tuerId: number, zielObjektId: number) =>
    anfrage<WartungTuer>('POST', `/api/wartung/tueren/${encodeURIComponent(String(tuerId))}/verschieben`,
        'Die Tür konnte nicht verschoben werden.', { zielObjektId });

export const kopiereTuer = (tuerId: number) =>
    anfrage<WartungTuer>('POST', `/api/wartung/tueren/${encodeURIComponent(String(tuerId))}/kopieren`,
        'Die Tür konnte nicht kopiert werden.');

// ── Protokolle ──────────────────────────────────────────────────────────────

export const sucheProtokolle = (filter: ListenFilter) => {
    const p = new URLSearchParams();
    if (filter.kundeId !== undefined) p.set('kundeId', String(filter.kundeId));
    if (filter.objektId !== undefined) p.set('objektId', String(filter.objektId));
    if (filter.tuerId !== undefined) p.set('tuerId', String(filter.tuerId));
    if (filter.status) p.set('status', filter.status);
    if (filter.mitArchiv) p.set('mitArchiv', 'true');
    if (filter.datumVon) p.set('datumVon', filter.datumVon);
    if (filter.datumBis) p.set('datumBis', filter.datumBis);
    if (filter.suche && filter.suche.trim()) p.set('suche', filter.suche.trim());
    p.set('seite', String(filter.seite ?? 0));
    p.set('groesse', String(filter.groesse ?? 20));
    return anfrage<Seite<WartungListeneintrag>>('GET', `/api/wartung/protokolle?${p.toString()}`,
        'Die Wartungen konnten nicht geladen werden.');
};

export const legeProtokollAn = (e: AnlegenEingabe) =>
    anfrage<WartungProtokoll>('POST', '/api/wartung/protokolle', 'Die Wartung konnte nicht angelegt werden.', {
        kundeId: e.kundeId,
        objektId: e.objektId,
        tuerId: e.tuerId,
        technikerId: e.technikerId,
        titel: leerZuNull(e.titel),
        datum: e.datum || null,
        intervallMonate: e.intervallMonate,
        module: e.module,
        bemerkungen: leerZuNull(e.bemerkungen),
        projektId: e.projektId ?? null,
        vorlageProtokollId: e.vorlageProtokollId ?? null,
        werteUebernehmen: e.werteUebernehmen,
    });

export const ladeProtokoll = (id: number) =>
    anfrage<WartungProtokoll>('GET', `/api/wartung/protokolle/${encodeURIComponent(String(id))}`,
        'Die Wartung konnte nicht geladen werden.');

/** Ergebnis der Sammeländerung: wie viele geändert wurden und welche (mit Grund) nicht. */
export interface ProtokollartErgebnis {
    geaendert: number;
    uebersprungen: { id: number | null; nummer: string | null; grund: string }[];
}

/** Setzt die Protokollart mehrerer Wartungen auf einmal. */
export const aendereProtokollart = (ids: number[], protokollTyp: string) =>
    anfrage<ProtokollartErgebnis>('POST', '/api/wartung/protokolle/protokollart', 'Die Protokollart konnte nicht geändert werden.', { ids, protokollTyp });

export const aendereKopf = (id: number, e: KopfEingabe) =>
    anfrage<WartungProtokoll>('PUT', `/api/wartung/protokolle/${encodeURIComponent(String(id))}/kopf`,
        'Die Angaben konnten nicht gespeichert werden.', {
            titel: leerZuNull(e.titel),
            protokollTyp: e.protokollTyp.trim(),
            datum: e.datum,
            intervallMonate: e.intervallMonate,
            technikerId: e.technikerId,
            projektId: e.projektId,
            objektId: e.objektId,
            bemerkungen: leerZuNull(e.bemerkungen),
            ansprechpartnerName: leerZuNull(e.ansprechpartnerName),
        });

export const bewertePruefpunkt = (id: number, pruefpunktId: number, ergebnis: WartungErgebnis | null, bemerkung: string) =>
    anfrage<InhaltAenderung>('PUT',
        `/api/wartung/protokolle/${encodeURIComponent(String(id))}/pruefpunkte/${encodeURIComponent(String(pruefpunktId))}`,
        'Die Bewertung konnte nicht gespeichert werden.', { ergebnis, bemerkung: leerZuNull(bemerkung) });

export const setzeDatenfeld = (id: number, datenfeldId: number, wert: string) =>
    anfrage<InhaltAenderung>('PUT',
        `/api/wartung/protokolle/${encodeURIComponent(String(id))}/datenfelder/${encodeURIComponent(String(datenfeldId))}`,
        'Der Wert konnte nicht gespeichert werden.', { wert });

export const unterschreibe = (id: number, rolle: 'TECHNIKER' | 'KUNDE', bild: string | null, name: string) =>
    anfrage<WartungProtokoll>('PUT', `/api/wartung/protokolle/${encodeURIComponent(String(id))}/unterschrift`,
        'Die Unterschrift konnte nicht gespeichert werden.', { rolle, bild, name });

export const wechsleStatus = (id: number, status: WartungStatus, grund?: string) =>
    anfrage<WartungProtokoll>('PUT', `/api/wartung/protokolle/${encodeURIComponent(String(id))}/status`,
        'Der Status konnte nicht geändert werden.', { status, grund: grund?.trim() ? grund.trim() : undefined });

/** Wer das Protokoll wann abgeschlossen, wieder geöffnet oder archiviert hat, der neueste zuerst. */
export const ladeVerlauf = (id: number) =>
    anfrage<WartungVerlaufEintrag[]>('GET', `/api/wartung/protokolle/${encodeURIComponent(String(id))}/verlauf`,
        'Der Verlauf konnte nicht geladen werden.');

// ── Sammelbericht ───────────────────────────────────────────────────────────

/** Der Dateiname steht im Antwort-Kopf, bei Umlauten als `filename*=UTF-8''…`. */
export function dateinameAusKopf(kopf: string | null, fallback: string): string {
    if (!kopf) return fallback;
    const kodiert = /filename\*=UTF-8''([^;]+)/i.exec(kopf);
    if (kodiert) {
        try {
            return decodeURIComponent(kodiert[1].trim());
        } catch {
            // kaputte Kodierung: weiter mit dem einfachen Namen
        }
    }
    const einfach = /filename="?([^";]+)"?/i.exec(kopf);
    return einfach ? einfach[1].trim() : fallback;
}

/** Holt den Sammelbericht als PDF. Ein POST, weil bis zu 200 Nummern nicht sicher in eine Adresse passen. */
export async function ladeSammelbericht(e: SammelberichtEingabe): Promise<{ blob: Blob; dateiname: string }> {
    let res: Response;
    try {
        res = await fetch('/api/wartung/sammelbericht/pdf', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ protokollIds: e.protokollIds, titel: leerZuNull(e.titel), mitDeckblatt: e.mitDeckblatt }),
        });
    } catch {
        throw new WartungFehler('Der Server ist nicht erreichbar. Bitte die Verbindung prüfen und noch einmal versuchen.', 0);
    }
    if (!res.ok) throw await fehlerAus(res, 'Der Sammelbericht konnte nicht erstellt werden.');
    return { blob: await res.blob(), dateiname: dateinameAusKopf(res.headers.get('Content-Disposition'), 'Sammelbericht.pdf') };
}

// ── Modul-Editor (nur Admin) ────────────────────────────────────────────────

export const ladeModulUebersicht = () =>
    anfrage<ModulUebersicht[]>('GET', '/api/wartung/admin/module', 'Die Module konnten nicht geladen werden.');

export const ladeModulDetail = (id: number) =>
    anfrage<ModulDetail>('GET', `/api/wartung/admin/module/${encodeURIComponent(String(id))}`, 'Das Modul konnte nicht geladen werden.');

export const legeModulAn = (m: ModulDetail) =>
    anfrage<ModulDetail>('POST', '/api/wartung/admin/module', 'Das Modul konnte nicht angelegt werden.', m);

export const speichereModul = (m: ModulDetail) =>
    anfrage<ModulDetail>('PUT', `/api/wartung/admin/module/${encodeURIComponent(String(m.id))}`, 'Das Modul konnte nicht gespeichert werden.', m);

export const setzeModulAktiv = (id: number, aktiv: boolean) =>
    anfrage<ModulDetail>('PUT', `/api/wartung/admin/module/${encodeURIComponent(String(id))}/aktiv`,
        'Das Modul konnte nicht geändert werden.', { aktiv });

export const setzeModulReihenfolge = (ids: number[]) =>
    anfrage<ModulUebersicht[]>('PUT', '/api/wartung/admin/module/reihenfolge', 'Die Reihenfolge konnte nicht gespeichert werden.', { ids });

export async function loescheModul(id: number): Promise<void> {
    let res: Response;
    try {
        res = await fetch(`/api/wartung/admin/module/${encodeURIComponent(String(id))}`, { method: 'DELETE' });
    } catch {
        throw new WartungFehler('Der Server ist nicht erreichbar. Bitte die Verbindung prüfen und noch einmal versuchen.', 0);
    }
    if (!res.ok) throw await fehlerAus(res, 'Das Modul konnte nicht gelöscht werden.');
}

export const ladeGlobaleFelder = () =>
    anfrage<GlobalesDatenfeld[]>('GET', '/api/wartung/admin/globale-felder', 'Die Datenfelder konnten nicht geladen werden.');

export const speichereGlobaleFelder = (felder: GlobalesDatenfeld[]) =>
    anfrage<GlobalesDatenfeld[]>('PUT', '/api/wartung/admin/globale-felder', 'Die Datenfelder konnten nicht gespeichert werden.', felder);

// ── Wartung (Anlage), Projekt, Bericht, Unterschrift, Prüfpunkte ────────────

export const ladeAnlage = (tuerId: number) =>
    anfrage<WartungAnlage>('GET', `/api/wartung/tueren/${encodeURIComponent(String(tuerId))}`, 'Die Wartung konnte nicht geladen werden.');

/** Ordnet die Wartung einem Projekt zu; {@code null} trennt sie vom Projekt. */
export const ordneProjektZu = (tuerId: number, projektId: number | null) =>
    anfrage<WartungAnlage>('PUT', `/api/wartung/tueren/${encodeURIComponent(String(tuerId))}/projekt`,
        'Das Projekt konnte nicht zugeordnet werden.', { projektId });

export const ladeProjektWartungen = (projektId: number) =>
    anfrage<ProjektWartung[]>('GET', `/api/wartung/projekte/${encodeURIComponent(String(projektId))}/wartungen`,
        'Die Wartungen des Projekts konnten nicht geladen werden.');

/** Erzeugt den PDF-Bericht der abgeschlossenen Prüfung und legt ihn im Projekt ab. */
export const erstelleBericht = (protokollId: number) =>
    anfrage<WartungBericht>('POST', `/api/wartung/protokolle/${encodeURIComponent(String(protokollId))}/bericht`,
        'Der Bericht konnte nicht erstellt werden.');

export const fuegePruefpunktHinzu = (protokollId: number, label: string, abschnittId: number | null) =>
    anfrage<WartungProtokoll>('POST', `/api/wartung/protokolle/${encodeURIComponent(String(protokollId))}/pruefpunkte`,
        'Der Prüfpunkt konnte nicht ergänzt werden.', { abschnittId, label });

export const aenderePruefpunktText = (protokollId: number, pruefpunktId: number, label: string) =>
    anfrage<WartungProtokoll>('PUT', `/api/wartung/protokolle/${encodeURIComponent(String(protokollId))}/pruefpunkte/${encodeURIComponent(String(pruefpunktId))}/text`,
        'Der Prüfpunkt konnte nicht geändert werden.', { label });

export const loeschePruefpunkt = (protokollId: number, pruefpunktId: number) =>
    anfrage<WartungProtokoll>('DELETE', `/api/wartung/protokolle/${encodeURIComponent(String(protokollId))}/pruefpunkte/${encodeURIComponent(String(pruefpunktId))}`,
        'Der Prüfpunkt konnte nicht gelöscht werden.');

/** Ergänzt ein Datenfeld im laufenden Feldeinsatz (nur dieses Protokoll). Ohne Modul steht es unter "Allgemein". */
export const fuegeDatenfeldHinzu = (protokollId: number, e: DatenfeldNeuEingabe) =>
    anfrage<WartungProtokoll>('POST', `/api/wartung/protokolle/${encodeURIComponent(String(protokollId))}/datenfelder`,
        'Das Datenfeld konnte nicht ergänzt werden.',
        { modulKey: e.modulKey, label: e.label.trim(), feldtyp: e.feldtyp, optionen: e.feldtyp === 'AUSWAHL' ? e.optionen : null });

/** Ändert die Bezeichnung eines Datenfelds; bei einer Auswahl auch die Möglichkeiten (ohne Angabe bleiben sie). */
export const aendereDatenfeldDefinition = (protokollId: number, datenfeldId: number, label: string, optionen: string[] | null) =>
    anfrage<WartungProtokoll>('PUT', `/api/wartung/protokolle/${encodeURIComponent(String(protokollId))}/datenfelder/${encodeURIComponent(String(datenfeldId))}/definition`,
        'Das Datenfeld konnte nicht geändert werden.', { label: label.trim(), optionen });

export const loescheDatenfeld = (protokollId: number, datenfeldId: number) =>
    anfrage<WartungProtokoll>('DELETE', `/api/wartung/protokolle/${encodeURIComponent(String(protokollId))}/datenfelder/${encodeURIComponent(String(datenfeldId))}`,
        'Das Datenfeld konnte nicht gelöscht werden.');

/** Die gespeicherte Unterschrift des angemeldeten Mitarbeiters, oder null, wenn es keine gibt. */
export async function ladeMeineUnterschrift(): Promise<GespeicherteUnterschrift | null> {
    let res: Response;
    try {
        res = await fetch('/api/wartung/unterschrift/meine');
    } catch {
        throw new WartungFehler('Der Server ist nicht erreichbar. Bitte die Verbindung prüfen und noch einmal versuchen.', 0);
    }
    if (res.status === 204) return null;
    if (!res.ok) throw await fehlerAus(res, 'Die gespeicherte Unterschrift konnte nicht geladen werden.');
    return (await res.json()) as GespeicherteUnterschrift;
}

export const speichereMeineUnterschrift = (bild: string, name: string) =>
    anfrage<GespeicherteUnterschrift>('PUT', '/api/wartung/unterschrift/meine', 'Die Unterschrift konnte nicht gespeichert werden.',
        { bild, name: leerZuNull(name) });

export async function loescheMeineUnterschrift(): Promise<void> {
    let res: Response;
    try {
        res = await fetch('/api/wartung/unterschrift/meine', { method: 'DELETE' });
    } catch {
        throw new WartungFehler('Der Server ist nicht erreichbar. Bitte die Verbindung prüfen und noch einmal versuchen.', 0);
    }
    if (!res.ok) throw await fehlerAus(res, 'Die Unterschrift konnte nicht gelöscht werden.');
}

/** Setzt die gespeicherte Unterschrift des angemeldeten Mitarbeiters als Techniker-Unterschrift ein (als Kopie). */
export const nutzeGespeicherteUnterschrift = (protokollId: number) =>
    anfrage<WartungProtokoll>('POST', `/api/wartung/protokolle/${encodeURIComponent(String(protokollId))}/unterschrift/gespeichert`,
        'Die gespeicherte Unterschrift konnte nicht eingesetzt werden.');

/** Die Projekte eines Kunden für die Auswahl bei der Zuordnung einer Wartung. */
export const ladeProjekteDesKunden = (kundeId: number) =>
    anfrage<Bezug[]>('GET', `/api/wartung/kunden/${encodeURIComponent(String(kundeId))}/projekte`, 'Die Projekte konnten nicht geladen werden.');

// ── Wartungsvertrag eines Projekts ──────────────────────────────────────────

const vertragPfad = (projektId: number) => `/api/wartung/projekte/${encodeURIComponent(String(projektId))}/vertrag`;

/** Der Vertrag des Projekts; {@code null}, wenn es noch keinen gibt. */
export async function ladeVertrag(projektId: number): Promise<WartungVertrag | null> {
    let res: Response;
    try {
        res = await fetch(vertragPfad(projektId));
    } catch {
        throw new WartungFehler('Der Server ist nicht erreichbar. Bitte die Verbindung prüfen und noch einmal versuchen.', 0);
    }
    if (res.status === 204) return null;
    if (!res.ok) throw await fehlerAus(res, 'Der Wartungsvertrag konnte nicht geladen werden.');
    return (await res.json()) as WartungVertrag;
}

export const speichereVertrag = (projektId: number, e: WartungVertragEingabe) =>
    anfrage<WartungVertrag>('PUT', vertragPfad(projektId), 'Der Wartungsvertrag konnte nicht gespeichert werden.', e);

export async function loescheVertrag(projektId: number): Promise<void> {
    let res: Response;
    try {
        res = await fetch(vertragPfad(projektId), { method: 'DELETE' });
    } catch {
        throw new WartungFehler('Der Server ist nicht erreichbar. Bitte die Verbindung prüfen und noch einmal versuchen.', 0);
    }
    if (!res.ok) throw await fehlerAus(res, 'Der Wartungsvertrag konnte nicht gelöscht werden.');
}
