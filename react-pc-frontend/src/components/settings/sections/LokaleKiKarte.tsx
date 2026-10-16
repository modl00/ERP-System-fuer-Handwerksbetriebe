import { useCallback, useEffect, useState } from 'react';
import { CheckCircle2, Download, Loader2, Server, XCircle } from 'lucide-react';
import { Button } from '../../ui/button';
import { Input } from '../../ui/input';
import { useToast } from '../../ui/toast';
import { useOptionalAuth } from '../../../auth/AuthContext';
import { SaveButton, SectionLoading, SettingsCard } from '../settingsUi';
import { parseErrorMessage } from '../settingsApi';

interface Konfiguration {
    adresse: string;
    modell: string;
    schluesselGesetzt: boolean;
    hilfeLokal: boolean;
}

interface Testergebnis {
    ok: boolean;
    art: string;
    meldung: string;
}

const BASIS = '/api/settings/ki-lokal';
const json = { 'Content-Type': 'application/json' };

/**
 * Anbindung einer lokalen KI (Ollama, LM Studio u. a.): Adresse, Modell und optional ein Schlüssel. Die KI-Hilfe lässt sich darauf umstellen,
 * dann verlassen die Fragen das eigene Netz nicht. Getestet wird immer der gespeicherte Stand.
 */
