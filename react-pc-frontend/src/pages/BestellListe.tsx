import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AlertTriangle, ChevronRight, Loader2, Package, Plus, RefreshCw, Search, Send } from 'lucide-react';

import { PageLayout } from '../components/layout/PageLayout';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { cn } from '../lib/utils';
import { useDarf } from '../features/funktionsrechte/useFunktionsrechte';
import { BestellFehler, LIEFERSTAND_TEXT, datum, geld, ladeBestellungen, type Bestellung, type Lieferstand } from '../features/bestellwesen/bestellwesenApi';

// ========== Types ==========

type Tab = 'ENTWURF' | 'BESTELLT' | 'ABGESCHLOSSEN' | 'STORNIERT';
type BesteltSubfilter = 'ALLE' | 'BESTELLT' | 'TEILWEISE' | 'UEBERFAELLIG';

interface LieferantGruppe {
    name: string;
    bestellungen: Bestellung[];
    summe: number;
}

// ========== Helpers ==========

const LIEFERSTAND_FARBE: Record<Lieferstand, string> = {
    ENTWURF: 'bg-slate-100 text-slate-700',
    BESTELLT: 'bg-amber-100 text-amber-800',
    TEILWEISE: 'bg-sky-100 text-sky-800',
    GELIEFERT: 'bg-emerald-100 text-emerald-800',
    STORNIERT: 'bg-slate-100 text-slate-500 line-through',
};

export function StandMarke({ b }: { b: Pick<Bestellung, 'lieferstand'> }) {
    return <span className={cn('inline-block rounded-full px-2.5 py-0.5 text-xs font-medium', LIEFERSTAND_FARBE[b.lieferstand])}>{LIEFERSTAND_TEXT[b.lieferstand]}</span>;
}

function bestellungPasstZuTab(b: Bestellung, t: Tab): boolean {
    if (t === 'ENTWURF') return b.status === 'ENTWURF';
    if (t === 'BESTELLT') return b.status === 'BESTELLT' && b.lieferstand !== 'GELIEFERT';
    if (t === 'ABGESCHLOSSEN') return b.lieferstand === 'GELIEFERT';
    return b.status === 'STORNIERT';
}

const BESTELLT_CHIPS: { schluessel: BesteltSubfilter; text: string }[] = [
    { schluessel: 'ALLE', text: 'Alle' },
    { schluessel: 'BESTELLT', text: 'Wartet auf Ware' },
    { schluessel: 'TEILWEISE', text: 'Teilweise geliefert' },
    { schluessel: 'UEBERFAELLIG', text: 'Überfällig' },
];

// ========== Tab-Leiste ==========

interface TabKnopfProps {
    active: boolean;
    label: string;
    count: number;
    attention?: boolean;
    onClick: () => void;
}

function TabKnopf({ active, label, count, attention, onClick }: TabKnopfProps) {
    return (
        <button
            type="button"
            role="tab"
            aria-selected={active}
            onClick={onClick}
            className={cn(
                'flex items-center gap-1.5 px-4 py-2 text-sm font-medium border-b-2 -mb-px transition-colors whitespace-nowrap',
                active ? 'border-rose-600 text-rose-700' : 'border-transparent text-slate-600 hover:text-slate-900',
            )}
        >
            {label}
            <span className={cn(
                'rounded-full px-1.5 py-0.5 text-xs font-medium tabular-nums',
                active ? 'bg-rose-100 text-rose-700' : 'bg-slate-100 text-slate-600',
                attention && count > 0 && !active && 'bg-rose-600 text-white',
            )}>
                {count}
            </span>
        </button>
    );
}

// ========== Zeilen-Tabelle ==========

