import { describe, expect, it } from 'vitest';
import { loeschenGesperrtGrund } from './modulListe';

describe('Löschen eines Moduls', () => {
    it('ist bei einem freien, selbst angelegten Modul erlaubt', () => {
        expect(loeschenGesperrtGrund({ systemModul: false, anzahlTueren: 0, anzahlProtokolle: 0 })).toBeUndefined();
    });

    it('ist bei mitgelieferten Modulen gesperrt', () => {
        expect(loeschenGesperrtGrund({ systemModul: true, anzahlTueren: 0, anzahlProtokolle: 0 })).toContain('Mitgelieferte');
    });

    it('ist gesperrt, sobald eine Tür oder ein Protokoll das Modul benutzt', () => {
        expect(loeschenGesperrtGrund({ systemModul: false, anzahlTueren: 1, anzahlProtokolle: 0 })).toContain('benutzt');
        expect(loeschenGesperrtGrund({ systemModul: false, anzahlTueren: 0, anzahlProtokolle: 3 })).toContain('benutzt');
    });
});
