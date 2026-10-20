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

const CHIPS: { schluessel: Lieferstand | 'ALLE' | 'UEBERFAELLIG'; text: string }[] = [
    { schluessel: 'ALLE', text: 'Alle' },
    { schluessel: 'ENTWURF', text: 'Entwürfe' },
    { schluessel: 'BESTELLT', text: 'Bestellt, Ware kommt noch' },
    { schluessel: 'TEILWEISE', text: 'Teilweise geliefert' },
    { schluessel: 'GELIEFERT', text: 'Geliefert' },
    { schluessel: 'UEBERFAELLIG', text: 'Überfällig' },
    { schluessel: 'STORNIERT', text: 'Storniert' },
];

const FARBE: Record<Lieferstand, string> = {
    ENTWURF: 'bg-slate-100 text-slate-700',
    BESTELLT: 'bg-amber-100 text-amber-800',
    TEILWEISE: 'bg-sky-100 text-sky-800',
    GELIEFERT: 'bg-emerald-100 text-emerald-800',
    STORNIERT: 'bg-slate-100 text-slate-500 line-through',
};

export function StandMarke({ b }: { b: Pick<Bestellung, 'lieferstand'> }) {
    return <span className={cn('inline-block rounded-full px-2.5 py-0.5 text-xs font-medium', FARBE[b.lieferstand])}>{LIEFERSTAND_TEXT[b.lieferstand]}</span>;
}

type Tab = 'LISTE' | 'VERSANDBEREIT';

interface LieferantGruppe {
    name: string;
    bestellungen: Bestellung[];
    summe: number;
}

