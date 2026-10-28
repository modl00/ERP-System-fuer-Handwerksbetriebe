import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
    Building2,
    Library,
    Calculator,
    ChevronRight,
    Wrench,
    Pencil,
    Plus,
    RefreshCw,
    Save,
    Search,
    Trash2,
    Eye,
    EyeOff,
    X
} from 'lucide-react';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { useToast } from '../components/ui/toast';
import { useConfirm } from '../components/ui/confirm-dialog';
import { parseErrorMessage } from '../components/settings/settingsApi';
import { StundensatzEditModal } from '../components/StundensatzEditModal';
import { ArbeitsgangFormelModal } from '../components/ArbeitsgangFormelModal';
import { ArbeitsgangMaschineModal } from '../components/ArbeitsgangMaschineModal';
import { useNavigate } from 'react-router-dom';
import { VerrechnungslohnRechnerDialog } from '../components/VerrechnungslohnRechnerDialog';
import { VorlagenUebernehmenDialog } from '../components/VorlagenUebernehmenDialog';
import { useOptionalAuth } from '../auth/AuthContext';
import { PageLayout } from '../components/layout/PageLayout';
import { cn } from '../lib/utils';
import { type Abteilung, type Arbeitsgang, type ArbeitsgangFormelFeld } from '../types';

const priceFormatter = new Intl.NumberFormat('de-DE', {
    style: 'currency',
    currency: 'EUR',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
});

const formatRate = (value: number | null) => {
    if (value === null) return '—';
    return priceFormatter.format(value);
};

/** Abteilungen werden nur noch von Admins in den Einstellungen gepflegt – hier nur der Weg dorthin. */
const AbteilungenVerwaltenHinweis = () => (
    <p className="text-sm text-slate-500">
        Abteilungen legen Sie unter{' '}
        <Link
            to="/einstellungen#berechtigungen"
            className="font-medium text-rose-700 underline-offset-2 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 rounded"
        >
            Einstellungen → Abteilungen &amp; Rechte
        </Link>
        {' '}an, benennen sie dort um oder löschen sie.
    </p>
);

// ==================== Abteilung List Component ====================
interface AbteilungListProps {
    abteilungen: Abteilung[];
    selectedId: number | null;
    onSelect: (id: number) => void;
}

const AbteilungList: React.FC<AbteilungListProps> = ({
    abteilungen,
    selectedId,
    onSelect,
}) => {
    if (!abteilungen.length) {
        return (
            <Card className="p-8 text-center text-slate-500 border-dashed space-y-2">
                <Building2 className="w-10 h-10 mx-auto text-rose-200" />
                <p className="font-medium text-slate-700">Keine Abteilungen vorhanden</p>
                <AbteilungenVerwaltenHinweis />
            </Card>
        );
    }

    return (
        <div className="space-y-2">
            {abteilungen.map((abteilung) => {
                const isSelected = selectedId === abteilung.id;
                return (
                    <div
                        key={abteilung.id}
                        className={cn(
                            'flex items-center justify-between gap-2 rounded-lg border px-3 py-2 cursor-pointer transition',
                            'border-slate-200 bg-white hover:border-rose-200 hover:shadow-sm',
                            isSelected ? 'border-rose-500 bg-rose-50 shadow-sm' : ''
                        )}
                        onClick={() => onSelect(abteilung.id)}
                    >
                        <div className="flex items-center gap-2 min-w-0">
                            <Building2 className="w-4 h-4 text-rose-600 flex-shrink-0" />
                            <span className="text-sm font-semibold text-slate-900 truncate">
                                {abteilung.name}
                            </span>
                        </div>
                        {isSelected && (
                            <ChevronRight className="w-4 h-4 text-rose-600 flex-shrink-0" />
                        )}
                    </div>
                );
            })}
        </div>
    );
};

// ==================== Arbeitsgang List Component ====================
/** Die Normzeiten als Kurztext, zum Beispiel "Rüsten 30 min · je Einheit 6,5 min". Leer, wenn keine Zeit angegeben ist. */
const zeitenKurz = (a: Arbeitsgang): string => {
    const teile: string[] = [];
    if (a.ruestzeitMinuten != null) teile.push(`Rüsten ${a.ruestzeitMinuten.toLocaleString('de-DE')} min`);
    if (a.bearbeitungszeitMinuten != null) teile.push(`je Einheit ${a.bearbeitungszeitMinuten.toLocaleString('de-DE')} min`);
    return teile.join(' · ');
};

