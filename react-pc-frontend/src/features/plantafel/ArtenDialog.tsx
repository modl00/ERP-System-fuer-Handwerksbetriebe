import { useEffect, useState } from 'react';
import { Loader2, Plus } from 'lucide-react';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { Button } from '../../components/ui/button';
import { useToast } from '../../components/ui/toast';
import { speichereTyp, type Vorgangstyp } from './plantafelApi';
import { ZuordnungDialog } from './ZuordnungDialog';

interface Props { offen: boolean; typen: Vorgangstyp[]; onSchliessen: () => void; onGeaendert: (typen: Vorgangstyp[]) => void }

/** Die Vorgangsarten der Plantafel: Name, Farbe und Schätzwert für die Dauer; nicht mehr gebrauchte lassen sich ausblenden. */
export function ArtenDialog(props: Props) {
    return (
        <Dialog open={props.offen} onOpenChange={o => { if (!o) props.onSchliessen(); }} className="w-full max-w-3xl max-h-[90vh]" aria-labelledby="arten-titel">
            <DialogContent>{props.offen && <Inhalt {...props} />}</DialogContent>
        </Dialog>
    );
}

interface Entwurf { id: number | null; name: string; farbe: string; standardDauerTage: string; aktiv: boolean; sortierung: number; abteilungId: string; stundenProTag: string; wartet: boolean; buero: boolean }

const leer = (sortierung: number): Entwurf => ({ id: null, name: '', farbe: '#be123c', standardDauerTage: '1', aktiv: true, sortierung, abteilungId: '', stundenProTag: '', wartet: false, buero: false });

const alsZahl = (text: string) => Number(text.trim().replace(',', '.'));

/** Leer oder eine Zahl über 0 bis 1000 (Stunden pro Arbeitstag). */
const stundenGueltig = (text: string) => text.trim() === '' || (Number.isFinite(alsZahl(text)) && alsZahl(text) > 0 && alsZahl(text) <= 1000);

interface Abteilung { id: number; name: string }

