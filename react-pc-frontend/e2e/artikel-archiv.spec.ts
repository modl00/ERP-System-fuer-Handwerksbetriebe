import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

/**
 * Artikel-Archiv: archivieren, wiederherstellen und (nur als Administrator) endgültig löschen, mit Blick auf die Verwendung in Projekten.
 * /api ist gestubbt (kein Backend). Nur Dummy-Daten (DSGVO).
 */

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

const ARTIKEL = { id: 5, produktname: 'Rundrohr 42,4 x 2', artikelnummer: 'RR-1', werkstoffName: 'Baustahl', verrechnungseinheit: { name: 'LAUFENDE_METER', anzeigename: 'Laufende Meter' } };
const VERWENDUNG = {
    id: 5, name: 'Rundrohr 42,4 x 2', archiviert: true, lieferantenpreise: 4, dokumente: 1, varianten: 0, grundartikelName: null,
    projekte: [{ projektId: 9, bauvorhaben: 'Geländer Musterstraße', menge: '3 Stück, 12,5 m' }],
};

interface Mitschrift { aufrufe: string[] }

async function stubApi(page: Page, admin: boolean): Promise<Mitschrift> {
    const mit: Mitschrift = { aufrufe: [] };
    let archiviert = false;
    let geloescht = false;
    await page.route('**/api/**', (route) => {
        const url = new URL(route.request().url());
        const pfad = url.pathname;
        const methode = route.request().method();
        if (pfad === '/api/auth/me') return json(route, { id: 1, username: 'max.mustermann', displayName: 'Max Mustermann', active: true, roles: admin ? ['ADMIN'] : ['USER'], admin, requiresInitialSetup: false });
        if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (pfad === '/api/aufschlaege/berechtigung') return json(route, { darfAendern: false });
        if (pfad === '/api/artikel/werkstoffe') return json(route, []);
        if (pfad === '/api/artikel/filteroptionen') return json(route, { herstellverfahren: [], fertigungszustand: [], profilform: [] });
        if (pfad === '/api/artikel' && methode === 'GET') {
            const imArchiv = url.searchParams.get('archiv') === 'true';
            const treffer = imArchiv ? (archiviert && !geloescht ? [{ ...ARTIKEL, archiviert: true }] : []) : (archiviert ? [] : [ARTIKEL]);
            return json(route, { artikel: treffer, gesamt: treffer.length, seite: 0, seitenGroesse: 12 });
        }
        if (pfad === '/api/artikel/5/detail') return json(route, { artikel: { ...ARTIKEL, archiviert }, lieferanten: [], preisverlauf: [] });
        if (pfad === '/api/artikel/5/archivieren' && methode === 'POST') { mit.aufrufe.push(`POST ${pfad}${url.search}`); archiviert = true; return json(route, { anzahl: 1 }); }
        if (pfad === '/api/artikel/5/wiederherstellen' && methode === 'POST') { mit.aufrufe.push(`POST ${pfad}`); archiviert = false; return route.fulfill({ status: 204 }); }
        if (pfad === '/api/artikel/5/verwendungen') return json(route, VERWENDUNG);
        if (pfad === '/api/artikel/5' && methode === 'DELETE') { mit.aufrufe.push(`DELETE ${pfad}`); geloescht = true; return json(route, { alsPlatzhalter: true, projekte: 1 }); }
        return json(route, []);
    });
    return mit;
}

test('Artikel archivieren, im Archiv wiederfinden und wiederherstellen', async ({ page }) => {
    const mit = await stubApi(page, false);
    await page.goto('/artikel/5');

    await page.getByRole('button', { name: 'Archivieren' }).click();
    const frage = page.getByRole('dialog', { name: 'Artikel archivieren?' });
    await expect(frage).toContainText('verschwindet aus Artikelliste und Auswahl');
    await frage.getByRole('button', { name: 'Archivieren' }).click();
    await expect(page.getByText('„Rundrohr 42,4 x 2“ ist im Archiv.')).toBeVisible();
    await expect(page.getByTestId('archiv-hinweis')).toBeVisible();
    expect(mit.aufrufe).toEqual(['POST /api/artikel/5/archivieren?mitVarianten=false']);

    // Die Liste hat ihn nicht mehr, das Archiv schon
    await page.goto('/artikel');
    await expect(page.getByText('Rundrohr 42,4 x 2')).toHaveCount(0);
    await page.getByRole('tab', { name: 'Archiv' }).click();
    await expect(page.getByTestId('artikel-archiv')).toBeVisible();
    await expect(page.getByText('Endgültig löschen darf nur ein Administrator.')).toBeVisible();
    await expect(page.getByLabel('Rundrohr 42,4 x 2 endgültig löschen')).toHaveCount(0);

    await page.getByLabel('Rundrohr 42,4 x 2 wiederherstellen').click();
    await expect(page.getByText(/ist wiederhergestellt/)).toBeVisible();
    expect(mit.aufrufe.at(-1)).toBe('POST /api/artikel/5/wiederherstellen');
    await page.getByRole('tab', { name: 'Artikel' }).click();
    await expect(page.getByText('Rundrohr 42,4 x 2').first()).toBeVisible();
});

test('Administrator löscht endgültig, sieht vorher die Verweise und bestätigt', async ({ page }, testInfo) => {
    const mit = await stubApi(page, true);
    await page.goto('/artikel/5');
    await page.getByRole('button', { name: 'Archivieren' }).click();
    await page.getByRole('dialog', { name: 'Artikel archivieren?' }).getByRole('button', { name: 'Archivieren' }).click();
    await expect(page.getByText('„Rundrohr 42,4 x 2“ ist im Archiv.')).toBeVisible();

    await page.goto('/artikel');
    await page.getByRole('tab', { name: 'Archiv' }).click();
    await page.getByLabel('Rundrohr 42,4 x 2 endgültig löschen').click();

    const fenster = page.getByRole('dialog', { name: 'Artikel endgültig löschen', exact: true });
    await expect(fenster.getByText('Geländer Musterstraße')).toBeVisible();
    await expect(fenster.getByText('3 Stück, 12,5 m')).toBeVisible();
    await designPruefung(page, testInfo, 'artikel-loeschen', { strengePruefungen: false });

    await fenster.getByRole('button', { name: 'Endgültig löschen', exact: true }).click();
    const frage = page.getByRole('dialog', { name: 'Artikel endgültig löschen?', exact: true });
    await expect(frage).toContainText('als Text mit Preis');
    expect(mit.aufrufe.filter(a => a.startsWith('DELETE'))).toHaveLength(0);
    await frage.getByRole('button', { name: 'Endgültig löschen', exact: true }).click();

    await expect(page.getByText('„Rundrohr 42,4 x 2“ ist gelöscht.')).toBeVisible();
    expect(mit.aufrufe.at(-1)).toBe('DELETE /api/artikel/5');
    await expect(page.getByTestId('artikel-archiv').getByText('Rundrohr 42,4 x 2')).toHaveCount(0);
});
