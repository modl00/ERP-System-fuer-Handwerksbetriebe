import { useMemo, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { LayoutGrid, Pencil } from 'lucide-react';
import { cn } from '../../lib/utils';
import { ALLE_FUNKTIONEN } from '../../features/schnellzugriff/schnellzugriffApi';
import { useSchnellzugriff } from '../../features/schnellzugriff/useSchnellzugriff';
import { SchnellzugriffDialog } from './SchnellzugriffDialog';
import type { FunktionsKategorie } from './AlleFunktionenDialog';

interface Props {
    /** Alles, was dieser Benutzer sehen darf (Rechte und Module sind schon abgezogen). */
    navigation: FunktionsKategorie[];
    istAdmin: boolean;
    /** Öffnet den Dialog „Alle Funktionen“ (für den festen Knopf rechts). */
    onAlleFunktionen: () => void;
    /** Zahl neuer Einträge je Adresse (zum Beispiel neue Sprachnachrichten). */
    zaehler?: Record<string, number>;
    /** Wie viele Funktionen im Menü ausgeblendet sind; steht am Knopf „Alle Funktionen“. */
    anzahlAusgeblendet?: number;
}

const knopf = 'group flex min-w-[4.5rem] shrink-0 flex-col items-center justify-center gap-1 rounded-lg p-2 transition-all hover:bg-white hover:shadow-sm focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500';

function SymbolKreis({ aktiv, children, zahl }: { aktiv?: boolean; children: React.ReactNode; zahl?: number }) {
    return (
        <div className={cn('relative rounded-full p-2 transition-colors', aktiv ? 'bg-rose-100 text-rose-600' : 'bg-slate-100 text-slate-500 group-hover:bg-rose-50 group-hover:text-rose-600')}>
            {children}
            {(zahl ?? 0) > 0 && (
                <span className="absolute -right-1.5 -top-1 flex h-[18px] min-w-[18px] items-center justify-center rounded-full bg-rose-600 px-1 text-[10px] font-bold text-white ring-2 ring-white" aria-label={`${zahl} neu`}>
                    {(zahl ?? 0) > 99 ? '99+' : zahl}
                </span>
            )}
        </div>
    );
}

/**
 * Die Schnellzugriffsleiste unter dem Hauptmenü (#290, #326): Funktionen, die man selbst auswählt, in eigener Reihenfolge, mit denselben Symbolen wie sonst im Programm.
 * Sie ändert sich nicht, wenn man oben einen Hauptpunkt anklickt. Rechts steht fest „Alle Funktionen“. Die Firma kann eine Vorgabe machen; wer selbst etwas
 * einstellt, sieht seine eigene Leiste. Was jemand nicht sehen darf (Rechte, Module), erscheint auch hier nicht.
 */
export function Schnellzugriffsleiste({ navigation, istAdmin, onAlleFunktionen, zaehler = {}, anzahlAusgeblendet = 0 }: Props) {
    const { bekannt, eintraege } = useSchnellzugriff();
    const [dialogOffen, setDialogOffen] = useState(false);
    const { pathname } = useLocation();

    const jeAdresse = useMemo(() => {
        const karte = new Map<string, FunktionsKategorie['subgroups'][number]['items'][number]>();
        for (const k of navigation) for (const sg of k.subgroups) for (const i of sg.items) if (!karte.has(i.href)) karte.set(i.href, i);
        return karte;
    }, [navigation]);

    // „Alle Funktionen“ steht immer rechts; ein alter Eintrag in der eigenen Leiste wird nicht doppelt gezeigt
    const sichtbar = bekannt ? eintraege.flatMap((adresse) => {
        const e = adresse === ALLE_FUNKTIONEN ? undefined : jeAdresse.get(adresse);
        return e ? [e] : [];
    }) : [];

    return (
        <div className="flex items-center gap-2 border-b border-slate-200 bg-slate-50/50 px-3 py-1.5" role="toolbar" aria-label="Schnellzugriff" data-testid="schnellzugriff">
            <div className="flex min-w-0 flex-1 items-center gap-1 overflow-x-auto">
                {bekannt && sichtbar.length === 0 && <span className="px-2 text-xs text-slate-500">Lege hier die Funktionen ab, die du am häufigsten brauchst.</span>}
                {sichtbar.map((e) => {
                    const aktiv = [e.href, ...(e.auch ?? [])].some((h) => pathname === h || pathname.startsWith(h + '/'));
                    return (
                        <Link key={e.href} to={e.href} aria-label={e.name} className={cn(knopf, aktiv && 'bg-white shadow-sm ring-1 ring-slate-200')}>
                            <SymbolKreis aktiv={aktiv} zahl={zaehler[e.href]}><e.icon className="h-5 w-5" /></SymbolKreis>
                            <span className={cn('max-w-[5.5rem] break-words text-center text-[10px] font-medium leading-tight', aktiv ? 'text-rose-700' : 'text-slate-600')} aria-hidden="true">{e.name}</span>
                        </Link>
                    );
                })}
            </div>
            <button type="button" onClick={() => setDialogOffen(true)} title="Schnellzugriff anpassen" aria-label="Schnellzugriff anpassen"
                className="shrink-0 rounded p-1.5 text-slate-500 hover:bg-slate-100 hover:text-rose-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-300">
                <Pencil className="h-4 w-4" aria-hidden="true" />
            </button>
            <button type="button" onClick={onAlleFunktionen} className={knopf} title={anzahlAusgeblendet > 0 ? `Alle Funktionen (${anzahlAusgeblendet} im Menü ausgeblendet)` : 'Alle Funktionen'}>
                <div className="relative rounded-full bg-slate-100 p-2 text-slate-500 transition-colors group-hover:bg-rose-50 group-hover:text-rose-600">
                    <LayoutGrid className="h-5 w-5" aria-hidden="true" />
                    {anzahlAusgeblendet > 0 && (
                        <span className="absolute -right-1.5 -top-1 flex h-[18px] min-w-[18px] items-center justify-center rounded-full bg-slate-500 px-1 text-[10px] font-bold text-white ring-2 ring-white" aria-label={`${anzahlAusgeblendet} ausgeblendet`}>{anzahlAusgeblendet}</span>
                    )}
                </div>
                <span className="text-center text-[10px] font-medium leading-tight text-slate-600">Alle Funktionen</span>
            </button>
            {dialogOffen && <SchnellzugriffDialog offen={dialogOffen} onSchliessen={() => setDialogOffen(false)} navigation={navigation} istAdmin={istAdmin} />}
        </div>
    );
}
