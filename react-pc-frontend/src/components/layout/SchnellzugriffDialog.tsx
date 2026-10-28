import { useId, useMemo, useState } from 'react';
import { ArrowDown, ArrowUp, Check, LayoutGrid, Loader2, RotateCcw, Search, X } from 'lucide-react';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../ui/dialog';
import { Button } from '../ui/button';
import { useToast } from '../ui/toast';
import { useConfirm } from '../ui/confirm-dialog';
import { ALLE_FUNKTIONEN, loescheEigeneLeiste, speichereEigeneLeiste, speichereLeistenVorgabe, type LeisteStand } from '../../features/schnellzugriff/schnellzugriffApi';
import { eintraegeAus, setzeLeistenStand, useSchnellzugriff } from '../../features/schnellzugriff/useSchnellzugriff';
import type { FunktionsKategorie } from './AlleFunktionenDialog';

interface Props {
    offen: boolean;
    onSchliessen: () => void;
    navigation: FunktionsKategorie[];
    istAdmin: boolean;
}

/** „Alle Funktionen" steht fest in der Leiste und wird nicht mehr gewählt; ein alter Eintrag fällt beim Speichern weg. */
const ohneAlle = (eintraege: string[]) => eintraege.filter((a) => a !== ALLE_FUNKTIONEN);

/** „Schnellzugriff anpassen": Funktionen mit Haken auf die Leiste legen, Reihenfolge ändern. Die Firma kann eine Vorgabe für alle speichern. */
export function SchnellzugriffDialog({ offen, onSchliessen, ...rest }: Props) {
    const titelId = useId();
    return (
        <Dialog open={offen} onOpenChange={(o) => { if (!o) onSchliessen(); }} className="w-full max-w-3xl" aria-labelledby={titelId}>
            <Inhalt titelId={titelId} onSchliessen={onSchliessen} {...rest} />
        </Dialog>
    );
}

