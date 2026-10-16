import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { apiPfad, holeBrowserFehler, installiereBrowserFehler, leereBrowserFehler } from './browserFehler';

/** Nur Dummy-Daten. */
const antwort = (status: number, body: unknown = '') => new Response(typeof body === 'string' ? body : JSON.stringify(body), { status });
const warte = () => new Promise(r => setTimeout(r, 0));

let echt: ReturnType<typeof vi.fn>;

beforeEach(() => {
    leereBrowserFehler();
    echt = vi.fn();
    window.fetch = echt as unknown as typeof window.fetch;
    // installiere wirkt nur einmal je Seite: Für jeden Test eine frische Verpackung um den neuen Stub
    vi.resetModules();
});
afterEach(() => { vi.unstubAllGlobals(); });

async function frisch() {
    const m = await import('./browserFehler');
    m.leereBrowserFehler();
    m.installiereBrowserFehler();
    return m;
}

describe('browserFehler', () => {
    it('merkt einen fehlgeschlagenen Aufruf mit Methode, Adresse ohne Parameter, Status und Meldung des Servers', async () => {
        const m = await frisch();
        echt.mockResolvedValue(antwort(400, { message: 'Der Mitarbeiter gehört schon zum Benutzer „Anna“.' }));

        const res = await window.fetch('/api/frontend-users?name=Max', { method: 'POST', body: '{"passwort":"geheim"}' });
        await warte();

        expect(res.status).toBe(400);
        expect(await res.json()).toEqual({ message: 'Der Mitarbeiter gehört schon zum Benutzer „Anna“.' });
        const f = m.holeBrowserFehler();
        expect(f).toHaveLength(1);
        expect(f[0]).toMatchObject({ methode: 'POST', pfad: '/api/frontend-users', status: 400, meldung: 'Der Mitarbeiter gehört schon zum Benutzer „Anna“.' });
        expect(JSON.stringify(f)).not.toContain('geheim');
        expect(JSON.stringify(f)).not.toContain('Max');
    });

    it('lässt die Antwort für den Aufrufer unversehrt', async () => {
        await frisch();
        echt.mockResolvedValue(antwort(500, { message: 'kaputt' }));
        const res = await window.fetch('/api/x');
        expect(await res.text()).toBe('{"message":"kaputt"}');
    });

    it('merkt erfolgreiche Aufrufe nicht und auch keine fremden Adressen', async () => {
        const m = await frisch();
        echt.mockResolvedValueOnce(antwort(200, {}));
        echt.mockResolvedValueOnce(antwort(500, 'Fehler'));
        await window.fetch('/api/ok');
        await window.fetch('https://fremd.example/api/x');
        await window.fetch('/assets/bild.png');
        await warte();
        expect(m.holeBrowserFehler()).toHaveLength(0);
    });

    it('merkt einen Aufruf ohne Verbindung und wirft den Fehler weiter', async () => {
        const m = await frisch();
        echt.mockRejectedValue(new TypeError('Failed to fetch'));
        await expect(window.fetch('/api/x')).rejects.toThrow('Failed to fetch');
        expect(m.holeBrowserFehler()[0]).toMatchObject({ pfad: '/api/x', status: null, meldung: 'Failed to fetch' });
    });

    it('derselbe Fehler direkt hintereinander steht nur einmal da', async () => {
        const m = await frisch();
        echt.mockImplementation(async () => antwort(401, { message: 'Bitte anmelden.' }));
        for (let i = 0; i < 5; i++) await window.fetch('/api/auth/me');
        await warte();
        expect(m.holeBrowserFehler()).toHaveLength(1);
    });

    it('behält höchstens 20 und gibt auf Wunsch die letzten zurück', async () => {
        const m = await frisch();
        echt.mockImplementation(async () => antwort(500, { message: 'x' }));
        for (let i = 0; i < 30; i++) await window.fetch(`/api/n${i}`);
        await warte();
        const alle = m.holeBrowserFehler(100);
        expect(alle).toHaveLength(20);
        expect(alle.at(-1)?.pfad).toBe('/api/n29');
        expect(m.holeBrowserFehler(3).map(f => f.pfad)).toEqual(['/api/n27', '/api/n28', '/api/n29']);
    });

    it('kürzt lange Meldungen', async () => {
        const m = await frisch();
        echt.mockResolvedValue(antwort(500, { message: 'a'.repeat(1_000) }));
        await window.fetch('/api/x');
        await warte();
        expect(m.holeBrowserFehler()[0].meldung.length).toBeLessThan(215);
    });

    it('merkt Fehler der Seite und nicht behandelte Zusagen', async () => {
        const m = await frisch();
        window.dispatchEvent(new ErrorEvent('error', { message: 'Cannot read properties of undefined' }));
        const e = new Event('unhandledrejection') as Event & { reason?: unknown };
        e.reason = new Error('Zusage abgelehnt');
        window.dispatchEvent(e);
        expect(m.holeBrowserFehler().map(f => f.meldung)).toEqual(['Cannot read properties of undefined', 'Zusage abgelehnt']);
    });

    it('apiPfad kennt nur Adressen dieses Programms', () => {
        expect(apiPfad('/api/a/b?x=1')).toBe('/api/a/b');
        expect(apiPfad('https://fremd.example/api/a')).toBeNull();
        expect(apiPfad('/seite')).toBeNull();
        expect(apiPfad(new URL('/api/z', window.location.origin))).toBe('/api/z');
    });

    it('installieren ist wiederholbar, ohne doppelt zu merken', async () => {
        const m = await frisch();
        m.installiereBrowserFehler();
        m.installiereBrowserFehler();
        echt.mockResolvedValue(antwort(500, { message: 'x' }));
        await window.fetch('/api/x');
        await warte();
        expect(m.holeBrowserFehler()).toHaveLength(1);
        expect(typeof holeBrowserFehler).toBe('function');
        expect(typeof installiereBrowserFehler).toBe('function');
    });
});
