import { useId, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Check, Loader2, RotateCcw, Search } from 'lucide-react';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../ui/dialog';
import { Button } from '../ui/button';
import { useToast } from '../ui/toast';
import { useConfirm } from '../ui/confirm-dialog';
import { cn } from '../../lib/utils';
import { loescheEigene, speichereEigene, speichereVorgabe } from '../../features/menue/menueApi';
import { setzeMenueStand, ausgeblendetAus, useMenue } from '../../features/menue/useMenue';

interface Eintrag {
    name: string;
    href: string;
    /** Weitere Adressen, die zu dieser Funktion gehören (für „hier bin ich gerade“). */
    auch?: string[];
    icon: React.ComponentType<{ className?: string }>;
}

export interface FunktionsKategorie {
    category: string;
    subgroups: { label: string; items: Eintrag[] }[];
}

interface Props {
    offen: boolean;
    onSchliessen: () => void;
    /** Alles, was dieser Benutzer sehen darf (Rechte und Module sind schon abgezogen), egal ob im Menü oder nicht. */
    navigation: FunktionsKategorie[];
    istAdmin: boolean;
}

/** „Alle Funktionen“: jede Funktion mit Haken, ob sie im Hauptmenü stehen soll. Ausgeblendetes bleibt hier erreichbar. */
export function AlleFunktionenDialog({ offen, onSchliessen, ...rest }: Props) {
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
    const { stand } = useMenue();
    // Entwurf: alle ausgeblendeten Adressen, auch solche, die dieser Benutzer gerade nicht sieht (andere Rechte, Modul aus)
    const [entwurf, setEntwurf] = useState<Set<string>>(() => (stand ? ausgeblendetAus(stand) : new Set()));
    const [suche, setSuche] = useState('');
    const [arbeitet, setArbeitet] = useState(false);

    const geaendert = useMemo(() => {
        const vorher = stand ? ausgeblendetAus(stand) : new Set<string>();
        return vorher.size !== entwurf.size || [...entwurf].some((a) => !vorher.has(a));
    }, [stand, entwurf]);

    const gefiltert = useMemo(() => {
        const q = suche.trim().toLocaleLowerCase('de-DE');
        if (q === '') return navigation;
        return navigation
            .map((k) => ({
                ...k,
                subgroups: k.subgroups
                    .map((sg) => ({ ...sg, items: sg.items.filter((i) => i.name.toLocaleLowerCase('de-DE').includes(q)) }))
                    .filter((sg) => sg.items.length > 0),
            }))
            .filter((k) => k.subgroups.length > 0);
    }, [navigation, suche]);

    const umschalten = (href: string) => {
        setEntwurf((alt) => {
            const neu = new Set(alt);
            if (neu.has(href)) neu.delete(href);
            else neu.add(href);
            return neu;
        });
    };

    const ausfuehren = async (aktion: () => Promise<Parameters<typeof setzeMenueStand>[0]>, erfolg: string, fehler: string) => {
        setArbeitet(true);
        try {
            setzeMenueStand(await aktion());
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
        if (await ausfuehren(() => speichereEigene([...entwurf]), 'Dein Menü ist gespeichert.', 'Dein Menü konnte nicht gespeichert werden.')) onSchliessen();
    };

    const aufVorgabe = async () => {
        if (await ausfuehren(loescheEigene, 'Dein Menü folgt wieder der Vorgabe.', 'Dein Menü konnte nicht zurückgesetzt werden.')) onSchliessen();
    };

    const fuerAlle = async () => {
        const ja = await confirm({
            title: 'Als Vorgabe für alle speichern?',
            message: 'Wer sein Menü noch nicht selbst eingestellt hat, sieht danach genau diese Auswahl. Eigene Einstellungen einzelner Benutzer bleiben unverändert.',
            confirmLabel: 'Für alle speichern',
            variant: 'warning',
        });
        if (!ja) return;
        if (await ausfuehren(() => speichereVorgabe([...entwurf]), 'Die Vorgabe für alle ist gespeichert.', 'Die Vorgabe konnte nicht gespeichert werden.')) onSchliessen();
    };

    return (
        <DialogContent>
            <DialogHeader>
                <DialogTitle id={titelId}>Alle Funktionen</DialogTitle>
                <DialogDescription>
                    Mit Haken steht die Funktion im Hauptmenü. Alles ohne Haken findest du hier jederzeit wieder, es geht nichts verloren.
                </DialogDescription>
            </DialogHeader>

            <div className="relative">
                <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
                <input
                    type="search"
                    value={suche}
                    onChange={(e) => setSuche(e.target.value)}
                    placeholder="Funktion suchen"
                    aria-label="Funktion suchen"
                    className="w-full rounded-md border border-slate-300 py-2 pl-9 pr-3 text-sm focus:border-rose-500 focus:outline-none focus:ring-2 focus:ring-rose-500"
                />
            </div>

            <div className="-m-1 min-h-0 flex-1 overflow-y-auto p-1">
                {gefiltert.length === 0 && <p className="py-6 text-center text-sm text-slate-500" role="status">Keine Funktion gefunden.</p>}
                {gefiltert.map((kategorie) => (
                    <section key={kategorie.category} className="mb-5" aria-label={kategorie.category}>
                        <h3 className="mb-2 text-sm font-semibold text-rose-700">{kategorie.category}</h3>
                        <div className="grid grid-cols-1 gap-x-6 gap-y-1 sm:grid-cols-2">
                            {kategorie.subgroups.flatMap((sg) => sg.items.map((item) => ({ ...item, gruppe: sg.label }))).map((item) => {
                                const imMenue = !entwurf.has(item.href);
                                return (
                                    <div key={`${kategorie.category}-${item.href}-${item.gruppe}`} className="flex items-center gap-3 rounded-md px-2 py-1.5 hover:bg-slate-50">
                                        <input
                                            type="checkbox"
                                            checked={imMenue}
                                            onChange={() => umschalten(item.href)}
                                            aria-label={`${item.name} im Hauptmenü zeigen`}
                                            className="h-4 w-4 shrink-0 rounded border-slate-300 accent-rose-600 focus:ring-2 focus:ring-rose-500"
                                        />
                                        <Link
                                            to={item.href}
                                            onClick={onSchliessen}
                                            className={cn('flex min-w-0 flex-1 items-center gap-2 text-sm hover:text-rose-700', imMenue ? 'text-slate-800' : 'text-slate-500')}
                                        >
                                            <item.icon className="h-4 w-4 shrink-0" aria-hidden="true" />
                                            <span className="truncate" title={item.name}>{item.name}</span>
                                            <span className="ml-auto shrink-0 text-xs text-slate-400">{item.gruppe}</span>
                                        </Link>
                                    </div>
                                );
                            })}
                        </div>
                    </section>
                ))}
            </div>

            <DialogFooter className="flex-wrap gap-2">
                <Button type="button" variant="ghost" size="sm" disabled={arbeitet || stand?.eigene == null} onClick={() => void aufVorgabe()}>
                    <RotateCcw className="mr-1.5 h-4 w-4" aria-hidden="true" />
                    Wie die Vorgabe
                </Button>
                {istAdmin && (
                    <Button type="button" variant="outline" size="sm" disabled={arbeitet} onClick={() => void fuerAlle()}>
                        Als Vorgabe für alle speichern
                    </Button>
                )}
                <Button type="button" variant="outline" size="sm" disabled={arbeitet} onClick={onSchliessen}>Abbrechen</Button>
                <Button type="button" size="sm" disabled={arbeitet || !geaendert} onClick={() => void speichern()}>
                    {arbeitet ? <Loader2 className="mr-1.5 h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> : <Check className="mr-1.5 h-4 w-4" aria-hidden="true" />}
                    Speichern
                </Button>
            </DialogFooter>
        </DialogContent>
    );
}