function Inhalt({ titelId, onSchliessen, navigation, istAdmin }: Omit<Props, 'offen'> & { titelId: string }) {
    const toast = useToast();
    const confirm = useConfirm();
    const { stand } = useSchnellzugriff();
    const [entwurf, setEntwurf] = useState<string[]>(() => (stand ? ohneAlle(eintraegeAus(stand)) : []));
    const [suche, setSuche] = useState('');
    const [arbeitet, setArbeitet] = useState(false);

    const funktionen = useMemo(() => {
        const karte = new Map<string, { name: string; icon: React.ComponentType<{ className?: string }> }>();
        for (const k of navigation) for (const sg of k.subgroups) for (const i of sg.items) if (!karte.has(i.href)) karte.set(i.href, { name: i.name, icon: i.icon });
        return karte;
    }, [navigation]);

    const geaendert = useMemo(() => {
        const vorher = stand ? ohneAlle(eintraegeAus(stand)) : [];
        return vorher.length !== entwurf.length || vorher.some((a, i) => a !== entwurf[i]);
    }, [stand, entwurf]);

    const gefiltert = useMemo(() => {
        const q = suche.trim().toLocaleLowerCase('de-DE');
        const passt = (name: string) => q === '' || name.toLocaleLowerCase('de-DE').includes(q);
        return navigation
            .map((k) => ({
                category: k.category,
                items: k.subgroups.flatMap((sg) => sg.items).filter((i) => passt(i.name)),
            }))
            .filter((k) => k.items.length > 0);
    }, [navigation, suche]);

    const umschalten = (adresse: string) => setEntwurf((alt) => (alt.includes(adresse) ? alt.filter((a) => a !== adresse) : [...alt, adresse]));
    const verschiebe = (index: number, richtung: -1 | 1) => setEntwurf((alt) => {
        const ziel = index + richtung;
        if (ziel < 0 || ziel >= alt.length) return alt;
        const neu = [...alt];
        [neu[index], neu[ziel]] = [neu[ziel], neu[index]];
        return neu;
    });

    const ausfuehren = async (aktion: () => Promise<LeisteStand>, erfolg: string, fehler: string) => {
        setArbeitet(true);
        try {
            setzeLeistenStand(await aktion());
            toast.success(erfolg);
            return true;
        } catch (e) {
            toast.error(e instanceof Error ? e.message : fehler);
            return false;
        } finally {
            setArbeitet(false);
        }
    };

    const speichern = async () => {
        if (await ausfuehren(() => speichereEigeneLeiste(entwurf), 'Deine Schnellzugriffsleiste ist gespeichert.', 'Deine Leiste konnte nicht gespeichert werden.')) onSchliessen();
    };
    const aufVorgabe = async () => {
        if (await ausfuehren(loescheEigeneLeiste, 'Deine Leiste folgt wieder der Vorgabe.', 'Deine Leiste konnte nicht zurückgesetzt werden.')) onSchliessen();
    };
    const fuerAlle = async () => {
        const ja = await confirm({
            title: 'Als Vorgabe für alle speichern?',
            message: 'Wer seine Leiste noch nicht selbst eingestellt hat, sieht danach genau diese Auswahl. Eigene Leisten einzelner Benutzer bleiben unverändert.',
            confirmLabel: 'Für alle speichern',
            variant: 'warning',
        });
        if (!ja) return;
        if (await ausfuehren(() => speichereLeistenVorgabe(entwurf), 'Die Vorgabe für alle ist gespeichert.', 'Die Vorgabe konnte nicht gespeichert werden.')) onSchliessen();
    };

    const zeile = (adresse: string, name: string, Icon: React.ComponentType<{ className?: string }>) => (
        <label key={adresse} className="flex cursor-pointer items-center gap-3 rounded-md px-2 py-1.5 hover:bg-slate-50">
            <input type="checkbox" checked={entwurf.includes(adresse)} onChange={() => umschalten(adresse)} aria-label={`${name} auf die Leiste legen`}
                className="h-4 w-4 shrink-0 rounded border-slate-300 accent-rose-600 focus:ring-2 focus:ring-rose-500" />
            <Icon className="h-4 w-4 shrink-0 text-slate-500" aria-hidden="true" />
            <span className="truncate text-sm text-slate-800" title={name}>{name}</span>
        </label>
    );

    return (
        <DialogContent>
            <DialogHeader>
                <DialogTitle id={titelId}>Schnellzugriff anpassen</DialogTitle>
                <DialogDescription>Wähle die Funktionen, die unter dem Menü stehen sollen, und bring sie in die Reihenfolge, die dir passt.</DialogDescription>
            </DialogHeader>

            <section aria-label="Deine Leiste" className="rounded-lg border border-slate-200 bg-slate-50 p-2">
                <h3 className="mb-1 px-1 text-xs font-semibold uppercase tracking-wide text-slate-500">Deine Leiste</h3>
                {entwurf.length === 0 && <p className="px-1 py-1 text-sm text-slate-500">Noch nichts ausgewählt. Setze unten einen Haken.</p>}
                <ol className="space-y-0.5">
                    {entwurf.map((adresse, i) => {
                        const f = funktionen.get(adresse);
                        const Icon = f?.icon ?? LayoutGrid;
                        const name = f?.name ?? adresse;
                        return (
                            <li key={adresse} className="flex items-center gap-2 rounded-md bg-white px-2 py-1 text-sm">
                                <Icon className="h-4 w-4 shrink-0 text-slate-500" aria-hidden="true" />
                                <span className="min-w-0 flex-1 truncate" title={name}>{name}</span>
                                {!f && <span className="shrink-0 text-xs text-amber-700">für dich nicht sichtbar</span>}
                                <button type="button" onClick={() => verschiebe(i, -1)} disabled={i === 0} aria-label={`${name} nach vorne`} className="rounded p-1 hover:bg-slate-100 disabled:opacity-30"><ArrowUp className="h-3.5 w-3.5" aria-hidden="true" /></button>
                                <button type="button" onClick={() => verschiebe(i, 1)} disabled={i === entwurf.length - 1} aria-label={`${name} nach hinten`} className="rounded p-1 hover:bg-slate-100 disabled:opacity-30"><ArrowDown className="h-3.5 w-3.5" aria-hidden="true" /></button>
                                <button type="button" onClick={() => umschalten(adresse)} aria-label={`${name} von der Leiste nehmen`} className="rounded p-1 hover:bg-slate-100"><X className="h-3.5 w-3.5" aria-hidden="true" /></button>
                            </li>
                        );
                    })}
                </ol>
            </section>

            <div className="relative">
                <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
                <input type="search" value={suche} onChange={(e) => setSuche(e.target.value)} placeholder="Funktion suchen" aria-label="Funktion suchen"
                    className="w-full rounded-md border border-slate-300 py-2 pl-9 pr-3 text-sm focus:border-rose-500 focus:outline-none focus:ring-2 focus:ring-rose-500" />
            </div>

            <div className="-m-1 min-h-0 flex-1 overflow-y-auto p-1">
                {gefiltert.length === 0 && suche.trim() !== '' && <p className="py-4 text-center text-sm text-slate-500" role="status">Keine Funktion gefunden.</p>}
                {gefiltert.map((k) => (
                    <section key={k.category} className="mb-4" aria-label={k.category}>
                        <h3 className="mb-1 text-sm font-semibold text-rose-700">{k.category}</h3>
                        <div className="grid grid-cols-1 gap-x-6 sm:grid-cols-2">{k.items.map((i) => zeile(i.href, i.name, i.icon))}</div>
                    </section>
                ))}
            </div>

            <DialogFooter className="flex-wrap gap-2">
                <Button type="button" variant="ghost" size="sm" disabled={arbeitet || stand?.eigene == null} onClick={() => void aufVorgabe()}>
                    <RotateCcw className="mr-1.5 h-4 w-4" aria-hidden="true" />Wie die Vorgabe
                </Button>
                {istAdmin && <Button type="button" variant="outline" size="sm" disabled={arbeitet} onClick={() => void fuerAlle()}>Als Vorgabe für alle speichern</Button>}
                <Button type="button" variant="outline" size="sm" disabled={arbeitet} onClick={onSchliessen}>Abbrechen</Button>
                <Button type="button" size="sm" disabled={arbeitet || !geaendert} onClick={() => void speichern()}>
                    {arbeitet ? <Loader2 className="mr-1.5 h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> : <Check className="mr-1.5 h-4 w-4" aria-hidden="true" />}
                    Speichern
                </Button>
            </DialogFooter>
        </DialogContent>
    );
}
