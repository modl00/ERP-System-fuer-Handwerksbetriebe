import { useCallback, useEffect, useRef, useState } from 'react';
import { Loader2, Plus, Save } from 'lucide-react';
import { Button } from '../../../components/ui/button';
import { Card } from '../../../components/ui/card';
import { useToast } from '../../../components/ui/toast';
import { ladeGlobaleFelder, speichereGlobaleFelder } from '../api';
import type { GlobalesDatenfeld } from '../types';
import { DatenfeldZeile } from './DatenfeldZeile';
import { entferne, ersetze, fehlerInDatenfeldern, verschiebe } from './modulBearbeiten';

/** Gemeinsame Datenfelder: stehen in jedem Protokoll, egal welche Module gewählt sind (Standort, Hersteller ...). */
export function GlobaleFelderEditor() {
    const toast = useToast();
    const [felder, setFelder] = useState<GlobalesDatenfeld[] | null>(null);
    const [gespeichert, setGespeichert] = useState('[]');
    const [fehler, setFehler] = useState(false);
    const [speichert, setSpeichert] = useState(false);
    // Eindeutiger Schlüssel je Zeile, damit eine Zeile beim Verschieben ihren Zustand mitnimmt
    const [schluessel, setSchluessel] = useState<string[]>([]);
    const zaehler = useRef(0);

    const frischerSchluessel = useCallback(() => `feld-${zaehler.current++}`, []);

    const laden = useCallback(async () => {
        setFehler(false);
        try {
            const geladen = await ladeGlobaleFelder();
            setFelder(geladen);
            setGespeichert(JSON.stringify(geladen));
            setSchluessel(geladen.map(() => frischerSchluessel()));
        } catch (e) {
            setFehler(true);
            toast.error(e instanceof Error ? e.message : 'Die Datenfelder konnten nicht geladen werden.');
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [frischerSchluessel]);

    useEffect(() => { void laden(); }, [laden]);

    const geaendert = felder !== null && JSON.stringify(felder) !== gespeichert;
    // Der Browser warnt beim Schließen des Tabs, solange etwas ungespeichert ist.
    useEffect(() => {
        if (!geaendert) return;
        const warnen = (e: BeforeUnloadEvent) => { e.preventDefault(); };
        window.addEventListener('beforeunload', warnen);
        return () => window.removeEventListener('beforeunload', warnen);
    }, [geaendert]);

    if (fehler) {
        return (
            <Card className="p-6 text-center">
                <p className="text-slate-700">Die gemeinsamen Datenfelder konnten nicht geladen werden.</p>
                <Button size="sm" className="mt-3" onClick={() => void laden()}>Noch einmal versuchen</Button>
            </Card>
        );
    }
    if (!felder) {
        return <div className="flex items-center gap-2 py-6 text-slate-500" role="status"><Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> Datenfelder werden geladen …</div>;
    }

    const speichern = async () => {
        const problem = fehlerInDatenfeldern(felder);
        if (problem) { toast.error(problem); return; }
        setSpeichert(true);
        try {
            const neu = await speichereGlobaleFelder(felder);
            setFelder(neu);
            setGespeichert(JSON.stringify(neu));
            setSchluessel(neu.map(() => frischerSchluessel()));
            toast.success('Gemeinsame Datenfelder gespeichert.');
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Die Datenfelder konnten nicht gespeichert werden.');
        } finally {
            setSpeichert(false);
        }
    };

    const bewege = (index: number, richtung: -1 | 1) => {
        setFelder(verschiebe(felder, index, richtung));
        setSchluessel(verschiebe(schluessel, index, richtung));
    };

    return (
        <Card className="space-y-4 p-4 sm:p-6">
            <div>
                <h3 className="text-base font-semibold text-slate-900">Gemeinsame Datenfelder</h3>
                <p className="mt-0.5 text-sm text-slate-500">
                    Diese Felder stehen in jeder Wartung unter „Angaben“, egal welche Module gewählt sind. Änderungen gelten nur für neue Wartungen.
                </p>
            </div>
            {felder.length === 0 && <p className="text-sm text-slate-500">Noch keine gemeinsamen Datenfelder.</p>}
            <div className="space-y-3">
                {felder.map((feld, i) => (
                    <DatenfeldZeile
                        key={schluessel[i]}
                        idPrefix={`global-${schluessel[i]}`}
                        nummer={i + 1}
                        feld={feld}
                        onChange={(neu) => setFelder(ersetze(felder, i, neu))}
                        onHoch={i > 0 ? () => bewege(i, -1) : undefined}
                        onRunter={i < felder.length - 1 ? () => bewege(i, 1) : undefined}
                        onEntfernen={() => { setFelder(entferne(felder, i)); setSchluessel(entferne(schluessel, i)); }}
                    />
                ))}
            </div>
            <div className="flex flex-wrap items-center justify-between gap-3">
                <Button variant="outline" size="sm" onClick={() => {
                    setFelder([...felder, { id: null, label: '', feldtyp: 'TEXT', optionen: [] }]);
                    setSchluessel([...schluessel, frischerSchluessel()]);
                }}>
                    <Plus className="h-4 w-4" aria-hidden="true" /> Datenfeld hinzufügen
                </Button>
                <div className="flex items-center gap-3">
                    {geaendert && <span role="status" className="text-sm text-amber-700">Noch nicht gespeichert</span>}
                    <Button onClick={() => void speichern()} disabled={!geaendert || speichert}>
                        {speichert ? <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> : <Save className="h-4 w-4" aria-hidden="true" />}
                        Speichern
                    </Button>
                </div>
            </div>
        </Card>
    );
}