interface ArbeitsgangListProps {
    arbeitsgaenge: Arbeitsgang[];
    onEditRate: (arbeitsgang: Arbeitsgang) => void;
    onEditFormel: (arbeitsgang: Arbeitsgang) => void;
    onEditMaschine: (arbeitsgang: Arbeitsgang) => void;
    onArchivieren: (arbeitsgang: Arbeitsgang) => void;
    onDelete: (id: number) => void;
}

const ArbeitsgangList: React.FC<ArbeitsgangListProps> = ({
    arbeitsgaenge,
    onEditRate,
    onEditFormel,
    onEditMaschine,
    onArchivieren,
    onDelete
}) => {
    const currentYear = new Date().getFullYear();

    if (!arbeitsgaenge.length) {
        return (
            <Card className="p-10 text-center text-slate-500">
                <Plus className="w-10 h-10 mx-auto mb-3 text-rose-200" />
                Keine Arbeitsgänge in dieser Abteilung
            </Card>
        );
    }

    return (
        <div className="space-y-2">
            {arbeitsgaenge.map((arbeitsgang) => {
                const isOutdated = arbeitsgang.stundensatzJahr !== null &&
                    currentYear - arbeitsgang.stundensatzJahr >= 1;
                const hasNoRate = arbeitsgang.stundensatz === null;

                return (
                    <Card
                        key={arbeitsgang.id}
                        className={`p-3 hover:border-rose-200 hover:shadow-sm transition ${arbeitsgang.archiviert ? 'bg-slate-50 opacity-75' : ''}`}
                    >
                        <div className="flex items-center justify-between gap-3">
                            <div className="min-w-0 flex-1">
                                <div className="flex items-center gap-2">
                                    <p className="text-sm font-semibold text-slate-900 truncate">
                                        {arbeitsgang.beschreibung}
                                    </p>
                                    {arbeitsgang.archiviert && (
                                        <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-slate-200 text-slate-700" data-testid={`ausgeblendet-${arbeitsgang.id}`}>
                                            ausgeblendet
                                        </span>
                                    )}
                                    {(isOutdated || hasNoRate) && (
                                        <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-orange-100 text-orange-700">
                                            Kalkulationssatz veraltet
                                        </span>
                                    )}
                                </div>
                                <p className="text-sm text-rose-700 mt-0.5">
                                    {formatRate(arbeitsgang.stundensatz)}
                                    <span className="text-slate-400 mx-1">/</span>
                                    <span className="text-slate-600">Stunde</span>
                                    {arbeitsgang.stundensatzJahr && (
                                        <span className="text-xs text-slate-400 ml-2">
                                            ({arbeitsgang.stundensatzJahr})
                                        </span>
                                    )}
                                </p>
                                {arbeitsgang.satzQuelle === 'VERRECHNUNG' && (
                                    <p className="text-xs text-rose-700 mt-0.5" data-testid={`satzquelle-${arbeitsgang.id}`}>
                                        Satz folgt dem Verrechnungssatz ({arbeitsgang.satzGruppe ? ({ AUSZUBILDENDER: 'Auszubildender', FACHARBEITER: 'Facharbeiter', MEISTER: 'Meister' } as Record<string, string>)[arbeitsgang.satzGruppe] ?? arbeitsgang.satzGruppe : 'ganzer Betrieb'})
                                    </p>
                                )}
                                <p className="text-xs text-slate-500 mt-0.5" data-testid={`zeiten-${arbeitsgang.id}`}>
                                    {arbeitsgang.formelBearbeitung ? `Zeit aus Formel: ${arbeitsgang.formelBearbeitung}` : (zeitenKurz(arbeitsgang) || 'Noch keine Zeiten (Rüstzeit, Bearbeitungszeit)')}
                                </p>
                                <p className="text-xs text-slate-500 mt-0.5" data-testid={`maschine-${arbeitsgang.id}`}>
                                    {arbeitsgang.maschinenStundensatz != null
                                        ? `${arbeitsgang.maschineId != null ? 'Maschine aus der Maschinenliste' : 'Maschine'}: ${arbeitsgang.maschinenBezeichnung ? `${arbeitsgang.maschinenBezeichnung}, ` : ''}${formatRate(arbeitsgang.maschinenStundensatz)} je Maschinenstunde`
                                        : 'Keine Maschine'}
                                </p>
                            </div>
                            <div className="flex items-center gap-1 flex-shrink-0">
                                <Button
                                    variant="ghost"
                                    size="sm"
                                    className="text-rose-700 hover:bg-rose-100"
                                    onClick={() => onEditMaschine(arbeitsgang)}
                                    title={arbeitsgang.maschineId != null ? 'Die Maschine steht in der Kostenrechnung: dort Satz und Zuordnung ändern' : 'Maschine und Maschinenstundensatz bearbeiten'}
                                    aria-label={`Maschine von ${arbeitsgang.beschreibung} bearbeiten`}
                                >
                                    <Wrench className="w-4 h-4" />
                                </Button>
                                <Button
                                    variant="ghost"
                                    size="sm"
                                    className="text-rose-700 hover:bg-rose-100"
                                    onClick={() => onEditFormel(arbeitsgang)}
                                    title="Zeit mit Formel berechnen (Eingabefelder wie Durchmesser und Länge)"
                                    aria-label={`Formel von ${arbeitsgang.beschreibung} bearbeiten`}
                                >
                                    <Calculator className="w-4 h-4" />
                                </Button>
                                <Button
                                    variant="ghost"
                                    size="sm"
                                    className="text-rose-700 hover:bg-rose-100"
                                    onClick={() => onEditRate(arbeitsgang)}
                                    title="Kalkulationssatz bearbeiten"
                                >
                                    <Pencil className="w-4 h-4" />
                                </Button>
                                <Button
                                    variant="ghost"
                                    size="sm"
                                    className="text-rose-700 hover:bg-rose-100"
                                    onClick={() => onArchivieren(arbeitsgang)}
                                    title={arbeitsgang.archiviert ? 'Wieder einblenden: erscheint wieder in der Auswahl' : 'Ausblenden: erscheint nicht mehr in der Auswahl, nichts wird gelöscht'}
                                    aria-label={`${arbeitsgang.beschreibung} ${arbeitsgang.archiviert ? 'wieder einblenden' : 'ausblenden'}`}
                                >
                                    {arbeitsgang.archiviert ? <Eye className="w-4 h-4" /> : <EyeOff className="w-4 h-4" />}
                                </Button>
                                <Button
                                    variant="ghost"
                                    size="sm"
                                    className="text-rose-700 hover:bg-rose-100"
                                    onClick={() => onDelete(arbeitsgang.id)}
                                    title="Löschen"
                                    aria-label={`${arbeitsgang.beschreibung} löschen`}
                                >
                                    <Trash2 className="w-4 h-4" />
                                </Button>
                            </div>
                        </div>
                    </Card>
                );
            })}
        </div>
    );
};

