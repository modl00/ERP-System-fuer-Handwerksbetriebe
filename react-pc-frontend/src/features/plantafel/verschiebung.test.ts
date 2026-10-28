import { describe, expect, it } from 'vitest';
import { neueZuweisung, verschobenerStart, verschobenerTermin } from './verschiebung';

describe('verschobenerStart', () => {
    it('rundet auf ganze Tage und lässt einen unveränderten Tag weg', () => {
        expect(verschobenerStart('2026-10-12', 1.2)).toBe('2026-10-13');
        expect(verschobenerStart('2026-10-14', -1)).toBe('2026-10-13');
        expect(verschobenerStart('2026-10-12', 0.3)).toBeNull();
    });

    it('ein Wochenende rückt auf den Montag', () => {
        expect(verschobenerStart('2026-10-15', 2)).toBe('2026-10-19');
        expect(verschobenerStart('2026-10-12', -1)).toBeNull(); // Sonntag, also wieder der Montag: keine Änderung
        expect(verschobenerStart('2026-10-16', 1, [1, 2, 3, 4, 5, 6])).toBe('2026-10-17'); // Samstag ist Arbeitstag
    });
});

describe('verschobenerTermin', () => {
    const mitZeit = { von: '2026-10-12', vonZeit: '09:00:00', bisZeit: '11:00:00' };

    it('rastet auf halbe Stunden ein und behält die Dauer', () => {
        expect(verschobenerTermin(mitZeit, 2 / 18)).toEqual({ datum: '2026-10-12', von: '10:00', bis: '12:00' });
        expect(verschobenerTermin(mitZeit, 0.01)).toBeNull();
    });

    it('ein ganzer Tag springt auf den nächsten Tag zur selben Uhrzeit', () => {
        expect(verschobenerTermin(mitZeit, 1)).toEqual({ datum: '2026-10-13', von: '09:00', bis: '11:00' });
        expect(verschobenerTermin(mitZeit, -1)).toEqual({ datum: '2026-10-11', von: '09:00', bis: '11:00' });
    });

    it('über das Tagesende hinaus beginnt er am nächsten Morgen', () => {
        expect(verschobenerTermin({ von: '2026-10-12', vonZeit: '16:00:00', bisZeit: null }, 2 / 18)).toEqual({ datum: '2026-10-13', von: '08:00', bis: null });
    });

    it('ohne Uhrzeit wandert er in ganzen Tagen', () => {
        expect(verschobenerTermin({ von: '2026-10-12', vonZeit: null, bisZeit: null }, 2.4)).toEqual({ datum: '2026-10-14', von: null, bis: null });
        expect(verschobenerTermin({ von: '2026-10-12', vonZeit: null, bisZeit: null }, 0.4)).toBeNull();
    });
});

describe('neueZuweisung', () => {
    const jetzt = { mitarbeiterIds: [4], ressourceId: null };

    it('in die Zeile eines anderen Mitarbeiters: die Person wechselt', () => {
        expect(neueZuweisung('M4', 'M5', jetzt)).toEqual({ mitarbeiterIds: [5], ressourceId: null });
    });

    it('in eine Ressource: sie kommt dazu, die Person bleibt', () => {
        expect(neueZuweisung('M4', 'R8', jetzt)).toEqual({ mitarbeiterIds: [4], ressourceId: 8 });
        expect(neueZuweisung('R8', 'R9', { mitarbeiterIds: [], ressourceId: 8 })).toEqual({ mitarbeiterIds: [], ressourceId: 9 });
    });

    it('aus „Ohne Zuweisung" in eine Person oder Ressource: dazu', () => {
        expect(neueZuweisung('A', 'M5', { mitarbeiterIds: [], ressourceId: null })).toEqual({ mitarbeiterIds: [5], ressourceId: null });
        expect(neueZuweisung('A', 'R8', { mitarbeiterIds: [], ressourceId: null })).toEqual({ mitarbeiterIds: [], ressourceId: 8 });
    });

    it('nach „Ohne Zuweisung": die Zuweisung der Quellzeile fällt weg, die andere bleibt', () => {
        expect(neueZuweisung('M4', 'A', { mitarbeiterIds: [4, 5], ressourceId: 8 })).toEqual({ mitarbeiterIds: [5], ressourceId: 8 });
        expect(neueZuweisung('R8', 'A', { mitarbeiterIds: [4], ressourceId: 8 })).toEqual({ mitarbeiterIds: [4], ressourceId: null });
    });

    it('dieselbe Zeile oder keine Änderung: null', () => {
        expect(neueZuweisung('M4', 'M4', jetzt)).toBeNull();
        expect(neueZuweisung('R8', 'M4', { mitarbeiterIds: [4], ressourceId: 8 })).toBeNull();
        expect(neueZuweisung('A', 'A', { mitarbeiterIds: [], ressourceId: null })).toBeNull();
    });

    it('eine zweite Person in der Quelle bleibt erhalten und wird nicht doppelt eingetragen', () => {
        expect(neueZuweisung('M4', 'M5', { mitarbeiterIds: [4, 5], ressourceId: null })).toEqual({ mitarbeiterIds: [5], ressourceId: null });
    });
});
