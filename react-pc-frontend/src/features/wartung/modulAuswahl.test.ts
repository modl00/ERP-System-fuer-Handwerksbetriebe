import { describe, expect, it } from 'vitest';
import {
    bereinigeAuswahl,
    fehlendeAngaben,
    gewaehltesBasis,
    kuerzestesIntervall,
    passendeZusatzModule,
    schalteZusatz,
    setzeBauart,
    waehleBasis,
} from './modulAuswahl';
import type { WartungModul } from './types';

const modul = (id: number, name: string, kategorie: 'BASIS' | 'ZUSATZ', extra: Partial<WartungModul> = {}): WartungModul => ({
    id, modulKey: `m_${id}`, name, beschreibung: null, icon: null, kategorie, systemModul: true,
    standardIntervallMonate: 12, andockbarAnBasisModulIds: [], untertypen: [], anzahlPruefpunkte: 10, ...extra,
});

const BAUARTEN = [{ schluessel: 'einflg', label: '1-flügelig' }, { schluessel: 'zweiflg', label: '2-flügelig' }];

const BST = modul(1, 'Brandschutztür', 'BASIS', { untertypen: BAUARTEN, standardIntervallMonate: 12 });
const RS = modul(2, 'Rauchschutztür', 'BASIS', { untertypen: BAUARTEN, standardIntervallMonate: 6 });
const FSA = modul(3, 'Feststellanlage', 'ZUSATZ', { andockbarAnBasisModulIds: [1, 2], untertypen: [{ schluessel: 'magnet', label: 'Haftmagnet' }] });
const BODEN = modul(4, 'Bodendichtung', 'ZUSATZ', { andockbarAnBasisModulIds: [2] });
const GLAS = modul(5, 'Glasfüllung', 'ZUSATZ');
const KATALOG = [BST, RS, FSA, BODEN, GLAS];

describe('Zusatzmodule passend zum Basis-Element', () => {
    it('zeigt ohne Basis-Element nur Module ohne Andock-Vorgabe', () => {
        expect(passendeZusatzModule(KATALOG, undefined).map((m) => m.name)).toEqual(['Glasfüllung']);
    });

    it('zeigt zur Brandschutztür Feststellanlage und Glasfüllung, aber keine Bodendichtung', () => {
        expect(passendeZusatzModule(KATALOG, BST).map((m) => m.name)).toEqual(['Feststellanlage', 'Glasfüllung']);
    });

    it('zeigt zur Rauchschutztür auch die Bodendichtung', () => {
        expect(passendeZusatzModule(KATALOG, RS).map((m) => m.name)).toEqual(['Feststellanlage', 'Bodendichtung', 'Glasfüllung']);
    });
});

describe('waehleBasis', () => {
    it('setzt das Basis-Element ohne Bauart, damit sie bewusst gewählt wird', () => {
        expect(waehleBasis(KATALOG, [], 1)).toEqual([{ modulId: 1, untertypSchluessel: null }]);
    });

    it('ersetzt das alte Basis-Element und behält passende Zusatzmodule', () => {
        const vorher = [{ modulId: 1, untertypSchluessel: 'einflg' }, { modulId: 3, untertypSchluessel: 'magnet' }];
        const nachher = waehleBasis(KATALOG, vorher, 2);
        expect(nachher.map((a) => a.modulId)).toEqual([2, 3]);
        expect(nachher[0].untertypSchluessel).toBeNull();
        expect(nachher[1].untertypSchluessel).toBe('magnet');
    });

    it('wirft Zusatzmodule heraus, die zum neuen Basis-Element nicht passen', () => {
        const vorher = [{ modulId: 2, untertypSchluessel: 'einflg' }, { modulId: 4, untertypSchluessel: null }];
        expect(waehleBasis(KATALOG, vorher, 1).map((a) => a.modulId)).toEqual([1]);
    });

    it('behält die Bauart, wenn dasselbe Basis-Element noch einmal gewählt wird', () => {
        const vorher = [{ modulId: 1, untertypSchluessel: 'zweiflg' }];
        expect(waehleBasis(KATALOG, vorher, 1)[0].untertypSchluessel).toBe('zweiflg');
    });

    it('ignoriert ein Zusatzmodul oder eine unbekannte Id als Basis-Element', () => {
        const vorher = [{ modulId: 1, untertypSchluessel: 'einflg' }];
        expect(waehleBasis(KATALOG, vorher, 3)).toBe(vorher);
        expect(waehleBasis(KATALOG, vorher, 999)).toBe(vorher);
    });
});