// ==================== Main Component ====================
export default function ArbeitsgangEditor() {
    const navigate = useNavigate();
    const toast = useToast();
    const confirmDialog = useConfirm();
    const [abteilungen, setAbteilungen] = useState<Abteilung[]>([]);
    const [arbeitsgaenge, setArbeitsgaenge] = useState<Arbeitsgang[]>([]);
    const [selectedAbteilungId, setSelectedAbteilungId] = useState<number | null>(null);
    const [loading, setLoading] = useState(true);
    const [newBeschreibung, setNewBeschreibung] = useState('');
    const [creatingArbeitsgang, setCreatingArbeitsgang] = useState(false);

    // Modal state
    const [editingArbeitsgang, setEditingArbeitsgang] = useState<Arbeitsgang | null>(null);
    const [editingFormel, setEditingFormel] = useState<Arbeitsgang | null>(null);
    const [editingMaschine, setEditingMaschine] = useState<Arbeitsgang | null>(null);
    const [rechnerOpen, setRechnerOpen] = useState(false);
    const [vorlagenOpen, setVorlagenOpen] = useState(false);
    const istAdmin = useOptionalAuth()?.isAdmin === true;

    // Search
    const [searchTerm, setSearchTerm] = useState('');
    // Ausgeblendete Arbeitsgänge (#235) stehen nur auf Wunsch in der Liste
    const [zeigeAusgeblendete, setZeigeAusgeblendete] = useState(false);

    // Filtered arbeitsgaenge for selected abteilung + search
    const filteredArbeitsgaenge = useMemo(() => {
        if (!selectedAbteilungId) return [];
        let filtered = arbeitsgaenge.filter(a => a.abteilungId === selectedAbteilungId && (zeigeAusgeblendete || !a.archiviert));
        if (searchTerm.trim()) {
            const term = searchTerm.toLowerCase();
            filtered = filtered.filter(a =>
                a.beschreibung.toLowerCase().includes(term)
            );
        }
        return filtered;
    }, [arbeitsgaenge, selectedAbteilungId, searchTerm, zeigeAusgeblendete]);
    const anzahlAusgeblendet = useMemo(() => arbeitsgaenge.filter(a => a.abteilungId === selectedAbteilungId && a.archiviert).length, [arbeitsgaenge, selectedAbteilungId]);

    // Selected abteilung
    const selectedAbteilung = useMemo(() => {
        return abteilungen.find(a => a.id === selectedAbteilungId) || null;
    }, [abteilungen, selectedAbteilungId]);

    // Load data – Ergebnisse erst in den Promise-Callbacks übernehmen, nicht synchron im Effekt.
    const ladeDaten = useCallback(() =>
        Promise.all([
            fetch('/api/abteilungen'),
            fetch('/api/arbeitsgaenge')
        ])
            .then(async ([abtRes, agRes]) => {
                if (abtRes.ok) {
                    const data = await abtRes.json();
                    setAbteilungen(Array.isArray(data) ? data : []);
                }
                if (agRes.ok) {
                    const data = await agRes.json();
                    setArbeitsgaenge(Array.isArray(data) ? data : []);
                }
            })
            .catch((err: unknown) => {
                console.error('Fehler beim Laden:', err);
            })
            .finally(() => {
                setLoading(false);
            }),
    []);

    /** Neu laden nach einer Änderung bzw. auf Knopfdruck – mit Ladehinweis. */
    const loadData = useCallback(() => {
        setLoading(true);
        return ladeDaten();
    }, [ladeDaten]);

    useEffect(() => {
        void ladeDaten();
    }, [ladeDaten]);

    // Auto-select first abteilung (auch wenn die gewählte nicht mehr existiert)
    if (abteilungen.length > 0 && (selectedAbteilungId === null || !abteilungen.some(a => a.id === selectedAbteilungId))) {
        setSelectedAbteilungId(abteilungen[0].id);
    }

    // Create Arbeitsgang
    const handleCreateArbeitsgang = async () => {
        if (!newBeschreibung.trim() || !selectedAbteilungId) return;
        setCreatingArbeitsgang(true);
        try {
            const res = await fetch('/api/arbeitsgaenge', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    beschreibung: newBeschreibung.trim(),
                    abteilungId: selectedAbteilungId
                })
            });
            if (res.ok) {
                setNewBeschreibung('');
                await loadData();
            } else {
                toast.error('Fehler beim Erstellen des Arbeitsgangs.');
            }
        } catch (err) {
            console.error(err);
            toast.error('Fehler beim Erstellen des Arbeitsgangs.');
        } finally {
            setCreatingArbeitsgang(false);
        }
    };

    // Delete Arbeitsgang
    const handleDeleteArbeitsgang = async (id: number) => {
        if (!await confirmDialog({ title: 'Arbeitsgang löschen', message: 'Möchten Sie diesen Arbeitsgang wirklich löschen?', variant: 'danger', confirmLabel: 'Löschen' })) return;
        try {
            const res = await fetch(`/api/arbeitsgaenge/${id}`, { method: 'DELETE' });
            if (res.ok) {
                await loadData();
            } else if (res.status === 409) {
                // Benutzt: Der Grund steht in der Meldung; als Lösung wird das Ausblenden angeboten
                const grund = await parseErrorMessage(res, 'Der Arbeitsgang wird noch benutzt und lässt sich nicht löschen.');
                if (await confirmDialog({ title: 'Arbeitsgang ausblenden?', message: grund, variant: 'warning', confirmLabel: 'Ausblenden' })) {
                    await setzeArchiviert(id, true);
                }
            } else {
                toast.error(await parseErrorMessage(res, 'Der Arbeitsgang konnte nicht gelöscht werden.'));
            }
        } catch (err) {
            console.error(err);
            toast.error('Der Arbeitsgang konnte nicht gelöscht werden. Ist der Server erreichbar?');
        }
    };

    // Ausblenden oder wieder einblenden (löscht nichts)
    const setzeArchiviert = async (id: number, archiviert: boolean) => {
        try {
            const res = await fetch(`/api/arbeitsgaenge/${id}/archiviert`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ archiviert }),
            });
            if (!res.ok) throw new Error(await parseErrorMessage(res, 'Das Ausblenden hat nicht geklappt.'));
            toast.success(archiviert ? 'Der Arbeitsgang ist ausgeblendet. Er steht nicht mehr in der Auswahl, alles Bestehende bleibt.' : 'Der Arbeitsgang steht wieder in der Auswahl.');
            await loadData();
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Das Ausblenden hat nicht geklappt.');
        }
    };

    // Update Stundensatz
    const handleSaveStundensatz = async (arbeitsgangId: number, neuerStundensatz: number) => {
        const res = await fetch(`/api/arbeitsgaenge/${arbeitsgangId}/stundensatz`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ stundensatz: neuerStundensatz })
        });
        if (!res.ok) {
            throw new Error('Speichern fehlgeschlagen');
        }
        await loadData();
    };

    // Woher der Kalkulationssatz kommt (von Hand oder Verrechnungssatz); ändert selbst keinen Satz
    const handleSaveSatzQuelle = async (arbeitsgangId: number, quelle: 'EIGEN' | 'VERRECHNUNG', gruppe: string | null) => {
        const res = await fetch(`/api/arbeitsgaenge/${arbeitsgangId}/satzquelle`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ quelle, gruppe })
        });
        if (!res.ok) {
            throw new Error('Speichern fehlgeschlagen');
        }
        await loadData();
    };

    // Formel (Eingabefelder, Bearbeitungs- und Rüstzeit-Formel) speichern; eine leere Bearbeitungsformel entfernt sie
    const handleSaveFormel = async (arbeitsgangId: number, formel: { felder: ArbeitsgangFormelFeld[]; bearbeitungFormel: string; ruestFormel: string }) => {
        const res = await fetch(`/api/arbeitsgaenge/${arbeitsgangId}/formel`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(formel)
        });
        if (!res.ok) {
            let text = 'Die Formel konnte nicht gespeichert werden.';
            try { const body = await res.json() as { message?: string }; if (body?.message) text = body.message; } catch { /* kein JSON */ }
            throw new Error(text);
        }
        toast.success(formel.bearbeitungFormel.trim() === '' ? 'Formel entfernt.' : 'Formel gespeichert.');
        await loadData();
    };

    // Maschine (Name und Kosten je Stunde) speichern; ohne Satz entfällt sie
    const handleSaveMaschine = async (arbeitsgangId: number, bezeichnung: string, stundensatz: number | null) => {
        const res = await fetch(`/api/arbeitsgaenge/${arbeitsgangId}/maschine`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ bezeichnung, stundensatz })
        });
        if (!res.ok) {
            let text = 'Die Maschine konnte nicht gespeichert werden.';
            try { const body = await res.json() as { message?: string }; if (body?.message) text = body.message; } catch { /* kein JSON */ }
            throw new Error(text);
        }
        toast.success(stundensatz == null ? 'Die Maschine wurde entfernt.' : 'Maschine gespeichert.');
        await loadData();
    };

    return (

        <PageLayout
            ribbonCategory="Arbeitsplanung"
            title="Arbeitsgänge"
            subtitle="Arbeitsgänge je Abteilung mit Kalkulationssatz (€/h), Rüstzeit und Bearbeitungszeit. Der Kalkulationssatz berechnet den Preis von Leistungen und bewertet gebuchte Arbeitszeit in der Nachkalkulation. Die Sätze für die Abrechnung nach Stunden stehen unter Stammdaten, Stundensätze (Abrechnung)."
            actions={
                <>
                    <Button
                        size="sm"
                        className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
                        onClick={() => setRechnerOpen(true)}
                    >
                        <Calculator className="w-4 h-4 mr-2" />
                        Was muss meine Stunde kosten?
                    </Button>
                    {istAdmin && (
                        <Button variant="outline" size="sm" className="border-rose-300 text-rose-700 hover:bg-rose-50" onClick={() => setVorlagenOpen(true)}>
                            <Library className="w-4 h-4 mr-2" />
                            Aus Vorlage ergänzen
                        </Button>
                    )}
                    <Button variant="outline" size="sm" onClick={loadData} disabled={loading}>
                        <RefreshCw className={cn("w-4 h-4 mr-2", loading && "animate-spin")} />
                        Aktualisieren
                    </Button>
                </>
            }
        >

            {/* Search Bar */}
            <Card className="p-4 border-0 shadow-sm rounded-xl">
                <div className="relative">
                    <Search className="absolute left-3 top-2.5 w-4 h-4 text-slate-400" />
                    <Input
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                        placeholder="Arbeitsgänge durchsuchen..."
                        className="pl-9"
                    />
                </div>
            </Card>

            {/* 3-Column Grid */}
            <div className="grid grid-cols-1 xl:grid-cols-[0.9fr_0.9fr_2.2fr] gap-6">
                {/* Column 1: Abteilungen */}
                <Card className="p-6 border-0 shadow-sm rounded-xl">
                    <div className="flex items-center justify-between mb-4">
                        <div>
                            <p className="text-xs uppercase tracking-wide text-slate-500">Abteilungen</p>
                            <h4 className="text-lg font-semibold text-slate-900">Struktur</h4>
                        </div>
                    </div>

                    {loading ? (
                        <div className="text-slate-500 text-sm py-6">Wird geladen...</div>
                    ) : (
                        <AbteilungList
                            abteilungen={abteilungen}
                            selectedId={selectedAbteilungId}
                            onSelect={setSelectedAbteilungId}
                        />
                    )}

                    {!loading && abteilungen.length > 0 && (
                        <div className="mt-4 pt-4 border-t border-slate-100">
                            <AbteilungenVerwaltenHinweis />
                        </div>
                    )}
                </Card>

                {/* Column 2: Arbeitsgänge List */}
                <Card className="p-6 border-0 shadow-sm rounded-xl">
                    <div className="flex items-center justify-between mb-4">
                        <div>
                            <p className="text-xs uppercase tracking-wide text-slate-500">Arbeitsgänge</p>
                            <h4 className="text-lg font-semibold text-slate-900">
                                {selectedAbteilung?.name || 'Auswählen'}
                            </h4>
                        </div>
                        {(anzahlAusgeblendet > 0 || zeigeAusgeblendete) && (
                            <label className="flex items-center gap-2 text-sm text-slate-600 cursor-pointer select-none">
                                <input type="checkbox" checked={zeigeAusgeblendete} onChange={e => setZeigeAusgeblendete(e.target.checked)}
                                    className="h-4 w-4 rounded border-slate-300 text-rose-600 focus:ring-rose-500" />
                                Ausgeblendete zeigen ({anzahlAusgeblendet})
                            </label>
                        )}
                    </div>

                    {selectedAbteilungId ? (
                        <ArbeitsgangList
                            arbeitsgaenge={filteredArbeitsgaenge}
                            onEditRate={(ag) => setEditingArbeitsgang(ag)}
                            onEditFormel={(ag) => setEditingFormel(ag)}
                            // Hat der Arbeitsgang eine Maschine aus der Maschinenliste, wird sie dort gepflegt (Kostenrechnung), nicht am Arbeitsgang
                            onEditMaschine={(ag) => (ag.maschineId != null ? navigate('/kostenrechnung') : setEditingMaschine(ag))}
                            onArchivieren={(ag) => void setzeArchiviert(ag.id, !ag.archiviert)}
                            onDelete={handleDeleteArbeitsgang}
                        />
                    ) : (
                        <Card className="p-10 text-center text-slate-500 border-dashed">
                            Wählen Sie eine Abteilung aus
                        </Card>
                    )}
                </Card>

                {/* Column 3: Create Form */}
                <div className="min-h-full">
                    {selectedAbteilungId ? (
                        <Card className="border-0 shadow-sm rounded-xl">
                            <div className="p-4 space-y-4">
                                <div className="flex items-center justify-between gap-3">
                                    <div>
                                        <p className="text-xs uppercase tracking-wide text-slate-500">Neuanlage</p>
                                        <h3 className="text-xl font-semibold text-slate-900">Neuer Arbeitsgang</h3>
                                    </div>
                                    <span className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-rose-50 text-rose-700 border border-rose-100">
                                        {selectedAbteilung?.name}
                                    </span>
                                </div>

                                <div className="space-y-2">
                                    <Label htmlFor="beschreibung">Bezeichnung</Label>
                                    <Input
                                        id="beschreibung"
                                        value={newBeschreibung}
                                        onChange={(e) => setNewBeschreibung(e.target.value)}
                                        placeholder="z.B. Montage Metallfassade"
                                        onKeyDown={(e) => e.key === 'Enter' && handleCreateArbeitsgang()}
                                    />
                                </div>

                                <div className="rounded-xl border border-dashed border-slate-300 p-4 bg-slate-50">
                                    <h4 className="text-sm font-semibold text-slate-900 mb-2">Hinweis</h4>
                                    <p className="text-sm text-slate-600 leading-relaxed flex items-center gap-1">
                                        Nach dem Erstellen können Sie mit dem Stift-Icon
                                        <Pencil className="w-3.5 h-3.5 inline text-rose-600" />
                                        den Kalkulationssatz (€/h) für das aktuelle Jahr festlegen.
                                    </p>
                                </div>

                                <div className="flex gap-3 pt-2">
                                    <Button
                                        className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
                                        size="sm"
                                        onClick={handleCreateArbeitsgang}
                                        disabled={!newBeschreibung.trim() || creatingArbeitsgang}
                                    >
                                        <Save className="w-4 h-4" />
                                        {creatingArbeitsgang ? 'Erstellt...' : 'Erstellen'}
                                    </Button>
                                    <Button
                                        variant="outline"
                                        size="sm"
                                        onClick={() => setNewBeschreibung('')}
                                    >
                                        <X className="w-4 h-4" /> Abbrechen
                                    </Button>
                                </div>
                            </div>
                        </Card>
                    ) : (
                        <Card className="p-10 h-full border-dashed border-slate-200 text-center text-slate-500">
                            Wählen Sie eine Abteilung aus, um einen neuen Arbeitsgang anzulegen.
                        </Card>
                    )}
                </div>
            </div>

            {/* Stundensatz Edit Modal */}
            {editingArbeitsgang && (
                <StundensatzEditModal
                    arbeitsgang={editingArbeitsgang}
                    isOpen={!!editingArbeitsgang}
                    onClose={() => setEditingArbeitsgang(null)}
                    onSave={handleSaveStundensatz}
                    onSaveQuelle={handleSaveSatzQuelle}
                />
            )}

            {/* Rüstzeit und Bearbeitungszeit */}
            {editingMaschine && (
                <ArbeitsgangMaschineModal
                    arbeitsgang={editingMaschine}
                    isOpen={!!editingMaschine}
                    onClose={() => setEditingMaschine(null)}
                    onSave={handleSaveMaschine}
                />
            )}

            {editingFormel && (
                <ArbeitsgangFormelModal
                    arbeitsgang={editingFormel}
                    isOpen={!!editingFormel}
                    onClose={() => setEditingFormel(null)}
                    onSave={handleSaveFormel}
                />
            )}

            {/* Verrechnungslohn-Rechner */}
            <VorlagenUebernehmenDialog art="arbeitsgaenge" offen={vorlagenOpen} onOpenChange={setVorlagenOpen} onFertig={() => void loadData()} />
            <VerrechnungslohnRechnerDialog
                open={rechnerOpen}
                onClose={() => setRechnerOpen(false)}
                onApplied={() => loadData()}
            />
        </PageLayout>
    );
}
