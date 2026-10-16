import { useEffect, useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { Card } from '../../components/ui/card';
import { cn } from '../../lib/utils';
import { anzeigeKurz } from '../plantafel/zeitachse';
import type { Ampel, Auftrag } from '../plantafel/plantafelApi';

type Zustand<T> = { art: 'laedt' } | { art: 'fehler' } | { art: 'da'; daten: T };

/** Lädt Daten einer Adresse; ein Fehler (kein Recht, Modul aus, Server weg) ist ein ruhiger Hinweis im Baustein, keine Störung der Seite. */
function useDaten<T>(url: string): Zustand<T> {
    const [zustand, setZustand] = useState<Zustand<T>>({ art: 'laedt' });
    useEffect(() => {
        let aktuell = true;
        fetch(url)
            .then(r => (r.ok ? r.json() : Promise.reject(new Error(String(r.status)))))
            .then(daten => { if (aktuell) setZustand({ art: 'da', daten: daten as T }); })
            .catch(() => { if (aktuell) setZustand({ art: 'fehler' }); });
        return () => { aktuell = false; };
    }, [url]);
    return zustand;
}

function Rahmen({ titel, link, linkText, kinder }: { titel: string; link?: string; linkText?: string; kinder: ReactNode }) {
    return (
        <Card className="flex h-full flex-col p-4" data-testid={`baustein-${titel}`}>
            <div className="mb-2 flex items-baseline justify-between gap-2">
                <h2 className="text-base font-semibold text-slate-900">{titel}</h2>
                {link && <Link to={link} className="text-xs font-medium text-rose-700 hover:underline">{linkText ?? 'Öffnen'}</Link>}
            </div>
            <div className="min-h-0 flex-1 text-sm text-slate-700">{kinder}</div>
        </Card>
    );
}

function Hinweis({ zustand, nichtDa }: { zustand: Zustand<unknown>; nichtDa?: string }) {
    if (zustand.art === 'laedt') return <p role="status" className="text-slate-500">Wird geladen …</p>;
    if (zustand.art === 'fehler') return <p className="text-slate-500">{nichtDa ?? 'Gerade nicht verfügbar.'}</p>;
    return null;
}

// ───────────────────────────── Aufgaben und Meldungen ─────────────────────────────

interface Meldungen {
    totalCount: number;
    categories: { type: string; label: string; count: number; link: string }[];
    recentItems: { type: string; title: string; subtitle: string; timestamp: string; link: string }[];
}

export function Aufgaben() {
    const z = useDaten<Meldungen>('/api/notifications/summary');
    return (
        <Rahmen titel="Das ist offen" kinder={
            z.art !== 'da' ? <Hinweis zustand={z} /> : z.daten.categories.length === 0 ? <p className="text-slate-500">Nichts offen. Alles erledigt.</p> : (
                <ul className="divide-y divide-slate-100">
                    {z.daten.categories.map(k => (
                        <li key={k.type}>
                            <Link to={k.link} className="flex items-center justify-between gap-3 py-1.5 hover:text-rose-700">
                                <span className="truncate">{k.label}</span>
                                <span className="shrink-0 rounded-full bg-rose-100 px-2 py-0.5 text-xs font-semibold text-rose-800">{k.count}</span>
                            </Link>
                        </li>
                    ))}
                </ul>
            )
        } />
    );
}

export function Neues() {
    const z = useDaten<Meldungen>('/api/notifications/summary');
    return (
        <Rahmen titel="Neueste Meldungen" kinder={
            z.art !== 'da' ? <Hinweis zustand={z} /> : z.daten.recentItems.length === 0 ? <p className="text-slate-500">Keine neuen Meldungen.</p> : (
                <ul className="divide-y divide-slate-100">
                    {z.daten.recentItems.slice(0, 6).map((m, i) => (
                        <li key={`${m.type}-${i}`}>
                            <Link to={m.link} className="block py-1.5 hover:text-rose-700">
                                <span className="block truncate font-medium text-slate-900">{m.title}</span>
                                <span className="block truncate text-xs text-slate-500">{m.subtitle}</span>
                            </Link>
                        </li>
                    ))}
                </ul>
            )
        } />
    );
}

// ───────────────────────────── Lager ─────────────────────────────

export function Lager() {
    const z = useDaten<{ anzahlArtikel: number; unterMindestbestand: number; negativ: number }>('/api/lager/zusammenfassung');
    return (
        <Rahmen titel="Lager" link="/lager" kinder={
            z.art !== 'da' ? <Hinweis zustand={z} nichtDa="Das Lager ist für dich nicht verfügbar." /> : (
                <ul className="space-y-1.5">
                    <li className="flex justify-between"><span>Artikel im Lager</span><span className="font-semibold tabular-nums">{z.daten.anzahlArtikel}</span></li>
                    <li className="flex justify-between"><span>Unter Mindestbestand</span><span className={cn('font-semibold tabular-nums', z.daten.unterMindestbestand > 0 && 'text-amber-700')}>{z.daten.unterMindestbestand}</span></li>
                    <li className="flex justify-between"><span>Negativer Bestand</span><span className={cn('font-semibold tabular-nums', z.daten.negativ > 0 && 'text-red-700')}>{z.daten.negativ}</span></li>
                </ul>
            )
        } />
    );
}

// ───────────────────────────── Plantafel ─────────────────────────────

const AMPEL: Record<Ampel, { text: string; klasse: string }> = {
    OK: { text: 'Im Plan', klasse: 'bg-emerald-100 text-emerald-800' },
    KNAPP: { text: 'Knapp', klasse: 'bg-amber-100 text-amber-800' },
    VERSPAETET: { text: 'Verspätet', klasse: 'bg-red-100 text-red-800' },
    OHNE_ZIEL: { text: 'Kein Zieltermin', klasse: 'bg-slate-100 text-slate-600' },
};

export function PlantafelBaustein() {
    const z = useDaten<Auftrag[]>('/api/plantafel/auftraege');
    return (
        <Rahmen titel="Lieferzeiten" link="/plantafel" linkText="Zur Plantafel" kinder={
            z.art !== 'da' ? <Hinweis zustand={z} /> : z.daten.length === 0 ? <p className="text-slate-500">Noch kein Auftrag in der Plantafel.</p> : (
                <ul className="divide-y divide-slate-100">
                    {z.daten.slice(0, 6).map(a => (
                        <li key={a.projektId} className="flex items-center justify-between gap-2 py-1.5">
                            <span className="min-w-0">
                                <span className="block truncate font-medium text-slate-900" title={a.bezeichnung}>{a.bezeichnung}</span>
                                <span className="block text-xs text-slate-500">Lieferzeit {anzeigeKurz(a.liefertermin)}</span>
                            </span>
                            <span className={cn('shrink-0 whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-medium', AMPEL[a.ampel].klasse)}>{AMPEL[a.ampel].text}{a.verspaetungTage ? ` +${a.verspaetungTage} ${a.verspaetungTage === 1 ? 'Tag' : 'Tage'}` : ''}</span>
                        </li>
                    ))}
                </ul>
            )
        } />
    );
}

