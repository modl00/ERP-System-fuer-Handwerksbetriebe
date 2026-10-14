import { hatFirmenBriefpapier, blöckeOhneLogoWennKopfVorhanden } from './vorlagenEinstellungen';
import type { FirmaBriefpapierInfo } from './vorlagenEinstellungen';
import type { FormBlock } from '../../types';

const ohneKopf: FirmaBriefpapierInfo = { briefpapierDateiname: 'bp.png', briefpapierEnthaeltKopf: false };
const mitKopf: FirmaBriefpapierInfo = { briefpapierDateiname: 'bp.png', briefpapierEnthaeltKopf: true };
const ohneDateiname: FirmaBriefpapierInfo = { briefpapierDateiname: null, briefpapierEnthaeltKopf: false };

const blöcke: FormBlock[] = [
    { id: '1', type: 'logo' } as FormBlock,
    { id: '2', type: 'text' } as FormBlock,
    { id: '3', type: 'logo' } as FormBlock,
];

describe('hatFirmenBriefpapier', () => {
    it('gibt true zurück, wenn ein Dateiname vorhanden ist', () => {
        expect(hatFirmenBriefpapier(ohneKopf)).toBe(true);
    });

    it('gibt false zurück, wenn kein Dateiname vorhanden ist', () => {
        expect(hatFirmenBriefpapier(ohneDateiname)).toBe(false);
    });
});

describe('blöckeOhneLogoWennKopfVorhanden', () => {
    it('gibt alle Blöcke zurück, wenn kein Kopf vorhanden', () => {
        expect(blöckeOhneLogoWennKopfVorhanden(blöcke, ohneKopf, true)).toHaveLength(3);
    });

    it('filtert Logo-Blöcke heraus, wenn Kopf vorhanden und Briefpapier aktiv', () => {
        const result = blöckeOhneLogoWennKopfVorhanden(blöcke, mitKopf, true);
        expect(result).toHaveLength(1);
        expect(result[0].type).toBe('text');
    });

    it('gibt alle Blöcke zurück, wenn firmenBriefpapierAktiv false', () => {
        expect(blöckeOhneLogoWennKopfVorhanden(blöcke, mitKopf, false)).toHaveLength(3);
    });

    it('gibt alle Blöcke zurück, wenn kein Dateiname vorhanden', () => {
        const mitKopfOhneDate: FirmaBriefpapierInfo = { briefpapierDateiname: null, briefpapierEnthaeltKopf: true };
        expect(blöckeOhneLogoWennKopfVorhanden(blöcke, mitKopfOhneDate, true)).toHaveLength(3);
    });

    it('gibt leere Liste zurück, wenn alle Blöcke Logos sind und Kopf aktiv', () => {
        const nurLogos = blöcke.filter(b => b.type === 'logo');
        expect(blöckeOhneLogoWennKopfVorhanden(nurLogos, mitKopf, true)).toHaveLength(0);
    });
});
