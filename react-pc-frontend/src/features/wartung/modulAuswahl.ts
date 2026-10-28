import type { ModulAuswahl, WartungModul } from './types';

/**
 * Regeln der Modulauswahl. Sie spiegeln, was das Backend prüft, damit der Nutzer gar nicht erst eine
 * Kombination wählen kann, die abgelehnt würde: ein Basis-Element, Zusatzmodule nur an passende
 * Basis-Elemente, bei Modulen mit Bauarten genau eine Bauart.
 */

export function basisModule(module: WartungModul[]): WartungModul[] {
    return module.filter((m) => m.kategorie === 'BASIS');
}

export function gewaehltesBasis(module: WartungModul[], auswahl: ModulAuswahl[]): WartungModul | undefined {
    const ids = new Set(auswahl.map((a) => a.modulId));
    return basisModule(module).find((m) => ids.has(m.id));
}

/** Zusatzmodule, die zum gewählten Basis-Element passen (ohne Basis nur die ohne Andock-Vorgabe). */
export function passendeZusatzModule(module: WartungModul[], basis: WartungModul | undefined): WartungModul[] {
    return module.filter((m) => m.kategorie === 'ZUSATZ'
        && (m.andockbarAnBasisModulIds.length === 0 || (basis !== undefined && m.andockbarAnBasisModulIds.includes(basis.id))));
}

/** Wählt ein Basis-Element. Das alte Basis-Element und Zusatzmodule, die nicht mehr passen, fallen weg. */
export function waehleBasis(module: WartungModul[], auswahl: ModulAuswahl[], basisId: number): ModulAuswahl[] {
    const neu = module.find((m) => m.id === basisId && m.kategorie === 'BASIS');
    if (!neu) return auswahl;
    const passend = new Set(passendeZusatzModule(module, neu).map((m) => m.id));
    const behalten = auswahl.filter((a) => passend.has(a.modulId));
    const bisher = auswahl.find((a) => a.modulId === basisId);
    return [{ modulId: basisId, untertypSchluessel: bisher?.untertypSchluessel ?? null }, ...behalten];
}

/** Schaltet ein Zusatzmodul an oder aus. Nicht passende Module werden nicht angenommen. */
export function schalteZusatz(module: WartungModul[], auswahl: ModulAuswahl[], zusatzId: number): ModulAuswahl[] {
    if (auswahl.some((a) => a.modulId === zusatzId)) {
        return auswahl.filter((a) => a.modulId !== zusatzId);
    }
    const erlaubt = passendeZusatzModule(module, gewaehltesBasis(module, auswahl)).some((m) => m.id === zusatzId);
    return erlaubt ? [...auswahl, { modulId: zusatzId, untertypSchluessel: null }] : auswahl;
}

export function setzeBauart(auswahl: ModulAuswahl[], modulId: number, schluessel: string | null): ModulAuswahl[] {
    return auswahl.map((a) => (a.modulId === modulId ? { ...a, untertypSchluessel: schluessel } : a));
}

/**
 * Was noch fehlt, bevor die Auswahl gespeichert werden kann, als Sätze für den Nutzer.
 * Eine leere Liste heißt: vollständig.
 */
export function fehlendeAngaben(module: WartungModul[], auswahl: ModulAuswahl[]): string[] {
    const fehlt: string[] = [];
    if (auswahl.length === 0) {
        fehlt.push('Bitte wählen, was geprüft wird.');
        return fehlt;
    }
    if (basisModule(module).length > 0 && !gewaehltesBasis(module, auswahl)) {
        fehlt.push('Bitte ein Basis-Element wählen (zum Beispiel Brandschutztür).');
    }
    for (const eintrag of auswahl) {
        const modul = module.find((m) => m.id === eintrag.modulId);
        if (!modul) {
            fehlt.push('Ein gewähltes Modul gibt es nicht mehr. Bitte neu wählen.');
            continue;
        }
        if (modul.untertypen.length > 0 && !modul.untertypen.some((u) => u.schluessel === eintrag.untertypSchluessel)) {
            fehlt.push(`Bitte die Bauart für „${modul.name}" wählen.`);
        }
    }
    return fehlt;
}

/** Kürzestes Standard-Intervall der gewählten Module in Monaten (12, wenn nichts gewählt ist). */
export function kuerzestesIntervall(module: WartungModul[], auswahl: ModulAuswahl[]): number {
    const intervalle = auswahl
        .map((a) => module.find((m) => m.id === a.modulId)?.standardIntervallMonate)
        .filter((n): n is number => typeof n === 'number' && n > 0);
    return intervalle.length > 0 ? Math.min(...intervalle) : 12;
}

/**
 * Bereinigt eine vorbelegte Auswahl (zum Beispiel von einer Tür) gegen den aktuellen Katalog: Module, die
 * es nicht mehr gibt oder die nicht mehr aktiv sind, fallen heraus.
 */
export function bereinigeAuswahl(module: WartungModul[], auswahl: ModulAuswahl[]): ModulAuswahl[] {
    const vorhanden = new Set(module.map((m) => m.id));
    return auswahl.filter((a) => vorhanden.has(a.modulId));
}
