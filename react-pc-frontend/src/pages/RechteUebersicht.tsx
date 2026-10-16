import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { AlertTriangle, CheckCircle2, Loader2, Search } from 'lucide-react';

import { PageLayout } from '../components/layout/PageLayout';
import { Card } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { cn } from '../lib/utils';
import { RechteReiter } from '../features/rechte/RechteReiter';
import { gruppiere, ladeRechteUebersicht, type Feld, type RechteUebersicht, type Stufe, type UebersichtBenutzer } from '../features/rechte/rechteUebersichtApi';

const FARBE: Record<Stufe, string> = {
    ALLES: 'bg-emerald-100 text-emerald-800 hover:bg-emerald-200',
    TEILWEISE: 'bg-amber-100 text-amber-800 hover:bg-amber-200',
    NICHTS: 'bg-slate-100 text-slate-500 hover:bg-slate-200',
};

/**
 * "Wer darf was?": alle aktiven Benutzer mit ihren Rechten auf einen Blick. Ein Klick auf ein Feld erklärt, woher das Recht kommt. Die Übersicht ändert nichts
 * und fragt dieselben Regeln ab, die bei jedem Zugriff gelten; geändert wird weiter bei den Abteilungen (Rechte) und den Benutzern (Mitarbeiter, Abteilung).
 */
