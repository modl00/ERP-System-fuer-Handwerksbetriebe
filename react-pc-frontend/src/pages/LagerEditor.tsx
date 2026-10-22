import { useCallback, useEffect, useState } from 'react';
import { AlertTriangle, ArrowRightLeft, ClipboardList, Gem, History, Loader2, PackageMinus, PackagePlus, Pencil, ScanLine, Search, BellRing, Warehouse } from 'lucide-react';
import { useAuth } from '../auth/AuthContext';

import { PageLayout } from '../components/layout/PageLayout';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { useToast } from '../components/ui/toast';
import { cn } from '../lib/utils';
import { ArtikelDialog, BuchungsDialog, MindestbestandDialog, UmbuchungsDialog, VerlaufDialog } from '../features/lager/LagerDialoge';
import { LieferscheinScanDialog } from '../features/lager/LieferscheinScanDialog';
import { LagerorteBereich } from '../features/lager/LagerorteBereich';
import {
    ladeBestand, ladeOrte, ladeZusammenfassung, mengeText,
    type Buchungsart, type LagerBestand, type Lagerort, type LagerZusammenfassung,
} from '../features/lager/lagerApi';

type Reiter = 'bestand' | 'orte';

interface BuchungsZiel { art: Buchungsart; artikel: LagerBestand | null }

