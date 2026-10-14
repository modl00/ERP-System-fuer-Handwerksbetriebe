import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

/**
 * Artikelvarianten: Grundartikel anlegen und daraus per Raster alle Größen auf einmal erzeugen.
 * /api ist gestubbt (kein Backend). Nur Dummy-Daten (DSGVO).
 */

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

const GRUNDARTIKEL = {
    id: 77, version: 1, produktname: 'Quadratrohr', kurzbezeichnung: 'QH', grundartikel: true, variantenAnzahl: 0,
    profilform: { name: 'QUADRATROHR', anzeigename: 'Quadratrohr' }, werkstoffId: 4, werkstoffName: 'Baustahl S235JR', verkaufsaufschlagProzent: 25,
};
const VORGABE = { profilform: 'QUADRATROHR', form: 'Quadratrohr', achseA: 'Kantenlänge (mm)', hinweisA: 'zum Beispiel 20 25 30 40 50', aMitZweitemMass: false, achseB: 'Wandstärke (mm)', hinweisB: 'zum Beispiel 1,5 2 3 4' };
const KOMBIS = [
    { schluessel: '20|2', name: 'QH 20x20x2', artikelnummer: 'QH-20x20x2', status: 'NEU', grund: null, kgProMeter: 1.0495, kgProQm: null },
    { schluessel: '25|2', name: 'QH 25x25x2', artikelnummer: 'QH-25x25x2', status: 'SCHON_DA', grund: 'Gibt es schon.', kgProMeter: null, kgProQm: null },
    { schluessel: '15|4', name: 'QH 15x15x4', artikelnummer: 'QH-15x15x4', status: 'NICHT_MOEGLICH', grund: 'Die Wandstärke ist zu groß für diese Kantenlänge: Die Rundungen in den Ecken passen nicht mehr hinein.', kgProMeter: null, kgProQm: null },
    { schluessel: '40|2', name: 'QH 40x40x2', artikelnummer: 'QH-40x40x2', status: 'NEU', grund: null, kgProMeter: 2.3053, kgProQm: null },
];

interface Mitschrift { grundartikel: Record<string, unknown>[]; raster: Record<string, unknown>[] }

async function stubApi(page: Page): Promise<Mitschrift> {
    const mit: Mitschrift = { grundartikel: [], raster: [] };
    let angelegt = false;
    await page.route('**/api/**', (route) => {
        const pfad = new URL(route.request().url()).pathname;
        const methode = route.request().method();
        if (pfad === '/api/auth/me') return json(route, { id: 1, username: 'anna.buero', displayName: 'Anna Büro', active: true, roles: ['USER'], admin: false, requiresInitialSetup: false });
        if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (pfad === '/api/aufschlaege/berechtigung') return json(route, { darfAendern: false });
        if (pfad === '/api/artikel') return json(route, { artikel: [], gesamt: 0 });
        if (pfad === '/api/artikel/werkstoffe') return json(route, []);
        if (pfad === '/api/artikel/werkstoffe/details') return json(route, [{ id: 4, name: 'Baustahl S235JR' }]);
        if (pfad === '/api/artikel/filteroptionen') return json(route, { herstellverfahren: [], fertigungszustand: [], profilform: [] });
        if (pfad === '/api/artikel/grundartikel/formen') return json(route, [{ ...VORGABE }, { ...VORGABE, profilform: 'RUNDROHR', form: 'Rundrohr' }]);
        if (pfad === '/api/artikel/grundartikel' && methode === 'POST') {
            mit.grundartikel.push(route.request().postDataJSON() as Record<string, unknown>);
            return json(route, { id: 77, produktname: 'Quadratrohr', kurzbezeichnung: 'QH' }, 201);
        }
        if (pfad === '/api/artikel/77/detail') return json(route, { artikel: { ...GRUNDARTIKEL, variantenAnzahl: angelegt ? 2 : 0 }, lieferanten: [], preisverlauf: [] });
        if (pfad === '/api/artikel/77/varianten/raster') return json(route, VORGABE);
        if (pfad === '/api/artikel/77/varianten/vorschau' && methode === 'POST') return json(route, KOMBIS);
        if (pfad === '/api/artikel/77/varianten' && methode === 'POST') {
            mit.raster.push(route.request().postDataJSON() as Record<string, unknown>);
            angelegt = true;
            return json(route, KOMBIS, 201);
        }
        if (pfad === '/api/artikel/77/varianten') {
            return json(route, angelegt ? [
                { id: 11, produktname: 'QH 20x20x2', artikelnummer: 'QH-20x20x2', abmessung: '20 x 20 x 2', kgProMeter: 1.0495, kgProQm: null, guenstigsterPreis: null },
                { id: 12, produktname: 'QH 40x40x2', artikelnummer: 'QH-40x40x2', abmessung: '40 x 40 x 2', kgProMeter: 2.3053, kgProQm: null, guenstigsterPreis: null },
            ] : []);
        }
        return json(route, []);
    });
    return mit;
}