const RechteUebersichtSeite = () => {
    const [daten, setDaten] = useState<RechteUebersicht | null>(null);
    const [fehler, setFehler] = useState('');
    const [suche, setSuche] = useState('');
    const [gewaehlt, setGewaehlt] = useState<{ profilId: number; schluessel: string } | null>(null);

    useEffect(() => {
        ladeRechteUebersicht().then(setDaten).catch((e: unknown) => setFehler(e instanceof Error ? e.message : 'Die Übersicht konnte nicht geladen werden.'));
    }, []);

    const gruppen = useMemo(() => (daten ? gruppiere(daten.bereiche) : []), [daten]);
    const benutzer = useMemo(() => {
        const q = suche.trim().toLowerCase();
        return (daten?.benutzer ?? []).filter((b) => !q || [b.anzeigename, b.benutzername, b.mitarbeiterName, ...b.abteilungen].filter(Boolean).join(' ').toLowerCase().includes(q));
    }, [daten, suche]);

    const detail = useMemo(() => {
        if (!daten || !gewaehlt) return null;
        const b = daten.benutzer.find((x) => x.profilId === gewaehlt.profilId);
        const bereich = daten.bereiche.find((x) => x.schluessel === gewaehlt.schluessel);
        const feld = b?.felder[gewaehlt.schluessel];
        return b && bereich && feld ? { b, bereich, feld } : null;
    }, [daten, gewaehlt]);

    const zelle = (b: UebersichtBenutzer, schluessel: string, feld: Feld | undefined, bereichName: string) => {
        if (!feld) return null;
        const aktiv = gewaehlt?.profilId === b.profilId && gewaehlt.schluessel === schluessel;
        return (
            <button type="button" onClick={() => setGewaehlt({ profilId: b.profilId, schluessel })} aria-pressed={aktiv}
                aria-label={`${b.anzeigename}, ${bereichName}: ${feld.text}`}
                className={cn('w-full rounded-lg px-2 py-1.5 text-xs font-medium transition-colors focus:outline-none focus:ring-2 focus:ring-rose-500', FARBE[feld.stufe], aktiv && 'ring-2 ring-slate-900')}>
                {feld.text}
            </button>
        );
    };

    return (
        <PageLayout ribbonCategory="Administration" title="WER DARF WAS?" subtitle="Alle aktiven Benutzer mit ihren Rechten auf einen Blick. Ein Klick auf ein Feld zeigt, woher das Recht kommt.">
            <RechteReiter />
            <div className="space-y-4">
                {fehler && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{fehler}</p>}
                {!daten && !fehler && <p className="flex items-center gap-2 text-slate-500"><Loader2 className="h-4 w-4 animate-spin" /> Die Übersicht wird geladen …</p>}

                {daten && daten.warnungen.length > 0 && (
                    <Card className="border-amber-200 bg-amber-50 p-4">
                        <h2 className="flex items-center gap-2 text-sm font-semibold text-amber-900"><AlertTriangle className="h-4 w-4" /> {daten.warnungen.length === 1 ? 'Ein Hinweis zur Einrichtung' : `${daten.warnungen.length} Hinweise zur Einrichtung`}</h2>
                        <ul className="mt-2 space-y-2">
                            {daten.warnungen.map((w, i) => (
                                <li key={`${w.art}-${w.profilId ?? 'x'}-${i}`} className="flex flex-wrap items-start justify-between gap-2 text-sm text-amber-900">
                                    <span className="min-w-0 flex-1">{w.text}</span>
                                    <Link to={w.ziel} className="shrink-0 font-medium text-rose-700 hover:underline">{w.ziel === '/benutzer' ? 'Benutzer öffnen' : 'Rechte der Abteilungen öffnen'}</Link>
                                </li>
                            ))}
                        </ul>
                    </Card>
                )}
                {daten && daten.warnungen.length === 0 && (
                    <p className="flex items-center gap-2 rounded-lg bg-emerald-50 p-3 text-sm text-emerald-800"><CheckCircle2 className="h-4 w-4" /> Die Einrichtung ist vollständig: jeder Benutzer hat einen Mitarbeiter mit Abteilung, und jedes Recht hat jemanden.</p>
                )}

                {daten && (
                    <>
                        <div className="flex flex-wrap items-center gap-3">
                            <div className="relative w-full sm:w-72">
                                <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                                <Input value={suche} onChange={(e) => setSuche(e.target.value)} placeholder="Benutzer oder Abteilung suchen" aria-label="Benutzer suchen" className="pl-9" />
                            </div>
                            <ul className="flex flex-wrap items-center gap-2 text-xs" aria-label="Legende">
                                <li className={cn('rounded-lg px-2 py-1 font-medium', FARBE.ALLES)}>darf alles</li>
                                <li className={cn('rounded-lg px-2 py-1 font-medium', FARBE.TEILWEISE)}>darf einen Teil</li>
                                <li className={cn('rounded-lg px-2 py-1 font-medium', FARBE.NICHTS)}>darf nicht</li>
                            </ul>
                            <div className="ml-auto flex gap-3 text-sm">
                                <Link to="/abteilung-berechtigungen" className="text-rose-700 hover:underline">Rechte der Abteilungen ändern</Link>
                                <Link to="/benutzer" className="text-rose-700 hover:underline">Benutzer verwalten</Link>
                            </div>
                        </div>

                        <Card className="overflow-x-auto p-0">
                            <table className="w-full min-w-[56rem] border-collapse text-left text-sm">
                                <thead>
                                    <tr className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                                        <th rowSpan={2} className="sticky left-0 z-10 min-w-[12rem] bg-slate-50 px-4 py-3 align-bottom">Benutzer</th>
                                        {gruppen.map((g) => <th key={g.gruppe} colSpan={g.bereiche.length} className="border-l border-slate-200 px-2 py-2 text-center">{g.gruppe}</th>)}
                                    </tr>
                                    <tr className="bg-slate-50 text-xs font-medium text-slate-700">
                                        {gruppen.flatMap((g) => g.bereiche.map((b, i) => (
                                            <th key={b.schluessel} title={b.beschreibung} className={cn('px-2 py-2 text-center align-bottom', i === 0 && 'border-l border-slate-200')}>{b.name}</th>
                                        )))}
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-slate-100">
                                    {benutzer.map((b) => (
                                        <tr key={b.profilId}>
                                            <th scope="row" className="sticky left-0 z-10 bg-white px-4 py-3 text-left font-normal">
                                                <p className="font-medium text-slate-900">{b.anzeigename}{b.administrator && <span className="ml-2 rounded-full bg-slate-900 px-2 py-0.5 text-[10px] font-medium uppercase text-white">Admin</span>}</p>
                                                <p className="text-xs text-slate-500">
                                                    {b.mitarbeiterName ? `${b.mitarbeiterName}${b.abteilungen.length ? ` · ${b.abteilungen.join(', ')}` : ' · keine Abteilung'}` : 'kein Mitarbeiter verknüpft'}
                                                </p>
                                            </th>
                                            {daten.bereiche.map((br) => <td key={br.schluessel} className="px-1.5 py-2 text-center">{zelle(b, br.schluessel, b.felder[br.schluessel], br.name)}</td>)}
                                        </tr>
                                    ))}
                                    {benutzer.length === 0 && <tr><td colSpan={daten.bereiche.length + 1} className="px-4 py-6 text-center text-slate-500">Dazu gibt es keinen Benutzer.</td></tr>}
                                </tbody>
                            </table>
                        </Card>

                        <Card className="p-4" aria-live="polite">
                            {detail ? (
                                <>
                                    <p className="text-sm font-semibold text-slate-900">{detail.b.anzeigename}: {detail.bereich.name}</p>
                                    <p className="mt-1 text-sm text-slate-700"><span className={cn('mr-2 rounded-lg px-2 py-0.5 text-xs font-medium', FARBE[detail.feld.stufe])}>{detail.feld.text}</span>{detail.feld.grund}</p>
                                    <p className="mt-2 text-xs text-slate-500">{detail.bereich.beschreibung}</p>
                                </>
                            ) : <p className="text-sm text-slate-500">Auf ein Feld klicken, um zu sehen, warum der Benutzer das darf oder nicht darf.</p>}
                        </Card>
                    </>
                )}
            </div>
        </PageLayout>
    );
};

export default RechteUebersichtSeite;
