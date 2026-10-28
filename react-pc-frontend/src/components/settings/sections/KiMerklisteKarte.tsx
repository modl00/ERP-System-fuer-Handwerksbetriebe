import { useCallback, useEffect, useState } from 'react';
import { BookMarked, Trash2 } from 'lucide-react';
import { Button } from '../../ui/button';
import { useConfirm } from '../../ui/confirm-dialog';
import { useToast } from '../../ui/toast';
import { SectionLoading, SettingsCard } from '../settingsUi';
import { parseErrorMessage } from '../settingsApi';

interface Eintrag { id: number; text: string; angelegtVon: string | null; angelegtAm: string }

const BASIS = '/api/settings/ki-merkliste';

/**
 * Was sich die KI gemerkt hat (#123, „Dazulernen"): Sätze, die ein Administrator im Gespräch bestätigt hat. Sie fließen in jedes Gespräch ein (nicht bei „Nur lokal"-Sperre
 * für Programmdaten) und lassen sich hier ansehen und löschen. Es gibt nichts Verstecktes.
 */
export function KiMerklisteKarte() {
    const toast = useToast();
    const bestaetige = useConfirm();
    const [liste, setListe] = useState<Eintrag[] | null>(null);
    const [fehler, setFehler] = useState('');

    const laden = useCallback(async () => {
        try {
            const res = await fetch(BASIS);
            if (!res.ok) throw new Error(await parseErrorMessage(res, 'Die Merkliste konnte nicht geladen werden.'));
            setListe(await res.json() as Eintrag[]);
            setFehler('');
        } catch (e) {
            setFehler(e instanceof Error ? e.message : 'Die Merkliste konnte nicht geladen werden.');
        }
    }, []);

    useEffect(() => { void laden(); }, [laden]);

    const loesche = async (e: Eintrag) => {
        if (!await bestaetige({ title: 'Eintrag löschen', message: `„${e.text}" aus der Merkliste löschen? Die KI vergisst das ab dem nächsten Gespräch.`, variant: 'danger', confirmLabel: 'Löschen' })) return;
        try {
            const res = await fetch(`${BASIS}/${e.id}`, { method: 'DELETE' });
            if (!res.ok) throw new Error(await parseErrorMessage(res, 'Der Eintrag konnte nicht gelöscht werden.'));
            toast.success('Der Eintrag ist gelöscht.');
            await laden();
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Der Eintrag konnte nicht gelöscht werden.');
        }
    };

    if (!liste && !fehler) return <SectionLoading />;

    return (
        <SettingsCard
            icon={<BookMarked className="w-5 h-5 text-rose-600" />}
            title="Was die KI sich gemerkt hat"
            description={
                <p>
                    Die KI merkt sich nur, was ein <strong>Administrator im Gespräch ausdrücklich bestätigt</strong> („Merk dir: …", dann „Ja, ausführen"). Alles steht hier, und Sie können es jederzeit löschen.
                    Die Sätze sind <strong>Hinweise, keine Anweisungen</strong>: Sie ändern nie die Regeln und Grenzen der KI. Es passen höchstens 50 Sätze hinein; E-Mail-Adressen, Bankdaten und Passwörter werden abgelehnt.
                </p>
            }
        >
            {fehler && <p role="alert" className="mb-3 rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{fehler}</p>}
            {liste && liste.length === 0 && <p className="rounded-lg border border-dashed border-slate-300 p-6 text-center text-sm text-slate-500" data-testid="merkliste-leer">Die KI hat sich noch nichts gemerkt. Sagen Sie ihr zum Beispiel: „Merk dir: Lieferant Muster liefert nur freitags."</p>}
            {liste && liste.length > 0 && (
                <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200" data-testid="merkliste">
                    {liste.map(e => (
                        <li key={e.id} className="flex items-start gap-3 p-3" data-testid={`merk-${e.id}`}>
                            <div className="min-w-0 flex-1">
                                <p className="text-sm text-slate-900">{e.text}</p>
                                <p className="text-xs text-slate-500">{e.angelegtVon ? `von ${e.angelegtVon}, ` : ''}am {new Date(e.angelegtAm).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })}</p>
                            </div>
                            <Button variant="outline" size="sm" onClick={() => void loesche(e)} aria-label={`„${e.text}" löschen`}><Trash2 className="w-4 h-4" /></Button>
                        </li>
                    ))}
                </ul>
            )}
        </SettingsCard>
    );
}
