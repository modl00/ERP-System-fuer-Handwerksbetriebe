import { describe, expect, it } from 'vitest';
import { istFreigegeben } from './freigaben';

describe('istFreigegeben', () => {
    it('lässt alle Seiten durch', () => {
        expect(istFreigegeben('/bestellungen')).toBe(true);
        expect(istFreigegeben('/projekte')).toBe(true);
        expect(istFreigegeben('/bestellungen/bedarf')).toBe(true);
    });
});
