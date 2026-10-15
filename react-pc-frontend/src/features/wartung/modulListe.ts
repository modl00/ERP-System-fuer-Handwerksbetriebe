import type { ModulUebersicht } from './types';

/** Grund, warum ein Modul sich nicht löschen lässt, sonst {@code undefined}. Benutzte Module bleiben, damit der Verlauf stimmt. */
export function loeschenGesperrtGrund(m: Pick<ModulUebersicht, 'systemModul' | 'anzahlTueren' | 'anzahlProtokolle'>): string | undefined {
    if (m.systemModul) return 'Mitgelieferte Module lassen sich nur deaktivieren.';
    if (m.anzahlTueren > 0 || m.anzahlProtokolle > 0) return 'Dieses Modul wird benutzt und lässt sich nur deaktivieren.';
    return undefined;
}
