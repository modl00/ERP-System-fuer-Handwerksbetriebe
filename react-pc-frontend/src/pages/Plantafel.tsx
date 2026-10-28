import { useCallback, useEffect, useMemo, useState } from 'react';
import { CalendarRange, Loader2, Plus, RefreshCw, Settings2 } from 'lucide-react';
import { PageLayout } from '../components/layout/PageLayout';
import { Button } from '../components/ui/button';
import { Belegung } from '../features/plantafel/Belegung';
import { VorgangDialog, type AuftragAktion } from '../features/plantafel/VorgangDialog';
import { TermineDialog } from '../features/plantafel/TermineDialog';
import { ArtenDialog } from '../features/plantafel/ArtenDialog';
import { DauerVorschlagDialog } from '../features/plantafel/DauerVorschlagDialog';
import { ErklaerungDialog } from '../features/plantafel/ErklaerungDialog';
import { AuftragAufnehmenDialog } from '../features/plantafel/AuftragAufnehmenDialog';
import { ladePlanungEinstellungen } from '../features/plantafel/planungEinstellungenApi';
import { AuftragPlanenDialog } from '../features/plantafel/AuftragPlanenDialog';
import { AuftragDialog } from '../features/plantafel/AuftragDialog';
import { KONFLIKT_EREIGNIS, ladeAuftraege, ladeOffene, ladeTypen, rechneNeu, type Auftrag, type OffenerAuftrag, type Vorgangstyp } from '../features/plantafel/plantafelApi';

type Dialog =
    | { art: 'vorgang'; projektId: number; vorgangId: number | null }
    | { art: 'termine'; projektId: number }
    | { art: 'erklaeren'; projektId: number }
    | { art: 'dauern'; projektId: number }
    | { art: 'aufnehmen' }
    | { art: 'planen'; projektId: number }
    | { art: 'auftrag'; projektId: number }
    | { art: 'arten' }
    | null;

const holen = () => Promise.all([ladeAuftraege(), ladeTypen()]);

const nachLieferzeit = (a: Auftrag, b: Auftrag) => (a.liefertermin ?? '9999').localeCompare(b.liefertermin ?? '9999') || a.projektId - b.projektId;

/**
 * Plantafel (#184, #331): eine Ansicht. Oben stehen die Aufträge mit ihren Vorgängen (Planung, Materialbeschaffung, Fertigung, Veredelung, Montage …), darunter die Mitarbeiter und Ressourcen.
 * Das Programm rechnet Start und Ende aus Aufwand, Mitarbeitern und freier Zeit; das Ende des letzten Vorgangs ist die Lieferzeit. Es schlägt nur vor und verschiebt nie ungefragt.
 * Aktionen für den ganzen Auftrag (Termine, Dauern, Termin erklären, Vorgang hinzufügen) stehen im Dialog eines Vorgangs.
 */
