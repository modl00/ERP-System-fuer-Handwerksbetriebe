import { parseErrorMessage } from "../settings/settingsApi";

export interface ProjektNutzung { projektId: number; bauvorhaben: string; menge: string }

/** Wo ein Artikel überall verwendet wird. */
export interface Verwendungen {
    id: number;
    name: string;
    archiviert: boolean;
    projekte: ProjektNutzung[];
    lieferantenpreise: number;
    dokumente: number;
    varianten: number;
    grundartikelName: string | null;
}

export interface Geloescht { alsPlatzhalter: boolean; projekte: number }

const basis = (id: number) => `/api/artikel/${encodeURIComponent(String(id))}`;

async function pruefe(res: Response, fallback: string): Promise<Response> {
    if (!res.ok) throw new Error(await parseErrorMessage(res, fallback));
    return res;
}

/** Archiviert einen Artikel; bei einem Grundartikel auf Wunsch samt Varianten. Gibt die Zahl der neu archivierten Artikel zurück. */
export async function archiviereArtikel(id: number, mitVarianten = false): Promise<number> {
    const res = await pruefe(await fetch(`${basis(id)}/archivieren?mitVarianten=${mitVarianten}`, { method: "POST" }), "Der Artikel konnte nicht archiviert werden.");
    return (await res.json() as { anzahl: number }).anzahl;
}

export async function stelleArtikelWiederHer(id: number): Promise<void> {
    await pruefe(await fetch(`${basis(id)}/wiederherstellen`, { method: "POST" }), "Der Artikel konnte nicht wiederhergestellt werden.");
}

export async function ladeVerwendungen(id: number, signal?: AbortSignal): Promise<Verwendungen> {
    const res = await pruefe(await fetch(`${basis(id)}/verwendungen`, { signal }), "Die Verwendung des Artikels konnte nicht geladen werden.");
    return await res.json() as Verwendungen;
}

export async function loescheArtikelEndgueltig(id: number): Promise<Geloescht> {
    const res = await pruefe(await fetch(basis(id), { method: "DELETE" }), "Der Artikel konnte nicht gelöscht werden.");
    return await res.json() as Geloescht;
}
