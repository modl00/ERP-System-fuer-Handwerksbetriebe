/** Kostensätze und Verrechnungssätze im Überblick (Stufe 3a von #166): nur Anzeige. */

export interface SatzZeile {
    art: 'BETRIEB' | 'GRUPPE' | 'PERSON';
    name: string;
    anzahl: number;
    lohnkosten: number;
    stunden: number;
    personalkostensatz: number | null;
    umlage: number | null;
    kostensatz: number | null;
    wagnisBetrag: number | null;
    gewinnBetrag: number | null;
    verrechnungssatz: number | null;
    hinweis: string | null;
}

/** Eine Fixkosten-Kostenstelle in den Gemeinkosten (#237): geschätzt im Jahr (Soll), echt bisher (Ist) und der Wert, mit dem gerechnet wird. */
export interface KostenstelleZeile {
    id: number;
    name: string;
    soll: number | null;
    ist: number;
    verwendet: number;
    sollGilt: boolean;
}

export interface Gemeinkosten {
    kostenstellen: KostenstelleZeile[];
    gesamtbetrag: number | null;
    gesamtbetragGilt: boolean;
    enthaeltSchaetzwerte: boolean;
    sollVorhanden: boolean;
}

export interface Kostensaetze {
    jahr: number;
    modus: 'RUECKWIRKEND' | 'HOCHRECHNUNG' | null;
    lohnkostenGesamt: number;
    stundenGesamt: number;
    gemeinkostenGesamt: number;
    umlage: number | null;
    wagnisProzent: number;
    gewinnProzent: number;
    betrieb: SatzZeile;
    gruppen: SatzZeile[];
    personen: SatzZeile[];
    hinweise: string[];
    gemeinkosten: Gemeinkosten;
}

export async function ladeKostensaetze(jahr: number, wagnis: number, gewinn: number, interneQuote: number | null, signal?: AbortSignal): Promise<Kostensaetze> {
    const q = new URLSearchParams({ jahr: String(jahr), wagnis: String(wagnis), gewinn: String(gewinn) });
    if (interneQuote != null) q.set('interneQuote', String(interneQuote));
    const res = await fetch(`/api/kostenrechnung/kostensaetze?${q.toString()}`, { signal });
    if (!res.ok) {
        let text = 'Die Sätze konnten nicht berechnet werden.';
        try {
            const body = await res.json() as { message?: string };
            if (body?.message) text = body.message;
        } catch { /* kein JSON */ }
        throw new Error(text);
    }
    return res.json() as Promise<Kostensaetze>;
}

// ── Übernahme des Verrechnungssatzes in die Arbeitsgänge, mit Vorschau (Stufe 3b) ──

export interface UebernahmeZeile {
    arbeitsgangId: number;
    name: string;
    abteilung: string | null;
    alt: number | null;
    /** Leer, wenn es für die gewählte Qualifikation keinen Satz gibt (art KEIN_SATZ). */
    neu: number | null;
    differenz: number | null;
    art: 'NEU' | 'GLEICH' | 'AENDERT' | 'KEIN_SATZ';
    quelle?: 'EIGEN' | 'VERRECHNUNG';
    gruppe?: string | null;
}

export interface UebernahmeVorschau {
    jahr: number;
    neuerSatz: number;
    zeilen: UebernahmeZeile[];
}

export interface UebernahmeParameter {
    jahr: number;
    wagnis: number;
    gewinn: number;
    interneQuote: number | null;
}

async function fehlerText(res: Response, vorgabe: string): Promise<string> {
    try {
        const body = await res.json() as { message?: string };
        if (body?.message) return body.message;
    } catch { /* kein JSON */ }
    return vorgabe;
}

export async function ladeUebernahmeVorschau(p: UebernahmeParameter): Promise<UebernahmeVorschau> {
    const q = new URLSearchParams({ jahr: String(p.jahr), wagnis: String(p.wagnis), gewinn: String(p.gewinn) });
    if (p.interneQuote != null) q.set('interneQuote', String(p.interneQuote));
    const res = await fetch(`/api/kostenrechnung/uebernahme/vorschau?${q.toString()}`);
    if (!res.ok) throw new Error(await fehlerText(res, 'Die Vorschau konnte nicht berechnet werden.'));
    return res.json() as Promise<UebernahmeVorschau>;
}

