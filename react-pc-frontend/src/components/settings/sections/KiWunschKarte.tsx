import { useCallback, useEffect, useState } from 'react';
import { Loader2, Send, TestTube } from 'lucide-react';
import { Button } from '../../ui/button';
import { Input } from '../../ui/input';
import { Label } from '../../ui/label';
import { useToast } from '../../ui/toast';
import { PasswordField, SaveButton, SectionLoading, SettingsCard, TestResultBanner } from '../settingsUi';
import { parseErrorMessage } from '../settingsApi';
import type { TestResult } from '../settingsApi';

interface Stand { adresse: string; repository: string; tokenGesetzt: boolean; verschluesselt: boolean; bereit: boolean }

const BASIS = '/api/settings/ki-wunsch';

/**
 * Wünsche an die Entwicklung (#138, #123): Die KI kann einen Verbesserungswunsch als Issue in Gitea anlegen, aber nur als Vorschlag, den ein Administrator bestätigt.
 * Hier stellt der Administrator Adresse, Repository und Zugangsschlüssel ein. Der Schlüssel wird nie wieder angezeigt.
 */
export function KiWunschKarte() {
    const toast = useToast();
    const [stand, setStand] = useState<Stand | null>(null);
    const [ladefehler, setLadefehler] = useState('');
    const [adresse, setAdresse] = useState('');
    const [repository, setRepository] = useState('');
    const [token, setToken] = useState('');
    const [entfernen, setEntfernen] = useState(false);
    const [speichert, setSpeichert] = useState(false);
    const [testet, setTestet] = useState(false);
    const [test, setTest] = useState<TestResult | null>(null);

    const uebernehmen = useCallback((s: Stand) => {
        setStand(s);
        setAdresse(s.adresse);
        setRepository(s.repository);
        setToken('');
        setEntfernen(false);
    }, []);

    useEffect(() => {
        let abgebrochen = false;
        fetch(BASIS)
            .then(async r => {
                if (!r.ok) throw new Error(await parseErrorMessage(r, 'Die Einstellungen konnten nicht geladen werden.'));
                return r.json() as Promise<Stand>;
            })
            .then(s => { if (!abgebrochen) uebernehmen(s); })
            .catch(e => { if (!abgebrochen) setLadefehler(e instanceof Error ? e.message : 'Die Einstellungen konnten nicht geladen werden.'); });
        return () => { abgebrochen = true; };
    }, [uebernehmen]);

    const geaendert = !!stand && (adresse.trim() !== stand.adresse || repository.trim() !== stand.repository || token.trim() !== '' || entfernen);

    const speichern = async () => {
        setSpeichert(true);
        try {
            const res = await fetch(BASIS, {
                method: 'PUT', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ adresse: adresse.trim(), repository: repository.trim(), token: entfernen ? '' : (token.trim() ? token.trim() : null) }),
            });
            if (!res.ok) throw new Error(await parseErrorMessage(res, 'Die Einstellungen konnten nicht gespeichert werden.'));
            uebernehmen(await res.json() as Stand);
            setTest(null);
            toast.success('Der Zugang zu Gitea ist gespeichert.');
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Die Einstellungen konnten nicht gespeichert werden.');
        } finally {
            setSpeichert(false);
        }
    };

    const teste = async () => {
        setTestet(true);
        setTest(null);
        try {
            const res = await fetch(`${BASIS}/test`, { method: 'POST' });
            if (!res.ok) throw new Error(await parseErrorMessage(res, 'Der Test ist fehlgeschlagen.'));
            const d = await res.json() as { ok: boolean; meldung: string };
            setTest({ success: d.ok, message: d.meldung });
        } catch (e) {
            setTest({ success: false, message: e instanceof Error ? e.message : 'Der Test ist fehlgeschlagen.' });
        } finally {
            setTestet(false);
        }
    };

    if (!stand && !ladefehler) return <SectionLoading />;

    return (
        <SettingsCard
            icon={<Send className="w-5 h-5 text-rose-600" />}
            title="Wünsche an die Entwicklung"
            description={
                <>
                    <p>Die KI kann aus einem Gespräch einen Verbesserungswunsch oder Fehlerbericht als <strong>Issue in Gitea</strong> anlegen. Sie macht daraus nur einen Vorschlag mit dem ganzen Text; <strong>erst Ihre Bestätigung legt ihn an</strong>. Nur Administratoren dürfen das, und Texte mit E-Mail-Adressen, Bankdaten oder Passwörtern werden abgelehnt.</p>
                    <p>Hier tragen Sie ein, wohin die Wünsche gehen. Ohne diese Angaben gibt es die Funktion nicht.</p>
                </>
            }
        >
            {ladefehler && <p role="alert" className="mb-3 rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{ladefehler}</p>}
            {stand && (
                <div className="space-y-4" data-testid="ki-wunsch">
                    <div className="grid gap-4 sm:grid-cols-2">
                        <div>
                            <Label htmlFor="wunschAdresse">Adresse von Gitea</Label>
                            <Input id="wunschAdresse" value={adresse} onChange={e => setAdresse(e.target.value)} placeholder="http://192.168.1.20:3050" />
                        </div>
                        <div>
                            <Label htmlFor="wunschRepo">Repository</Label>
                            <Input id="wunschRepo" value={repository} onChange={e => setRepository(e.target.value)} placeholder="Besitzer/Name" />
                        </div>
                    </div>
                    <PasswordField id="wunschToken" label="Zugangsschlüssel (Token)" value={token} onChange={v => { setToken(v); setEntfernen(false); }} isSet={stand.tokenGesetzt && !entfernen}
                        placeholder="Neuen Schlüssel eintragen" className="sm:max-w-lg" />
                    <p className="text-xs text-slate-500">
                        {stand.tokenGesetzt
                            ? (stand.verschluesselt ? 'Ein Schlüssel ist hinterlegt und verschlüsselt gespeichert. Er wird nie wieder angezeigt.' : 'Ein Schlüssel ist hinterlegt. Er wird nie wieder angezeigt. Hinweis: Er liegt wie die anderen KI-Schlüssel unverschlüsselt in den Einstellungen; für verschlüsselte Ablage muss der Server-Schlüssel „mail.credentials.encryption-key" gesetzt sein.')
                            : 'Der Schlüssel braucht in Gitea das Recht, Issues im Repository zu schreiben. Er wird nie wieder angezeigt.'}
                    </p>
                    {stand.tokenGesetzt && (
                        <label className="flex items-center gap-2 text-xs text-slate-600 cursor-pointer select-none">
                            <input type="checkbox" checked={entfernen} onChange={e => { setEntfernen(e.target.checked); if (e.target.checked) setToken(''); }} className="h-3.5 w-3.5 rounded border-slate-300 text-rose-600" />
                            Gespeicherten Schlüssel entfernen
                        </label>
                    )}
                    <div className="flex flex-wrap items-center gap-3">
                        <Button variant="outline" onClick={() => void teste()} disabled={geaendert || !stand.bereit || testet}
                            title={geaendert ? 'Zuerst speichern, getestet wird der gespeicherte Stand' : !stand.bereit ? 'Zuerst Adresse, Repository und Schlüssel speichern' : undefined}>
                            {testet ? <Loader2 className="w-4 h-4 animate-spin motion-reduce:animate-none" /> : <TestTube className="w-4 h-4" />} Verbindung testen
                        </Button>
                    </div>
                    <TestResultBanner result={test} />
                    <SaveButton onClick={() => void speichern()} saving={speichert} disabled={!geaendert}>Speichern</SaveButton>
                </div>
            )}
        </SettingsCard>
    );
}