function BestellTabelle({ liste }: { liste: Bestellung[] }) {
    const navigate = useNavigate();
    if (liste.length === 0) return null;
    return (
        <Card className="overflow-hidden p-0">
            <table className="w-full text-left text-sm">
                <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                    <tr>
                        <th className="px-4 py-3">Bestellnummer</th>
                        <th className="px-4 py-3">Lieferant</th>
                        <th className="hidden px-4 py-3 md:table-cell">Projekte</th>
                        <th className="hidden px-4 py-3 lg:table-cell">Bestellt am</th>
                        <th className="px-4 py-3">Geliefert</th>
                        <th className="hidden px-4 py-3 text-right lg:table-cell">Summe</th>
                        <th className="px-4 py-3">Stand</th>
                    </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                    {liste.map((b) => (
                        <tr
                            key={b.id}
                            tabIndex={0}
                            role="link"
                            aria-label={`Bestellung ${b.bestellnummer ?? 'Entwurf'} öffnen`}
                            onClick={() => navigate(`/bestellungen/bestellung/${b.id}`)}
                            onKeyDown={(e) => { if (e.key === 'Enter') navigate(`/bestellungen/bestellung/${b.id}`); }}
                            className="cursor-pointer hover:bg-slate-50 focus:bg-slate-50 focus:outline-none"
                        >
                            <td className="px-4 py-3 font-medium text-slate-900">{b.bestellnummer ?? <span className="text-slate-500">Entwurf</span>}</td>
                            <td className="px-4 py-3 text-slate-800">{b.lieferantName}</td>
                            <td className="hidden max-w-xs truncate px-4 py-3 text-slate-600 md:table-cell" title={b.projekte.join(', ')}>{b.projekte.join(', ') || '–'}</td>
                            <td className="hidden px-4 py-3 text-slate-600 lg:table-cell">{datum(b.bestelltAm) || '–'}</td>
                            <td className="px-4 py-3 text-slate-700">{b.status === 'BESTELLT' ? `${b.anzahlGeliefert} von ${b.anzahlPositionen}` : `${b.anzahlPositionen} Pos.`}</td>
                            <td className="hidden px-4 py-3 text-right text-slate-700 lg:table-cell">{b.summe > 0 ? geld(b.summe) : '–'}</td>
                            <td className="px-4 py-3">
                                <StandMarke b={b} />
                                {b.ueberfaellig && (
                                    <span className="ml-2 inline-flex items-center gap-1 text-xs font-medium text-rose-700" title="Noch nicht vollständig geliefert">
                                        <AlertTriangle className="h-3.5 w-3.5" /> seit {b.tageSeitBestellung} Tagen
                                    </span>
                                )}
                            </td>
                        </tr>
                    ))}
                </tbody>
            </table>
        </Card>
    );
}

// ========== Versandbereit-Ansicht ==========

function VersandbereitAbschnitt({ gruppen }: { gruppen: LieferantGruppe[] }) {
    const navigate = useNavigate();
    if (gruppen.length === 0) return null;
    return (
        <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 space-y-3">
            <div className="flex items-center gap-2">
                <Send className="w-4 h-4 text-rose-600" aria-hidden="true" />
                <h2 className="text-sm font-semibold text-slate-800">Versandbereit</h2>
                <span className="text-xs text-slate-500">– nach Lieferant gruppiert</span>
            </div>
            <div className="space-y-2">
                {gruppen.map((g) => (
                    <Card key={g.name} className="overflow-hidden p-0">
                        <div className="flex items-center justify-between bg-white px-4 py-2.5 border-b border-slate-100">
                            <div>
                                <p className="font-semibold text-slate-900">{g.name}</p>
                                <p className="text-xs text-slate-500">{g.bestellungen.length === 1 ? '1 Bestellung' : `${g.bestellungen.length} Bestellungen`}{g.summe > 0 ? ` · ${geld(g.summe)}` : ''}</p>
                            </div>
                        </div>
                        <ul className="divide-y divide-slate-100">
                            {g.bestellungen.map((b) => (
                                <li
                                    key={b.id}
                                    tabIndex={0}
                                    role="link"
                                    aria-label={`Bestellung ${b.bestellnummer ?? 'Entwurf'} öffnen`}
                                    onClick={() => navigate(`/bestellungen/bestellung/${b.id}`)}
                                    onKeyDown={(e) => { if (e.key === 'Enter') navigate(`/bestellungen/bestellung/${b.id}`); }}
                                    className="flex items-center justify-between px-4 py-2.5 cursor-pointer hover:bg-slate-50 focus:bg-slate-50 focus:outline-none"
                                >
                                    <div className="flex items-center gap-3">
                                        <Package className="w-4 h-4 text-slate-400 shrink-0" />
                                        <div>
                                            <span className="text-sm font-medium text-slate-900">
                                                {b.bestellnummer ?? <span className="text-slate-500 font-normal">Entwurf</span>}
                                            </span>
                                            {b.projekte.length > 0 && (
                                                <span className="ml-2 text-xs text-slate-500">{b.projekte.join(', ')}</span>
                                            )}
                                        </div>
                                    </div>
                                    <div className="flex items-center gap-3 shrink-0">
                                        {b.summe > 0 && <span className="text-sm text-slate-600">{geld(b.summe)}</span>}
                                        <span className="text-xs text-slate-400">{b.anzahlPositionen} Pos.</span>
                                        <ChevronRight className="w-4 h-4 text-slate-400" />
                                    </div>
                                </li>
                            ))}
                        </ul>
                    </Card>
                ))}
            </div>
        </div>
    );
}

