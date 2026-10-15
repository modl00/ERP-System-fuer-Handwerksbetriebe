import { useEffect, useMemo, useState } from 'react';
import { Library, Loader2 } from 'lucide-react';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { Button } from '../../components/ui/button';
import { useToast } from '../../components/ui/toast';
import { parseErrorMessage } from '../../components/settings/settingsApi';

interface Zeile { name: string; einheit: string; kategorie: string; baugruppe: boolean; vorhanden: boolean; beschreibung: string; positionen: number; enthaelt: string[] }
interface FremdZeile { bezeichnung: string; lieferant: string; einheit: string; preis: number | null; mindestpreis: number | null; erklaerung: string }
interface Vorschau { hinweis: string; leistungen: Zeile[]; fremdleistungen: FremdZeile[]; maschinen: number; kategorien: number }
interface Ergebnis { leistungen: number; uebersprungen: number; arbeitsgaenge: number; artikel: number; maschinen: number; kategorien: number; lieferanten: number; fremdleistungen: number }

interface Props { offen: boolean; onOpenChange: (offen: boolean) => void; onFertig: () => void }

const zahl = (n: number | null) => (n == null ? '–' : n.toLocaleString('de-DE', { maximumFractionDigits: 2 }));

/** Text für die Rückmeldung nach dem Übernehmen: nur nennen, was wirklich angelegt wurde. */
export function ergebnisText(e: Ergebnis): string {
    const teile: string[] = [];
    const nenne = (n: number, eins: string, viele: string) => { if (n > 0) teile.push(`${n} ${n === 1 ? eins : viele}`); };
    nenne(e.leistungen, 'Leistung', 'Leistungen');
    nenne(e.artikel, 'Artikel', 'Artikel');
    nenne(e.arbeitsgaenge, 'Arbeitsgang', 'Arbeitsgänge');
    nenne(e.maschinen, 'Maschine', 'Maschinen');
    nenne(e.kategorien, 'Kategorie', 'Kategorien');
    nenne(e.lieferanten, 'Beispiel-Lieferant', 'Beispiel-Lieferanten');
    nenne(e.fremdleistungen, 'Fremdleistung', 'Fremdleistungen');
    return teile.length === 0 ? 'Es war schon alles vorhanden, nichts wurde angelegt.' : `Angelegt: ${teile.join(', ')}.`;
}

/**
 * Fertige Leistungen für Metallbau übernehmen: verschachtelt (Leistungen → Baugruppen → Artikel, Arbeitsgänge mit Maschinen, Fremdleistungen) und mit erfundenen Zeiten.
 * Das Programm legt alles an, was dafür fehlt; Vorhandenes wird übersprungen. Erst die Vorschau, dann die Auswahl.
 */
export function LeistungenVorlageDialog({ offen, onOpenChange, onFertig }: Props) {
    return (
        <Dialog open={offen} onOpenChange={onOpenChange} className="w-full max-w-4xl max-h-[90vh]" aria-labelledby="leistungen-vorlage-titel">
            <DialogContent>{offen && <Inhalt onSchliessen={() => onOpenChange(false)} onFertig={onFertig} />}</DialogContent>
        </Dialog>
    );
}

