import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Calendar, Clock, Plus, Users } from 'lucide-react';
import { Button } from './ui/button';

interface Teilnehmer {
    id: number;
    name: string;
}

interface KalenderEintrag {
    id: number;
    titel: string;
    beschreibung: string | null;
    datum: string;
    startZeit: string | null;
    endeZeit: string | null;
    ganztaegig: boolean;
    farbe: string | null;
    erstellerName: string | null;
    teilnehmer: Teilnehmer[];
}

interface ProjektTermineTabProps {
    projektId: number;
    projektName?: string;
}

const WOCHENTAGE = ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa'];
const MONATE = ['Jan', 'Feb', 'Mrz', 'Apr', 'Mai', 'Jun', 'Jul', 'Aug', 'Sep', 'Okt', 'Nov', 'Dez'];

function formatDatum(isoDate: string): string {
    const d = new Date(isoDate + 'T00:00:00');
    return `${WOCHENTAGE[d.getDay()]}, ${d.getDate()}. ${MONATE[d.getMonth()]} ${d.getFullYear()}`;
}

function formatZeit(zeit: string): string {
    return zeit.slice(0, 5);
}

export function ProjektTermineTab({ projektId, projektName }: ProjektTermineTabProps) {
    const navigate = useNavigate();
    const [termine, setTermine] = useState<KalenderEintrag[] | null>(null);
    const [fehler, setFehler] = useState('');

    useEffect(() => {
        let abgebrochen = false;
        fetch(`/api/kalender/projekt/${projektId}`)
            .then(r => {
                if (!r.ok) throw new Error('Termine konnten nicht geladen werden.');
                return r.json() as Promise<KalenderEintrag[]>;
            })
            .then(daten => { if (!abgebrochen) setTermine(daten); })
            .catch((e: unknown) => { if (!abgebrochen) setFehler(e instanceof Error ? e.message : 'Unbekannter Fehler'); });
        return () => { abgebrochen = true; };
    }, [projektId]);

    const oeffneKalenderMitProjekt = () => {
        navigate(`/kalender?projektId=${projektId}&projektName=${encodeURIComponent(projektName ?? '')}`);
    };

    const oeffneTermin = (id: number) => {
        navigate(`/kalender?terminId=${id}`);
    };

    return (
        <div className="space-y-4">
            <div className="flex items-center justify-between">
                <h3 className="text-sm font-medium text-slate-700">Termine</h3>
                <Button variant="outline" size="sm" onClick={oeffneKalenderMitProjekt} className="gap-1.5">
                    <Plus className="h-3.5 w-3.5" />
                    Neuer Termin
                </Button>
            </div>

            {fehler && (
                <p className="text-sm text-rose-600 bg-rose-50 border border-rose-200 rounded px-3 py-2">{fehler}</p>
            )}

            {termine === null && !fehler && (
                <div className="space-y-2">
                    {[1, 2, 3].map(i => (
                        <div key={i} className="h-16 bg-slate-100 rounded animate-pulse" />
                    ))}
                </div>
            )}

            {termine !== null && termine.length === 0 && (
                <div className="text-center py-10 text-slate-400">
                    <Calendar className="h-8 w-8 mx-auto mb-2 opacity-40" />
                    <p className="text-sm">Noch keine Termine für dieses Projekt.</p>
                </div>
            )}

            {termine !== null && termine.length > 0 && (
                <ul className="divide-y divide-slate-100">
                    {termine.map(t => (
                        <li key={t.id}>
                            <button
                                type="button"
                                className="w-full text-left px-1 py-3 hover:bg-slate-50 rounded transition-colors"
                                onClick={() => oeffneTermin(t.id)}
                            >
                                <div className="flex items-start gap-3">
                                    <div
                                        className="mt-0.5 h-3 w-3 rounded-full flex-shrink-0"
                                        style={{ backgroundColor: t.farbe ?? '#6366f1' }}
                                    />
                                    <div className="min-w-0 flex-1">
                                        <p className="text-sm font-medium text-slate-800 truncate">{t.titel}</p>
                                        <div className="flex flex-wrap items-center gap-x-3 gap-y-0.5 mt-0.5">
                                            <span className="text-xs text-slate-500">{formatDatum(t.datum)}</span>
                                            {!t.ganztaegig && t.startZeit && (
                                                <span className="inline-flex items-center gap-1 text-xs text-slate-500">
                                                    <Clock className="h-3 w-3" />
                                                    {formatZeit(t.startZeit)}
                                                    {t.endeZeit ? ` – ${formatZeit(t.endeZeit)}` : ''}
                                                </span>
                                            )}
                                            {t.teilnehmer.length > 0 && (
                                                <span className="inline-flex items-center gap-1 text-xs text-slate-500">
                                                    <Users className="h-3 w-3" />
                                                    {t.teilnehmer.map(tn => tn.name).join(', ')}
                                                </span>
                                            )}
                                        </div>
                                        {t.beschreibung && (
                                            <p className="text-xs text-slate-400 mt-0.5 truncate">
                                                {t.beschreibung.length > 100 ? t.beschreibung.slice(0, 100) + '…' : t.beschreibung}
                                            </p>
                                        )}
                                    </div>
                                </div>
                            </button>
                        </li>
                    ))}
                </ul>
            )}
        </div>
    );
}