function Inhalt({ typen, onSchliessen, onGeaendert }: Props) {
    const toast = useToast();
    const [liste, setListe] = useState<Entwurf[]>(() => typen.map(t => ({ id: t.id, name: t.name, farbe: t.farbe, standardDauerTage: String(t.standardDauerTage), aktiv: t.aktiv, sortierung: t.sortierung, abteilungId: t.abteilungId == null ? '' : String(t.abteilungId), stundenProTag: t.stundenProTag == null ? '' : String(t.stundenProTag).replace('.', ','), wartet: t.wartet, buero: t.buero })));
    const [abteilungen, setAbteilungen] = useState<Abteilung[]>([]);

    useEffect(() => {
        let aktuell = true;
        fetch('/api/abteilungen').then(r => (r.ok ? r.json() : [])).then(d => { if (aktuell && Array.isArray(d)) setAbteilungen(d as Abteilung[]); }).catch(() => { /* ohne Liste bleibt die Auswahl leer */ });
        return () => { aktuell = false; };
    }, []);
    const [arbeitet, setArbeitet] = useState<number | 'neu' | null>(null);
    const [zuordnungOffen, setZuordnungOffen] = useState(false);
    const [fehler, setFehler] = useState('');
    const [neuerEintrag, setNeuerEintrag] = useState<Entwurf>(() => leer((typen.at(-1)?.sortierung ?? 0) + 10));

    const aendere = (i: number, teil: Partial<Entwurf>) => setListe(alt => alt.map((e, j) => (j === i ? { ...e, ...teil } : e)));

    const speichern = async (e: Entwurf, schluessel: number | 'neu') => {
        setFehler('');
        const tage = Number(e.standardDauerTage);
        if (!Number.isInteger(tage) || tage < 0) {
            setFehler('Der Schätzwert muss eine ganze Zahl von Arbeitstagen sein (0 oder mehr).');
            return;
        }
        if (!stundenGueltig(e.stundenProTag)) {
            setFehler('Die Stunden pro Arbeitstag müssen größer als 0 sein (oder leer bleiben).');
            return;
        }
        setArbeitet(schluessel);
        try {
            const gespeichert = await speichereTyp(e.id, {
                name: e.name, farbe: e.farbe, standardDauerTage: tage, aktiv: e.aktiv, sortierung: e.sortierung,
                abteilungId: e.abteilungId === '' ? null : Number(e.abteilungId), stundenProTag: e.stundenProTag.trim() === '' ? null : alsZahl(e.stundenProTag),
                wartet: e.wartet, buero: e.buero,
            });
            const neuListe = e.id == null ? [...liste, { ...e, id: gespeichert.id }] : liste.map(x => (x.id === e.id ? { ...x, name: gespeichert.name } : x));
            setListe(neuListe);
            if (e.id == null) setNeuerEintrag(leer(e.sortierung + 10));
            onGeaendert(neuListe.map(x => ({ id: x.id as number, name: x.name, farbe: x.farbe, sortierung: x.sortierung, standardDauerTage: Number(x.standardDauerTage), aktiv: x.aktiv, abteilungId: x.abteilungId === '' ? null : Number(x.abteilungId), stundenProTag: x.stundenProTag.trim() === '' ? null : alsZahl(x.stundenProTag), wartet: x.wartet, buero: x.buero })));
            toast.success(e.id == null ? 'Die Vorgangsart wurde angelegt.' : 'Die Vorgangsart wurde gespeichert.');
        } catch (err) {
            setFehler(err instanceof Error ? err.message : 'Die Vorgangsart konnte nicht gespeichert werden.');
        } finally {
            setArbeitet(null);
        }
    };

    const zeile = (e: Entwurf, schluessel: number | 'neu', aendern: (t: Partial<Entwurf>) => void) => (
        <li key={schluessel} className="flex flex-wrap items-center gap-2 px-3 py-2" data-testid={`art-${schluessel}`}>
            <input type="color" aria-label={`Farbe von ${e.name || 'neuer Art'}`} value={e.farbe} onChange={ev => aendern({ farbe: ev.target.value })} className="h-8 w-9 cursor-pointer rounded border border-slate-300" />
            <input aria-label="Name der Vorgangsart" value={e.name} maxLength={100} onChange={ev => aendern({ name: ev.target.value })} placeholder="Name" className="min-w-0 flex-1 rounded-lg border border-slate-300 px-3 py-1.5 text-sm" />
            <label className="flex items-center gap-1.5 text-xs text-slate-600">
                Schätzwert
                <input aria-label={`Schätzwert in Arbeitstagen für ${e.name || 'neue Art'}`} inputMode="numeric" value={e.standardDauerTage} onChange={ev => aendern({ standardDauerTage: ev.target.value })} className="w-14 rounded border border-slate-300 px-2 py-1 text-right text-sm" />
                Tg.
            </label>
            <label className="flex items-center gap-1.5 text-xs text-slate-600">
                Arbeitszeit aus
                <select aria-label={`Abteilung für ${e.name || 'neue Art'}`} value={e.abteilungId} onChange={ev => aendern({ abteilungId: ev.target.value })} className="max-w-40 rounded border border-slate-300 bg-white px-2 py-1 text-sm">
                    <option value="">keine (kein Vorschlag)</option>
                    {abteilungen.map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
                </select>
            </label>
            <label className="flex items-center gap-1.5 text-xs text-slate-600">
                Std. pro Tag
                <input aria-label={`Stunden pro Arbeitstag für ${e.name || 'neue Art'}`} inputMode="decimal" value={e.stundenProTag} onChange={ev => aendern({ stundenProTag: ev.target.value })} placeholder="z. B. 24" className="w-16 rounded border border-slate-300 px-2 py-1 text-right text-sm" />
            </label>
            <label className="flex items-center gap-1.5 text-xs text-slate-600" title="Braucht keine Mitarbeiter, nur Zeit (zum Beispiel Material liefern lassen oder Verzinken beim Lieferanten). Die Dauer zählt in Arbeitstagen.">
                <input type="checkbox" aria-label={`${e.name || 'Neue Art'} ist ein Wartevorgang`} className="h-4 w-4 accent-rose-600" checked={e.wartet} onChange={ev => aendern({ wartet: ev.target.checked })} /> Wartevorgang
            </label>
            <label className="flex items-center gap-1.5 text-xs text-slate-600" title="Büroarbeit zählt auch an den Bürotagen einer Person; Fertigung und Montage nicht.">
                <input type="checkbox" aria-label={`${e.name || 'Neue Art'} ist Büroarbeit`} className="h-4 w-4 accent-rose-600" checked={e.buero} onChange={ev => aendern({ buero: ev.target.checked })} /> Büroarbeit
            </label>
            <label className="flex items-center gap-1.5 text-xs text-slate-600">
                <input type="checkbox" className="h-4 w-4 accent-rose-600" checked={e.aktiv} onChange={ev => aendern({ aktiv: ev.target.checked })} /> in Gebrauch
            </label>
            <Button size="sm" variant="outline" onClick={() => void speichern(e, schluessel)} disabled={arbeitet !== null || e.name.trim() === ''} aria-label={schluessel === 'neu' ? 'Neue Vorgangsart anlegen' : `${e.name} speichern`}>
                {arbeitet === schluessel ? <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> : schluessel === 'neu' ? <Plus className="h-4 w-4" aria-hidden="true" /> : null}
                {schluessel === 'neu' ? 'Anlegen' : 'Speichern'}
            </Button>
        </li>
    );

    return (
        <>
            <DialogHeader>
                <DialogTitle id="arten-titel">Vorgangsarten</DialogTitle>
                <DialogDescription>Zum Beispiel Planung, Materialbeschaffung, Fertigung, Veredelung, Montage. Der Schätzwert gilt, solange keine genauere Dauer bekannt ist. Mit „Arbeitszeit aus" (Abteilung) holt das Programm den Aufwand in Stunden aus dem Angebot. Ein Wartevorgang (Material, Fremdleistung) braucht keine Mitarbeiter, nur Zeit in Arbeitstagen; alle anderen Vorgänge rechnet das Programm aus Aufwand, Mitarbeitern und freier Zeit. Eine Art, die schon Vorgänge hat, lässt sich ausblenden („in Gebrauch" abhaken).</DialogDescription>
            </DialogHeader>
            <div className="min-h-0 flex-1 space-y-3 overflow-y-auto py-1">
                {fehler && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{fehler}</p>}
                <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200">
                    {liste.map((e, i) => zeile(e, e.id as number, t => aendere(i, t)))}
                </ul>
                <p className="text-sm font-medium text-slate-900">Neue Vorgangsart</p>
                <ul className="rounded-lg border border-dashed border-slate-300">{zeile(neuerEintrag, 'neu', t => setNeuerEintrag(alt => ({ ...alt, ...t })))}</ul>
            </div>
            <DialogFooter>
                <Button variant="outline" className="mr-auto" onClick={() => setZuordnungOffen(true)} title="Zu welcher Vorgangsart die gestempelte Zeit eines Arbeitsgangs zählt (Fortschritt)">Gestempelte Zeit zuordnen</Button>
                <Button variant="outline" onClick={onSchliessen}>Schließen</Button>
            </DialogFooter>
            <ZuordnungDialog offen={zuordnungOffen} typen={liste.map(e => ({ id: e.id as number, name: e.name, farbe: e.farbe, sortierung: e.sortierung, standardDauerTage: Number(e.standardDauerTage), aktiv: e.aktiv, abteilungId: null, stundenProTag: null, wartet: e.wartet, buero: e.buero }))} onSchliessen={() => setZuordnungOffen(false)} />
        </>
    );
}