// ========== Hauptkomponente ==========

/** Übersicht aller Bestellungen an Lieferanten: Was ist bestellt, was ist schon da, was ist überfällig? */
const BestellListe = () => {
    const navigate = useNavigate();
    const darfAnlegen = useDarf('BESTELLUNGEN', 'ANLEGEN');
    const [liste, setListe] = useState<Bestellung[] | null>(null);
    const [fehler, setFehler] = useState('');
    const [tab, setTab] = useState<Tab>('ENTWURF');
    const [subfilter, setSubfilter] = useState<BesteltSubfilter>('ALLE');
    const [suche, setSuche] = useState('');

    const laden = useCallback(() => {
        ladeBestellungen()
            .then((l) => { setListe(l); setFehler(''); })
            .catch((e: unknown) => { setListe([]); setFehler(e instanceof BestellFehler ? e.message : 'Die Bestellungen konnten nicht geladen werden.'); });
    }, []);
    useEffect(() => { laden(); }, [laden]);

    const zaehler = useMemo(() => {
        const z = { ENTWURF: 0, BESTELLT: 0, ABGESCHLOSSEN: 0, STORNIERT: 0, TEILWEISE: 0, UEBERFAELLIG: 0 };
        for (const b of (liste ?? [])) {
            if (bestellungPasstZuTab(b, 'ENTWURF')) z.ENTWURF++;
            if (bestellungPasstZuTab(b, 'BESTELLT')) {
                z.BESTELLT++;
                if (b.lieferstand === 'TEILWEISE') z.TEILWEISE++;
                if (b.ueberfaellig) z.UEBERFAELLIG++;
            }
            if (bestellungPasstZuTab(b, 'ABGESCHLOSSEN')) z.ABGESCHLOSSEN++;
            if (bestellungPasstZuTab(b, 'STORNIERT')) z.STORNIERT++;
        }
        return z;
    }, [liste]);

    const versandbereitGruppen = useMemo<LieferantGruppe[]>(() => {
        const map = new Map<string, LieferantGruppe>();
        for (const b of (liste ?? []).filter((b) => b.status === 'ENTWURF')) {
            const key = b.lieferantName ?? 'Ohne Lieferant';
            if (!map.has(key)) map.set(key, { name: key, bestellungen: [], summe: 0 });
            const g = map.get(key)!;
            g.bestellungen.push(b);
            g.summe += b.summe ?? 0;
        }
        return Array.from(map.values()).sort((a, b) => a.name.localeCompare(b.name, 'de'));
    }, [liste]);

    // Mit Suche: tabübergreifend; ohne Suche: nur aktueller Tab + Subfilter
    const sichtbar = useMemo(() => {
        const q = suche.trim().toLowerCase();
        return (liste ?? []).filter((b) => {
            if (!q) {
                if (!bestellungPasstZuTab(b, tab)) return false;
                if (tab === 'BESTELLT' && subfilter !== 'ALLE') {
                    if (subfilter === 'UEBERFAELLIG') return b.ueberfaellig;
                    if (subfilter === 'BESTELLT') return b.lieferstand === 'BESTELLT';
                    if (subfilter === 'TEILWEISE') return b.lieferstand === 'TEILWEISE';
                }
                return true;
            }
            return [b.bestellnummer, b.lieferantName, ...b.projekte].filter(Boolean).join(' ').toLowerCase().includes(q);
        });
    }, [liste, tab, subfilter, suche]);

    const wechselTab = (t: Tab) => { setTab(t); setSubfilter('ALLE'); setSuche(''); };

    const TABS: { key: Tab; label: string; attention?: boolean }[] = [
        { key: 'ENTWURF', label: 'Entwürfe' },
        { key: 'BESTELLT', label: 'Bestellt', attention: zaehler.UEBERFAELLIG > 0 },
        { key: 'ABGESCHLOSSEN', label: 'Abgeschlossen' },
        { key: 'STORNIERT', label: 'Storno' },
    ];

    const sucheAktiv = suche.trim().length > 0;

    return (
        <PageLayout
            ribbonCategory="Einkauf"
            title="BESTELLSCHEINE"
            subtitle="Was ist bestellt, was ist schon da und was ist überfällig? Jede Bestellung hat eine Bestellnummer."
            actions={darfAnlegen !== false && (
                <Button size="sm" onClick={() => navigate('/bestellungen/bestellung/neu')}><Plus className="w-4 h-4" /> Neue Bestellung</Button>
            )}
        >
            <div className="space-y-4">
                {/* Tab-Leiste */}
                <div className="flex items-center gap-1 border-b border-slate-200">
                    {TABS.map(t => (
                        <TabKnopf
                            key={t.key}
                            active={tab === t.key}
                            label={t.label}
                            count={zaehler[t.key]}
                            attention={t.attention}
                            onClick={() => wechselTab(t.key)}
                        />
                    ))}
                    <Button variant="outline" size="sm" onClick={laden} aria-label="Neu laden" className="ml-auto mb-1"><RefreshCw className="w-4 h-4" /></Button>
                </div>

                {fehler && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{fehler}</p>}
                {liste === null && <p className="flex items-center gap-2 text-slate-500"><Loader2 className="h-4 w-4 animate-spin" /> Bestellungen werden geladen …</p>}

                {liste !== null && (
                    <>
                        {/* Sub-Filter für „Bestellt" */}
                        {tab === 'BESTELLT' && !sucheAktiv && (
                            <div className="flex flex-wrap items-center gap-2">
                                {BESTELLT_CHIPS.map((c) => {
                                    const anzahl = c.schluessel === 'ALLE' ? zaehler.BESTELLT
                                        : c.schluessel === 'UEBERFAELLIG' ? zaehler.UEBERFAELLIG
                                        : c.schluessel === 'TEILWEISE' ? zaehler.TEILWEISE
                                        : zaehler.BESTELLT - zaehler.TEILWEISE;
                                    return (
                                        <button key={c.schluessel} type="button" onClick={() => setSubfilter(c.schluessel)} aria-pressed={subfilter === c.schluessel}
                                            className={cn('rounded-full border px-3 py-1.5 text-sm transition-colors',
                                                subfilter === c.schluessel ? 'border-rose-600 bg-rose-600 text-white' : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50',
                                                c.schluessel === 'UEBERFAELLIG' && subfilter !== c.schluessel && zaehler.UEBERFAELLIG > 0 && 'border-rose-300 text-rose-700')}>
                                            {c.text}
                                            <span className={cn('ml-1 text-xs', subfilter === c.schluessel ? 'text-rose-100' : 'text-slate-400')}>{anzahl}</span>
                                        </button>
                                    );
                                })}
                            </div>
                        )}

                        {/* Suche */}
                        <div className="relative w-full sm:w-72">
                            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                            <Input value={suche} onChange={(e) => setSuche(e.target.value)} placeholder="Nummer, Lieferant oder Projekt" className="pl-9" aria-label="Bestellungen durchsuchen" />
                        </div>

                        {/* Inhaltsbereich */}
                        {sucheAktiv ? (
                            sichtbar.length === 0
                                ? <p className="text-slate-600">Dazu gibt es keine Bestellung.</p>
                                : <BestellTabelle liste={sichtbar} />
                        ) : tab === 'ENTWURF' ? (
                            zaehler.ENTWURF === 0 ? (
                                <Card className="p-8 text-center">
                                    <p className="text-lg font-medium text-slate-900">Alles erledigt – keine Entwürfe offen.</p>
                                    <p className="mt-1 text-sm text-slate-600">Sobald Sie eine neue Bestellung anlegen, erscheint sie hier bis zum Absenden.</p>
                                    {darfAnlegen !== false && <Button className="mt-4" onClick={() => navigate('/bestellungen/bestellung/neu')}><Plus className="w-4 h-4" /> Neue Bestellung</Button>}
                                </Card>
                            ) : (
                                <VersandbereitAbschnitt gruppen={versandbereitGruppen} />
                            )
                        ) : (
                            sichtbar.length === 0
                                ? <p className="text-slate-600">Dazu gibt es keine Bestellung.</p>
                                : <BestellTabelle liste={sichtbar} />
                        )}

                        {/* Leere Gesamtliste */}
                        {liste.length === 0 && !fehler && (
                            <Card className="p-8 text-center">
                                <p className="text-lg font-medium text-slate-900">Noch keine Bestellung angelegt.</p>
                                <p className="mt-1 text-sm text-slate-600">Eine Bestellung füllen Sie aus dem Mindestbestand des Lagers, aus dem Material eines Projekts oder frei aus dem Artikelstamm.</p>
                                {darfAnlegen !== false && <Button className="mt-4" onClick={() => navigate('/bestellungen/bestellung/neu')}><Plus className="w-4 h-4" /> Erste Bestellung anlegen</Button>}
                            </Card>
                        )}
                    </>
                )}
            </div>
        </PageLayout>
    );
};

export default BestellListe;
