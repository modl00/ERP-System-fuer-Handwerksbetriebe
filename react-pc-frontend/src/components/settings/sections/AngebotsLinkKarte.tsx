import { useCallback, useEffect, useState } from 'react';
import { Link2 } from 'lucide-react';
import { Input } from '../../ui/input';
import { useToast } from '../../ui/toast';
import { cn } from '../../../lib/utils';
import { SaveButton, SectionLoading, SettingsCard } from '../settingsUi';
import { parseErrorMessage } from '../settingsApi';

interface Konfiguration {
    aktiv: boolean;
    adresse: string;
    adresseAusKonfiguration: boolean;
    moeglich: boolean;
    gueltigkeitTage: number;
}

const BASIS = '/api/settings/angebots-link';
const json = { 'Content-Type': 'application/json' };

/**
 * Link „Angebot ansehen und annehmen" in Angebots-E-Mails: Schalter, öffentliche Adresse und Standard-Gültigkeit (#88).
 * Ohne Adresse oder bei ausgeschaltetem Schalter geht die E-Mail ohne Link raus, statt einen toten Link zu verschicken.
 */
export function AngebotsLinkKarte() {
    const toast = useToast();
    const [stand, setStand] = useState<Konfiguration | null>(null);
    const [ladefehler, setLadefehler] = useState('');
    const [aktiv, setAktiv] = useState(false);
    const [adresse, setAdresse] = useState('');
    const [tage, setTage] = useState('14');
    const [speichert, setSpeichert] = useState(false);

    const uebernehmen = useCallback((roh: Partial<Konfiguration>) => {
        // Fehlende Felder (zum Beispiel bei einem unvollständigen Server-Stand) gelten als „aus".
        const k: Konfiguration = {
            aktiv: !!roh.aktiv, adresse: roh.adresse ?? '', adresseAusKonfiguration: !!roh.adresseAusKonfiguration,
            moeglich: !!roh.moeglich, gueltigkeitTage: roh.gueltigkeitTage ?? 14,
        };
        setStand(k);
        setAktiv(k.aktiv);
        setAdresse(k.adresse);
        setTage(String(k.gueltigkeitTage));
    }, []);

    useEffect(() => {
        let abgebrochen = false;
        fetch(BASIS)
            .then(async res => {
                if (!res.ok) throw new Error(await parseErrorMessage(res, 'Die Einstellung konnte nicht geladen werden.'));
                return res.json() as Promise<Konfiguration>;
            })
            .then(k => { if (!abgebrochen) uebernehmen(k); })
            .catch(e => { if (!abgebrochen) setLadefehler(e instanceof Error ? e.message : 'Die Einstellung konnte nicht geladen werden.'); });
        return () => { abgebrochen = true; };
    }, [uebernehmen]);

    const tageZahl = Number(tage);
    const tageGueltig = Number.isInteger(tageZahl) && tageZahl >= 1 && tageZahl <= 365;
    const geaendert = !!stand && (aktiv !== stand.aktiv || adresse.trim() !== stand.adresse || (tageGueltig && tageZahl !== stand.gueltigkeitTage));
    const adresseFehlt = aktiv && adresse.trim() === '';

    const speichern = async () => {
        setSpeichert(true);
        try {
            const res = await fetch(BASIS, {
                method: 'PUT', headers: json,
                body: JSON.stringify({ aktiv, adresse: adresse.trim(), gueltigkeitTage: tageGueltig ? tageZahl : null }),
            });
            if (!res.ok) throw new Error(await parseErrorMessage(res, 'Die Einstellung konnte nicht gespeichert werden.'));
            uebernehmen(await res.json() as Konfiguration);
            toast.success('Die Einstellung für den Annahme-Link wurde gespeichert.');
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Die Einstellung konnte nicht gespeichert werden.');
        } finally {
            setSpeichert(false);
        }
    };

    if (!stand && !ladefehler) return <SectionLoading />;

    return (
        <SettingsCard
            icon={<Link2 className="w-5 h-5 text-rose-600" />}
            title="Online-Annahme von Angeboten"
            description={
                <p>
                    Angebots-E-Mails können einen Knopf „Jetzt ansehen und annehmen" enthalten. Der Kunde sieht das Angebot online und nimmt es mit einem Klick an.
                    Das funktioniert nur, wenn Ihr Programm für Kunden <strong>aus dem Internet</strong> erreichbar ist.
                </p>
            }
        >
            {ladefehler && <div role="alert" className="p-3 bg-rose-50 border border-rose-200 text-sm text-rose-800 rounded-lg">{ladefehler}</div>}
            {stand && (
                <div className="space-y-4">
                    <div className="flex items-start justify-between gap-4 rounded-lg border border-slate-200 p-4">
                        <div className="min-w-0">
                            <p id="angebotslink-titel" className="font-semibold text-slate-900">Link in Angebots-E-Mails einfügen</p>
                            <p id="angebotslink-text" className="mt-0.5 text-sm text-slate-500">
                                Ausgeschaltet geht die E-Mail nur mit dem PDF raus, und beim Versenden entfällt die Frage nach der Gültigkeit.
                            </p>
                        </div>
                        <button
                            type="button"
                            role="switch"
                            aria-checked={aktiv}
                            aria-labelledby="angebotslink-titel"
                            aria-describedby="angebotslink-text"
                            onClick={() => setAktiv(a => !a)}
                            className={cn(
                                'relative inline-flex h-7 w-12 shrink-0 items-center rounded-full border transition-colors focus:outline-none focus:ring-2 focus:ring-rose-500 focus:ring-offset-2',
                                aktiv ? 'border-rose-600 bg-rose-600' : 'border-slate-300 bg-slate-200',
                            )}
                        >
                            <span className="sr-only">{aktiv ? 'Eingeschaltet' : 'Ausgeschaltet'}</span>
                            <span className={cn('inline-block h-5 w-5 rounded-full bg-white shadow transition-transform', aktiv ? 'translate-x-6' : 'translate-x-1')} aria-hidden="true" />
                        </button>
                    </div>

                    <div className="grid gap-3 sm:grid-cols-[12rem_1fr] sm:items-start">
                        <label htmlFor="angebotslink-adresse" className="text-sm font-medium text-slate-800 sm:pt-2">Öffentliche Adresse</label>
                        <div className="space-y-1">
                            <Input id="angebotslink-adresse" value={adresse} onChange={e => setAdresse(e.target.value)}
                                placeholder="https://erp.ihre-firma.de" spellCheck={false} autoComplete="off" className="font-mono text-sm" />
                            <p className="text-xs text-slate-500">
                                Die Adresse, unter der Ihre Kunden das Programm erreichen. Muss mit https:// beginnen.
                                {stand.adresseAusKonfiguration && ' Derzeit gilt die Adresse aus der Konfigurationsdatei.'}
                            </p>
                            {adresseFehlt && (
                                <p role="alert" className="text-xs text-amber-700">
                                    Zum Einschalten bitte die Adresse eintragen. Ohne Adresse bleibt der Link aus.
                                </p>
                            )}
                        </div>

                        <label htmlFor="angebotslink-tage" className="text-sm font-medium text-slate-800 sm:pt-2">Gültig für</label>
                        <div className="space-y-1">
                            <div className="flex items-center gap-2">
                                <Input id="angebotslink-tage" type="number" min={1} max={365} value={tage} onChange={e => setTage(e.target.value)} className="w-24" />
                                <span className="text-sm text-slate-600">Tage</span>
                            </div>
                            <p className="text-xs text-slate-500">Vorschlag beim Versenden, dort lässt es sich je Angebot ändern (1 bis 365).</p>
                            {!tageGueltig && <p role="alert" className="text-xs text-amber-700">Bitte eine Zahl zwischen 1 und 365 eintragen.</p>}
                        </div>
                    </div>

                    <div className="flex flex-wrap items-center gap-3">
                        <SaveButton onClick={() => void speichern()} saving={speichert} disabled={!geaendert || adresseFehlt || !tageGueltig}>Speichern</SaveButton>
                        <span role="status" className="text-sm text-slate-600">
                            {stand.moeglich ? 'Angebots-E-Mails enthalten den Link.' : 'Angebots-E-Mails gehen ohne Link raus.'}
                        </span>
                    </div>
                </div>
            )}
        </SettingsCard>
    );
}