/** Übersicht aller Bestellungen an Lieferanten: Was ist bestellt, was ist schon da, was ist überfällig? */
const BestellListe = () => {
    const navigate = useNavigate();
    const darfAnlegen = useDarf('BESTELLUNGEN', 'ANLEGEN');
    const [liste, setListe] = useState<Bestellung[] | null>(null);
    const [fehler, setFehler] = useState('');
    const [chip, setChip] = useState<(typeof CHIPS)[number]['schluessel']>('ALLE');
    const [suche, setSuche] = useState('');
    const [tab, setTab] = useState<Tab>('LISTE');

    const laden = useCallback(() => {
        ladeBestellungen()
            .then((l) => { setListe(l); setFehler(''); })
            .catch((e: unknown) => { setListe([]); setFehler(e instanceof BestellFehler ? e.message : 'Die Bestellungen konnten nicht geladen werden.'); });
    }, []);
    useEffect(() => { laden(); }, [laden]);

    const zaehler = useMemo(() => {
        const z: Record<string, number> = { ALLE: liste?.length ?? 0, UEBERFAELLIG: 0 };
        (liste ?? []).forEach((b) => { z[b.lieferstand] = (z[b.lieferstand] ?? 0) + 1; if (b.ueberfaellig) z.UEBERFAELLIG += 1; });
        return z;
    }, [liste]);

    const sichtbar = useMemo(() => {
        const q = suche.trim().toLowerCase();
        return (liste ?? []).filter((b) => {
            if (chip === 'UEBERFAELLIG' ? !b.ueberfaellig : chip !== 'ALLE' && b.lieferstand !== chip) return false;
            if (!q) return true;
            return [b.bestellnummer, b.lieferantName, ...b.projekte].filter(Boolean).join(' ').toLowerCase().includes(q);
        });
    }, [liste, chip, suche]);

    const versandbereitGruppen = useMemo<LieferantGruppe[]>(() => {
        const entwuerfe = (liste ?? []).filter((b) => b.lieferstand === 'ENTWURF');
        const map = new Map<string, LieferantGruppe>();
        for (const b of entwuerfe) {
            const key = b.lieferantName ?? 'Ohne Lieferant';
            if (!map.has(key)) map.set(key, { name: key, bestellungen: [], summe: 0 });
            const g = map.get(key)!;
            g.bestellungen.push(b);
            g.summe += b.summe ?? 0;
        }
        return Array.from(map.values()).sort((a, b) => a.name.localeCompare(b.name, 'de'));
    }, [liste]);

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
                {/* Tabs */}
                <div className="flex items-center gap-1 border-b border-slate-200">
                    <button
                        type="button"
                        onClick={() => setTab('LISTE')}
                        className={cn('px-4 py-2 text-sm font-medium border-b-2 -mb-px transition-colors',
                            tab === 'LISTE' ? 'border-rose-600 text-rose-700' : 'border-transparent text-slate-600 hover:text-slate-900')}
                    >
                        Alle Bestellscheine
                    </button>
                    <button
                        type="button"
                        onClick={() => setTab('VERSANDBEREIT')}
                        className={cn('flex items-center gap-1.5 px-4 py-2 text-sm font-medium border-b-2 -mb-px transition-colors',
                            tab === 'VERSANDBEREIT' ? 'border-rose-600 text-rose-700' : 'border-transparent text-slate-600 hover:text-slate-900')}
                    >
                        <Send className="w-3.5 h-3.5" />
                        Versandbereit
                        {(zaehler.ENTWURF ?? 0) > 0 && (
                            <span className={cn('rounded-full px-1.5 py-0.5 text-xs font-medium',
                                tab === 'VERSANDBEREIT' ? 'bg-rose-100 text-rose-700' : 'bg-slate-100 text-slate-600')}>
                                {zaehler.ENTWURF}
                            </span>
                        )}
                    </button>
                    <Button variant="outline" size="sm" onClick={laden} aria-label="Neu laden" className="ml-auto mb-1"><RefreshCw className="w-4 h-4" /></Button>
                </div>

                {fehler && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{fehler}</p>}
                {liste === null && <p className="flex items-center gap-2 text-slate-500"><Loader2 className="h-4 w-4 animate-spin" /> Bestellungen werden geladen …</p>}

                {/* Tab: Alle Bestellscheine */}
                {tab === 'LISTE' && liste !== null && (
                    <>
                        <div className="flex flex-wrap items-center gap-2">
                            {CHIPS.map((c) => (
                                <button key={c.schluessel} type="button" onClick={() => setChip(c.schluessel)} aria-pressed={chip === c.schluessel}
                                    className={cn('rounded-full border px-3 py-1.5 text-sm transition-colors',
                                        chip === c.schluessel ? 'border-rose-600 bg-rose-600 text-white' : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50',
                                        c.schluessel === 'UEBERFAELLIG' && chip !== c.schluessel && (zaehler.UEBERFAELLIG ?? 0) > 0 && 'border-rose-300 text-rose-700')}>
                                    {c.text} <span className={cn('ml-1 text-xs', chip === c.schluessel ? 'text-rose-100' : 'text-slate-400')}>{zaehler[c.schluessel] ?? 0}</span>
                                </button>
                            ))}
                            <div className="relative ml-auto w-full sm:w-72">
                                <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                                <Input value={suche} onChange={(e) => setSuche(e.target.value)} placeholder="Nummer, Lieferant oder Projekt" className="pl-9" aria-label="Bestellungen durchsuchen" />
                            </div>
                        </div>

                        {!fehler && liste.length === 0 && (
                            <Card className="p-8 text-center">
                                <p className="text-lg font-medium text-slate-900">Noch keine Bestellung angelegt.</p>
                                <p className="mt-1 text-sm text-slate-600">Eine Bestellung füllen Sie aus dem Mindestbestand des Lagers, aus dem Material eines Projekts oder frei aus dem Artikelstamm.</p>
                                {darfAnlegen !== false && <Button className="mt-4" onClick={() => navigate('/bestellungen/bestellung/neu')}><Plus className="w-4 h-4" /> Erste Bestellung anlegen</Button>}
                            </Card>
                        )}
                        {liste.length > 0 && sichtbar.length === 0 && <p className="text-slate-600">Dazu gibt es keine Bestellung.</p>}

                        {sichtbar.length > 0 && (
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
                                        {sichtbar.map((b) => (
                                            <tr key={b.id} tabIndex={0} role="link" aria-label={`Bestellung ${b.bestellnummer ?? 'Entwurf'} öffnen`}
                                                onClick={() => navigate(`/bestellungen/bestellung/${b.id}`)}
                                                onKeyDown={(e) => { if (e.key === 'Enter') navigate(`/bestellungen/bestellung/${b.id}`); }}
                                                className="cursor-pointer hover:bg-slate-50 focus:bg-slate-50 focus:outline-none">
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
                        )}
                    </>
                )}

                {/* Tab: Versandbereit */}
                {tab === 'VERSANDBEREIT' && liste !== null && (
                    <>
                        {versandbereitGruppen.length === 0 ? (
                            <Card className="p-8 text-center">
                                <p className="text-lg font-medium text-slate-900">Alles erledigt – keine Entwürfe offen.</p>
                                <p className="mt-1 text-sm text-slate-600">Sobald Sie eine neue Bestellung anlegen, erscheint sie hier bis zum Absenden.</p>
                                {darfAnlegen !== false && <Button className="mt-4" onClick={() => navigate('/bestellungen/bestellung/neu')}><Plus className="w-4 h-4" /> Neue Bestellung</Button>}
                            </Card>
                        ) : (
                            <div className="space-y-3">
                                <p className="text-sm text-slate-500">{versandbereitGruppen.length === 1 ? '1 Lieferant' : `${versandbereitGruppen.length} Lieferanten`} mit offenen Entwürfen – öffnen Sie eine Bestellung, um sie zu versenden.</p>
                                {versandbereitGruppen.map((g) => (
                                    <Card key={g.name} className="overflow-hidden p-0">
                                        <div className="flex items-center justify-between bg-slate-50 px-4 py-3 border-b border-slate-200">
                                            <div>
                                                <p className="font-semibold text-slate-900">{g.name}</p>
                                                <p className="text-xs text-slate-500">{g.bestellungen.length === 1 ? '1 Bestellung' : `${g.bestellungen.length} Bestellungen`}{g.summe > 0 ? ` · ${geld(g.summe)}` : ''}</p>
                                            </div>
                                        </div>
                                        <ul className="divide-y divide-slate-100">
                                            {g.bestellungen.map((b) => (
                                                <li key={b.id}
                                                    tabIndex={0}
                                                    role="link"
                                                    aria-label={`Bestellung ${b.bestellnummer ?? 'Entwurf'} öffnen`}
                                                    onClick={() => navigate(`/bestellungen/bestellung/${b.id}`)}
                                                    onKeyDown={(e) => { if (e.key === 'Enter') navigate(`/bestellungen/bestellung/${b.id}`); }}
                                                    className="flex items-center justify-between px-4 py-2.5 cursor-pointer hover:bg-slate-50 focus:bg-slate-50 focus:outline-none">
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
                        )}
                    </>
                )}
            </div>
        </PageLayout>
    );
};

export default BestellListe;
