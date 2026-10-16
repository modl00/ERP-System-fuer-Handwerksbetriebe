/**
 * Merkt sich die letzten fehlgeschlagenen Aufrufe und Fehler im Browser (#255), damit der KI-Chat sie einem Fehlerbericht mitgeben kann. Es bleibt nur im Speicher dieser
 * Seite; gemerkt wird nur Zeit, Methode, Adresse ohne Parameter, Statuscode und eine kurze Meldung, nie der Inhalt der Anfrage. Weitergegeben wird nichts, solange ein
 * Administrator einen Fehlerbericht nicht bestätigt.
 */

export interface BrowserFehler {
    zeit: string;
    methode: string;
    pfad: string;
    status: number | null;
    meldung: string;
}

const MAX = 20;
const MAX_MELDUNG = 200;
const eintraege: BrowserFehler[] = [];
let installiert = false;

const uhrzeit = (d = new Date()) => d.toLocaleTimeString('de-DE', { hour12: false });
const kuerze = (s: string, max = MAX_MELDUNG) => (s.length > max ? `${s.slice(0, max)} …` : s).replace(/\s+/g, ' ').trim();

function merke(f: BrowserFehler) {
    const letzter = eintraege[eintraege.length - 1];
    // Derselbe Fehler direkt hintereinander (zum Beispiel ein Abruf im Takt) wird nur einmal gemerkt
    if (letzter && letzter.methode === f.methode && letzter.pfad === f.pfad && letzter.status === f.status && letzter.meldung === f.meldung) {
        letzter.zeit = f.zeit;
        return;
    }
    eintraege.push(f);
    while (eintraege.length > MAX) eintraege.shift();
}

/** Der Pfad einer Adresse ohne Parameter; nur Adressen dieses Programms (/api/…), alles andere gilt als fremd. */
export function apiPfad(eingabe: RequestInfo | URL): string | null {
    let roh: string;
    try {
        roh = typeof eingabe === 'string' ? eingabe : eingabe instanceof URL ? eingabe.pathname : (eingabe as Request).url;
        const u = new URL(roh, window.location.origin);
        if (u.origin !== window.location.origin) return null;
        return u.pathname.startsWith('/api/') ? u.pathname : null;
    } catch {
        return null;
    }
}

/** Sammelt die Fehler. Mehrfach aufrufbar; wirkt nur einmal. */
export function installiereBrowserFehler(): void {
    if (installiert || typeof window === 'undefined') return;
    installiert = true;
    const original = window.fetch.bind(window);
    window.fetch = async (eingabe: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
        const pfad = apiPfad(eingabe);
        const methode = (init?.method ?? (typeof eingabe === 'object' && 'method' in eingabe ? (eingabe as Request).method : 'GET')).toUpperCase();
        try {
            const res = await original(eingabe, init);
            if (pfad && !res.ok) {
                const zeit = uhrzeit();
                // Die Meldung des Servers lesen, ohne die Antwort für den Aufrufer zu verbrauchen
                res.clone().text().then(text => {
                    let meldung = '';
                    try {
                        const j = JSON.parse(text) as { message?: unknown; error?: unknown };
                        meldung = typeof j.message === 'string' ? j.message : typeof j.error === 'string' ? j.error : '';
                    } catch { /* kein JSON: ohne Meldung */ }
                    merke({ zeit, methode, pfad, status: res.status, meldung: kuerze(meldung) });
                }, () => merke({ zeit, methode, pfad, status: res.status, meldung: '' }));
            }
            return res;
        } catch (e) {
            if (pfad) merke({ zeit: uhrzeit(), methode, pfad, status: null, meldung: kuerze(e instanceof Error ? e.message : 'Keine Verbindung') });
            throw e;
        }
    };
    window.addEventListener('error', e => {
        merke({ zeit: uhrzeit(), methode: 'FEHLER', pfad: '/seite', status: null, meldung: kuerze(e.message || 'Unbekannter Fehler im Browser') });
    });
    window.addEventListener('unhandledrejection', e => {
        const grund = e.reason instanceof Error ? e.reason.message : typeof e.reason === 'string' ? e.reason : 'Unbehandelter Fehler';
        merke({ zeit: uhrzeit(), methode: 'FEHLER', pfad: '/seite', status: null, meldung: kuerze(grund) });
    });
}

/** Die letzten Fehler, älteste zuerst, höchstens {@code anzahl}. */
export function holeBrowserFehler(anzahl = 10): BrowserFehler[] {
    return eintraege.slice(-anzahl).map(f => ({ ...f }));
}

/** Nur für Tests. */
export function leereBrowserFehler(): void {
    eintraege.length = 0;
}
