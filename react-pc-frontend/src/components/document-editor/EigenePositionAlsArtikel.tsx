import { useState } from 'react';
import { Loader2, Package } from 'lucide-react';
import { Button } from '../ui/button';
import { DecimalInput } from '../ui/decimal-input';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../ui/dialog';
import { Input } from '../ui/input';
import { Label } from '../ui/label';
import { SearchableSelect } from '../ui/searchable-select';
import { useToast } from '../ui/toast';
import { CategoryTreeModal } from '../CategoryTreeModal';
import { formatDecimalInput, validateDecimalInput } from '../../lib/numberInput';
import type { DocBlock } from './types';
import { aufschlagFuer, unitMap } from './helpers';

const EINHEITEN = Object.entries(unitMap).map(([wert, kurz]) => ({ value: wert, label: kurz }));
const EINHEIT_AUS_KUERZEL: Record<string, string> = Object.fromEntries(Object.entries(unitMap).map(([wert, kurz]) => [kurz.toLowerCase(), wert]));

/**
 * Eine einmalige Position nur für dieses Angebot als Artikel in den Artikelstamm übernehmen (#55, #205): Bezeichnung, Kategorie und Einheit bestätigen, auf Wunsch Lieferant und
 * Einkaufspreis. Aus dem Einkaufspreis und dem Verkaufspreis der Position ergibt sich der Aufschlag, damit der Artikel denselben Verkaufspreis liefert. Die Position verweist danach
 * auf den Artikel; Preis und Text im Angebot bleiben unverändert.
 */
