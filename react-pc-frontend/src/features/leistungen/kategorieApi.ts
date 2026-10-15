import { parseErrorMessage } from '../../components/settings/settingsApi';

export const EINHEITEN = [
    { value: 'STUECK', label: 'Stück' },
    { value: 'LAUFENDE_METER', label: 'Laufende Meter' },
    { value: 'QUADRATMETER', label: 'Quadratmeter' },
    { value: 'KILOGRAMM', label: 'Kilogramm' },
    { value: 'LITER', label: 'Liter' },
];

export interface NeueKategorie {
    id: number;
    bezeichnung: string;
}

/** Legt eine Kategorie an (POST /api/produktkategorien) und gibt die neue zurück; wirft mit der Meldung des Servers. */
export async function legeKategorieAn(bezeichnung: string, parentId: string | null, einheit: string): Promise<NeueKategorie> {
    const form = new FormData();
    form.append('bezeichnung', bezeichnung);
    form.append('verrechnungseinheit', einheit);
    if (parentId) form.append('parentId', parentId);
    const res = await fetch('/api/produktkategorien', { method: 'POST', body: form });
    if (!res.ok) throw new Error(await parseErrorMessage(res, 'Die Kategorie konnte nicht angelegt werden.'));
    const daten = await res.json() as { id: number; bezeichnung: string };
    return { id: daten.id, bezeichnung: daten.bezeichnung };
}

