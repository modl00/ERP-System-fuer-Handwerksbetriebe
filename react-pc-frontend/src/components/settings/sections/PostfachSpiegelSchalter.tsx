import { useEffect, useState } from 'react';
import { MailCheck } from 'lucide-react';
import { useToast } from '../../ui/toast';
import { SettingsCard } from '../settingsUi';
import { parseErrorMessage } from '../settingsApi';

/** Option: Löschen und Zuordnen aus dem E-Mail-Center auch im Postfach beim Provider nachführen. Standard aus, weil es das Postfach verändert. */
export function PostfachSpiegelSchalter() {
    const toast = useToast();
    const [aktiv, setAktiv] = useState<boolean | null>(null);
    const [speichert, setSpeichert] = useState(false);

    useEffect(() => {
        let aktuell = true;
        fetch('/api/settings/email/postfach-spiegeln').then(r => (r.ok ? r.json() : null)).then((d: { aktiv: boolean } | null) => { if (aktuell && d) setAktiv(d.aktiv); }).catch(() => { /* ohne Anzeige bleibt der Schalter verborgen */ });
        return () => { aktuell = false; };
    }, []);

    if (aktiv === null) return null;

    const umschalten = async (neu: boolean) => {
        setSpeichert(true);
        try {
            const res = await fetch('/api/settings/email/postfach-spiegeln', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ aktiv: neu }) });
            if (!res.ok) throw new Error(await parseErrorMessage(res, 'Die Einstellung konnte nicht gespeichert werden.'));
            const d = await res.json() as { aktiv: boolean };
            setAktiv(d.aktiv);
            toast.success(d.aktiv ? 'Änderungen werden jetzt im Postfach nachgeführt.' : 'Das Postfach bleibt unberührt.');
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Die Einstellung konnte nicht gespeichert werden.');
        } finally {
            setSpeichert(false);
        }
    };

    return (
        <SettingsCard
            icon={<MailCheck className="w-5 h-5 text-rose-600" />}
            title="Änderungen im Postfach nachführen"
            description={
                <>
                    <p>Wenn Sie im E-Mail-Center etwas ändern, kann das Programm dasselbe im Postfach beim Provider tun, damit es dort nicht anders aussieht:</p>
                    <ul className="list-disc pl-5">
                        <li><strong>Löschen</strong> im Programm: Die Mail wird im Postfach in den <strong>Papierkorb</strong> verschoben (nicht endgültig gelöscht).</li>
                        <li><strong>Zuordnen</strong> zu einem Projekt oder einer Anfrage: Die Mail bekommt im Postfach eine <strong>Markierung</strong> mit der Nummer (zum Beispiel „ERP_2026-0412“). Sie bleibt im Posteingang.</li>
                    </ul>
                    <p>Das gilt für das Haupt-Postfach und nur für Mails, die das Programm vom Server geholt hat. Ist der Provider gerade nicht erreichbar, bleibt die Aktion im Programm trotzdem gültig. Standardmäßig ist das aus, weil das Postfach verändert wird.</p>
                </>
            }
        >
            <label className="flex items-center gap-3 text-sm text-slate-800">
                <input type="checkbox" className="h-4 w-4 accent-rose-600" checked={aktiv} disabled={speichert} onChange={e => void umschalten(e.target.checked)} />
                Löschen und Zuordnen auch im Postfach nachführen
            </label>
        </SettingsCard>
    );
}