const LagerEditor = () => {
    const toast = useToast();
    const { isAdmin } = useAuth();
    const [reiter, setReiter] = useState<Reiter>('bestand');
    const [sucheText, setSucheText] = useState('');
    const [suche, setSuche] = useState('');
    const [nurBedarf, setNurBedarf] = useState(false);
    const [zeilen, setZeilen] = useState<LagerBestand[] | null>(null);
    const [ladefehler, setLadefehler] = useState('');
    const [orte, setOrte] = useState<Lagerort[]>([]);
    const [zusammenfassung, setZusammenfassung] = useState<LagerZusammenfassung | null>(null);
    const [buchung, setBuchung] = useState<BuchungsZiel | null>(null);
    const [mindestbestand, setMindestbestand] = useState<LagerBestand | null>(null);
    const [verlauf, setVerlauf] = useState<LagerBestand | null>(null);
    const [umbuchung, setUmbuchung] = useState<LagerBestand | null>(null);
    const [bearbeiten, setBearbeiten] = useState<LagerBestand | null>(null);

    // Die Suche erst nach einer kurzen Pause abschicken
    useEffect(() => {
        const timer = setTimeout(() => setSuche(sucheText), 300);
        return () => clearTimeout(timer);
    }, [sucheText]);

    // Neu laden: den Zähler erhöhen (die Effekte darunter laden dann erneut)
    const [stand, setStand] = useState(0);
    const ladeListe = useCallback(() => setStand(n => n + 1), []);

    useEffect(() => {
        let abgebrochen = false;
        Promise.all([ladeBestand(suche, nurBedarf), ladeZusammenfassung()])
            .then(([liste, uebersicht]) => {
                if (abgebrochen) return;
                setZeilen(liste);
                setZusammenfassung(uebersicht);
                setLadefehler('');
            })
            .catch(e => {
                if (abgebrochen) return;
                const text = e instanceof Error ? e.message : 'Der Bestand konnte nicht geladen werden.';
                setLadefehler(text);
                toast.error(text);
            });
        return () => { abgebrochen = true; };
    }, [suche, nurBedarf, stand, toast]);

    useEffect(() => {
        let abgebrochen = false;
        ladeOrte()
            .then(liste => { if (!abgebrochen) setOrte(liste); })
            .catch(e => { if (!abgebrochen) toast.error(e instanceof Error ? e.message : 'Die Lagerorte konnten nicht geladen werden.'); });
        return () => { abgebrochen = true; };
    }, [stand, toast]);

    const bedarf = zusammenfassung ? zusammenfassung.unterMindestbestand + zusammenfassung.negativ : 0;
    const oeffneBuchung = (art: Buchungsart, artikel: LagerBestand | null = null) => setBuchung({ art, artikel });
    const [lieferscheinScan, setLieferscheinScan] = useState(false);

    return (
        <PageLayout
            ribbonCategory="Katalog"
            title="LAGER"
            subtitle="Bestand, Zugänge und Entnahmen. Wer Material „aus Lager“ ins Projekt nimmt, bucht automatisch ab."
            actions={
                <div className="flex gap-2">
                    <Button variant="outline" size="sm" className="border-slate-200 text-slate-700 hover:bg-slate-50" onClick={() => setLieferscheinScan(true)} disabled={orte.every(o => !o.aktiv)}
                        title={orte.every(o => !o.aktiv) ? 'Zuerst einen Lagerort anlegen' : undefined}>
                        <Gem className="w-4 h-4 text-rose-500" /> <ScanLine className="w-4 h-4" /> Lieferschein scannen
                    </Button>
                    <Button variant="outline" size="sm" className="border-rose-300 text-rose-700 hover:bg-rose-50" onClick={() => oeffneBuchung('ABGANG')} disabled={orte.every(o => !o.aktiv)}>
                        <PackageMinus className="w-4 h-4" /> Entnahme buchen
                    </Button>
                    <Button size="sm" className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" onClick={() => oeffneBuchung('ZUGANG')} disabled={orte.every(o => !o.aktiv)}
                        title={orte.every(o => !o.aktiv) ? 'Zuerst einen Lagerort anlegen' : undefined}>
                        <PackagePlus className="w-4 h-4" /> Zugang buchen
                    </Button>
                </div>
            }
        >
            <div role="tablist" aria-label="Lager" className="flex gap-1 border-b border-slate-200 mb-5">
                {([['bestand', 'Bestand'], ['orte', 'Lagerorte']] as [Reiter, string][]).map(([schluessel, text]) => (
                    <button key={schluessel} type="button" role="tab" aria-selected={reiter === schluessel}
                        className={cn('px-4 py-2 text-sm font-medium border-b-2 -mb-px cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-300 rounded-t-md',
                            reiter === schluessel ? 'border-rose-600 text-rose-700' : 'border-transparent text-slate-600 hover:text-slate-900')}
                        onClick={() => setReiter(schluessel)}>
                        {text}
                    </button>
                ))}
            </div>

            {reiter === 'orte' ? (
                <LagerorteBereich orte={orte} istAdmin={isAdmin} onGeaendert={() => { ladeListe(); }} />
            ) : (
                <div className="space-y-4">
                    <div className="flex flex-col sm:flex-row gap-3 sm:items-center">
                        <div className="relative flex-1 max-w-md">
                            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                            <input type="text" value={sucheText} onChange={e => setSucheText(e.target.value)} placeholder="Artikel oder Artikelnummer suchen…" aria-label="Lagerbestand durchsuchen"
                                className="w-full pl-10 pr-4 py-2.5 bg-white border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-rose-500/20 focus:border-rose-400 transition-all" />
                        </div>
                        <button type="button" aria-pressed={nurBedarf} onClick={() => setNurBedarf(v => !v)} data-testid="filter-bedarf"
                            className={cn('inline-flex items-center gap-2 px-3 py-2 text-xs font-medium rounded-lg transition-all cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-300',
                                nurBedarf ? 'bg-rose-600 text-white shadow-sm' : 'bg-slate-100 text-slate-600 hover:bg-slate-200')}>
                            <AlertTriangle className="w-3.5 h-3.5" /> Nur Handlungsbedarf{zusammenfassung ? ` (${bedarf})` : ''}
                        </button>
                    </div>

                    {ladefehler ? (
                        <div role="alert" className="p-3 bg-rose-50 border border-rose-200 text-sm text-rose-800 rounded-lg">{ladefehler}</div>
                    ) : zeilen == null ? (
                        <div className="flex items-center gap-2 text-slate-500 text-sm py-10"><Loader2 className="w-4 h-4 animate-spin motion-reduce:animate-none" /> Bestand wird geladen…</div>
                    ) : zeilen.length === 0 ? (
                        <Card className="p-12 text-center border-dashed border-slate-200">
                            <Warehouse className="w-12 h-12 text-slate-200 mx-auto mb-3" />
                            {suche || nurBedarf ? (
                                <p className="text-slate-500">Nichts gefunden. Passen Sie die Suche oder den Filter an.</p>
                            ) : (
                                <>
                                    <p className="text-slate-700 font-medium">Noch nichts im Lager.</p>
                                    <p className="text-sm text-slate-500 mt-1 max-w-md mx-auto">
                                        Buchen Sie den ersten Zugang, zum Beispiel die Ware vom letzten Lieferschein. Material, das Sie im Projekt „aus Lager“ nehmen, wird automatisch abgebucht.
                                    </p>
                                    <Button size="sm" className="mt-4 bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" onClick={() => oeffneBuchung('ZUGANG')}>
                                        <PackagePlus className="w-4 h-4" /> Ersten Zugang buchen
                                    </Button>
                                </>
                            )}
                        </Card>
                    ) : (
                        <Card className="overflow-x-auto border-slate-200">
                            <table className="w-full text-sm" data-testid="lager-tabelle">
                                <thead>
                                    <tr className="text-left text-xs uppercase tracking-wide text-slate-500 border-b border-slate-200 bg-slate-50/60">
                                        <th scope="col" className="px-4 py-3 font-medium">Artikel</th>
                                        <th scope="col" className="px-4 py-3 font-medium text-right">Bestand</th>
                                        <th scope="col" className="px-4 py-3 font-medium text-right">Mindestbestand</th>
                                        <th scope="col" className="px-4 py-3 font-medium">Lagerorte</th>
                                        <th scope="col" className="px-4 py-3 font-medium text-right"><span className="sr-only">Aktionen</span></th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {zeilen.map(z => (
                                        <tr key={z.artikelId} className="border-b border-slate-100 last:border-0 align-top" data-testid={`lager-zeile-${z.artikelId}`}>
                                            <td className="px-4 py-3">
                                                <div className="font-medium text-slate-900">{z.bezeichnung}</div>
                                                {z.artikelnummer && <div className="text-xs text-slate-500">{z.artikelnummer}</div>}
                                            </td>
                                            <td className="px-4 py-3 text-right">
                                                <div className={cn('tabular-nums font-semibold', z.negativ ? 'text-rose-700' : 'text-slate-900')}>{mengeText(z.bestand, z.einheit)}</div>
                                                {z.negativ && <div className="text-xs text-rose-700" title="Es wurde mehr entnommen als eingebucht. Bitte Zugang oder Inventur buchen.">Bestand fehlt: Zugang oder Inventur buchen</div>}
                                                {!z.negativ && z.unterMindestbestand && z.mindestbestand != null && (
                                                    <div className="text-xs text-amber-700">unter Mindestbestand: es fehlen {mengeText(z.mindestbestand - z.bestand, z.einheit)}</div>
                                                )}
                                            </td>
                                            <td className="px-4 py-3 text-right tabular-nums text-slate-600">{z.mindestbestand != null ? mengeText(z.mindestbestand, z.einheit) : '–'}</td>
                                            <td className="px-4 py-3">
                                                <div className="flex flex-wrap gap-1">
                                                    {z.orte.length === 0 ? <span className="text-slate-400">–</span> : z.orte.map(o => (
                                                        <span key={o.lagerortId} className="inline-flex rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-700">{o.lagerortName}: {mengeText(o.bestand, z.einheit)}</span>
                                                    ))}
                                                </div>
                                            </td>
                                            <td className="px-4 py-3">
                                                <div className="flex justify-end gap-0.5">
                                                    <Button variant="ghost" size="sm" className="text-rose-700 hover:bg-rose-100" aria-label={`Zugang für ${z.bezeichnung} buchen`} title="Zugang buchen" onClick={() => oeffneBuchung('ZUGANG', z)}><PackagePlus className="w-4 h-4" /></Button>
                                                    <Button variant="ghost" size="sm" className="text-rose-700 hover:bg-rose-100" aria-label={`Entnahme für ${z.bezeichnung} buchen`} title="Entnahme buchen" onClick={() => oeffneBuchung('ABGANG', z)}><PackageMinus className="w-4 h-4" /></Button>
                                                    <Button variant="ghost" size="sm" className="text-rose-700 hover:bg-rose-100" aria-label={`Inventur für ${z.bezeichnung} buchen`} title="Inventur: gezählten Bestand eintragen" onClick={() => oeffneBuchung('KORREKTUR', z)}><ClipboardList className="w-4 h-4" /></Button>
                                                    <Button variant="ghost" size="sm" className="text-rose-700 hover:bg-rose-100" aria-label={`${z.bezeichnung} umbuchen`} title="Umbuchen: an einen anderen Lagerort legen" onClick={() => setUmbuchung(z)}><ArrowRightLeft className="w-4 h-4" /></Button>
                                                    <Button variant="ghost" size="sm" className="text-slate-600 hover:bg-slate-100" aria-label={`Mindestbestand für ${z.bezeichnung} festlegen`} title="Mindestbestand festlegen" onClick={() => setMindestbestand(z)}><BellRing className="w-4 h-4" /></Button>
                                                    <Button variant="ghost" size="sm" className="text-slate-600 hover:bg-slate-100" aria-label={`${z.bezeichnung} bearbeiten`} title="Artikel bearbeiten (Bezeichnung, Einheit)" onClick={() => setBearbeiten(z)}><Pencil className="w-4 h-4" /></Button>
                                                    <Button variant="ghost" size="sm" className="text-slate-600 hover:bg-slate-100" aria-label={`Verlauf von ${z.bezeichnung} anzeigen`} title="Verlauf anzeigen" onClick={() => setVerlauf(z)}><History className="w-4 h-4" /></Button>
                                                </div>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </Card>
                    )}
                </div>
            )}

            <BuchungsDialog offen={buchung != null} art={buchung?.art ?? 'ZUGANG'} artikel={buchung?.artikel ?? null} orte={orte}
                onSchliessen={() => setBuchung(null)}
                onGebucht={(zeile, art) => {
                    toast.success(`${art === 'ZUGANG' ? 'Zugang' : art === 'ABGANG' ? 'Entnahme' : 'Inventur'} gebucht. Bestand: ${mengeText(zeile.bestand, zeile.einheit)}`);
                    ladeListe();
                }} />
            <MindestbestandDialog artikel={mindestbestand} onSchliessen={() => setMindestbestand(null)} onGespeichert={() => { ladeListe(); }} />
            <VerlaufDialog artikel={verlauf} onSchliessen={() => setVerlauf(null)} />
            <UmbuchungsDialog artikel={umbuchung} orte={orte} onSchliessen={() => setUmbuchung(null)} onGebucht={() => { ladeListe(); }} />
            <ArtikelDialog artikel={bearbeiten} onSchliessen={() => setBearbeiten(null)} onGespeichert={() => { ladeListe(); }} />
            <LieferscheinScanDialog offen={lieferscheinScan} orte={orte} onSchliessen={() => setLieferscheinScan(false)} onGebucht={() => { ladeListe(); }} />
        </PageLayout>
    );
};

export default LagerEditor;
