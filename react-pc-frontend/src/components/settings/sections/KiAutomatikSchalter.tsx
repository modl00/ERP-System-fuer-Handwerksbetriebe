import { useEffect, useState } from 'react';
import { Sparkles } from 'lucide-react';
import { useToast } from '../../ui/toast';
import { SettingsCard } from '../settingsUi';
import { parseErrorMessage } from '../settingsApi';

/** Option: neue E-Mails vollautomatisch mit der KI zuordnen. Standard aus; wirkt nur für Mails, die nach dem Einschalten ankommen. */
export function KiAutomatikSchalter() {
    const toast = useToast();
    const [an, setAn] = useState<boolean | null>(null);
    const [seit, setSeit] = useState('');
    const [speichert, setSpeichert] = useState(false);

    useEffect(() => {
        let aktuell = true;
        fetch('/api/settings/email/ki-automatik').then(r => (r.ok ? r.json() : null)).then((d: { vollautomatisch: boolean; seit: string } | null) => {
            if (aktuell && d) { setAn(d.vollautomatisch); setSeit(d.seit); }
        }).catch(() => { /* ohne Anzeige bleibt der Schalter verborgen */ });
        return () => { aktuell = false; };
    }, []);

    if (an === null) return null;

    const umschalten = async (neu: boolean) => {
        setSpeichert(true);
        try {
            const res = await fetch('/api/settings/email/ki-automatik', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ vollautomatisch: neu }) });
            if (!res.ok) throw new Error(await parseErrorMessage(res, 'Die Einstellung konnte nicht gespeichert werden.'));
            const d = await res.json() as { vollautomatisch: boolean; seit: string };
            setAn(d.vollautomatisch);
            setSeit(d.seit);
            toast.success(d.vollautomatisch ? 'Neue E-Mails werden jetzt automatisch zugeordnet.' : 'Die automatische Zuordnung ist aus.');
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Die Einstellung konnte nicht gespeichert werden.');
        } finally {
            setSpeichert(false);
        }
    };

    return (
        <SettingsCard
            icon={<Sparkles className="w-5 h-5 text-rose-600" />}
            title="E-Mails automatisch zuordnen (KI)"
            description={
                <>
                    <p>Im E-Mail-Center können Sie ausgewählte Mails mit der KI abgleichen und die Vorschläge bestätigen. Hier schalten Sie zusätzlich <strong>vollautomatisch</strong> ein: Neue Mails werden dann alle paar Minuten ohne Rückfrage dem passenden Lieferanten, Projekt oder der Anfrage zugeordnet.</p>
                    <p>Es gilt nur für Mails, die <strong>nach dem Einschalten</strong> ankommen. Eine falsche Zuordnung lässt sich im E-Mail-Center ändern. Es wird nichts versendet oder gelöscht.</p>
                </>
            }
        >
            <label className="flex items-center gap-3 text-sm text-slate-800">
                <input type="checkbox" className="h-4 w-4 accent-rose-600" checked={an} disabled={speichert} onChange={e => void umschalten(e.target.checked)} />
                Neue E-Mails vollautomatisch zuordnen
            </label>
            {an && seit && <p className="mt-2 text-xs text-slate-500">Eingeschaltet für Mails ab {new Date(seit).toLocaleString('de-DE', { dateStyle: 'medium', timeStyle: 'short' })}.</p>}
        </SettingsCard>
    );
}