export function LokaleKiKarte() {
    const toast = useToast();
    // Nicht-werfende Variante: Die Karte wird in Tests auch ohne Anmelde-Rahmen gebaut.
    const auth = useOptionalAuth();
    const [stand, setStand] = useState<Konfiguration | null>(null);
    const [ladefehler, setLadefehler] = useState('');
    const [adresse, setAdresse] = useState('');
    const [modell, setModell] = useState('');
    const [schluessel, setSchluessel] = useState('');
    const [schluesselLoeschen, setSchluesselLoeschen] = useState(false);
    const [hilfeLokal, setHilfeLokal] = useState(false);
    const [modelle, setModelle] = useState<string[] | null>(null);
    const [laedtModelle, setLaedtModelle] = useState(false);
    const [speichert, setSpeichert] = useState(false);
    const [test, setTest] = useState<Testergebnis | 'laeuft' | null>(null);

    const uebernehmen = useCallback((k: Konfiguration) => {
        setStand(k);
        setAdresse(k.adresse);
        setModell(k.modell);
        setHilfeLokal(k.hilfeLokal);
        setSchluessel('');
        setSchluesselLoeschen(false);
    }, []);

    useEffect(() => {
        let abgebrochen = false;
        fetch(BASIS)
            .then(async res => {
                if (!res.ok) throw new Error(await parseErrorMessage(res, 'Die Einstellungen der lokalen KI konnten nicht geladen werden.'));
                return res.json() as Promise<Konfiguration>;
            })
            .then(k => { if (!abgebrochen) uebernehmen(k); })
            .catch(e => { if (!abgebrochen) setLadefehler(e instanceof Error ? e.message : 'Die Einstellungen der lokalen KI konnten nicht geladen werden.'); });
        return () => { abgebrochen = true; };
    }, [uebernehmen]);

    const geaendert = !!stand && (
        adresse.trim() !== stand.adresse || modell.trim() !== stand.modell || hilfeLokal !== stand.hilfeLokal
        || schluessel !== '' || schluesselLoeschen
    );
    const kannTesten = !!stand && !geaendert && stand.adresse !== '' && stand.modell !== '' && test !== 'laeuft';

    const speichern = async () => {
        setSpeichert(true);
        try {
            const res = await fetch(BASIS, {
                method: 'PUT', headers: json,
                body: JSON.stringify({
                    adresse: adresse.trim(), modell: modell.trim(), hilfeLokal,
                    // null = Schlüssel bleibt wie er ist, '' = Schlüssel entfernen
                    schluessel: schluesselLoeschen ? '' : (schluessel !== '' ? schluessel : null),
                }),
            });
            if (!res.ok) throw new Error(await parseErrorMessage(res, 'Die Einstellungen konnten nicht gespeichert werden.'));
            uebernehmen(await res.json() as Konfiguration);
            setTest(null);
            toast.success('Die Einstellungen der lokalen KI wurden gespeichert.');
            // Anmeldedaten neu holen, damit „kiAktiv“ app-weit sofort stimmt (KI-Hilfe in
            // der Menüleiste, KI-Knöpfe) – auch wenn die lokale KI statt Gemini aktiv wird.
            await auth?.refreshMe();
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Die Einstellungen konnten nicht gespeichert werden.');
        } finally {
            setSpeichert(false);
        }
    };

    const ladeModelle = async () => {
        setLaedtModelle(true);
        try {
            const res = await fetch(`${BASIS}/modelle`);
            if (!res.ok) throw new Error(await parseErrorMessage(res, 'Die Modelle konnten nicht geladen werden.'));
            const liste = await res.json() as string[];
            setModelle(liste);
            toast.success(liste.length === 1 ? '1 Modell gefunden.' : `${liste.length} Modelle gefunden.`);
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Die Modelle konnten nicht geladen werden.');
        } finally {
            setLaedtModelle(false);
        }
    };

    const teste = async () => {
        setTest('laeuft');
        try {
            const res = await fetch(`${BASIS}/test`, { method: 'POST', headers: json });
            if (!res.ok) throw new Error(await parseErrorMessage(res, 'Der Test ist fehlgeschlagen.'));
            setTest(await res.json() as Testergebnis);
        } catch (e) {
            setTest({ ok: false, art: 'FEHLER', meldung: e instanceof Error ? e.message : 'Der Test ist fehlgeschlagen.' });
        }
    };

    if (!stand && !ladefehler) return <SectionLoading />;

    return (
        <SettingsCard
            icon={<Server className="w-5 h-5 text-rose-600" />}
            title="Lokale KI"
            description={
                <p>
                    Eine KI auf einem eigenen Rechner im Netz (zum Beispiel mit Ollama). Die Fragen verlassen dann Ihr Netz nicht.
                    Auf dem KI-Rechner muss Ollama von außen erreichbar sein (Umgebungsvariable <span className="font-mono">OLLAMA_HOST=0.0.0.0</span>, Port 11434 in der Firewall frei).
                </p>
            }
        >
            {ladefehler && <div role="alert" className="p-3 bg-rose-50 border border-rose-200 text-sm text-rose-800 rounded-lg">{ladefehler}</div>}
            {stand && (
                <div className="space-y-4">
                    <div className="grid gap-3 sm:grid-cols-[10rem_1fr] sm:items-center">
                        <label htmlFor="ki-lokal-adresse" className="text-sm font-medium text-slate-800">Adresse</label>
                        <Input id="ki-lokal-adresse" value={adresse} onChange={e => { setAdresse(e.target.value); setTest(null); }}
                            placeholder="http://192.168.178.50:11434" spellCheck={false} autoComplete="off" className="font-mono text-sm" />

                        <label htmlFor="ki-lokal-modell" className="text-sm font-medium text-slate-800">Modell</label>
                        <div className="flex gap-2">
                            <Input id="ki-lokal-modell" list="ki-lokal-modelle" value={modell} onChange={e => { setModell(e.target.value); setTest(null); }}
                                placeholder="qwen2.5:7b" spellCheck={false} autoComplete="off" className="flex-1 font-mono text-sm" />
                            <Button variant="outline" onClick={() => void ladeModelle()} disabled={laedtModelle || !stand.adresse}
                                title={stand.adresse ? undefined : 'Zuerst die Adresse speichern'}
                                className="border-rose-300 text-rose-700 hover:bg-rose-50">
                                {laedtModelle ? <Loader2 className="w-4 h-4 animate-spin motion-reduce:animate-none" /> : <Download className="w-4 h-4" />}
                                Modelle laden
                            </Button>
                        </div>
                        <datalist id="ki-lokal-modelle">{(modelle ?? []).map(m => <option key={m} value={m} />)}</datalist>

                        <label htmlFor="ki-lokal-schluessel" className="text-sm font-medium text-slate-800">Schlüssel (optional)</label>
                        <div className="space-y-1">
                            <Input id="ki-lokal-schluessel" type="password" value={schluessel} autoComplete="new-password"
                                onChange={e => { setSchluessel(e.target.value); setSchluesselLoeschen(false); }}
                                placeholder={stand.schluesselGesetzt && !schluesselLoeschen ? 'Gespeichert, zum Ersetzen neu eingeben' : 'Nur nötig, wenn der Server einen verlangt'}
                                className="font-mono text-sm" />
                            {stand.schluesselGesetzt && (
                                <label className="flex items-center gap-2 text-xs text-slate-600 cursor-pointer select-none">
                                    <input type="checkbox" checked={schluesselLoeschen} onChange={e => { setSchluesselLoeschen(e.target.checked); if (e.target.checked) setSchluessel(''); }}
                                        className="h-3.5 w-3.5 rounded border-slate-300 text-rose-600 focus:ring-rose-500" />
                                    Gespeicherten Schlüssel entfernen
                                </label>
                            )}
                        </div>
                    </div>

                    <label className="flex items-start gap-3 cursor-pointer select-none">
                        <input type="checkbox" checked={hilfeLokal} onChange={e => setHilfeLokal(e.target.checked)}
                            className="mt-1 h-4 w-4 rounded border-slate-300 text-rose-600 focus:ring-rose-500" />
                        <span>
                            <span className="font-medium text-slate-900">KI-Hilfe (Chat) nutzt die lokale KI</span>
                            <span className="block text-xs text-slate-500">
                                Statt Gemini. Kleine Modelle antworten kürzer und kennen weniger vom Programm; eine Websuche gibt es dabei nicht.
                            </span>
                        </span>
                    </label>

                    <div className="flex flex-wrap items-center gap-3">
                        <SaveButton onClick={() => void speichern()} saving={speichert} disabled={!geaendert}>
                            Speichern
                        </SaveButton>
                        <Button variant="outline" onClick={() => void teste()} disabled={!kannTesten}
                            title={geaendert ? 'Zuerst speichern, getestet wird der gespeicherte Stand' : undefined}>
                            {test === 'laeuft' ? <Loader2 className="w-4 h-4 animate-spin motion-reduce:animate-none" /> : null} Testen
                        </Button>
                    </div>

                    {test && test !== 'laeuft' && (
                        <div role="status" className={`flex items-start gap-2 text-sm rounded-lg p-3 ${test.ok ? 'bg-emerald-50 text-emerald-800' : 'bg-rose-50 text-rose-800'}`}>
                            {test.ok ? <CheckCircle2 className="w-4 h-4 mt-0.5 shrink-0" aria-hidden="true" /> : <XCircle className="w-4 h-4 mt-0.5 shrink-0" aria-hidden="true" />}
                            <span>{test.meldung}</span>
                        </div>
                    )}
                </div>
            )}
        </SettingsCard>
    );
}