/** Schreibt den Satz aus der Vorschau für die gewählten Arbeitsgänge; gibt die Anzahl der geschriebenen Sätze zurück. */
export async function uebernehmeSaetze(p: UebernahmeParameter, erwarteterSatz: number, arbeitsgangIds: number[]): Promise<number> {
    const res = await fetch('/api/kostenrechnung/uebernahme', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ jahr: p.jahr, wagnis: p.wagnis, gewinn: p.gewinn, interneQuote: p.interneQuote, erwarteterSatz, arbeitsgangIds }),
    });
    if (!res.ok) throw new Error(await fehlerText(res, 'Die Sätze konnten nicht übernommen werden.'));
    return ((await res.json()) as { geschrieben: number }).geschrieben;
}

// ── Schätzung der Gemeinkosten (#237) ──

/** Setzt (oder löscht mit {@code null}) die Schätzung einer Kostenstelle für ein Jahr. */
export async function speichereSoll(kostenstelleId: number, betrag: number | null): Promise<void> {
    const res = await fetch(`/api/kostenrechnung/kostenstellen/${kostenstelleId}/soll`, {
        method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ jahresSoll: betrag }),
    });
    if (!res.ok) throw new Error(await fehlerText(res, 'Die Schätzung konnte nicht gespeichert werden.'));
}

/** Der Gesamtbetrag als Ersatz, wenn bei keiner Kostenstelle eine Schätzung steht. */
export async function ladeGesamtbetrag(): Promise<number | null> {
    const res = await fetch('/api/kostenrechnung/gemeinkosten-schaetzung');
    if (!res.ok) throw new Error(await fehlerText(res, 'Der Gesamtbetrag konnte nicht geladen werden.'));
    const body = await res.json() as { jahresbetrag: number | null };
    return body.jahresbetrag ?? null;
}

export async function speichereGesamtbetrag(betrag: number | null): Promise<void> {
    const res = await fetch('/api/kostenrechnung/gemeinkosten-schaetzung', {
        method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ jahresbetrag: betrag }),
    });
    if (!res.ok) throw new Error(await fehlerText(res, 'Der Gesamtbetrag konnte nicht gespeichert werden.'));
}

// ── Gespeicherte Vorgaben und Schätzung der internen Zeit (#308) ──

/** Leer (null) heißt: nicht gespeichert. */
export interface Vorgaben { wagnis: number | null; gewinn: number | null; interneZeit: number | null }

export interface InterneZeitSchaetzung {
    jahr: number;
    prozent: number | null;
    interneStunden: number | null;
    sollstunden: number | null;
    hinweis: string | null;
}

async function pruefe(res: Response, fallback: string): Promise<void> {
    if (res.ok) return;
    let text = fallback;
    try {
        const body = await res.json() as { message?: string };
        if (body?.message) text = body.message;
    } catch { /* kein JSON */ }
    throw new Error(text);
}

export async function ladeVorgaben(): Promise<Vorgaben> {
    const res = await fetch('/api/kostenrechnung/vorgaben');
    await pruefe(res, 'Die gespeicherten Vorgaben konnten nicht geladen werden.');
    return res.json() as Promise<Vorgaben>;
}

export async function speichereVorgaben(v: Vorgaben): Promise<Vorgaben> {
    const res = await fetch('/api/kostenrechnung/vorgaben', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(v) });
    await pruefe(res, 'Die Vorgaben konnten nicht gespeichert werden.');
    return res.json() as Promise<Vorgaben>;
}

export async function ladeInterneZeitSchaetzung(jahr: number): Promise<InterneZeitSchaetzung> {
    const res = await fetch(`/api/kostenrechnung/interne-zeit-schaetzung?jahr=${jahr}`);
    await pruefe(res, 'Die interne Zeit konnte nicht geschätzt werden.');
    return res.json() as Promise<InterneZeitSchaetzung>;
}