test('Artikel: Grundartikel anlegen und Varianten per Raster erzeugen', async ({ page }, testInfo) => {
    const mit = await stubApi(page);
    await page.goto('/artikel');

    // Grundartikel anlegen
    await page.getByRole('button', { name: 'Grundartikel anlegen' }).click();
    const anlegen = page.getByRole('dialog', { name: 'Grundartikel anlegen' });
    await expect(anlegen).toBeVisible();
    await anlegen.getByLabel('Kurzbezeichnung *').fill('QH');
    await anlegen.getByLabel('Name *').fill('Quadratrohr');
    await anlegen.getByRole('combobox', { name: 'Form' }).click();
    await page.getByRole('option', { name: 'Quadratrohr' }).click();
    await anlegen.getByLabel('Aufschlag in Prozent').fill('25');
    await designPruefung(page, testInfo, 'artikel-grundartikel-anlegen', { strengePruefungen: false });
    await anlegen.getByRole('button', { name: 'Grundartikel anlegen' }).click();

    expect(mit.grundartikel[0]).toMatchObject({ kurzbezeichnung: 'QH', produktname: 'Quadratrohr', profilform: 'QUADRATROHR', verkaufsaufschlagProzent: 25 });

    // Weiter auf die Seite des Grundartikels: noch keine Varianten, keine Preisabschnitte
    await expect(page).toHaveURL(/\/artikel\/77$/);
    await expect(page.getByTestId('artikel-varianten')).toBeVisible();
    await expect(page.getByText('Es gibt noch keine Varianten')).toBeVisible();
    await expect(page.getByText('Wer liefert das?')).toHaveCount(0);

    // Raster: Vorschau zeigt neu / schon da / nicht möglich
    await page.getByRole('button', { name: 'Varianten anlegen' }).first().click();
    const raster = page.getByRole('dialog', { name: 'Varianten anlegen' });
    await raster.getByLabel('Kantenlänge (mm)').fill('15 20 25 40');
    await raster.getByLabel('Wandstärke (mm)').fill('2 4');
    await raster.getByRole('button', { name: 'Vorschau' }).click();
    await expect(raster.getByTestId('varianten-vorschau')).toBeVisible();
    await expect(raster.getByText('Gibt es schon')).toBeVisible();
    await expect(raster.getByText(/Rundungen in den Ecken/)).toBeVisible();
    await expect(raster.getByRole('status')).toHaveText('2 von 2 neuen Varianten werden angelegt.');
    await designPruefung(page, testInfo, 'artikel-varianten-raster', { strengePruefungen: false });

    // eine Variante abwählen, den Rest anlegen
    await raster.getByRole('checkbox', { name: 'QH 40x40x2 anlegen' }).uncheck();
    await expect(raster.getByRole('status')).toHaveText('1 von 2 neuen Varianten werden angelegt.');
    await raster.getByRole('button', { name: '1 Variante anlegen' }).click();

    await expect(page.getByText('1 Variante ist angelegt.')).toBeVisible();
    expect(mit.raster[0]).toEqual({ a: ['15 20 25 40'], b: ['2 4'], ausgeschlossen: ['40|2'] });
    await expect(page.getByRole('link', { name: 'QH 20x20x2' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'QH 40x40x2' })).toBeVisible();
});
