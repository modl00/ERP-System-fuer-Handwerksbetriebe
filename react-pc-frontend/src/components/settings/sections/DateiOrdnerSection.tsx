import { useCallback, useEffect, useState } from 'react';
import { ChevronDown, ChevronUp, Copy, FolderCheck, FolderOpen, Loader2, Server, Share2 } from 'lucide-react';
import { Input } from '../../ui/input';
import { Label } from '../../ui/label';
import { Button } from '../../ui/button';
import { useToast } from '../../ui/toast';
import { SaveButton, SectionLoading, SettingsCard, TestResultBanner } from '../settingsUi';
import { parseErrorMessage } from '../settingsApi';
import type { TestResult } from '../settingsApi';
import { SMB_VERSIONEN, smbVariablen, type SmbVersion } from './netzwerkordner';

/**
 * Gemeinsamer Ablageort für Zeichnungen und Dateien (HiCAD, Tenado, Excel).
 *
 * <p>Die Seite erkennt, wo das Programm läuft (Windows oder Docker), und zeigt nur, was dort gilt: unter Windows ein Ordner auf dem Rechner mit der
 * Windows-Freigabe, im Docker ein Pfad im Container mit der Freigabe am NAS.</p>
 *
 * <p>Der Ordner liegt auf dem Server-Rechner. Damit Kollegen ihn erreichen,
 * braucht es zusätzlich eine Netzwerk-Adresse — die Anleitung dafür steht
 * ausklappbar direkt darunter, statt in einer separaten Dokumentation.</p>
 */
