import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { AlertCircle, Loader2 } from 'lucide-react';
import { Card } from '../ui/card';
import { Button } from '../ui/button';
import { useToast } from '../ui/toast';

type Grund = 'KEIN_MITARBEITER' | 'KEINE_ABTEILUNG' | 'KEIN_RECHT';

interface Rechte {
    darfSehen: boolean;
    darfScannen: boolean;
    grund?: Grund | null;
    kannMitarbeiterAnlegen?: boolean;
}

interface Props {
    /** Wird aufgerufen, sobald der Zugang frei ist (zum Beispiel nach „Mich als Mitarbeiter anlegen"). */
    onFreigeschaltet: () => void;
}

/**
 * Erklärt, warum „Belege und Kasse" gesperrt ist, und nennt den einen Schritt, der
 * fehlt (#85). Der Server liefert den Grund; Administratoren ohne Mitarbeiter können
 * sich hier mit einem Klick einen anlegen.
 */
export function BelegeKeinZugang({ onFreigeschaltet }: Props) {
    const toast = useToast();
    const [rechte, setRechte] = useState<Rechte | null>(null);
    const [laedt, setLaedt] = useState(true);
    const [legtAn, setLegtAn] = useState(false);

    useEffect(() => {
        let aktiv = true;
        (async () => {
            try {
                const res = await fetch('/api/buchhaltung/me/permissions');
                if (aktiv && res.ok) setRechte(await res.json());
            } catch {
                // Ohne Antwort zeigen wir die allgemeine Erklärung.
            } finally {
                if (aktiv) setLaedt(false);
            }
        })();
        return () => { aktiv = false; };
    }, []);

    const mitarbeiterAnlegen = async () => {
        setLegtAn(true);
        try {
            const res = await fetch('/api/buchhaltung/me/mitarbeiter-anlegen', { method: 'POST' });
            if (!res.ok) {
                const fehler = await res.json().catch(() => null);
                toast.error(fehler?.message || 'Der Mitarbeiter konnte nicht angelegt werden.');
                return;
            }
            const neu: Rechte = await res.json();
            if (neu.darfSehen) {
                toast.success('Sie sind jetzt als Mitarbeiter angelegt. Belege und Kasse sind freigeschaltet.');
                onFreigeschaltet();
            } else {
                setRechte(neu);
            }
        } catch {
            toast.error('Keine Verbindung zum Programm. Bitte erneut versuchen.');
        } finally {
            setLegtAn(false);
        }
    };

    if (laedt) {
        return (
            <div className="flex justify-center py-16" data-testid="belege-kein-zugang-laedt">
                <Loader2 className="w-8 h-8 animate-spin text-rose-500" />
            </div>
        );
    }

    const grund = rechte?.grund ?? null;
    const linkKlasse = 'font-medium text-rose-700 underline underline-offset-2 hover:text-rose-800';

    return (
        <Card className="p-12 text-center border-amber-200 bg-amber-50" data-testid="belege-kein-zugang">
            <AlertCircle className="w-12 h-12 mx-auto mb-3 text-amber-500" />
            <p className="font-medium text-amber-900">Belege und Kasse sind für Sie noch nicht freigegeben</p>

            {grund === 'KEIN_MITARBEITER' && (
                <div className="text-sm mt-2 text-amber-800 max-w-md mx-auto space-y-3">
                    <p>
                        Ihr Benutzer ist <strong>keinem Mitarbeiter zugeordnet</strong>. Belege und Kasse halten fest,
                        wer etwas gebucht hat, darum braucht es dort einen Mitarbeiter.
                    </p>
                    {rechte?.kannMitarbeiterAnlegen ? (
                        <>
                            <p>
                                Als Administrator können Sie sich mit einem Klick selbst anlegen. Der neue Mitarbeiter
                                führt kein Zeitkonto und taucht in der Zeiterfassung nicht auf. Abteilung und
                                Anschrift können Sie später unter <Link to="/mitarbeiter" className={linkKlasse}>Mitarbeiter</Link> ergänzen.
                            </p>
                            <Button onClick={mitarbeiterAnlegen} disabled={legtAn}>
                                {legtAn ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : null}
                                Mich als Mitarbeiter anlegen
                            </Button>
                        </>
                    ) : (
                        <p>
                            Ein Administrator ordnet Ihnen unter <Link to="/benutzer" className={linkKlasse}>Benutzer</Link> einen
                            Mitarbeiter zu.
                        </p>
                    )}
                </div>
            )}

            {grund === 'KEINE_ABTEILUNG' && (
                <p className="text-sm mt-2 text-amber-800 max-w-md mx-auto">
                    Ihr Mitarbeiter gehört <strong>keiner Abteilung</strong> an. Ein Administrator weist sie unter{' '}
                    <Link to="/mitarbeiter" className={linkKlasse}>Mitarbeiter</Link> zu.
                </p>
            )}

            {grund === 'KEIN_RECHT' && (
                <p className="text-sm mt-2 text-amber-800 max-w-md mx-auto">
                    <strong>Keine Ihrer Abteilungen darf Belege sehen.</strong> Ein Administrator schaltet sie unter{' '}
                    <Link to="/abteilung-berechtigungen" className={linkKlasse}>Rechte der Abteilungen</Link> in der Zeile
                    „Belege &amp; Kasse" frei.
                </p>
            )}

            {!grund && (
                <p className="text-sm mt-2 text-amber-800 max-w-md mx-auto">
                    Ihr Benutzer braucht einen Mitarbeiter mit einer Abteilung, die Belege sehen darf (Administration → Wer darf was? →
                    Rechte der Abteilungen, Zeile „Belege &amp; Kasse"). Administratoren sind nach der Zuordnung zu einem
                    Mitarbeiter automatisch freigeschaltet.
                </p>
            )}
        </Card>
    );
}
