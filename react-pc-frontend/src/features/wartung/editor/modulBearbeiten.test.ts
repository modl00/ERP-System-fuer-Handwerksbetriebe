import { describe, expect, it } from 'vitest';
import {
    bereinige, entferne, ersetze, fehlerInDatenfeldern, fehlerImModul, istGeaendert, kopieAlsNeu, leeresModul, leererAbschnitt,
    optionenAusText, verschiebe, verschiebeImBereich, entferneBauart, schluesselVergeben, neueZeile,
} from './modulBearbeiten';
import type { ModulDetail } from '../types';

function modul(): ModulDetail {
    return {
        ...leeresModul(),
        id: 7,
        version: 3,
        modulKey: 'm_bst',
        name: 'Brandschutztür',
        systemModul: true,
        anzahlTueren: 4,
        untertypen: [{ id: 1, schluessel: '1fluegelig', label: '1-flügelig' }, { id: 2, schluessel: '2fluegelig', label: '2-flügelig' }],
        abschnitte: [{
            id: 10, bereich: 'PRUEFPUNKTE', label: 'Prüfpunkte',
            pruefpunkte: [{ id: 100, label: 'Schild lesbar', nurBeiUntertyp: null }, { id: 101, label: 'Zweiter Flügel', nurBeiUntertyp: '2fluegelig' }],
            datenfelder: [],
        }, {
            id: 11, bereich: 'DATENFELDER', label: 'Daten', pruefpunkte: [],
            datenfelder: [{ id: 200, label: 'Zustand', feldtyp: 'AUSWAHL', optionen: ['gut', 'schlecht'] }],
        }],
    };
}

describe('Listen bearbeiten', () => {
    it('verschiebt um eine Stelle und lässt den Rand in Ruhe', () => {
        expect(verschiebe(['a', 'b', 'c'], 1, -1)).toEqual(['b', 'a', 'c']);
        expect(verschiebe(['a', 'b', 'c'], 1, 1)).toEqual(['a', 'c', 'b']);
        const liste = ['a', 'b'];
        expect(verschiebe(liste, 0, -1)).toBe(liste);
        expect(verschiebe(liste, 1, 1)).toBe(liste);
        expect(verschiebe(liste, 5, 1)).toBe(liste);
    });

    it('verändert die ursprüngliche Liste nie', () => {
        const liste = ['a', 'b', 'c'];
        verschiebe(liste, 0, 1);
        entferne(liste, 1);
        ersetze(liste, 2, 'x');
        expect(liste).toEqual(['a', 'b', 'c']);
        expect(entferne(liste, 1)).toEqual(['a', 'c']);
        expect(ersetze(liste, 2, 'x')).toEqual(['a', 'b', 'x']);
    });

    it('zerlegt Optionen zeilenweise ohne leere Zeilen', () => {
        expect(optionenAusText(' gut \n\n mittel\n   \nschlecht')).toEqual(['gut', 'mittel', 'schlecht']);
        expect(optionenAusText('')).toEqual([]);
    });
});

describe('Kopie als neues Modul', () => {
    it('entfernt alle Nummern, behält Inhalt und Bauart-Schlüssel und benennt es um', () => {
        const kopie = kopieAlsNeu(modul());

        expect(kopie).toMatchObject({ id: null, version: null, modulKey: null, name: 'Brandschutztür (Kopie)', systemModul: false, anzahlTueren: 0 });
        expect(kopie.untertypen.map((u) => [u.id, u.schluessel])).toEqual([[null, '1fluegelig'], [null, '2fluegelig']]);
        expect(kopie.abschnitte.every((a) => a.id === null)).toBe(true);
        expect(kopie.abschnitte[0].pruefpunkte.map((p) => p.id)).toEqual([null, null]);
        expect(kopie.abschnitte[0].pruefpunkte[1].nurBeiUntertyp).toBe('2fluegelig');
        expect(kopie.abschnitte[1].datenfelder[0]).toMatchObject({ id: null, optionen: ['gut', 'schlecht'] });
    });

    it('teilt keine Listen mit dem Original', () => {
        const original = modul();
        const kopie = kopieAlsNeu(original);
        kopie.abschnitte[1].datenfelder[0].optionen.push('neu');
        expect(original.abschnitte[1].datenfelder[0].optionen).toEqual(['gut', 'schlecht']);
    });
});

