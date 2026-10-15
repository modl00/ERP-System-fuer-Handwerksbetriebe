import { parseIsoDatum } from '../../lib/datum';
import type { WartungErgebnis, WartungStatus } from './types';

/** Anzeige und Farbe je Status. Die Farben folgen dem Design-System: Rose nur für Handlungsbedarf. */
export const STATUS_ANZEIGE: Record<WartungStatus, { label: string; klasse: string }> = {
    OFFEN: { label: 'Offen', klasse: 'bg-slate-100 text-slate-700 border-slate-200' },
    IN_BEARBEITUNG: { label: 'In Arbeit', klasse: 'bg-amber-50 text-amber-700 border-amber-200' },
    ABGESCHLOSSEN: { label: 'Abgeschlossen', klasse: 'bg-emerald-50 text-emerald-700 border-emerald-200' },
    ARCHIVIERT: { label: 'Archiviert', klasse: 'bg-slate-50 text-slate-500 border-slate-200' },
};

export const ERGEBNIS_ANZEIGE: Record<WartungErgebnis, { kurz: string; lang: string }> = {
    IO: { kurz: 'i.O.', lang: 'In Ordnung' },
    NIO: { kurz: 'n.i.O.', lang: 'Nicht in Ordnung' },
    NA: { kurz: 'n.a.', lang: 'Nicht anwendbar' },
};

/** Auswahl der Prüfintervalle in Monaten. */
export const INTERVALL_OPTIONEN = [1, 3, 6, 12, 24, 36, 60];

export const PROTOKOLLARTEN = ['Wartungsprotokoll', 'Instandhaltungsprotokoll', 'Prüfprotokoll', 'Inspektionsprotokoll'];

/** `2026-03-10` wird `10.03.2026`. Unlesbare Eingaben kommen unverändert zurück statt als "Invalid Date". */
export function formatDatum(iso: string | null | undefined): string {
    if (!iso) return '–';
    const datum = parseIsoDatum(iso.slice(0, 10));
    if (!datum) return iso;
    const zweistellig = (n: number) => String(n).padStart(2, '0');
    return `${zweistellig(datum.getDate())}.${zweistellig(datum.getMonth() + 1)}.${datum.getFullYear()}`;
}

/** Ist die Fälligkeit vor heute? `heute` ist als Parameter da, damit der Test nicht von der Uhr abhängt. */
export function istUeberfaellig(faelligIso: string | null | undefined, heute: string): boolean {
    if (!faelligIso) return false;
    return faelligIso.slice(0, 10) < heute;
}

/** Tage bis zur Fälligkeit (negativ = überfällig). `null`, wenn kein gültiges Datum vorliegt. */
export function tageBis(faelligIso: string | null | undefined, heute: string): number | null {
    if (!faelligIso) return null;
    const ziel = parseIsoDatum(faelligIso.slice(0, 10));
    const start = parseIsoDatum(heute);
    if (!ziel || !start) return null;
    return Math.round((ziel.getTime() - start.getTime()) / 86_400_000);
}

const PNG_PRAEFIX = 'data:image/png;base64,';

/**
 * Nur ein echtes PNG als Data-URL darf als Bild angezeigt werden. Das Backend prüft dasselbe; hier ist es
 * die zweite Sicherung, falls ein anderer Weg etwas Fremdes in das Feld geschrieben hätte.
 */
export function istPngDataUrl(wert: string | null | undefined): wert is string {
    return typeof wert === 'string' && wert.startsWith(PNG_PRAEFIX) && wert.length > PNG_PRAEFIX.length
        && /^[A-Za-z0-9+/]+={0,2}$/.test(wert.slice(PNG_PRAEFIX.length));
}
