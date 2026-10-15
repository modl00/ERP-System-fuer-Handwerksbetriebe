import { afterEach, describe, expect, it, vi } from 'vitest';
import { kundeName, sucheKunden } from './kundenSuche';

function antwort(kunden: { id: number; name: string; ort?: string }[]) {
    return Promise.resolve({ ok: true, json: () => Promise.resolve({ kunden }) } as Response);
}

describe('Kundensuche', () => {
    afterEach(() => vi.unstubAllGlobals());

    it('merkt sich den Namen, auch wenn eine spätere Suche den Kunden nicht mehr enthält', async () => {
        const abruf = vi.fn()
            .mockReturnValueOnce(antwort([{ id: 7, name: 'Max Mustermann GmbH', ort: 'Würzburg' }]))
            .mockReturnValueOnce(antwort([{ id: 8, name: 'Erika Beispiel AG' }]));
        vi.stubGlobal('fetch', abruf);

        await sucheKunden('max');
        await sucheKunden('erika');

        expect(kundeName('7')).toBe('Max Mustermann GmbH');
        expect(kundeName('8')).toBe('Erika Beispiel AG');
        expect(kundeName('999')).toBe('');
    });

    it('kodiert den Suchbegriff und stellt für die Filteransicht "Alle Kunden" voran', async () => {
        const abruf = vi.fn().mockReturnValue(antwort([{ id: 1, name: 'A & B' }]));
        vi.stubGlobal('fetch', abruf);

        const treffer = await sucheKunden('a&b ü', true);

        expect(abruf.mock.calls[0][0]).toContain('q=a%26b%20%C3%BC');
        expect(treffer[0]).toEqual({ value: '', label: 'Alle Kunden' });
        expect(treffer).toHaveLength(2);
    });

    it('meldet einen Serverfehler mit deutscher Meldung', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false } as Response));
        await expect(sucheKunden('x')).rejects.toThrow('Die Kunden konnten nicht geladen werden.');
    });
});