describe('Prüfung vor dem Speichern', () => {
    it('akzeptiert ein vollständiges Modul', () => {
        expect(fehlerImModul(modul())).toBeNull();
    });

    it('meldet fehlenden Namen, leere Bauart, doppelte Bauart, leere Überschrift und leeren Prüfpunkt', () => {
        expect(fehlerImModul({ ...modul(), name: '  ' })).toBe('Bitte einen Namen eintragen.');
        expect(fehlerImModul({ ...modul(), untertypen: [{ id: null, schluessel: null, label: ' ' }] })).toBe('Bitte jede Bauart benennen.');
        expect(fehlerImModul({ ...modul(), untertypen: [{ id: null, schluessel: null, label: 'A' }, { id: null, schluessel: null, label: ' a ' }] })).toBe('Zwei Bauarten heißen gleich.');
        const m = modul();
        m.abschnitte[0].label = '';
        expect(fehlerImModul(m)).toBe('Bitte jeden Abschnitt benennen.');
        const n = modul();
        n.abschnitte[0].pruefpunkte[0].label = ' ';
        expect(fehlerImModul(n)).toBe('Bitte jeden Prüfpunkt beschreiben.');
    });

    it('verlangt bei einer Auswahl Möglichkeiten ohne Doppelte', () => {
        const m = modul();
        m.abschnitte[1].datenfelder[0].optionen = [];
        expect(fehlerImModul(m)).toContain('fehlen die Auswahlmöglichkeiten');
        m.abschnitte[1].datenfelder[0].optionen = ['a', 'a'];
        expect(fehlerImModul(m)).toContain('doppelt');
    });

    it('prüft auch die gemeinsamen Datenfelder', () => {
        expect(fehlerInDatenfeldern([{ id: null, label: 'Standort', feldtyp: 'TEXT', optionen: [] }])).toBeNull();
        expect(fehlerInDatenfeldern([{ id: null, label: '', feldtyp: 'TEXT', optionen: [] }])).toBe('Bitte jedes Datenfeld benennen.');
        expect(fehlerInDatenfeldern([
            { id: null, label: 'Standort', feldtyp: 'TEXT', optionen: [] },
            { id: 3, label: ' standort ', feldtyp: 'TEXT', optionen: [] },
        ])).toBe('Zwei Datenfelder heißen gleich.');
    });
});

describe('Bereinigen und Änderungen erkennen', () => {
    it('wirft neue leere Zeilen und leere Abschnitte weg, behält aber vorhandene Einträge', () => {
        const m = modul();
        m.abschnitte[0].pruefpunkte.push({ id: null, label: '  ', nurBeiUntertyp: null });
        m.abschnitte.push(leererAbschnitt('PRUEFPUNKTE'));
        m.abschnitte.push(leererAbschnitt('DATENFELDER'));

        const sauber = bereinige({ ...m, name: ' Brandschutztür ' });

        expect(sauber.name).toBe('Brandschutztür');
        expect(sauber.abschnitte).toHaveLength(2);
        expect(sauber.abschnitte[0].pruefpunkte).toHaveLength(2);
    });

    it('erkennt Änderungen am Inhalt', () => {
        const vorher = modul();
        expect(istGeaendert(vorher, modul())).toBe(false);
        const nachher = modul();
        nachher.abschnitte[0].pruefpunkte[0].label = 'Schild gut lesbar';
        expect(istGeaendert(vorher, nachher)).toBe(true);
        expect(istGeaendert(null, null)).toBe(false);
    });
});

describe('Abschnitte und Bauarten im Editor', () => {
    it('verschiebt Abschnitte nur innerhalb ihrer Art', () => {
        const a = (id: number, bereich: 'PRUEFPUNKTE' | 'DATENFELDER') => ({ ...leererAbschnitt(bereich), id });
        const liste = [a(1, 'PRUEFPUNKTE'), a(2, 'DATENFELDER'), a(3, 'PRUEFPUNKTE'), a(4, 'DATENFELDER')];

        expect(verschiebeImBereich(liste, 2, -1).map((x) => x.id)).toEqual([3, 2, 1, 4]);
        expect(verschiebeImBereich(liste, 1, 1).map((x) => x.id)).toEqual([1, 4, 3, 2]);
        expect(verschiebeImBereich(liste, 0, -1)).toBe(liste);
        expect(verschiebeImBereich(liste, 3, 1)).toBe(liste);
        expect(verschiebeImBereich(liste, 9, 1)).toBe(liste);
    });

    it('entfernt eine Bauart und lässt ihre Prüfpunkte für alle gelten, mit Zahl der betroffenen', () => {
        const { modul: neu, betroffenePruefpunkte } = entferneBauart(modul(), 1);

        expect(betroffenePruefpunkte).toBe(1);
        expect(neu.untertypen.map((u) => u.schluessel)).toEqual(['1fluegelig']);
        expect(neu.abschnitte[0].pruefpunkte[1]).toMatchObject({ label: 'Zweiter Flügel', nurBeiUntertyp: null });
        expect(fehlerImModul(neu)).toBeNull();
    });

    it('meldet einen Prüfpunkt, der auf eine entfernte Bauart verweist', () => {
        const m = modul();
        m.untertypen = [m.untertypen[0]];
        expect(fehlerImModul(m)).toBe('Der Prüfpunkt „Zweiter Flügel" gilt für eine Bauart, die es nicht mehr gibt.');
    });

    it('vergibt Schlüssel, ohne vorhandene zu ändern', () => {
        const m = schluesselVergeben(modul());
        const keys = [...m.untertypen.map((u) => u.clientKey), ...m.abschnitte.map((a) => a.clientKey), ...m.abschnitte[0].pruefpunkte.map((p) => p.clientKey)];
        expect(keys.every(Boolean)).toBe(true);
        expect(new Set(keys).size).toBe(keys.length);
        expect(schluesselVergeben(m).untertypen[0].clientKey).toBe(m.untertypen[0].clientKey);
        expect(neueZeile({ label: 'x' }).clientKey).toBeTruthy();
    });
});
