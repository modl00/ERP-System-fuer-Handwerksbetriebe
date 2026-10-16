import { afterEach, describe, expect, it, vi } from 'vitest';
import { ladeDarstellung, speichereFarbe, wendeFarbeAn, wendeGemerkteFarbeAn, merkeFarbe, synchronisiereFarbe } from './darstellung';

afterEach(() => {
    vi.restoreAllMocks();
    document.documentElement.removeAttribute('style');
    localStorage.clear();
});

describe('darstellung', () => {
    it('setzt und entfernt die Farbvariablen', () => {
        wendeFarbeAn('#0f766e');
        expect(document.documentElement.style.getPropertyValue('--farbe-600')).toBe('15 118 110');
        wendeFarbeAn(null);
        expect(document.documentElement.style.getPropertyValue('--farbe-600')).toBe('');
    });

    it('ignoriert ungültige Farben und lässt das Standard-Rosé stehen', () => {
        wendeFarbeAn('#0f766e');
        wendeFarbeAn('rot; background:url(x)');
        expect(document.documentElement.style.getPropertyValue('--farbe-600')).toBe('');
    });

    it('nimmt beim Start die gemerkte Farbe', () => {
        merkeFarbe('#1d4ed8');
        wendeGemerkteFarbeAn();
        expect(document.documentElement.style.getPropertyValue('--farbe-600')).toBe('29 78 216');
    });

    it('verwirft eine beschädigte gemerkte Farbe', () => {
        localStorage.setItem('programmfarbe', 'kaputt');
        wendeGemerkteFarbeAn();
        expect(document.documentElement.style.getPropertyValue('--farbe-600')).toBe('');
    });

    it('lädt die Farbe vom Server', async () => {
        vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ farbe: '#0f766e' }), { status: 200 }));
        expect(await ladeDarstellung()).toEqual({ farbe: '#0f766e' });
    });

    it('behandelt eine unbrauchbare Serverantwort als Standard-Rosé', async () => {
        vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ farbe: 'evil' }), { status: 200 }));
        expect(await ladeDarstellung()).toEqual({ farbe: null });
    });

    it('zeigt die Fehlermeldung des Servers beim Speichern', async () => {
        vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ message: 'Bitte eine Farbe wie #e11d48 angeben.' }), { status: 400 }));
        await expect(speichereFarbe('rot')).rejects.toThrow('Bitte eine Farbe wie #e11d48 angeben.');
    });

    it('synchronisiert und merkt sich die Farbe', async () => {
        vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ farbe: '#7e22ce' }), { status: 200 }));
        await synchronisiereFarbe();
        expect(document.documentElement.style.getPropertyValue('--farbe-600')).toBe('126 34 206');
        expect(localStorage.getItem('programmfarbe')).toBe('#7e22ce');
    });

    it('bleibt bei einem Netzfehler bei der bisherigen Farbe', async () => {
        wendeFarbeAn('#0f766e');
        vi.spyOn(globalThis, 'fetch').mockRejectedValue(new Error('offline'));
        await synchronisiereFarbe();
        expect(document.documentElement.style.getPropertyValue('--farbe-600')).toBe('15 118 110');
    });
});