export function DateiOrdnerSection({ onSaved }: { onSaved?: () => void }) {
    const toast = useToast();
    const [loading, setLoading] = useState(true);

    const [pfad, setPfad] = useState('');
    const [networkUrl, setNetworkUrl] = useState('');
    const [saving, setSaving] = useState(false);
    const [testing, setTesting] = useState(false);
    const [freigebend, setFreigebend] = useState(false);
    const [testResult, setTestResult] = useState<TestResult | null>(null);
    const [anleitungOffen, setAnleitungOffen] = useState(false);
    // Netzwerkordner mit Benutzer und Passwort (nur Docker): erzeugt die Zeilen für den Stack, das Passwort wird hier nie eingegeben
    const [smbOffen, setSmbOffen] = useState(false);
    const [smbAdresse, setSmbAdresse] = useState('');
    const [smbBenutzer, setSmbBenutzer] = useState('');
    const [smbVersion, setSmbVersion] = useState<SmbVersion>('vers=3.0');
    const [umgebung, setUmgebung] = useState<'WINDOWS' | 'DOCKER' | 'LINUX'>('WINDOWS');
    const [standardPfad, setStandardPfad] = useState('');
    const windows = umgebung === 'WINDOWS';

    const loadSettings = useCallback(async () => {
        setLoading(true);
        try {
            const res = await fetch('/api/settings/datei-ordner');
            if (res.ok) {
                const data = await res.json();
                setPfad(data?.pfad || '');
                setNetworkUrl(data?.networkUrl || '');
                if (data?.umgebung === 'DOCKER' || data?.umgebung === 'LINUX') setUmgebung(data.umgebung);
                setStandardPfad(data?.standardPfad || '');
            }
        } catch {
            toast.error('Datei-Ordner konnte nicht geladen werden.');
        } finally {
            setLoading(false);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    useEffect(() => {
        loadSettings();
    }, [loadSettings]);

    const handleTest = async () => {
        setTesting(true);
        setTestResult(null);
        try {
            const res = await fetch('/api/settings/datei-ordner/test', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ pfad }),
            });
            setTestResult(await res.json());
        } catch {
            setTestResult({ success: false, message: 'Prüfung fehlgeschlagen – Server nicht erreichbar.' });
        } finally {
            setTesting(false);
        }
    };

    const handleSave = async () => {
        setSaving(true);
        try {
            const res = await fetch('/api/settings/datei-ordner', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ pfad, networkUrl }),
            });
            if (!res.ok) {
                toast.error(await parseErrorMessage(res, 'Datei-Ordner konnte nicht gespeichert werden.'));
                return;
            }
            toast.success('Datei-Ordner gespeichert.');
            onSaved?.();
        } catch {
            toast.error('Datei-Ordner konnte nicht gespeichert werden.');
        } finally {
            setSaving(false);
        }
    };

    const handleFreigeben = async () => {
        setFreigebend(true);
        setTestResult(null);
        try {
            const res = await fetch('/api/settings/datei-ordner/freigeben', { method: 'POST' });
            setTestResult(await res.json());
        } catch {
            setTestResult({ success: false, message: 'Freigabe fehlgeschlagen – Server nicht erreichbar.' });
        } finally {
            setFreigebend(false);
        }
    };

    if (loading) return <SectionLoading />;

    return (
        <SettingsCard
            icon={<FolderOpen className="w-5 h-5 text-rose-600" />}
            title="Wo sollen Zeichnungen und Dateien liegen?"
            description={
                windows ? (
                    <p>
                        In diesen Ordner legt das Programm Zeichnungen aus HiCAD (.sza) und Tenado (.tcd) sowie
                        Tabellen (Excel, CSV, OpenOffice), die Sie an Projekten hochladen. Ein lokaler Ordner
                        (C:\Zeichnungen), ein Netzlaufwerk (Z:\Zeichnungen) oder eine Netzwerk-Adresse
                        (\\server\zeichnungen) funktionieren.
                    </p>
                ) : (
                    <p>
                        In diesen Ordner legt das Programm Zeichnungen aus HiCAD (.sza) und Tenado (.tcd) sowie
                        Tabellen (Excel, CSV, OpenOffice), die Sie an Projekten hochladen.
                        Das Programm läuft hier im Docker-Container: Die Dateien liegen im <strong>Datenordner auf dem Gerät</strong> (zum Beispiel Ihrer Synology) und werden mit dem Gerät gesichert.
                        Die Windows-Rechner erreichen sie über die <strong>Netzwerk-Adresse</strong> unten.
                    </p>
                )
            }
        >
            <div className="space-y-4">
                <div>
                    <Label htmlFor="dateiOrdnerPfad">{windows ? 'Ordner auf diesem Rechner' : 'Ordner im Container'}</Label>
                    <div className="flex flex-col sm:flex-row gap-2">
                        <Input
                            id="dateiOrdnerPfad"
                            className="flex-1 sm:max-w-lg"
                            value={pfad}
                            onChange={(e) => setPfad(e.target.value)}
                            placeholder={windows ? 'C:\\Zeichnungen' : (standardPfad || '/app/uploads/CADdrawings')}
                        />
                        <Button variant="outline" onClick={handleTest} disabled={testing || !pfad.trim()}>
                            {testing ? <Loader2 className="w-4 h-4 animate-spin" /> : <FolderCheck aria-hidden="true" className="w-4 h-4" />}
                            {testing ? 'Prüfe...' : 'Ordner prüfen'}
                        </Button>
                    </div>
                    {!windows && (
                        <p className="text-xs text-slate-500 mt-1" data-testid="docker-ordner-hinweis">
                            Standard: <span className="font-mono">{standardPfad || '/app/uploads/CADdrawings'}</span> (auf dem Gerät: Ordner ERP_DATEN/dokumente/CADdrawings).
                            Ein anderer Pfad muss von außen in den Container eingebunden sein.
                            {standardPfad && pfad.trim() !== standardPfad && (
                                <button type="button" className="ml-2 text-rose-700 hover:underline" onClick={() => setPfad(standardPfad)}>Standard verwenden</button>
                            )}
                        </p>
                    )}
                </div>

                <div>
                    <Label htmlFor="dateiOrdnerNetworkUrl">{windows ? 'Netzwerk-Adresse für Kollegen (optional)' : 'Netzwerk-Adresse für die Windows-Rechner (HiCAD, Tenado)'}</Label>
                    <Input
                        id="dateiOrdnerNetworkUrl"
                        className="sm:max-w-lg"
                        value={networkUrl}
                        onChange={(e) => setNetworkUrl(e.target.value)}
                        placeholder={windows ? '\\\\server\\zeichnungen' : '\\\\nas\\erp\\dokumente\\CADdrawings'}
                    />
                    <p className="text-xs text-slate-500 mt-1">
                        {windows
                            ? 'So erreichen andere Rechner den Ordner. Leer lassen, wenn nur dieser Rechner ihn nutzt.'
                            : 'So öffnen die Windows-Rechner die Zeichnungen (\\\\nas\\freigabe\\… oder smb://nas/freigabe/…). Die Freigabe richten Sie am NAS ein, nicht hier.'}
                    </p>
                </div>
            </div>

            <TestResultBanner result={testResult} className="mt-3" />

            {!windows && (
                <div className="mt-5 rounded-lg border border-slate-200 p-4" data-testid="smb-hilfe">
                    <button type="button" aria-expanded={smbOffen} onClick={() => setSmbOffen(v => !v)} className="flex w-full items-center gap-2 text-left text-sm font-semibold text-slate-800">
                        <Server className="w-4 h-4 text-rose-600" aria-hidden="true" />
                        Die Dateien liegen auf einem anderen Gerät, mit Benutzername und Passwort?
                        {smbOffen ? <ChevronUp className="ml-auto w-4 h-4" aria-hidden="true" /> : <ChevronDown className="ml-auto w-4 h-4" aria-hidden="true" />}
                    </button>
                    {smbOffen && <SmbHilfe adresse={smbAdresse} onAdresse={setSmbAdresse} benutzer={smbBenutzer} onBenutzer={setSmbBenutzer} version={smbVersion} onVersion={setSmbVersion} />}
                </div>
            )}

            <button
                type="button"
                aria-expanded={anleitungOffen}
                className="mt-4 flex items-center gap-1 text-sm text-rose-700 hover:text-rose-800"
                onClick={() => setAnleitungOffen((prev) => !prev)}
            >
                {anleitungOffen ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
                {windows ? 'So geben Sie den Ordner im Netzwerk frei' : 'So gibst du den Datenordner am NAS frei'}
            </button>
            {anleitungOffen && !windows && (
                <div className="mt-2 p-4 rounded-lg bg-slate-50 text-sm text-slate-700 space-y-2" data-testid="nas-anleitung">
                    <p>1. Am NAS (zum Beispiel Synology: <strong>Systemsteuerung → Gemeinsamer Ordner</strong>) den Ordner mit den Dokumenten freigeben, in dem <span className="font-mono">CADdrawings</span> liegt (<span className="font-mono">ERP_DATEN/dokumente</span>).</p>
                    <p>2. Für die Mitarbeiter Rechte zum Lesen und Schreiben vergeben (Benutzer und Passwort verwaltet das NAS).</p>
                    <p>3. Die Adresse der Freigabe (zum Beispiel <span className="font-mono">{'\\\\nas\\erp\\dokumente\\CADdrawings'}</span>) oben als Netzwerk-Adresse eintragen. Auf den Windows-Rechnern mit HiCAD oder Tenado muss der Launcher eingerichtet sein.</p>
                    <p className="text-xs text-slate-500">Liegen die Dateien auf einem <strong>anderen</strong> Gerät mit Benutzername und Passwort, wird der Netzwerkordner beim Docker-Stack eingebunden (Zusatzdatei <span className="font-mono">docker-compose.netzwerk.yml</span>, Zugangsdaten als Variablen im Stack, nie im Programm). Siehe docs/DOCKER.md.</p>
                </div>
            )}
            {anleitungOffen && windows && (
                <div className="mt-2 p-4 rounded-lg bg-slate-50 text-sm text-slate-700 space-y-2">
                    <p>1. Im Windows-Explorer mit der rechten Maustaste auf den Ordner klicken → <strong>Eigenschaften</strong>.</p>
                    <p>2. Reiter <strong>Freigabe</strong> → <strong>Freigeben...</strong> → Kollegen oder „Jeder" hinzufügen → <strong>Freigeben</strong>.</p>
                    <p>3. Die angezeigte Adresse (z. B. \\BUERO-PC\Zeichnungen) oben als Netzwerk-Adresse eintragen.</p>
                    <div className="pt-2">
                        <Button variant="outline" onClick={handleFreigeben} disabled={freigebend || !pfad.trim()}>
                            {freigebend ? <Loader2 className="w-4 h-4 animate-spin" /> : <Share2 className="w-4 h-4" />}
                            {freigebend ? 'Gebe frei...' : 'Oder: Ordner automatisch freigeben'}
                        </Button>
                        <p className="text-xs text-slate-500 mt-1">
                            Windows fragt dabei einmal nach Administrator-Rechten. Der Ordner muss vorher gespeichert sein.
                        </p>
                    </div>
                </div>
            )}

            <SaveButton onClick={handleSave} saving={saving} disabled={!pfad.trim()}>
                Datei-Ordner speichern
            </SaveButton>
        </SettingsCard>
    );
}

