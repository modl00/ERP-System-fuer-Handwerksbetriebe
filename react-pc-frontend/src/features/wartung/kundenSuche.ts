import type { SearchableSelectOption } from '../../components/ui/searchable-select';

/** Namen aller bisher gefundenen Kunden. Die Auswahl zeigt sonst nichts mehr an, sobald eine spätere Suche den gewählten Kunden nicht mehr enthält. */
const bekannteNamen = new Map<string, string>();

/** Anzeigename eines schon gefundenen Kunden, sonst leer. */
export function kundeName(id: string): string {
    return bekannteNamen.get(id) ?? '';
}

/** Sucht Kunden auf dem Server (die Liste ist seitenweise, daher keine lokale Filterung). */
export async function sucheKunden(term: string, mitAlle = false): Promise<SearchableSelectOption[]> {
    const res = await fetch(`/api/kunden?q=${encodeURIComponent(term)}&size=20`);
    if (!res.ok) throw new Error('Die Kunden konnten nicht geladen werden.');
    const daten = await res.json() as { kunden?: { id: number; name: string; kundennummer?: string; ort?: string }[] };
    const treffer = (daten.kunden ?? []).map((k) => ({
        value: String(k.id),
        label: k.name,
        sublabel: [k.kundennummer, k.ort].filter(Boolean).join(' · '),
    }));
    treffer.forEach((t) => bekannteNamen.set(t.value, t.label));
    return mitAlle ? [{ value: '', label: 'Alle Kunden' }, ...treffer] : treffer;
}