describe('schalteZusatz', () => {
    const mitBst = [{ modulId: 1, untertypSchluessel: 'einflg' }];

    it('schaltet ein passendes Zusatzmodul an und wieder aus', () => {
        const an = schalteZusatz(KATALOG, mitBst, 3);
        expect(an.map((a) => a.modulId)).toEqual([1, 3]);
        expect(schalteZusatz(KATALOG, an, 3).map((a) => a.modulId)).toEqual([1]);
    });

    it('lehnt ein unpassendes Zusatzmodul ab', () => {
        expect(schalteZusatz(KATALOG, mitBst, 4)).toBe(mitBst);
    });

    it('lehnt ein Zusatzmodul mit Andock-Vorgabe ab, solange kein Basis-Element gewählt ist', () => {
        expect(schalteZusatz(KATALOG, [], 3)).toEqual([]);
        expect(schalteZusatz(KATALOG, [], 5).map((a) => a.modulId)).toEqual([5]);
    });
});

describe('setzeBauart und gewaehltesBasis', () => {
    it('setzt die Bauart nur am angegebenen Modul', () => {
        const auswahl = [{ modulId: 1, untertypSchluessel: null }, { modulId: 3, untertypSchluessel: null }];
        expect(setzeBauart(auswahl, 3, 'magnet')).toEqual([
            { modulId: 1, untertypSchluessel: null }, { modulId: 3, untertypSchluessel: 'magnet' }]);
    });

    it('findet das gewählte Basis-Element', () => {
        expect(gewaehltesBasis(KATALOG, [{ modulId: 3, untertypSchluessel: null }, { modulId: 2, untertypSchluessel: null }])?.name).toBe('Rauchschutztür');
        expect(gewaehltesBasis(KATALOG, [{ modulId: 3, untertypSchluessel: null }])).toBeUndefined();
    });
});

describe('fehlendeAngaben', () => {
    it('verlangt zuerst, dass überhaupt etwas gewählt ist', () => {
        expect(fehlendeAngaben(KATALOG, [])).toEqual(['Bitte wählen, was geprüft wird.']);
    });

    it('verlangt ein Basis-Element', () => {
        expect(fehlendeAngaben(KATALOG, [{ modulId: 5, untertypSchluessel: null }])[0]).toMatch(/Basis-Element/);
    });

    it('verlangt die Bauart für Module mit Bauarten', () => {
        const fehlt = fehlendeAngaben(KATALOG, [{ modulId: 1, untertypSchluessel: null }]);
        expect(fehlt).toEqual(['Bitte die Bauart für „Brandschutztür“ wählen.']);
    });

    it('verlangt die Bauart auch für das Zusatzmodul', () => {
        const fehlt = fehlendeAngaben(KATALOG, [{ modulId: 1, untertypSchluessel: 'einflg' }, { modulId: 3, untertypSchluessel: null }]);
        expect(fehlt).toEqual(['Bitte die Bauart für „Feststellanlage“ wählen.']);
    });

    it('lehnt eine Bauart ab, die es für das Modul nicht gibt', () => {
        expect(fehlendeAngaben(KATALOG, [{ modulId: 1, untertypSchluessel: 'schiebe' }])).toHaveLength(1);
    });

    it('meldet nichts, wenn alles vollständig ist', () => {
        expect(fehlendeAngaben(KATALOG, [
            { modulId: 1, untertypSchluessel: 'zweiflg' }, { modulId: 3, untertypSchluessel: 'magnet' }, { modulId: 5, untertypSchluessel: null },
        ])).toEqual([]);
    });

    it('meldet ein Modul, das es im Katalog nicht mehr gibt', () => {
        expect(fehlendeAngaben(KATALOG, [{ modulId: 1, untertypSchluessel: 'einflg' }, { modulId: 99, untertypSchluessel: null }])[0]).toMatch(/gibt es nicht mehr/);
    });
});

describe('Intervall und Bereinigung', () => {
    it('nimmt das kürzeste Standard-Intervall der gewählten Module', () => {
        expect(kuerzestesIntervall(KATALOG, [{ modulId: 1, untertypSchluessel: null }, { modulId: 2, untertypSchluessel: null }])).toBe(6);
    });

    it('nimmt 12 Monate, wenn nichts gewählt ist oder das Modul fehlt', () => {
        expect(kuerzestesIntervall(KATALOG, [])).toBe(12);
        expect(kuerzestesIntervall(KATALOG, [{ modulId: 99, untertypSchluessel: null }])).toBe(12);
    });

    it('wirft Module aus einer Vorbelegung, die es nicht mehr gibt', () => {
        const vorbelegung = [{ modulId: 1, untertypSchluessel: 'einflg' }, { modulId: 99, untertypSchluessel: null }];
        expect(bereinigeAuswahl(KATALOG, vorbelegung)).toEqual([{ modulId: 1, untertypSchluessel: 'einflg' }]);
    });
});