export default function Plantafel() {
    const [auftraege, setAuftraege] = useState<Auftrag[] | null>(null);
    const [typen, setTypen] = useState<Vorgangstyp[]>([]);
    const [fehler, setFehler] = useState('');
    const [dialog, setDialog] = useState<Dialog>(null);
    /** Zählt jede Änderung an einem Vorgang, damit die Ansicht nach Mitarbeitern neu lädt. */
    const [aenderungen, setAenderungen] = useState(0);
    /** Bearbeiten-Modus: Nur dann lässt sich ziehen und ein Termin per Klick anlegen; sonst zeigt ein Klick auf einen Vorgang seine Einzelheiten. */
    const [bearbeiten, setBearbeiten] = useState(false);
    /** Aufträge, deren Planung mit „Später" übersprungen wurde: neu, noch nicht eingeplant. */
    const [offene, setOffene] = useState<OffenerAuftrag[]>([]);

    const uebernehmen = useCallback((a: Auftrag[], t: Vorgangstyp[]) => {
        setFehler('');
        setAuftraege([...a].sort(nachLieferzeit));
        setTypen(t);
    }, []);
    const meldeFehler = useCallback((e: unknown) => {
        setFehler(e instanceof Error ? e.message : 'Die Plantafel konnte nicht geladen werden.');
        setAuftraege(alt => alt ?? []);
    }, []);
    const laden = useCallback(() => holen().then(([a, t]) => uebernehmen(a, t), meldeFehler), [uebernehmen, meldeFehler]);

    useEffect(() => {
        let aktuell = true;
        // Urlaub, Termine und der neue Tag ändern die Termine, ohne dass jemand die Plantafel anfasst: beim Öffnen einmal neu rechnen lassen
        rechneNeu().catch(() => undefined).then(() => holen()).then(([a, t]) => {
            if (!aktuell) return;
            uebernehmen(a, t);
            setAenderungen(n => n + 1);
        }, e => { if (aktuell) meldeFehler(e); });
        return () => { aktuell = false; };
    }, [uebernehmen, meldeFehler]);

    useEffect(() => {
        let aktuell = true;
        ladeOffene().then(l => { if (aktuell && Array.isArray(l)) setOffene(l); }, () => undefined);
        return () => { aktuell = false; };
    }, [aenderungen]);

    // Hat jemand anderes einen Vorgang inzwischen geändert (HTTP 409), lädt die Ansicht neu; die Meldung dazu zeigt die Stelle, die gespeichert hat
    useEffect(() => {
        const neuLaden = () => { setAenderungen(n => n + 1); void laden(); };
        window.addEventListener(KONFLIKT_EREIGNIS, neuLaden);
        return () => window.removeEventListener(KONFLIKT_EREIGNIS, neuLaden);
    }, [laden]);

    // Arbeitswoche aus den Einstellungen „Planung" (graue Streifen, Springen auf den nächsten Arbeitstag); ohne Antwort gilt Montag bis Freitag
    const [arbeitstage, setArbeitstage] = useState<number[] | undefined>(undefined);
    useEffect(() => {
        let aktuell = true;
        ladePlanungEinstellungen().then(e => { if (aktuell && Array.isArray(e?.arbeitstage) && e.arbeitstage.length > 0) setArbeitstage(e.arbeitstage); }, () => undefined);
        return () => { aktuell = false; };
    }, []);

    const ersetze = useCallback((neu: Auftrag) => {
        setAenderungen(n => n + 1);
        setAuftraege(alt => {
            const liste = alt ?? [];
            const vorhanden = liste.some(a => a.projektId === neu.projektId);
            return (vorhanden ? liste.map(a => (a.projektId === neu.projektId ? neu : a)) : [...liste, neu]).sort(nachLieferzeit);
        });
    }, []);

    const aktuellerAuftrag = useMemo(() => (dialog && 'projektId' in dialog ? auftraege?.find(a => a.projektId === dialog.projektId) ?? null : null), [dialog, auftraege]);
    const aktuellerVorgang = dialog?.art === 'vorgang' && dialog.vorgangId != null ? aktuellerAuftrag?.vorgaenge.find(v => v.id === dialog.vorgangId) ?? null : null;
    const schliessen = () => setDialog(null);
    /** Aktionen für den ganzen Auftrag, erreichbar im Dialog eines Vorgangs. */
    const auftragAktion = (aktion: AuftragAktion) => {
        if (!aktuellerAuftrag) return;
        const projektId = aktuellerAuftrag.projektId;
        if (aktion === 'vorgang-neu') setDialog({ art: 'vorgang', projektId, vorgangId: null });
        else setDialog({ art: aktion === 'termine' ? 'termine' : aktion === 'dauern' ? 'dauern' : 'erklaeren', projektId });
    };

    return (
        <PageLayout
            ribbonCategory="Planung"
            title="Plantafel"
            subtitle="Wer arbeitet wann woran: die Aufträge mit ihren Vorgängen, darunter Mitarbeiter und Ressourcen. Das Programm rechnet Start und Ende aus Aufwand, Mitarbeitern und freier Zeit; das Ende des letzten Vorgangs ist die Lieferzeit. Es verschiebt nichts ungefragt."
            actions={
                <>
                    <Button size="sm" className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" onClick={() => setDialog({ art: 'aufnehmen' })}>
                        <Plus className="mr-2 h-4 w-4" aria-hidden="true" /> Auftrag aufnehmen
                    </Button>
                    <Button variant="outline" size="sm" onClick={() => setDialog({ art: 'arten' })}>
                        <Settings2 className="mr-2 h-4 w-4" aria-hidden="true" /> Vorgangsarten
                    </Button>
                    <Button variant="outline" size="sm" onClick={() => { setAenderungen(n => n + 1); void laden(); }}>
                        <RefreshCw className="mr-2 h-4 w-4" aria-hidden="true" /> Aktualisieren
                    </Button>
                </>
            }
        >
            <Belegung onAuftragOeffnen={projektId => setDialog({ art: 'auftrag', projektId })} arbeitstage={arbeitstage} auftraege={auftraege ?? []} bearbeiten={bearbeiten} onBearbeiten={setBearbeiten} aktualisierung={aenderungen} onAuftrag={ersetze} onVorgang={(projektId, vorgangId) => setDialog({ art: 'vorgang', projektId, vorgangId })} />
            {offene.length > 0 && (
                <div className="rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900" role="status" data-testid="offene-auftraege">
                    <p className="font-medium">Neu, noch nicht eingeplant ({offene.length})</p>
                    <p className="text-xs">Diese Aufträge stehen mit geschätzten Werten in der Plantafel, bis Zieltermin, Fremdleistung, Montage und Personen feststehen.</p>
                    <ul className="mt-1.5 flex flex-wrap gap-2">
                        {offene.map(o => (
                            <li key={o.projektId}>
                                <Button variant="outline" size="sm" onClick={() => setDialog({ art: 'planen', projektId: o.projektId })}>
                                    Jetzt planen: {o.bezeichnung}{o.auftragsnummer ? ` (${o.auftragsnummer})` : ''}
                                </Button>
                            </li>
                        ))}
                    </ul>
                </div>
            )}
            {fehler && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{fehler}</p>}
            {auftraege === null && <p role="status" className="flex items-center gap-2 text-sm text-slate-600"><Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> Die Plantafel wird geladen …</p>}

            {auftraege !== null && auftraege.length === 0 && !fehler && (
                <div className="rounded-xl border border-dashed border-slate-300 bg-white p-8 text-center" data-testid="plantafel-leer">
                    <CalendarRange className="mx-auto mb-3 h-8 w-8 text-slate-400" aria-hidden="true" />
                    <p className="font-medium text-slate-900">Noch kein Auftrag in der Plantafel.</p>
                    <p className="mx-auto mt-1 max-w-xl text-sm text-slate-600">Mit „Auftrag aufnehmen" legt das Programm die Vorgänge nacheinander an, mit geschätzten Dauern. Danach lassen sich Dauer, Reihenfolge und feste Termine anpassen.</p>
                </div>
            )}

            {dialog?.art === 'vorgang' && aktuellerAuftrag && (
                <VorgangDialog offen auftrag={aktuellerAuftrag} vorgang={aktuellerVorgang} typen={typen} onSchliessen={schliessen} onAuftrag={ersetze} onAuftragAktion={auftragAktion}
                    nurAnsehen={!bearbeiten && aktuellerVorgang != null} onBearbeiten={() => setBearbeiten(true)} />
            )}
            {dialog?.art === 'auftrag' && aktuellerAuftrag && (
                <AuftragDialog offen auftrag={aktuellerAuftrag} onSchliessen={schliessen} onAuftrag={ersetze} onVorgang={(projektId, vorgangId) => setDialog({ art: 'vorgang', projektId, vorgangId })}
                    onAktion={aktion => setDialog({ art: aktion === 'dauern' ? 'dauern' : 'erklaeren', projektId: aktuellerAuftrag.projektId })}
                    onEntfernt={projektId => { setAuftraege(alt => (alt ?? []).filter(a => a.projektId !== projektId)); setAenderungen(n => n + 1); setDialog(null); }} />
            )}
            {dialog?.art === 'termine' && aktuellerAuftrag && <TermineDialog offen auftrag={aktuellerAuftrag} onSchliessen={schliessen} onAuftrag={ersetze} />}
            {dialog?.art === 'dauern' && aktuellerAuftrag && <DauerVorschlagDialog offen auftrag={aktuellerAuftrag} onSchliessen={schliessen} onAuftrag={ersetze} />}
            {dialog?.art === 'erklaeren' && aktuellerAuftrag && <ErklaerungDialog offen auftrag={aktuellerAuftrag} onSchliessen={schliessen} />}
            {dialog?.art === 'planen' && <AuftragPlanenDialog offen projektId={dialog.projektId} onSchliessen={schliessen} onGeplant={a => { ersetze(a); void laden(); }} />}
            <AuftragAufnehmenDialog offen={dialog?.art === 'aufnehmen'} bereitsDrin={(auftraege ?? []).map(a => a.projektId)} onSchliessen={schliessen} onAuftrag={ersetze} />
            <ArtenDialog offen={dialog?.art === 'arten'} typen={typen} onSchliessen={schliessen} onGeaendert={setTypen} />
        </PageLayout>
    );
}
