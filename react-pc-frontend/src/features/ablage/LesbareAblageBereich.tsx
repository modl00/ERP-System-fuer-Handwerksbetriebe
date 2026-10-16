import { useEffect, useState } from 'react';
import { AlertTriangle, CheckCircle2, Eye, FolderTree, Loader2, RefreshCw } from 'lucide-react';

import { Button } from '../../components/ui/button';
import { SettingsCard } from '../../components/settings/settingsUi';
import { useToast } from '../../components/ui/toast';
import { baueAuf, ladeStatus, type LesbareAblageErgebnis, type LesbareAblageStatus } from './ablageApi';

/** Einstellungen, Dateien: die lesbare Ablage anzeigen, als Vorschau prüfen und aufbauen. */
export function LesbareAblageBereich() {
    const toast = useToast();
    const [status, setStatus] = useState<LesbareAblageStatus | null>(null);
    const [laeuft, setLaeuft] = useState<'vorschau' | 'aufbau' | null>(null);
    const [ergebnis, setErgebnis] = useState<LesbareAblageErgebnis | null>(null);

    useEffect(() => {
        let abgebrochen = false;
        ladeStatus()
            .then(s => { if (!abgebrochen) setStatus(s); })
            .catch(e => { if (!abgebrochen) toast.error(e instanceof Error ? e.message : 'Der Stand der lesbaren Ablage konnte nicht geladen werden.'); });
        return () => { abgebrochen = true; };
    }, [toast]);

    const starte = async (probelauf: boolean) => {
        setLaeuft(probelauf ? 'vorschau' : 'aufbau');
        try {
            const e = await baueAuf(probelauf);
            setErgebnis(e);
            if (!probelauf) toast.success(`Lesbare Ablage aufgebaut: ${e.dateien} Dateien.`);
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Das hat nicht geklappt.');
        } finally {
            setLaeuft(null);
        }
    };

    return (
        <SettingsCard
            className="mt-6"
            icon={<FolderTree className="w-5 h-5 text-rose-600" />}
            title="Lesbare Ablage"
            description={
                <>
                    <p>
                        Ihre Dokumente liegen im Datenordner unter Zufallsnamen. Die lesbare Ablage ist ein zusätzlicher Ordner, in dem dieselben Dokumente
                        <strong> nach Projekt, Jahr und Lieferant mit ihren richtigen Namen</strong> erscheinen, so wie Sie es aus der Dateiverwaltung kennen.
                    </p>
                    <p>
                        Die Dateien werden dafür <strong>nicht verschoben und nicht doppelt gespeichert</strong>. Die Anwendung arbeitet unverändert weiter,
                        Sie können den Ordner jederzeit löschen und neu aufbauen. Enthalten sind Projekte, Anfragen und Angebote sowie Lieferantendokumente;
                        Personalunterlagen und Kassenbelege bleiben draußen.
                    </p>
                </>
            }
        >
            <div className="space-y-4">
                {status && (
                    <dl className="text-sm grid grid-cols-1 sm:grid-cols-[170px_1fr] gap-x-4 gap-y-1">
                        <dt className="text-slate-500">Ordner</dt>
                        <dd className="font-mono text-slate-800 break-all" data-testid="ablage-ordner">{status.zielordner}</dd>
                        <dt className="text-slate-500">Aufbau</dt>
                        <dd className="text-slate-800">
                            {status.automatisch
                                ? 'Jede Nacht automatisch, dazu jederzeit per Knopfdruck.'
                                : 'Nur per Knopfdruck. Für den Aufbau jede Nacht die Einstellung ERP_ABLAGE_LESBAR_AUTOMATISCH=true setzen.'}
                        </dd>
                    </dl>
                )}

                <div className="flex flex-wrap gap-2">
                    <Button variant="outline" size="sm" className="border-rose-300 text-rose-700 hover:bg-rose-50" onClick={() => starte(true)} disabled={laeuft != null}>
                        {laeuft === 'vorschau' ? <Loader2 className="w-4 h-4 animate-spin motion-reduce:animate-none" /> : <Eye className="w-4 h-4" />} Vorschau
                    </Button>
                    <Button size="sm" className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" onClick={() => starte(false)} disabled={laeuft != null}>
                        {laeuft === 'aufbau' ? <Loader2 className="w-4 h-4 animate-spin motion-reduce:animate-none" /> : <RefreshCw className="w-4 h-4" />} Jetzt aufbauen
                    </Button>
                </div>

                {ergebnis && (
                    <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 text-sm space-y-2" data-testid="ablage-ergebnis" role="status">
                        <p className="font-medium text-slate-900 flex items-center gap-2">
                            <CheckCircle2 className="w-4 h-4 text-emerald-600" />
                            {ergebnis.probelauf ? 'Vorschau (es wurde nichts geändert)' : 'Aufgebaut'}: {ergebnis.dateien} Dateien
                        </p>
                        <ul className="text-slate-700 grid grid-cols-2 sm:grid-cols-3 gap-x-4">
                            <li>neu: <strong>{ergebnis.neu}</strong></li>
                            <li>unverändert: <strong>{ergebnis.unveraendert}</strong></li>
                            <li>aktualisiert: <strong>{ergebnis.ersetzt}</strong></li>
                            <li>entfernt: <strong>{ergebnis.entfernt}</strong></li>
                            <li>Datei nicht gefunden: <strong>{ergebnis.fehlend}</strong></li>
                        </ul>
                        {ergebnis.kopiert > 0 && (
                            <p className="flex items-start gap-2 text-amber-800 bg-amber-50 border border-amber-200 rounded-md p-2">
                                <AlertTriangle className="w-4 h-4 mt-0.5 shrink-0" />
                                Ihr Speicher unterstützt hier keine Verknüpfungen: {ergebnis.kopiert} Dateien wurden kopiert und belegen doppelten Platz.
                            </p>
                        )}
                        {ergebnis.beispiele.length > 0 && (
                            <details>
                                <summary className="cursor-pointer text-slate-600">Beispiele neuer Einträge</summary>
                                <ul className="mt-1 font-mono text-xs text-slate-700 space-y-0.5">
                                    {ergebnis.beispiele.map(b => <li key={b} className="break-all">{b}</li>)}
                                </ul>
                            </details>
                        )}
                        {ergebnis.fehler.length > 0 && (
                            <div role="alert" className="text-rose-800">
                                <p className="font-medium">{ergebnis.fehler.length} Probleme:</p>
                                <ul className="list-disc pl-5 text-xs space-y-0.5">
                                    {ergebnis.fehler.slice(0, 5).map(f => <li key={f} className="break-all">{f}</li>)}
                                </ul>
                            </div>
                        )}
                    </div>
                )}
            </div>
        </SettingsCard>
    );
}
