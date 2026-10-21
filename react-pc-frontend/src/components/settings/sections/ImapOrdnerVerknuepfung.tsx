import { useEffect, useState } from 'react';
import { FolderSync, Loader2, Wand2 } from 'lucide-react';
import { Button } from '../../ui/button';
import { useToast } from '../../ui/toast';
import { InfoButton } from '../../ui/info-button';
import { SaveButton, SettingsCard } from '../settingsUi';
import { parseErrorMessage } from '../settingsApi';

interface OrdnerEinstellungen {
    papierkorb: string;
    spam: string;
}

/**
 * Verknüpft Papierkorb und Spam-Ordner mit echten IMAP-Ordnern auf dem Mailserver.
 * Beim Löschen oder Spam-Markieren wird die E-Mail serverseitig verschoben.
 */
export function ImapOrdnerVerknuepfung() {
    const toast = useToast();
    const [laedt, setLaedt] = useState(false);
    const [laedeOrdner, setLaedeOrdner] = useState(false);
    const [speichert, setSpeichert] = useState(false);
    const [erkennt, setErkennt] = useState(false);
    const [ordner, setOrdner] = useState<string[]>([]);
    const [einstellungen, setEinstellungen] = useState<OrdnerEinstellungen>({ papierkorb: '', spam: '' });
    const [bearbeitet, setBearbeitet] = useState<OrdnerEinstellungen>({ papierkorb: '', spam: '' });

    const laden = async () => {
        setLaedt(true);
        try {
            const res = await fetch('/api/emails/ordner-einstellungen');
            if (!res.ok) throw new Error(await parseErrorMessage(res, 'Ordner-Einstellungen konnten nicht geladen werden.'));
            const data = await res.json() as OrdnerEinstellungen;
            setEinstellungen(data);
            setBearbeitet(data);
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Ordner-Einstellungen konnten nicht geladen werden.');
        } finally {
            setLaedt(false);
        }
    };

    const ordnerLaden = async () => {
        setLaedeOrdner(true);
        try {
            const res = await fetch('/api/emails/imap-ordner');
            if (!res.ok) throw new Error(await parseErrorMessage(res, 'IMAP-Ordner konnten nicht abgerufen werden.'));
            const data = await res.json() as string[];
            setOrdner(data);
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'IMAP-Ordner konnten nicht abgerufen werden.');
        } finally {
            setLaedeOrdner(false);
        }
    };

    const autoErkennen = async () => {
        setErkennt(true);
        try {
            const res = await fetch('/api/emails/ordner-einstellungen/auto-erkennen', { method: 'POST' });
            if (!res.ok) throw new Error(await parseErrorMessage(res, 'Automatische Erkennung fehlgeschlagen.'));
            const data = await res.json() as { papierkorb: string; spam: string };
            const neues = {
                papierkorb: data.papierkorb || bearbeitet.papierkorb,
                spam: data.spam || bearbeitet.spam,
            };
            setBearbeitet(neues);
            if (data.papierkorb || data.spam) {
                toast.success('Ordner automatisch erkannt. Bitte prüfen und speichern.');
            } else {
                toast.info('Server meldet keine speziellen Ordner-Attribute. Bitte manuell auswählen.');
            }
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Automatische Erkennung fehlgeschlagen.');
        } finally {
            setErkennt(false);
        }
    };

    const speichern = async () => {
        setSpeichert(true);
        try {
            const res = await fetch('/api/emails/ordner-einstellungen', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(bearbeitet),
            });
            if (!res.ok) throw new Error(await parseErrorMessage(res, 'Ordner-Verknüpfung konnte nicht gespeichert werden.'));
            setEinstellungen({ ...bearbeitet });
            toast.success('Ordner-Verknüpfung gespeichert.');
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Ordner-Verknüpfung konnte nicht gespeichert werden.');
        } finally {
            setSpeichert(false);
        }
    };

    useEffect(() => { void laden(); }, []);

    const geaendert = bearbeitet.papierkorb !== einstellungen.papierkorb
        || bearbeitet.spam !== einstellungen.spam;

    return (
        <SettingsCard
            icon={<FolderSync className="w-5 h-5 text-rose-600" />}
            title="Ordner-Verknüpfung"
            description="Wenn du eine E-Mail löschst oder als Spam markierst, wird sie auch auf dem Mailserver in den richtigen Ordner verschoben."
            info={
                <InfoButton titel="Ordner-Verknüpfung">
                    <p><strong>Was passiert im Hintergrund?</strong><br />
                        Das Programm verbindet sich beim Löschen oder Spam-Markieren mit dem Mailserver und verschiebt die Nachricht in den hier gewählten Ordner.</p>
                    <p><strong>Was bewirkt das?</strong><br />
                        Die E-Mail erscheint danach auch im Webmail oder in anderen Mail-Programmen im Papierkorb bzw. Spam. Das Programm löscht nie endgültig – nur eine Verschiebung. Ist kein Ordner gewählt, bleibt alles wie bisher: Löschen und Spam-Markierung wirken nur im Programm selbst.</p>
                </InfoButton>
            }
        >
            {laedt ? (
                <div className="flex items-center gap-2 text-slate-500 text-sm">
                    <Loader2 className="w-4 h-4 animate-spin" />
                    <span>Wird geladen…</span>
                </div>
            ) : (
                <div className="space-y-4">
                    <div className="flex flex-wrap items-center gap-2">
                        <Button
                            variant="outline"
                            size="sm"
                            onClick={() => { void ordnerLaden(); }}
                            disabled={laedeOrdner}
                        >
                            {laedeOrdner
                                ? <Loader2 className="w-4 h-4 animate-spin" />
                                : <FolderSync className="w-4 h-4" />}
                            IMAP-Ordner abrufen
                        </Button>
                        <Button
                            variant="outline"
                            size="sm"
                            onClick={() => { void autoErkennen(); }}
                            disabled={erkennt}
                            title="Erkennt Papierkorb und Spam automatisch anhand von Mailserver-Attributen"
                        >
                            {erkennt
                                ? <Loader2 className="w-4 h-4 animate-spin" />
                                : <Wand2 className="w-4 h-4" />}
                            Automatisch erkennen
                        </Button>
                    </div>

                    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                        <OrdnerAuswahl
                            label="Papierkorb-Ordner"
                            wert={bearbeitet.papierkorb}
                            ordner={ordner}
                            onChange={v => setBearbeitet(p => ({ ...p, papierkorb: v }))}
                        />
                        <OrdnerAuswahl
                            label="Spam / Junk-Ordner"
                            wert={bearbeitet.spam}
                            ordner={ordner}
                            onChange={v => setBearbeitet(p => ({ ...p, spam: v }))}
                        />
                    </div>

                    <SaveButton
                        onClick={() => { void speichern(); }}
                        saving={speichert}
                        disabled={!geaendert}
                    >
                        Speichern
                    </SaveButton>
                </div>
            )}
        </SettingsCard>
    );
}

interface OrdnerAuswahlProps {
    label: string;
    wert: string;
    ordner: string[];
    onChange: (v: string) => void;
}

function OrdnerAuswahl({ label, wert, ordner, onChange }: OrdnerAuswahlProps) {
    return (
        <div className="space-y-1">
            <label className="text-sm font-medium text-slate-700">{label}</label>
            {ordner.length > 0 ? (
                <select
                    value={wert}
                    onChange={e => onChange(e.target.value)}
                    className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-rose-500 focus:border-transparent"
                >
                    <option value="">— Kein Ordner gewählt —</option>
                    {ordner.map(o => (
                        <option key={o} value={o}>{o}</option>
                    ))}
                </select>
            ) : (
                <input
                    type="text"
                    value={wert}
                    onChange={e => onChange(e.target.value)}
                    placeholder="z. B. Trash oder INBOX.Papierkorb"
                    className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-rose-500 focus:border-transparent"
                />
            )}
            {wert && (
                <p className="text-xs text-slate-500">
                    Gewählt: <span className="font-mono">{wert}</span>
                </p>
            )}
        </div>
    );
}
