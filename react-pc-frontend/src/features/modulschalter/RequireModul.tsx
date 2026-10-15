import { Link } from 'react-router-dom';
import { Loader2, PowerOff } from 'lucide-react';
import { PageLayout } from '../../components/layout/PageLayout';
import { Card } from '../../components/ui/card';
import { useAuth } from '../../auth/AuthContext';
import { useModulAktiv, type AllgemeinesModul } from './useFeatures';

interface Props {
    modul: AllgemeinesModul;
    /** Seitentitel in der Kopfzeile, zum Beispiel "Mietabrechnung". */
    titel: string;
    ribbonCategory: string;
    /** Im Satz: "Das Mietabrechnungsmodul". */
    modulName: string;
    /** Was ausgeblendet statt gelöscht ist: "Die bisherigen Abrechnungen". */
    erhalten: string;
    children: React.ReactElement;
}

/**
 * Schützt die Seiten eines Moduls (Mietabrechnung, Neuigkeiten): Ist es in den Einstellungen ausgeschaltet, kommt statt der Seite ein klarer
 * Hinweis (auch bei einem gespeicherten Lesezeichen). Der Server sperrt die Schnittstelle des Moduls zusätzlich.
 */
export function RequireModul({ modul, titel, ribbonCategory, modulName, erhalten, children }: Props) {
    const aktiv = useModulAktiv(modul);
    const { isAdmin } = useAuth();

    if (aktiv === null) {
        return (
            <div className="flex items-center justify-center gap-2 py-24 text-slate-500" role="status">
                <Loader2 className="h-5 w-5 motion-safe:animate-spin" aria-hidden="true" /> Wird geladen …
            </div>
        );
    }
    if (!aktiv) {
        return (
            <PageLayout ribbonCategory={ribbonCategory} title={titel} subtitle="Dieses Modul ist ausgeschaltet.">
                <Card className="mx-auto max-w-xl p-8 text-center">
                    <PowerOff className="mx-auto h-8 w-8 text-slate-300" aria-hidden="true" />
                    <p className="mt-3 font-medium text-slate-800">{modulName} ist ausgeschaltet.</p>
                    <p className="mt-1 text-sm text-slate-500">
                        {erhalten} sind nicht gelöscht, sie sind nur ausgeblendet.
                        {isAdmin ? ' Unter Einstellungen → Module lässt sich das Modul wieder einschalten.' : ' Ein Administrator kann das Modul wieder einschalten.'}
                    </p>
                    {isAdmin && (
                        <Link to="/einstellungen#module" className="mt-4 inline-flex items-center rounded-lg border border-rose-200 bg-white px-4 py-2 text-sm text-rose-700 hover:bg-rose-50">
                            Zu den Einstellungen
                        </Link>
                    )}
                </Card>
            </PageLayout>
        );
    }
    return children;
}
