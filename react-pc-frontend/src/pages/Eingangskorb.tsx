import { useCallback, useEffect, useState } from 'react';
import { Archive, FileText, Inbox, Loader2, RefreshCw, Trash2 } from 'lucide-react';
import { PageHeader } from '../components/PageHeader';
import { Button } from '../components/ui/button';
import { useToast } from '../components/ui/toast';
import { ZuordnungsModal } from '../components/eingangskorb/ZuordnungsModal';

interface EingangEintrag {
    id: number;
    dateiname: string | null;
    typ: string | null;
    uploadDatum: string;
    lieferantId: number;
    lieferantName: string;
    emailBetreff: string | null;
    emailAbsender: string | null;
    eingangStatus: string;
}

function formatDatum(iso: string): string {
    try {
        return new Date(iso).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' });
    } catch {
        return iso;
    }
}

function typLabel(typ: string | null): string {
    switch (typ) {
        case 'RECHNUNG': return 'Rechnung';
        case 'LIEFERSCHEIN': return 'Lieferschein';
        case 'AUFTRAGSBESTAETIGUNG': return 'Auftragsbestätigung';
        case 'ANGEBOT': return 'Angebot';
        default: return typ ?? 'Dokument';
    }
}

export default function Eingangskorb() {
    const [eintraege, setEintraege] = useState<EingangEintrag[]>([]);
    const [laden, setLaden] = useState(true);
    const [aktuellerEintrag, setAktuellerEintrag] = useState<EingangEintrag | null>(null);
    const { toast } = useToast();

    const ladeEintraege = useCallback(async () => {
        setLaden(true);
        try {
            const res = await fetch('/api/eingangskorb');
            if (!res.ok) throw new Error('Fehler beim Laden');
            const data: EingangEintrag[] = await res.json();
            setEintraege(data);
        } catch {
            toast.error('Eingangskorb konnte nicht geladen werden.');
        } finally {
            setLaden(false);
        }
    }, [toast]);

    useEffect(() => { void ladeEintraege(); }, [ladeEintraege]);

    const inPapierkorb = async (id: number) => {
        try {
            const res = await fetch(`/api/eingangskorb/${id}/papierkorb`, { method: 'POST' });
            if (!res.ok) throw new Error();
            setEintraege(prev => prev.filter(e => e.id !== id));
            toast.success('Dokument wurde in den Papierkorb verschoben.');
        } catch {
            toast.error('Dokument konnte nicht verschoben werden.');
        }
    };

    return (
        <div className="max-w-4xl mx-auto px-4 py-8">
            <PageHeader
                category="Dokumente"
                title="EINGANGSKORB"
                description="Neu eingegangene Lieferantendokumente aus dem E-Mail-Import, die noch einem Projekt zugeordnet werden müssen."
                actions={
                    <Button variant="outline" onClick={ladeEintraege} disabled={laden}>
                        <RefreshCw className={`w-4 h-4 ${laden ? 'animate-spin' : ''}`} />
                        Aktualisieren
                    </Button>
                }
            />

            {laden ? (
                <div className="flex items-center justify-center py-24 text-slate-400">
                    <Loader2 className="w-6 h-6 animate-spin mr-3" />
                    <span>Lade Eingangskorb…</span>
                </div>
            ) : eintraege.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-24 text-slate-400">
                    <Inbox className="w-12 h-12 mb-4 text-slate-300" />
                    <p className="text-lg font-medium">Eingangskorb ist leer</p>
                    <p className="text-sm mt-1">Alle Dokumente wurden bereits einem Projekt zugeordnet.</p>
                </div>
            ) : (
                <div className="space-y-3">
                    <p className="text-sm text-slate-500 mb-4">
                        {eintraege.length} {eintraege.length === 1 ? 'Dokument' : 'Dokumente'} warten auf Zuordnung
                    </p>
                    {eintraege.map(eintrag => (
                        <div
                            key={eintrag.id}
                            className="bg-white border border-slate-200 rounded-xl p-4 flex items-start gap-4 shadow-sm hover:shadow-md transition-shadow"
                        >
                            <div className="flex-shrink-0 mt-0.5 text-rose-400">
                                <FileText className="w-6 h-6" />
                            </div>
                            <div className="flex-1 min-w-0">
                                <div className="flex items-center gap-2 flex-wrap">
                                    <p className="font-medium text-slate-800 truncate">
                                        {eintrag.dateiname ?? 'Dokument'}
                                    </p>
                                    <span className="text-xs bg-slate-100 text-slate-600 px-2 py-0.5 rounded-full flex-shrink-0">
                                        {typLabel(eintrag.typ)}
                                    </span>
                                </div>
                                <p className="text-sm text-slate-600 mt-0.5">
                                    <span className="font-medium">{eintrag.lieferantName}</span>
                                    {' · '}
                                    <span>{formatDatum(eintrag.uploadDatum)}</span>
                                </p>
                                {eintrag.emailBetreff && (
                                    <p className="text-xs text-slate-400 mt-1 truncate">
                                        <Archive className="w-3 h-3 inline mr-1" />
                                        {eintrag.emailBetreff}
                                        {eintrag.emailAbsender && ` · ${eintrag.emailAbsender}`}
                                    </p>
                                )}
                            </div>
                            <div className="flex items-center gap-2 flex-shrink-0">
                                <Button
                                    size="sm"
                                    onClick={() => setAktuellerEintrag(eintrag)}
                                >
                                    Projekt zuordnen
                                </Button>
                                <button
                                    onClick={() => inPapierkorb(eintrag.id)}
                                    title="In Papierkorb"
                                    className="text-slate-400 hover:text-red-500 transition-colors p-1"
                                >
                                    <Trash2 className="w-4 h-4" />
                                </button>
                            </div>
                        </div>
                    ))}
                </div>
            )}

            <ZuordnungsModal
                eintrag={aktuellerEintrag}
                onClose={() => setAktuellerEintrag(null)}
                onZugeordnet={() => {
                    setAktuellerEintrag(null);
                    void ladeEintraege();
                    toast.success('Dokument wurde erfolgreich dem Projekt zugeordnet.');
                }}
            />
        </div>
    );
}
