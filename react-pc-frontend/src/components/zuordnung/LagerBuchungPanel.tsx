/**
 * Panel im ZuordnungModal: wenn eine LAGER-Kostenstelle gewählt wird,
 * lädt dieser Bereich eine Vorschau aller Positionen mit ihrem Abgleich-Status
 * und ermöglicht das manuelle Anlegen fehlender Artikel.
 */
import { useEffect, useState } from 'react';
import { CheckCircle2, AlertCircle, RefreshCw, Warehouse } from 'lucide-react';
import { Button } from '../ui/button';
import { useToast } from '../ui/toast';
import { CreateArticleModal } from '../CreateArticleModal';
import type { Artikel } from '../../types';

interface VorschauPosition {
    positionId: number;
    bezeichnung: string;
    menge: number;
    mengeneinheit: string;
    externeArtikelnummer: string | null;
    artikelId: number | null;
    artikelName: string | null;
    abgeglichen: boolean;
}

interface LagerBuchungPanelProps {
    geschaeftsdokumentId: number;
    lagerOrtId: number | null;
    /** Aktive Lagerorte für das Dropdown */
    lagerorte: { id: number; name: string }[];
    onLagerOrtChange: (id: number | null) => void;
    /** Wird aufgerufen wenn sich die expliziten Artikel-IDs ändern */
    onArtikelZuordnungenChange: (zuordnungen: Array<{ positionId: number; artikelId: number }>) => void;
}

export function LagerBuchungPanel({
    geschaeftsdokumentId,
    lagerOrtId,
    lagerorte,
    onLagerOrtChange,
    onArtikelZuordnungenChange,
}: LagerBuchungPanelProps) {
    const toast = useToast();
    const [vorschau, setVorschau] = useState<VorschauPosition[]>([]);
    const [laedt, setLaedt] = useState(false);
    const [artikelAnlegenFuer, setArtikelAnlegenFuer] = useState<VorschauPosition | null>(null);
    // Manuell zugeordnete Artikel: positionId → artikelId
    const [manuell, setManuell] = useState<Map<number, number>>(new Map());

    const ladeVorschau = async () => {
        setLaedt(true);
        try {
            const res = await fetch(`/api/bestellungen-uebersicht/${geschaeftsdokumentId}/lager-vorschau`);
            if (res.ok) {
                const data: VorschauPosition[] = await res.json();
                setVorschau(data);
            } else {
                toast.error('Vorschau konnte nicht geladen werden.');
            }
        } catch {
            toast.error('Vorschau konnte nicht geladen werden.');
        } finally {
            setLaedt(false);
        }
    };

    useEffect(() => {
        ladeVorschau();
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [geschaeftsdokumentId]);

    const artikelAngelegt = (pos: VorschauPosition, artikel: Artikel) => {
        const neu = new Map(manuell);
        neu.set(pos.positionId, artikel.id);
        setManuell(neu);
        setArtikelAnlegenFuer(null);
        onArtikelZuordnungenChange(
            Array.from(neu.entries()).map(([positionId, artikelId]) => ({ positionId, artikelId }))
        );
        toast.success(`Artikel „${artikel.produktname}" angelegt.`);
        // Vorschau aktualisieren: Position jetzt als abgeglichen zeigen
        setVorschau(prev => prev.map(p =>
            p.positionId === pos.positionId
                ? { ...p, artikelId: artikel.id, artikelName: artikel.produktname, abgeglichen: true }
                : p
        ));
    };

    const abgeglichenCount = vorschau.filter(p => p.abgeglichen || manuell.has(p.positionId)).length;
    const nichtAbgeglichenCount = vorschau.filter(p => !p.abgeglichen && !manuell.has(p.positionId)).length;

    return (
        <div className="rounded-xl border border-slate-200 bg-slate-50 p-4 space-y-3">
            <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                    <Warehouse className="w-4 h-4 text-rose-600 shrink-0" />
                    <span className="font-medium text-sm text-slate-800">Artikel ins Lager buchen</span>
                </div>
                <button
                    type="button"
                    onClick={ladeVorschau}
                    disabled={laedt}
                    className="text-slate-400 hover:text-slate-600 disabled:opacity-40"
                    aria-label="Vorschau neu laden"
                >
                    <RefreshCw className={`w-3.5 h-3.5 ${laedt ? 'animate-spin' : ''}`} />
                </button>
            </div>

            {/* Lagerort-Auswahl */}
            <div className="flex items-center gap-3">
                <label className="text-sm text-slate-600 shrink-0">Lagerort</label>
                <select
                    value={lagerOrtId ?? ''}
                    onChange={e => onLagerOrtChange(e.target.value ? Number(e.target.value) : null)}
                    className="flex-1 px-3 py-1.5 border border-slate-200 rounded-lg text-sm bg-white focus:ring-2 focus:ring-rose-500 focus:border-transparent"
                >
                    <option value="">– kein Lagerzugang –</option>
                    {lagerorte.map(o => (
                        <option key={o.id} value={o.id}>{o.name}</option>
                    ))}
                </select>
            </div>

            {/* Positionen-Vorschau */}
            {vorschau.length === 0 && !laedt && (
                <p className="text-xs text-amber-700">
                    Noch keine Positionen erkannt – bitte erst Positionen auslesen (Modus „Nach Positionen" wählen).
                </p>
            )}

            {vorschau.length > 0 && (
                <div className="space-y-1.5">
                    {vorschau.map(pos => {
                        const istAbgeglichen = pos.abgeglichen || manuell.has(pos.positionId);
                        return (
                            <div key={pos.positionId} className="flex items-center gap-2 text-xs">
                                {istAbgeglichen ? (
                                    <CheckCircle2 className="w-3.5 h-3.5 text-green-600 shrink-0" />
                                ) : (
                                    <AlertCircle className="w-3.5 h-3.5 text-amber-500 shrink-0" />
                                )}
                                <span className="flex-1 truncate text-slate-700">
                                    {pos.bezeichnung}
                                    <span className="text-slate-400 ml-1">
                                        {pos.menge} {pos.mengeneinheit}
                                    </span>
                                </span>
                                {istAbgeglichen ? (
                                    <span className="text-green-600 shrink-0">
                                        {pos.artikelName ?? (manuell.has(pos.positionId) ? 'angelegt' : 'gefunden')}
                                    </span>
                                ) : (
                                    <Button
                                        size="sm"
                                        variant="outline"
                                        className="h-6 px-2 text-xs border-amber-300 text-amber-700 hover:bg-amber-50 shrink-0"
                                        onClick={() => setArtikelAnlegenFuer(pos)}
                                    >
                                        Artikel anlegen
                                    </Button>
                                )}
                            </div>
                        );
                    })}
                </div>
            )}

            {vorschau.length > 0 && (
                <p className="text-xs text-slate-500">
                    {abgeglichenCount} von {vorschau.length} Positionen abgeglichen
                    {nichtAbgeglichenCount > 0 && ` – ${nichtAbgeglichenCount} werden beim Buchen neu angelegt`}
                </p>
            )}

            {/* CreateArticleModal für unbekannte Positionen */}
            {artikelAnlegenFuer && (
                <CreateArticleModal
                    vorausfuellung={{ name: artikelAnlegenFuer.bezeichnung, externeNummer: artikelAnlegenFuer.externeArtikelnummer ?? undefined }}
                    onClose={() => setArtikelAnlegenFuer(null)}
                    onSave={artikel => artikelAngelegt(artikelAnlegenFuer, artikel)}
                />
            )}
        </div>
    );
}