/** Erzeugt aus Adresse und Benutzer die Zeilen für die Variablen des Docker-Stacks (docker-compose.netzwerk.yml). Das Passwort gehört nicht in dieses Programm. */
function SmbHilfe({ adresse, onAdresse, benutzer, onBenutzer, version, onVersion }: {
    adresse: string; onAdresse: (v: string) => void; benutzer: string; onBenutzer: (v: string) => void; version: SmbVersion; onVersion: (v: SmbVersion) => void;
}) {
    const toast = useToast();
    const e = smbVariablen(adresse, benutzer, version);
    const kopieren = async () => {
        try {
            await navigator.clipboard.writeText(e.zeilen);
            toast.success('Die Zeilen sind kopiert. Das Passwort tragen Sie im Stack selbst ein.');
        } catch {
            toast.error('Das Kopieren hat nicht geklappt. Bitte die Zeilen von Hand markieren und kopieren.');
        }
    };
    return (
        <div className="mt-3 space-y-3 text-sm text-slate-700">
            <p>Das Programm kann einen Netzwerkordner im Container nicht selbst einhängen, das macht der Stack. Hier erzeugen Sie die Angaben dafür. <strong>Das Passwort geben Sie hier nicht ein</strong>: Es steht nur im Stack (Portainer), nie im Programm.</p>
            <div className="grid gap-3 sm:grid-cols-2">
                <div>
                    <Label htmlFor="smbAdresse">Adresse der Freigabe</Label>
                    <Input id="smbAdresse" value={adresse} onChange={ev => onAdresse(ev.target.value)} placeholder="nas.local/erp-dokumente" />
                </div>
                <div>
                    <Label htmlFor="smbBenutzer">Benutzer der Freigabe</Label>
                    <Input id="smbBenutzer" value={benutzer} onChange={ev => onBenutzer(ev.target.value)} autoComplete="off" />
                </div>
                <div>
                    <Label htmlFor="smbVersion">Alter des Geräts</Label>
                    <select id="smbVersion" value={version} onChange={ev => onVersion(ev.target.value as SmbVersion)} className="min-h-10 w-full rounded-md border border-slate-300 bg-white px-3 text-sm">
                        {SMB_VERSIONEN.map(v => <option key={v.wert} value={v.wert}>{v.text}</option>)}
                    </select>
                </div>
            </div>
            {e.fehler && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-rose-800">{e.fehler}</p>}
            {e.warnung && <p role="status" className="rounded-lg bg-amber-50 p-3 text-amber-900">{e.warnung}</p>}
            {e.zeilen && (
                <div className="space-y-2">
                    <pre className="overflow-x-auto rounded-lg bg-slate-900 p-3 font-mono text-xs text-slate-100" data-testid="smb-zeilen">{e.zeilen}</pre>
                    <Button variant="outline" size="sm" onClick={() => void kopieren()}><Copy className="w-4 h-4" /> Zeilen kopieren</Button>
                    <ol className="list-decimal space-y-1 pl-5 text-slate-700">
                        <li>In <strong>Portainer</strong> den Stack öffnen → <strong>Editor</strong>.</li>
                        <li>Bei <strong>Additional paths</strong> die Datei <span className="font-mono">docker-compose.netzwerk.yml</span> hinzufügen.</li>
                        <li>Unter <strong>Environment variables</strong> die vier Zeilen eintragen und bei <span className="font-mono">ERP_SMB_PASSWORT</span> Ihr Passwort einsetzen.</li>
                        <li><strong>Update the stack</strong> wählen und warten, bis das Programm neu gestartet ist.</li>
                        <li>Hier oben auf <strong>„Ordner prüfen“</strong> klicken: Das Programm legt eine Testdatei an. Klappt es nicht, steht dort der Grund (zum Beispiel falsches Passwort oder Adresse nicht erreichbar).</li>
                    </ol>
                </div>
            )}
        </div>
    );
}
