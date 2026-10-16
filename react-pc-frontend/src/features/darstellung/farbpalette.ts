/**
 * Programmfarbe: aus EINER Hauptfarbe (Stufe 600) werden die übrigen Stufen berechnet, die das Programm unter dem
 * Namen „rose“ verwendet (siehe tailwind.config.js). Hell wird zur Hauptfarbe hin mit Weiß gemischt, dunkel mit Schwarz.
 * Das Standard-Rosé bleibt unberührt, solange keine Farbe gewählt ist.
 */

export type Stufe = 50 | 100 | 200 | 300 | 400 | 500 | 600 | 700 | 800 | 900 | 950;

export const STUFEN: readonly Stufe[] = [50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 950];

/** Anteil Weiß (positiv) oder Schwarz (negativ), der unter die Hauptfarbe gemischt wird. */
const MISCHUNG: Record<Stufe, number> = {
    50: 0.94, 100: 0.88, 200: 0.76, 300: 0.58, 400: 0.38, 500: 0.18,
    600: 0,
    700: -0.16, 800: -0.32, 900: -0.46, 950: -0.7,
};

export interface FarbPreset {
    name: string;
    /** Hauptfarbe (Stufe 600) als #rrggbb. */
    farbe: string;
}

/** Fertige Auswahl. Das erste ist das mitgelieferte Rosé; die übrigen sind für weißen Text auf der Hauptfarbe ausreichend dunkel. */
export const FARB_PRESETS: readonly FarbPreset[] = [
    { name: 'Rosé (Standard)', farbe: '#e11d48' },
    { name: 'Rot', farbe: '#dc2626' },
    { name: 'Orange', farbe: '#c2410c' },
    { name: 'Gelb-Braun', farbe: '#a16207' },
    { name: 'Grün', farbe: '#15803d' },
    { name: 'Türkis', farbe: '#0f766e' },
    { name: 'Blau', farbe: '#1d4ed8' },
    { name: 'Violett', farbe: '#7e22ce' },
    { name: 'Anthrazit', farbe: '#334155' },
];

export const STANDARD_FARBE = FARB_PRESETS[0].farbe;

const HEX = /^#[0-9a-fA-F]{6}$/;

/** Ist der Text eine Farbe wie {@code #e11d48}? */
export function istGueltigeFarbe(wert: string): boolean {
    return HEX.test(wert);
}

/** Tippt jemand ohne Raute oder in Großbuchstaben, wird das zu {@code #rrggbb}; sonst {@code null}. */
export function normalisiereFarbe(eingabe: string): string | null {
    const t = eingabe.trim();
    const mitRaute = t.startsWith('#') ? t : `#${t}`;
    return istGueltigeFarbe(mitRaute) ? mitRaute.toLowerCase() : null;
}

function inRgb(hex: string): [number, number, number] {
    return [parseInt(hex.slice(1, 3), 16), parseInt(hex.slice(3, 5), 16), parseInt(hex.slice(5, 7), 16)];
}

/** Die Palette als {@code "r g b"} je Stufe, passend zu {@code rgb(var(--farbe-600) / <alpha-value>)}. */
export function erzeugePalette(hex: string): Record<Stufe, string> {
    if (!istGueltigeFarbe(hex)) throw new Error('Keine gültige Farbe.');
    const basis = inRgb(hex);
    const ergebnis = {} as Record<Stufe, string>;
    for (const stufe of STUFEN) {
        const m = MISCHUNG[stufe];
        const ziel = m >= 0 ? 255 : 0;
        const anteil = Math.abs(m);
        const kanaele = basis.map((k) => Math.round(k + (ziel - k) * anteil));
        ergebnis[stufe] = kanaele.join(' ');
    }
    return ergebnis;
}

/** Relative Helligkeit (0 dunkel bis 1 hell) nach WCAG, für die Kontrastwarnung. */
function helligkeit(hex: string): number {
    const [r, g, b] = inRgb(hex).map((k) => {
        const c = k / 255;
        return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
    });
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

/** Kontrast zwischen weißem Text und der Hauptfarbe (1 bis 21). Unter 4,5 wird Weiß auf Schaltflächen schwer lesbar. */
export function kontrastZuWeiss(hex: string): number {
    return 1.05 / (helligkeit(hex) + 0.05);
}