export function EigenePositionAlsArtikel({ block, onUebernommen }: { block: DocBlock; onUebernommen: (artikelId: number) => void }) {
    const toast = useToast();
    const [offen, setOffen] = useState(false);
    const [name, setName] = useState('');
    const [einheit, setEinheit] = useState('STUECK');
    const [kategorie, setKategorie] = useState<{ id: number; name: string } | null>(null);
    const [waehleKategorie, setWaehleKategorie] = useState(false);
    const [lieferant, setLieferant] = useState('');
    const [lieferantName, setLieferantName] = useState('');
    const [einkauf, setEinkauf] = useState('');
    const [meldung, setMeldung] = useState('');
    const [speichert, setSpeichert] = useState(false);

    const verkauf = block.price ?? 0;
    const gefuellt = (block.title ?? '').trim().length > 0;
    const ek = validateDecimalInput(einkauf, { label: 'Der Einkaufspreis', required: false, min: 0.0001, max: 1000000 });
    const ekWert = ek.valid ? ek.value ?? null : null;
    const aufschlag = aufschlagFuer(verkauf, ekWert);

    const oeffne = () => {
        setName((block.title ?? '').trim());
        setEinheit(EINHEIT_AUS_KUERZEL[(block.unit ?? '').trim().toLowerCase()] ?? 'STUECK');
        setKategorie(null); setLieferant(''); setLieferantName(''); setEinkauf(''); setMeldung('');
        setOffen(true);
    };

    const sucheLieferant = async (begriff: string) => {
        const res = await fetch(`/api/lieferanten?q=${encodeURIComponent(begriff)}&size=20`);
        if (!res.ok) return [];
        const d = await res.json() as { lieferanten?: { id: number; lieferantenname?: string; firmenname?: string }[] };
        return (d.lieferanten ?? []).map(l => ({ value: String(l.id), label: l.lieferantenname || l.firmenname || `Lieferant ${l.id}` }));
    };

    const speichere = async () => {
        if (!name.trim()) { setMeldung('Bitte eine Bezeichnung für den Artikel eintragen.'); return; }
        if (!kategorie) { setMeldung('Bitte eine Kategorie wählen, damit der Artikel im Stamm zu finden ist.'); return; }
        if (!ek.valid) { setMeldung(ek.message); return; }
        if (ekWert != null && !lieferant) { setMeldung('Zum Einkaufspreis gehört der Lieferant. Bitte einen Lieferanten wählen (oder den Einkaufspreis leer lassen).'); return; }
        setSpeichert(true);
        setMeldung('');
        try {
            const res = await fetch('/api/artikel', {
                method: 'POST', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ produktname: name.trim(), verrechnungseinheit: einheit, kategorieId: kategorie.id, ...(ekWert != null ? { lieferantId: Number(lieferant), preis: ekWert } : {}) }),
            });
            const d = await res.json().catch(() => ({})) as { id?: number; message?: string };
            if (!res.ok || d.id == null) { setMeldung(d.message ?? 'Der Artikel konnte nicht gespeichert werden.'); return; }
            // Text für den Kunden, interne Kurzbeschreibung und Aufschlag (damit der Verkaufspreis stimmt) setzt ein zweiter Schritt
            const texte = await fetch(`/api/artikel/${d.id}/dokumenttexte`, {
                method: 'PATCH', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ kurzbeschreibung: name.trim(), beschreibung: block.description ?? '', ...(aufschlag != null ? { verkaufsaufschlagProzent: aufschlag } : {}) }),
            });
            toast.success(texte.ok
                ? `„${name.trim()}" steht jetzt im Artikelstamm. Die Position verweist darauf; Preis und Text im Angebot bleiben unverändert.`
                : `„${name.trim()}" steht im Artikelstamm, aber Text und Aufschlag konnten nicht übernommen werden. Bitte im Artikel nachtragen.`);
            setOffen(false);
            onUebernommen(d.id);
        } catch {
            setMeldung('Der Artikel konnte nicht gespeichert werden. Ist der Server erreichbar?');
        } finally {
            setSpeichert(false);
        }
    };

    return (
        <>
            <Button type="button" variant="outline" size="sm" className="mt-1 h-7 gap-1 border-rose-300 px-2 text-[11px] text-rose-700 hover:bg-rose-50" disabled={!gefuellt} onClick={oeffne}
                title={gefuellt ? undefined : 'Zuerst einen Titel für die Position eintragen'}>
                <Package className="h-3 w-3" aria-hidden="true" /> Als Artikel übernehmen
            </Button>
            <Dialog open={offen} onOpenChange={o => { if (!o && !speichert && !waehleKategorie) setOffen(false); }}>
                <DialogContent className="sm:max-w-[560px]" aria-label="Als Artikel übernehmen">
                    <DialogHeader>
                        <DialogTitle>Als Artikel übernehmen</DialogTitle>
                        <DialogDescription>Aus dieser Position wird ein Artikel im Artikelstamm, den Sie in anderen Angeboten wieder einfügen können. Das Angebot bleibt, wie es ist.</DialogDescription>
                    </DialogHeader>
                    <div className="space-y-3">
                        <div>
                            <Label htmlFor="ea-name">Bezeichnung des Artikels</Label>
                            <Input id="ea-name" value={name} maxLength={255} onChange={e => setName(e.target.value)} />
                        </div>
                        <div>
                            <Label>Kategorie</Label>
                            <div>
                                <Button type="button" variant="outline" size="sm" onClick={() => setWaehleKategorie(true)}>{kategorie ? kategorie.name : 'Kategorie wählen'}</Button>
                            </div>
                        </div>
                        <div>
                            <Label htmlFor="ea-einheit">Abgerechnet wird</Label>
                            <select id="ea-einheit" value={einheit} onChange={e => setEinheit(e.target.value)} className="min-h-10 w-full rounded-md border border-slate-300 bg-white px-3 text-sm">
                                {EINHEITEN.map(e => <option key={e.value} value={e.value}>{e.label}</option>)}
                            </select>
                        </div>
                        <div className="grid gap-3 sm:grid-cols-2">
                            <div>
                                <Label>Lieferant (optional)</Label>
                                <SearchableSelect value={lieferant} onChange={v => { setLieferant(v); if (!v) setLieferantName(''); }} onSearch={sucheLieferant} selectedLabel={lieferantName} placeholder="Lieferant wählen" searchPlaceholder="Name eingeben …" />
                            </div>
                            <DecimalInput label="Einkaufspreis netto je Einheit (€, optional)" value={einkauf} onChange={setEinkauf} min={0.0001} max={1000000} />
                        </div>
                        <p className="text-xs text-slate-500" data-testid="aufschlag-hinweis">
                            Verkaufspreis in der Position: {verkauf.toLocaleString('de-DE', { style: 'currency', currency: 'EUR' })}.{' '}
                            {aufschlag != null
                                ? `Daraus ergibt sich ein Aufschlag von ${formatDecimalInput(aufschlag)} % auf den Einkaufspreis, damit der Artikel denselben Verkaufspreis liefert.${ekWert != null && verkauf < ekWert ? ' Achtung: Der Verkaufspreis liegt unter dem Einkaufspreis, der Aufschlag bleibt bei 0 %.' : ''}`
                                : 'Ohne Einkaufspreis bleibt der Aufschlag leer; den Einkaufspreis und den Aufschlag pflegen Sie dann im Artikel.'}
                        </p>
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
            {waehleKategorie && (
                <CategoryTreeModal onSelect={(id, kName) => { setKategorie({ id: Number(id), name: kName }); setWaehleKategorie(false); }} onClose={() => setWaehleKategorie(false)} />
            )}
        </>
    );
}
