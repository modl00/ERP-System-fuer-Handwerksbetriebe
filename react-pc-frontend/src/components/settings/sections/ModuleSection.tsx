import { useState } from 'react';
import { ClipboardCheck, Globe, Home, Loader2, Warehouse, type LucideIcon } from 'lucide-react';
import { Card } from '../../ui/card';
import { useConfirm } from '../../ui/confirm-dialog';
import { useToast } from '../../ui/toast';
import { cn } from '../../../lib/utils';
import { schalteLagermodul, schalteModul, schalteWartungsmodul, type Features } from '../../../features/modulschalter/api';
import { setzeFeaturesStand, useLagerAktiv, useModulAktiv, useWartungAktiv } from '../../../features/modulschalter/useFeatures';

interface ModulKarteProps {
    id: string;
    icon: LucideIcon;
    titel: string;
    beschreibung: string;
    /** Name im Satz: "Das Wartungsmodul ..." */
    modulName: string;
    warnung: string;
    aktiv: boolean | null;
    schalten: (aktiv: boolean) => Promise<Features>;
}

/** Eine Zeile mit Schalter für ein Modul. Ausgeschaltet wird nur ausgeblendet, es wird nichts gelöscht. */
function ModulKarte({ id, icon: Icon, titel, beschreibung, modulName, warnung, aktiv, schalten }: ModulKarteProps) {
    const toast = useToast();
    const confirm = useConfirm();
    const [arbeitet, setArbeitet] = useState(false);

    const umschalten = async () => {
        if (aktiv === null || arbeitet) return;
        const neu = !aktiv;
        if (!neu) {
            const ja = await confirm({
                title: `${modulName} ausschalten?`,
                message: warnung,
                confirmLabel: 'Ausschalten',
                variant: 'warning',
            });
            if (!ja) return;
        }
        setArbeitet(true);
        try {
            setzeFeaturesStand(await schalten(neu));
            toast.success(neu ? `Das ${modulName} ist eingeschaltet.` : `Das ${modulName} ist ausgeschaltet. Die Daten bleiben erhalten.`);
        } catch (e) {
            toast.error(e instanceof Error ? e.message : `Das ${modulName} konnte nicht umgeschaltet werden.`);
        } finally {
            setArbeitet(false);
        }
    };

    return (
        <>
            <div className="mt-5 flex items-start justify-between gap-4 rounded-lg border border-slate-200 p-4">
                <div className="flex min-w-0 items-start gap-3">
                    <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-rose-100 text-rose-600">
                        <Icon className="h-5 w-5" aria-hidden="true" />
                    </span>
                    <div className="min-w-0">
                        <p id={`modul-${id}-titel`} className="font-semibold text-slate-900">{titel}</p>
                        <p id={`modul-${id}-text`} className="mt-0.5 text-sm text-slate-500">{beschreibung}</p>
                    </div>
                </div>
                <button
                    type="button"
                    role="switch"
                    aria-checked={aktiv === true}
                    aria-labelledby={`modul-${id}-titel`}
                    aria-describedby={`modul-${id}-text`}
                    disabled={aktiv === null || arbeitet}
                    onClick={() => void umschalten()}
                    className={cn(
                        'relative inline-flex h-7 w-12 shrink-0 items-center rounded-full border transition-colors focus:outline-none focus:ring-2 focus:ring-rose-500 focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-60',
                        aktiv ? 'border-rose-600 bg-rose-600' : 'border-slate-300 bg-slate-200',
                    )}
                >
                    <span className="sr-only">{aktiv ? 'Eingeschaltet' : 'Ausgeschaltet'}</span>
                    {arbeitet
                        ? <Loader2 className="absolute left-1/2 top-1/2 h-4 w-4 -translate-x-1/2 -translate-y-1/2 text-white motion-safe:animate-spin" aria-hidden="true" />
                        : <span className={cn('inline-block h-5 w-5 rounded-full bg-white shadow transition-transform', aktiv ? 'translate-x-6' : 'translate-x-1')} aria-hidden="true" />}
                </button>
            </div>
            <p className="mt-3 text-sm text-slate-600" aria-live="polite">
                {aktiv === null ? 'Wird geladen …' : aktiv ? `Das ${modulName} ist eingeschaltet.` : `Das ${modulName} ist ausgeschaltet.`}
            </p>
        </>
    );
}

/** Module der Anwendung ein- und ausschalten. Ausgeschaltet wird nur ausgeblendet, es wird nichts gelöscht. */
export function ModuleSection() {
    const wartung = useWartungAktiv();
    const lager = useLagerAktiv();
    const miete = useModulAktiv('miete');
    const website = useModulAktiv('website');

    return (
        <Card className="p-4 sm:p-6">
            <h2 className="text-lg font-semibold text-slate-900">Module</h2>
            <p className="mt-0.5 text-sm text-slate-500">Funktionen ein- oder ausschalten, die nicht jeder Betrieb braucht.</p>

            <ModulKarte
                id="wartung"
                icon={ClipboardCheck}
                titel="Wartung"
                beschreibung="Wartungen und Feldeinsätze mit Prüfliste, Unterschrift und Bericht im Projekt. Ausgeschaltet sind Menüpunkt, Seiten und der Bereich im Projekt weg; vorhandene Daten bleiben erhalten."
                modulName="Wartungsmodul"
                warnung="Menüpunkt, Seiten und der Bereich „Wartungen“ im Projekt verschwinden für alle Benutzer. Die bisherigen Wartungen und Prüfungen werden nicht gelöscht und sind nach dem Einschalten wieder da."
                aktiv={wartung}
                schalten={schalteWartungsmodul}
            />
            <ModulKarte
                id="lager"
                icon={Warehouse}
                titel="Lager"
                beschreibung="Lagerorte, Bestand und Buchungen. Ausgeschaltet sind Menüpunkt und Seite weg und Projektmaterial „aus Lager“ bucht nichts mehr ab; vorhandene Daten bleiben erhalten."
                modulName="Lagermodul"
                warnung="Menüpunkt und Seite „Lager“ verschwinden für alle Benutzer und Projektmaterial „aus Lager“ bucht nichts mehr ab. Bestand und Buchungen werden nicht gelöscht und sind nach dem Einschalten wieder da."
                aktiv={lager}
                schalten={schalteLagermodul}
            />
            <ModulKarte
                id="miete"
                icon={Home}
                titel="Mietabrechnung"
                beschreibung="Mietobjekte, Räume, Verbrauch und Nebenkostenabrechnung. Ausgeschaltet sind Menüpunkt und Seite weg; vorhandene Daten bleiben erhalten."
                modulName="Mietabrechnungsmodul"
                warnung="Menüpunkt und Seite „Mietabrechnung“ verschwinden für alle Benutzer. Mietobjekte und Abrechnungen werden nicht gelöscht und sind nach dem Einschalten wieder da."
                aktiv={miete}
                schalten={(aktiv) => schalteModul('miete', aktiv)}
            />
            <ModulKarte
                id="website"
                icon={Globe}
                titel="Neuigkeiten (Website)"
                beschreibung="Beiträge für den Bereich „Aktuelles“ einer Firmen-Website pflegen. Wer keine passende Website hat, schaltet das aus; dann sind Menüpunkt und Seite weg."
                modulName="Neuigkeiten-Modul"
                warnung="Menüpunkt und Seite „Neuigkeiten“ verschwinden für alle Benutzer. Die Beiträge liegen auf der Website und bleiben dort unverändert."
                aktiv={website}
                schalten={(aktiv) => schalteModul('website', aktiv)}
            />
        </Card>
    );
}
