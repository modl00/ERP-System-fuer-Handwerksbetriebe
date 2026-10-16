import { useEffect, useMemo, useState } from 'react';
import { BookmarkPlus, Loader2 } from 'lucide-react';
import { Button } from '../ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../ui/dialog';
import { Input } from '../ui/input';
import { Label } from '../ui/label';
import { SearchableSelect } from '../ui/searchable-select';
import { useToast } from '../ui/toast';
import type { DocBlock } from './types';
import { unitMap } from './helpers';

/** Die Einheiten, die eine Leistung im Stamm haben kann (Name der Aufzählung → Kürzel im Dokument). */
const EINHEITEN = Object.entries(unitMap).map(([wert, kurz]) => ({ value: wert, label: kurz }));
const EINHEIT_AUS_KUERZEL: Record<string, string> = Object.fromEntries(Object.entries(unitMap).map(([wert, kurz]) => [kurz.toLowerCase(), wert]));

interface Kategorie { id: number; bezeichnung?: string; pfad?: string; parentId?: number | null }

/** „Rohre / Geländer / Pfosten“ aus der Kategorie und ihren Eltern. */
function pfade(liste: Kategorie[]): { value: string; label: string }[] {
    const nachId = new Map(liste.map(k => [k.id, k]));
    const pfad = (k: Kategorie): string => {
        const eltern = k.parentId != null ? nachId.get(k.parentId) : undefined;
        const name = k.bezeichnung || k.pfad || `Kategorie ${k.id}`;
        return eltern ? `${pfad(eltern)} / ${name}` : name;
    };
    return liste.map(k => ({ value: String(k.id), label: pfad(k) })).sort((a, b) => a.label.localeCompare(b.label, 'de'));
}

/**
 * Eine einmalige Position nur für dieses Angebot in den Leistungsstamm übernehmen (#55): Name, Kategorie und Einheit bestätigen, dann entsteht eine Leistung mit dem Text
 * und dem Preis der Position, und die Position verweist danach auf sie. Preis und Text im Angebot bleiben, wie sie sind.
 */