function Inhalt({ onSchliessen, onFertig }: { onSchliessen: () => void; onFertig: () => void }) {
    const toast = useToast();
    const [daten, setDaten] = useState<Vorschau | null>(null);
    const [fehler, setFehler] = useState('');
    const [auswahl, setAuswahl] = useState<Set<string>>(new Set());
    const [filter, setFilter] = useState('');
    const [speichert, setSpeichert] = useState(false);

    useEffect(() => {
        let aktuell = true;
        (async () => {
            try {
                const res = await fetch('/api/vorlagen/metallbau/leistungen');
                if (res.status === 403) throw new Error('Die Vorlagen übernehmen dürfen nur Administratoren.');
                if (!res.ok) throw new Error(await parseErrorMessage(res, 'Die Vorlagen konnten nicht geladen werden.'));
                const v = await res.json() as Vorschau;
                if (!aktuell) return;
                setDaten(v);
                setAuswahl(new Set(v.leistungen.filter(z => !z.baugruppe && !z.vorhanden).map(z => z.name)));
            } catch (e) {
                if (aktuell) setFehler(e instanceof Error ? e.message : 'Die Vorlagen konnten nicht geladen werden.');
            }
        })();
        return () => { aktuell = false; };
    }, []);

    // Baugruppen kommen von selbst mit den Leistungen, die sie brauchen: Zur Auswahl stehen die Leistungen
    const leistungen = useMemo(() => (daten?.leistungen ?? []).filter(z => !z.baugruppe), [daten]);
    const baugruppen = useMemo(() => (daten?.leistungen ?? []).filter(z => z.baugruppe), [daten]);
    const sichtbar = useMemo(() => {
        const f = filter.trim().toLowerCase();
        return f ? leistungen.filter(z => `${z.name} ${z.kategorie}`.toLowerCase().includes(f)) : leistungen;
    }, [leistungen, filter]);
    const neue = sichtbar.filter(z => !z.vorhanden).map(z => z.name);
    const alleGewaehlt = neue.length > 0 && neue.every(n => auswahl.has(n));
    const umschalten = (n: string) => setAuswahl(alt => { const neu = new Set(alt); if (!neu.delete(n)) neu.add(n); return neu; });
    const alleUmschalten = () => setAuswahl(alt => { const neu = new Set(alt); for (const n of neue) { if (alleGewaehlt) neu.delete(n); else neu.add(n); } return neu; });

    const uebernehmen = async () => {
        setSpeichert(true);
        try {
            const res = await fetch('/api/vorlagen/metallbau/leistungen/uebernehmen', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ namen: [...auswahl] }) });
            if (!res.ok) throw new Error(await parseErrorMessage(res, 'Die Leistungen konnten nicht übernommen werden.'));
            const e = await res.json() as Ergebnis;
            toast.success(ergebnisText(e));
            if (e.uebersprungen > 0) toast.info(`${e.uebersprungen} waren schon vorhanden und wurden übersprungen.`);
            onFertig();
            onSchliessen();
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Die Leistungen konnten nicht übernommen werden.');
            setSpeichert(false);
        }
    };

    return (
        <>
            <DialogHeader>
                <DialogTitle id="leistungen-vorlage-titel" className="flex items-center gap-2"><Library className="h-5 w-5 text-rose-600" aria-hidden="true" /> Fertige Leistungen für Metallbau übernehmen</DialogTitle>
                <DialogDescription>Geländer, Treppen, Tore, Zäune, Vordächer, Gitter und mehr: vollständig kalkuliert mit erfundenen Zeiten. Jede Leistung besteht aus Baugruppen, diese aus Artikeln, Arbeitsgängen mit Maschinen und Fremdleistungen. Was dafür fehlt, legt das Programm mit an.</DialogDescription>
            </DialogHeader>
            {fehler && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{fehler}</p>}
            {!daten && !fehler && <p role="status" className="flex items-center gap-2 text-sm text-slate-600"><Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> Wird geladen …</p>}
            {daten && (
                <div className="min-h-0 flex-1 space-y-3 overflow-y-auto" data-testid="leistungen-vorschau">
                    <p className="rounded-lg bg-slate-50 p-3 text-xs text-slate-600">{daten.hinweis}</p>
                    <div className="flex flex-wrap items-center gap-3">
                        <input type="search" value={filter} onChange={e => setFilter(e.target.value)} placeholder="Suchen, zum Beispiel „Tor“ oder „Treppe“" aria-label="Leistungen durchsuchen" className="min-w-0 flex-1 rounded-lg border border-slate-300 px-3 py-1.5 text-sm" />
                        <Button variant="outline" size="sm" onClick={alleUmschalten} disabled={neue.length === 0}>{alleGewaehlt ? 'Auswahl aufheben' : 'Alle neuen auswählen'}</Button>
                    </div>
                    <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200">
                        {sichtbar.map(z => (
                            <li key={z.name} className="flex gap-3 px-3 py-2 text-sm" data-testid={`vorlage-${z.name}`}>
                                <input type="checkbox" className="mt-1 h-4 w-4 accent-rose-600" aria-label={`${z.name} übernehmen`} disabled={z.vorhanden} checked={auswahl.has(z.name)} onChange={() => umschalten(z.name)} />
                                <div className="min-w-0 flex-1">
                                    <p className="font-medium text-slate-900">{z.name} <span className="font-normal text-slate-500">· {z.kategorie} · je {z.einheit}</span></p>
                                    <p className="text-xs text-slate-600">{z.beschreibung}</p>
                                    {z.enthaelt.length > 0 && <p className="text-xs text-slate-500">Mit Baugruppen: {z.enthaelt.join('; ')}</p>}
                                </div>
                                <span className="shrink-0 text-xs">{z.vorhanden ? <span className="text-slate-500">vorhanden</span> : <span className="font-medium text-emerald-700">neu</span>}</span>
                            </li>
                        ))}
                        {sichtbar.length === 0 && <li className="px-3 py-3 text-sm text-slate-600">Keine Leistung gefunden.</li>}
                    </ul>
                    <p className="text-xs text-slate-500">Dazu kommen {baugruppen.length} Baugruppen, {daten.maschinen} Maschinen und {daten.kategorien} Kategorien, soweit die gewählten Leistungen sie brauchen. Es werden keine Preise und Stundensätze angelegt; die Leistungen sind erst „kalkuliert“, wenn Sie Stundensätze und Artikelpreise eintragen.</p>

                    <section className="rounded-lg border border-sky-200 bg-sky-50 p-3 text-sm text-sky-950" aria-label="Was ist eine Charge?" data-testid="charge-erklaerung">
                        <h4 className="font-semibold">Fremdleistungen und „Chargen“ – kurz erklärt</h4>
                        <p className="mt-1 text-xs">Eine <b>Fremdleistung</b> ist Arbeit, die ein anderer Betrieb für Sie macht, zum Beispiel Verzinken oder Pulverbeschichten. Eine <b>Charge</b> ist eine <b>Ladung</b>: Der Veredler bearbeitet mehrere Aufträge zusammen in einem Durchgang (ein Zinkbad, ein Lkw voll). Kosten, die einmal je Ladung entstehen (Fahrt, Aufhängen, Anlagenstart), teilen sich auf die Menge der Ladung auf. Beispiel: 90 Euro je Ladung bei 600 kg Ladung heißt, dass ein Auftrag mit 150 kg 22,50 Euro davon bezahlt.</p>
                        <ul className="mt-2 space-y-1 text-xs">
                            {daten.fremdleistungen.map(f => (
                                <li key={f.bezeichnung}><b>{f.bezeichnung}</b> bei {f.lieferant}: {zahl(f.preis)} Euro je {f.einheit}, mindestens {zahl(f.mindestpreis)} Euro. {f.erklaerung}</li>
                            ))}
                        </ul>
                    </section>
                </div>
            )}
            <DialogFooter>
                <Button variant="outline" onClick={onSchliessen} disabled={speichert}>Abbrechen</Button>
                <Button className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" onClick={() => void uebernehmen()} disabled={speichert || auswahl.size === 0}>
                    {speichert && <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" />} Leistungen übernehmen ({auswahl.size})
                </Button>
            </DialogFooter>
        </>
    );
}
