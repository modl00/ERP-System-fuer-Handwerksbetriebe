import { useMemo, useState } from 'react';

import { Button } from '../../components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { cn } from '../../lib/utils';
import { mengeText, type Vorschlag } from './bestellwesenApi';

interface Props {
    titel: string;
    beschreibung: string;
    vorschlaege: Vorschlag[];
    /** Lieferant der Bestellung; Vorschläge anderer Lieferanten sind zunächst nicht angehakt. Leer = noch keiner gewählt. */
    lieferantId: number | null;
    onUebernehmen: (gewaehlt: Vorschlag[]) => void;
    onSchliessen: () => void;
}

const schluessel = (v: Vorschlag, i: number) => `${v.artikelInProjektId ?? 'x'}-${v.artikelId}-${i}`;

/**
 * Zeigt, was sich bestellen ließe (Mindestbestand des Lagers oder Material eines Projekts) und lässt Zeilen abhaken und die Menge anpassen. Vorschläge,
 * die zu einem anderen Lieferanten gehören als die Bestellung, sind nicht vorgewählt und kenntlich gemacht, damit nichts versehentlich beim falschen
 * Lieferanten landet.
 */
export function VorschlaegeDialog({ titel, beschreibung, vorschlaege, lieferantId, onUebernehmen, onSchliessen }: Props) {
    const passt = (v: Vorschlag) => lieferantId === null || v.lieferantId === null || v.lieferantId === lieferantId;
    const [an, setAn] = useState<Record<string, boolean>>(() => Object.fromEntries(vorschlaege.map((v, i) => [schluessel(v, i), passt(v)])));
    const [mengen, setMengen] = useState<Record<string, string>>(() => Object.fromEntries(vorschlaege.map((v, i) => [schluessel(v, i), mengeText(v.menge)])));

    const gewaehlt = useMemo(() => vorschlaege.flatMap((v, i) => {
        const k = schluessel(v, i);
        if (!an[k]) return [];
        const menge = Number((mengen[k] ?? '').replace(',', '.'));
        return Number.isFinite(menge) && menge > 0 ? [{ ...v, menge }] : [];
    }), [vorschlaege, an, mengen]);
    const ungueltig = vorschlaege.some((v, i) => {
        const k = schluessel(v, i);
        const menge = Number((mengen[k] ?? '').replace(',', '.'));
        return an[k] && !(Number.isFinite(menge) && menge > 0);
    });

    return (
        <Dialog open onOpenChange={(o) => { if (!o) onSchliessen(); }} aria-label={titel}>
            <DialogContent className="sm:max-w-[760px]">
                <DialogHeader>
                    <DialogTitle>{titel}</DialogTitle>
                    <DialogDescription>{beschreibung}</DialogDescription>
                </DialogHeader>
                {vorschlaege.length === 0 ? (
                    <p className="rounded-lg bg-slate-50 p-4 text-sm text-slate-700">Hier gibt es im Moment nichts zu bestellen.</p>
                ) : (
                    <ul className="max-h-[50vh] divide-y divide-slate-100 overflow-y-auto rounded-lg border border-slate-200">
                        {vorschlaege.map((v, i) => {
                            const k = schluessel(v, i);
                            return (
                                <li key={k} className={cn('flex items-start gap-3 p-3', !passt(v) && 'bg-amber-50/60')}>
                                    <input
                                        type="checkbox"
                                        className="mt-1 h-5 w-5 accent-rose-600"
                                        checked={!!an[k]}
                                        onChange={(e) => setAn((a) => ({ ...a, [k]: e.target.checked }))}
                                        aria-label={`${v.bezeichnung} übernehmen`}
                                    />
                                    <div className="min-w-0 flex-1">
                                        <p className="font-medium text-slate-900">{v.bezeichnung}</p>
                                        <p className="text-xs text-slate-500">
                                            {[v.artikelnummer, v.projektName, v.hinweis, v.kommentar].filter(Boolean).join(' · ')}
                                        </p>
                                        <p className="text-xs text-slate-500">
                                            {v.lieferantName ? `Lieferant: ${v.lieferantName}` : 'Kein Lieferant hinterlegt'}
                                            {!passt(v) && <span className="ml-2 font-medium text-amber-700">anderer Lieferant als diese Bestellung</span>}
                                        </p>
                                    </div>
                                    <div className="flex shrink-0 items-center gap-1">
                                        <input
                                            inputMode="decimal"
                                            aria-label={`Menge ${v.bezeichnung}`}
                                            className="w-24 rounded-lg border border-slate-300 px-2 py-1.5 text-right text-sm focus:outline-none focus:ring-2 focus:ring-rose-500"
                                            value={mengen[k] ?? ''}
                                            onChange={(e) => setMengen((m) => ({ ...m, [k]: e.target.value }))}
                                        />
                                        <span className="w-8 text-sm text-slate-600">{v.einheit}</span>
                                    </div>
                                </li>
                            );
                        })}
                    </ul>
                )}
                {ungueltig && <p role="alert" className="text-sm text-rose-700">Bitte bei jeder angehakten Zeile eine Menge größer als 0 eintragen.</p>}
                <DialogFooter className="gap-2">
                    <Button variant="outline" onClick={onSchliessen}>Abbrechen</Button>
                    <Button onClick={() => onUebernehmen(gewaehlt)} disabled={gewaehlt.length === 0 || ungueltig}>
                        {gewaehlt.length === 0 ? 'Nichts gewählt' : `${gewaehlt.length} ${gewaehlt.length === 1 ? 'Position' : 'Positionen'} übernehmen`}
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