export function EigenePositionUebernehmen({ block, onUebernommen }: { block: DocBlock; onUebernommen: (leistungId: number, kategorieId: number) => void }) {
    const toast = useToast();
    const [offen, setOffen] = useState(false);
    const [name, setName] = useState('');
    const [kategorie, setKategorie] = useState('');
    const [einheit, setEinheit] = useState('STUECK');
    const [kategorien, setKategorien] = useState<Kategorie[] | null>(null);
    const [meldung, setMeldung] = useState('');
    const [speichert, setSpeichert] = useState(false);

    const oeffne = () => {
        setName((block.title ?? '').trim());
        setEinheit(EINHEIT_AUS_KUERZEL[(block.unit ?? '').trim().toLowerCase()] ?? 'STUECK');
        setKategorie('');
        setMeldung('');
        setOffen(true);
    };

    useEffect(() => {
        if (!offen || kategorien) return;
        let abgebrochen = false;
        fetch('/api/produktkategorien?light=true')
            .then(r => (r.ok ? r.json() : []))
            .then(d => { if (!abgebrochen) setKategorien(Array.isArray(d) ? d : []); })
            .catch(() => { if (!abgebrochen) { setKategorien([]); setMeldung('Die Kategorien konnten nicht geladen werden.'); } });
        return () => { abgebrochen = true; };
    }, [offen, kategorien]);

    const optionen = useMemo(() => pfade(kategorien ?? []), [kategorien]);
    const gefuellt = (block.title ?? '').trim().length > 0;

    const speichere = async () => {
        if (!name.trim()) { setMeldung('Bitte einen Namen für die Leistung eintragen.'); return; }
        if (!kategorie) { setMeldung('Bitte eine Kategorie wählen, damit die Leistung im Katalog zu finden ist.'); return; }
        setSpeichert(true);
        setMeldung('');
        try {
            // Wurde die Position kalkuliert, gibt es schon eine temporäre Leistung: Sie wird samt Kalkulation übernommen, statt eine zweite anzulegen
            const temporaer = block.temporaereLeistung === true && block.leistungId != null;
            const res = await fetch(temporaer ? `/api/leistungen/${block.leistungId}/uebernehmen` : '/api/leistungen', {
                method: 'POST', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name: name.trim(), description: block.description ?? '', price: block.price ?? 0, unit: einheit, folderId: Number(kategorie) }),
            });
            const d = await res.json().catch(() => ({})) as { id?: number; message?: string };
            if (!res.ok || d.id == null) { setMeldung(d.message ?? 'Die Leistung konnte nicht gespeichert werden.'); return; }
            toast.success(`„${name.trim()}“ steht jetzt im Leistungsstamm. Die Position verweist darauf; Preis und Text im Angebot bleiben unverändert.`);
            setOffen(false);
            onUebernommen(d.id, Number(kategorie));
        } catch {
            setMeldung('Die Leistung konnte nicht gespeichert werden. Ist der Server erreichbar?');
        } finally {
            setSpeichert(false);
        }
    };

    return (
        <>
            <Button type="button" variant="outline" size="sm" className="mt-1 h-7 gap-1 border-rose-300 px-2 text-[11px] text-rose-700 hover:bg-rose-50" disabled={!gefuellt} onClick={oeffne}
                title={gefuellt ? undefined : 'Zuerst einen Titel für die Position eintragen'}>
                <BookmarkPlus className="h-3 w-3" aria-hidden="true" /> In den Leistungsstamm übernehmen
            </Button>
            <Dialog open={offen} onOpenChange={o => { if (!o && !speichert) setOffen(false); }}>
                <DialogContent className="sm:max-w-[520px]" aria-label="In den Leistungsstamm übernehmen">
                    <DialogHeader>
                        <DialogTitle>In den Leistungsstamm übernehmen</DialogTitle>
                        <DialogDescription>Aus dieser Position wird eine Leistung im Katalog, die Sie in anderen Angeboten wieder einfügen können. Das Angebot bleibt, wie es ist.</DialogDescription>
                    </DialogHeader>
                    <div className="space-y-3">
                        <div>
                            <Label htmlFor="ep-name">Name der Leistung</Label>
                            <Input id="ep-name" value={name} maxLength={255} onChange={e => setName(e.target.value)} />
                        </div>
                        <div>
                            <Label>Kategorie</Label>
                            <SearchableSelect value={kategorie} onChange={setKategorie} options={optionen} placeholder="Kategorie wählen" searchPlaceholder="Kategorie suchen …" />
                        </div>
                        <div>
                            <Label htmlFor="ep-einheit">Abgerechnet wird</Label>
                            <select id="ep-einheit" value={einheit} onChange={e => setEinheit(e.target.value)} className="min-h-10 w-full rounded-md border border-slate-300 bg-white px-3 text-sm">
                                {EINHEITEN.map(e => <option key={e.value} value={e.value}>{e.label}</option>)}
                            </select>
                        </div>
                        <p className="text-xs text-slate-500">Preis: {(block.price ?? 0).toLocaleString('de-DE', { style: 'currency', currency: 'EUR' })} je Einheit (wie in der Position).</p>
                        {meldung && <p role="alert" className="rounded-lg bg-rose-50 p-2 text-sm text-rose-800">{meldung}</p>}
                    </div>
                    <DialogFooter>
                        <Button variant="outline" onClick={() => setOffen(false)} disabled={speichert}>Abbrechen</Button>
                        <Button className="bg-rose-600 text-white hover:bg-rose-700" onClick={() => void speichere()} disabled={speichert}>
                            {speichert ? <Loader2 className="mr-2 h-4 w-4 animate-spin motion-reduce:animate-none" aria-hidden="true" /> : null}Speichern
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </>
    );
}
