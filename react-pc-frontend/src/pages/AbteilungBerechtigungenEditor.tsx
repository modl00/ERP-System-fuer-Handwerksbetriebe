import { useState, useEffect, useCallback, useRef } from 'react';
import { PageLayout } from '../components/layout/PageLayout';
import { RechteReiter } from '../features/rechte/RechteReiter';
import { Card } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { useToast } from '../components/ui/toast';
import { useConfirm } from '../components/ui/confirm-dialog';
import { FunktionsrechteBereich } from '../features/funktionsrechte/FunktionsrechteBereich';
import { CalendarCheck, Shield, Users, Save, Loader2, Check, Eye, FileText, Wallet, Bell, Phone, Plus, Trash2 } from 'lucide-react';

interface TypBerechtigung {
    typ: string;
    darfSehen: boolean;
    darfScannen: boolean;
}

interface AbteilungBerechtigung {
    abteilungId: number;
    abteilungName: string;
    berechtigungen: TypBerechtigung[];
    darfMonatAbschliessen: boolean;
    darfTelefonSehen: boolean;
    darfRechnungenGenehmigen: boolean;
    darfRechnungenSehen: boolean;
    darfFreigabeAnnahmePushen: boolean;
    darfWebseitenAnfragenPushen: boolean;
}

const DOKUMENT_TYP_LABELS: Record<string, string> = {
    'ANFRAGE': 'Anfrage',
    'AUFTRAGSBESTAETIGUNG': 'Auftragsbestätigung',
    'LIEFERSCHEIN': 'Lieferschein',
    'RECHNUNG': 'Rechnung',
    'BELEG': 'Belege & Kasse'
};

export default function AbteilungBerechtigungenEditor() {
    const toast = useToast();
    const confirm = useConfirm();
    // Stabile Referenz, damit loadBerechtigungen keine wechselnde Abhaengigkeit bekommt.
    const toastRef = useRef(toast);
    toastRef.current = toast;
    const [loadError, setLoadError] = useState(false);
    const [berechtigungen, setBerechtigungen] = useState<AbteilungBerechtigung[]>([]);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState<number | null>(null);
    const [saveSuccess, setSaveSuccess] = useState<number | null>(null);
    const [neuerName, setNeuerName] = useState('');
    const [anlegenLaeuft, setAnlegenLaeuft] = useState(false);
    const [loeschenLaeuft, setLoeschenLaeuft] = useState<number | null>(null);

    const loadBerechtigungen = useCallback(async () => {
        setLoading(true);
        setLoadError(false);
        try {
            const res = await fetch('/api/abteilungen/berechtigungen');
            if (!res.ok) throw new Error('Berechtigungen konnten nicht geladen werden.');
            const data = await res.json();
            setBerechtigungen(data.map((abt: AbteilungBerechtigung) => ({
                ...abt,
                darfMonatAbschliessen: abt.darfMonatAbschliessen === true,
                darfTelefonSehen: abt.darfTelefonSehen === true,
            })));
        } catch (err) {
            setLoadError(true);
            toastRef.current.error(err instanceof Error ? err.message : 'Berechtigungen konnten nicht geladen werden.');
        }
        setLoading(false);
    }, []);

    useEffect(() => {
        loadBerechtigungen();
    }, [loadBerechtigungen]);

    const handleAbteilungAnlegen = async () => {
        const name = neuerName.trim();
        if (!name) return;
        setAnlegenLaeuft(true);
        try {
            const res = await fetch('/api/abteilungen', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name }),
            });
            if (!res.ok) throw new Error('Abteilung konnte nicht angelegt werden.');
            toast.success(`Abteilung „${name}" angelegt.`);
            setNeuerName('');
            await loadBerechtigungen();
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Abteilung konnte nicht angelegt werden.');
        }
        setAnlegenLaeuft(false);
    };

    const handleAbteilungLoeschen = async (abteilungId: number, abteilungName: string) => {
        const ok = await confirm({
            title: 'Abteilung löschen?',
            message: `Abteilung „${abteilungName}" dauerhaft löschen? Abteilungen mit Arbeitsgängen können nicht gelöscht werden.`,
            confirmLabel: 'Löschen',
            variant: 'danger',
        });
        if (!ok) return;
        setLoeschenLaeuft(abteilungId);
        try {
            const res = await fetch(`/api/abteilungen/${abteilungId}`, { method: 'DELETE' });
            if (res.status === 409) throw new Error(`„${abteilungName}" hat noch Arbeitsgänge und kann nicht gelöscht werden.`);
            if (!res.ok) throw new Error('Abteilung konnte nicht gelöscht werden.');
            toast.success(`Abteilung „${abteilungName}" gelöscht.`);
            await loadBerechtigungen();
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Abteilung konnte nicht gelöscht werden.');
        }
        setLoeschenLaeuft(null);
    };

    const handleToggle = (abteilungId: number, typ: string, field: 'darfSehen' | 'darfScannen') => {
        setBerechtigungen(prev => prev.map(abt => {
            if (abt.abteilungId !== abteilungId) return abt;
            return {
                ...abt,
                berechtigungen: abt.berechtigungen.map(b => {
                    if (b.typ !== typ) return b;
                    const newValue = !b[field];
                    // Wenn darfScannen aktiviert wird, auch darfSehen aktivieren
                    if (field === 'darfScannen' && newValue) {
                        return { ...b, darfScannen: true, darfSehen: true };
                    }
                    // Wenn darfSehen deaktiviert wird, auch darfScannen deaktivieren
                    if (field === 'darfSehen' && !newValue) {
                        return { ...b, darfSehen: false, darfScannen: false };
                    }
                    return { ...b, [field]: newValue };
                })
            };
        }));
    };

    const handleToggleRechnungsFlag = (abteilungId: number, field: 'darfRechnungenGenehmigen' | 'darfRechnungenSehen' | 'darfMonatAbschliessen' | 'darfTelefonSehen') => {
        setBerechtigungen(prev => prev.map(abt => {
            if (abt.abteilungId !== abteilungId) return abt;
            return { ...abt, [field]: !abt[field] };
        }));
    };

    const handleTogglePushFlag = (abteilungId: number, field: 'darfFreigabeAnnahmePushen' | 'darfWebseitenAnfragenPushen') => {
        setBerechtigungen(prev => prev.map(abt => {
            if (abt.abteilungId !== abteilungId) return abt;
            return { ...abt, [field]: !abt[field] };
        }));
    };

    const handleSave = async (abteilung: AbteilungBerechtigung) => {
        setSaving(abteilung.abteilungId);
        try {
            const res = await fetch(`/api/abteilungen/${abteilung.abteilungId}/berechtigungen`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    berechtigungen: abteilung.berechtigungen,
                    darfMonatAbschliessen: abteilung.darfMonatAbschliessen,
                    darfTelefonSehen: abteilung.darfTelefonSehen,
                    darfRechnungenGenehmigen: abteilung.darfRechnungenGenehmigen,
                    darfRechnungenSehen: abteilung.darfRechnungenSehen,
                    darfFreigabeAnnahmePushen: abteilung.darfFreigabeAnnahmePushen,
                    darfWebseitenAnfragenPushen: abteilung.darfWebseitenAnfragenPushen
                })
            });
            if (!res.ok) throw new Error(res.status === 403 ? 'Nur Administratoren dürfen Berechtigungen ändern.' : 'Berechtigungen konnten nicht gespeichert werden.');
            if (res.ok) {
                toast.success('Berechtigungen gespeichert.');
                setSaveSuccess(abteilung.abteilungId);
                setTimeout(() => setSaveSuccess(null), 2000);
            }
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Berechtigungen konnten nicht gespeichert werden.');
        }
        setSaving(null);
    };

    if (loading) {
        return (
            <PageLayout>
                <div className="flex justify-center py-12">
                    <Loader2 className="w-8 h-8 animate-spin text-rose-600" />
                </div>
            </PageLayout>
        );
    }

    return (
        <PageLayout>
            <RechteReiter />
            {/* Header */}
            <div className="flex flex-col md:flex-row justify-between gap-4 md:items-end mb-8">
                <div>
                    <p className="text-sm font-semibold text-rose-600 uppercase tracking-wide">
                        Administration
                    </p>
                    <h1 className="text-3xl font-bold text-slate-900">
                        RECHTEVERWALTUNG
                    </h1>
                    <p className="text-slate-500 mt-1">
                        Rechte pro Abteilung: Funktionen in Programm und Handy-App, Lieferanten-Dokumente, Monatsabschluss und Telefon
                    </p>
                </div>
            </div>

            {/* Abteilungsverwaltung */}
            <Card className="mb-8 p-6">
                <div className="flex items-center gap-3 mb-4">
                    <div className="w-9 h-9 bg-rose-100 rounded-lg flex items-center justify-center">
                        <Users className="w-5 h-5 text-rose-600" />
                    </div>
                    <div>
                        <h2 className="text-lg font-bold text-slate-900">Abteilungen</h2>
                        <p className="text-sm text-slate-500">Werkstatt, Büro, Montage und eigene Abteilungen verwalten</p>
                    </div>
                </div>
                <ul className="mb-4 divide-y divide-slate-100">
                    {berechtigungen.map(abt => (
                        <li key={abt.abteilungId} className="flex items-center justify-between py-2">
                            <span className="text-sm font-medium text-slate-900">{abt.abteilungName}</span>
                            <button
                                type="button"
                                title={`„${abt.abteilungName}" löschen`}
                                aria-label={`Abteilung „${abt.abteilungName}" löschen`}
                                disabled={loeschenLaeuft === abt.abteilungId}
                                onClick={() => void handleAbteilungLoeschen(abt.abteilungId, abt.abteilungName)}
                                className="inline-flex h-8 w-8 items-center justify-center rounded-md text-slate-400 hover:bg-rose-50 hover:text-rose-600 disabled:opacity-40 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
                            >
                                {loeschenLaeuft === abt.abteilungId
                                    ? <Loader2 className="w-4 h-4 animate-spin" aria-hidden="true" />
                                    : <Trash2 className="w-4 h-4" aria-hidden="true" />}
                            </button>
                        </li>
                    ))}
                    {berechtigungen.length === 0 && !loading && (
                        <li className="py-3 text-sm text-slate-400">Noch keine Abteilungen vorhanden.</li>
                    )}
                </ul>
                <form
                    onSubmit={e => { e.preventDefault(); void handleAbteilungAnlegen(); }}
                    className="flex gap-2"
                >
                    <input
                        type="text"
                        value={neuerName}
                        onChange={e => setNeuerName(e.target.value)}
                        placeholder="Name der neuen Abteilung"
                        maxLength={80}
                        className="flex-1 rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-rose-400 focus:outline-none focus:ring-2 focus:ring-rose-500"
                    />
                    <Button
                        type="submit"
                        size="sm"
                        disabled={anlegenLaeuft || !neuerName.trim()}
                        className="bg-rose-600 text-white hover:bg-rose-700"
                    >
                        {anlegenLaeuft ? <Loader2 className="w-4 h-4 animate-spin mr-1" aria-hidden="true" /> : <Plus className="w-4 h-4 mr-1" aria-hidden="true" />}
                        Anlegen
                    </Button>
                </form>
            </Card>

            <FunktionsrechteBereich />

            <h2 className="mb-3 text-xl font-bold text-slate-900">Lieferanten-Dokumentenrechte und weitere Rechte</h2>

            {/* Info Card */}
            <Card className="mb-6 p-4 bg-blue-50 border-blue-200">
                <div className="flex items-start gap-3">
                    <Shield className="w-5 h-5 text-blue-600 mt-0.5" />
                    <div>
                        <p className="text-blue-900 font-medium">Berechtigungen</p>
                        <p className="text-blue-700 text-sm">
                            <strong>Sehen</strong>: Mitarbeiter kann diesen Dokumenttyp in der App sehen.<br />
                            <strong>Scannen</strong>: Mitarbeiter kann diesen Dokumenttyp hochladen. Setzt "Sehen" voraus.
                        </p>
                    </div>
                </div>
            </Card>

            {loadError && <div role="alert" className="mb-6 rounded-lg border border-rose-200 bg-rose-50 p-4 text-rose-800">
                Berechtigungen konnten nicht geladen werden.
                <Button variant="outline" size="sm" onClick={loadBerechtigungen} className="ml-4">Erneut laden</Button>
            </div>}
            {/* Berechtigungen per Abteilung */}
            <div className="space-y-6">
                {berechtigungen.map(abt => (
                    <Card key={abt.abteilungId} className="p-6">
                        <div className="flex items-center justify-between mb-4">
                            <div className="flex items-center gap-3">
                                <div className="w-10 h-10 bg-rose-100 rounded-lg flex items-center justify-center">
                                    <Users className="w-5 h-5 text-rose-600" />
                                </div>
                                <h2 className="text-xl font-bold text-slate-900">{abt.abteilungName}</h2>
                            </div>
                            <Button
                                onClick={() => handleSave(abt)}
                                disabled={saving === abt.abteilungId}
                                className="bg-rose-600 hover:bg-rose-700 text-white"
                            >
                                {saving === abt.abteilungId ? (
                                    <Loader2 className="w-4 h-4 animate-spin mr-2" />
                                ) : saveSuccess === abt.abteilungId ? (
                                    <Check className="w-4 h-4 mr-2" />
                                ) : (
                                    <Save className="w-4 h-4 mr-2" />
                                )}
                                {saveSuccess === abt.abteilungId ? 'Gespeichert' : 'Speichern'}
                            </Button>
                        </div>
                        <div className="mb-5 rounded-lg border border-slate-200 bg-slate-50 p-4">
                            <label className="flex cursor-pointer items-start gap-3">
                                <input type="checkbox" checked={abt.darfMonatAbschliessen}
                                    disabled={saving === abt.abteilungId}
                                    onChange={() => handleToggleRechnungsFlag(abt.abteilungId, 'darfMonatAbschliessen')}
                                    className="mt-1 h-4 w-4 shrink-0 accent-rose-600 focus:ring-2 focus:ring-rose-500" />
                                <span>
                                    <span className="flex items-center gap-2 text-sm font-semibold text-slate-900"><CalendarCheck aria-hidden="true" className="h-4 w-4 text-rose-600" />Monate abschließen und wieder öffnen</span>
                                    <span className="mt-1 block text-sm text-slate-600">Mitarbeiter dieser Abteilung dürfen Monatsstände prüfen und festhalten. Gilt auch für Administratoren nur mit diesem Recht.</span>
                                </span>
                            </label>
                            <label className="mt-4 flex cursor-pointer items-start gap-3 border-t border-slate-200 pt-4">
                                <input type="checkbox" checked={abt.darfTelefonSehen}
                                    disabled={saving === abt.abteilungId}
                                    onChange={() => handleToggleRechnungsFlag(abt.abteilungId, 'darfTelefonSehen')}
                                    className="mt-1 h-4 w-4 shrink-0 accent-rose-600 focus:ring-2 focus:ring-rose-500" />
                                <span>
                                    <span className="flex items-center gap-2 text-sm font-semibold text-slate-900"><Phone aria-hidden="true" className="h-4 w-4 text-rose-600" />Anrufe &amp; Anrufbeantworter</span>
                                    <span className="mt-1 block text-sm text-slate-600">Sieht die Anrufliste, hört Nachrichten auf dem Anrufbeantworter ab und bekommt bei Anrufen das Anruf-Fenster.</span>
                                </span>
                            </label>
                        </div>

                        {/* Matrix Table */}
                        <div className="overflow-x-auto">
                            <table className="w-full">
                                <thead>
                                    <tr className="border-b border-slate-200">
                                        <th className="text-left py-3 px-4 font-semibold text-slate-700">Dokumenttyp</th>
                                        <th className="text-center py-3 px-4 font-semibold text-slate-700 w-32">
                                            <div className="flex items-center justify-center gap-1">
                                                <Eye className="w-4 h-4" /> Sehen
                                            </div>
                                        </th>
                                        <th className="text-center py-3 px-4 font-semibold text-slate-700 w-32">
                                            <div className="flex items-center justify-center gap-1">
                                                <FileText className="w-4 h-4" /> Scannen
                                            </div>
                                        </th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {abt.berechtigungen.map(b => (
                                        <tr key={b.typ} className="border-b border-slate-100 hover:bg-slate-50">
                                            <td className="py-3 px-4 font-medium text-slate-900">
                                                {DOKUMENT_TYP_LABELS[b.typ] || b.typ}
                                            </td>
                                            <td className="text-center py-3 px-4">
                                                <button
                                                    onClick={() => handleToggle(abt.abteilungId, b.typ, 'darfSehen')}
                                                    className={`w-6 h-6 rounded-md border-2 transition-colors ${
                                                        b.darfSehen
                                                            ? 'bg-green-500 border-green-500'
                                                            : 'bg-white border-slate-300 hover:border-slate-400'
                                                    }`}
                                                >
                                                    {b.darfSehen && <Check className="w-full h-full text-white p-0.5" />}
                                                </button>
                                            </td>
                                            <td className="text-center py-3 px-4">
                                                <button
                                                    onClick={() => handleToggle(abt.abteilungId, b.typ, 'darfScannen')}
                                                    className={`w-6 h-6 rounded-md border-2 transition-colors ${
                                                        b.darfScannen
                                                            ? 'bg-green-500 border-green-500'
                                                            : 'bg-white border-slate-300 hover:border-slate-400'
                                                    }`}
                                                >
                                                    {b.darfScannen && <Check className="w-full h-full text-white p-0.5" />}
                                                </button>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>

                        {/* Offene Posten Berechtigungen */}
                        <div className="mt-6 pt-4 border-t border-slate-200">
                            <div className="flex items-center gap-2 mb-3">
                                <Wallet className="w-4 h-4 text-slate-600" />
                                <h3 className="font-semibold text-slate-700">Eingangsrechnungen (Offene Posten)</h3>
                            </div>
                            <div className="space-y-3">
                                <label className="flex items-center gap-3 cursor-pointer">
                                    <button
                                        onClick={() => handleToggleRechnungsFlag(abt.abteilungId, 'darfRechnungenGenehmigen')}
                                        className={`w-6 h-6 rounded-md border-2 transition-colors flex-shrink-0 ${
                                            abt.darfRechnungenGenehmigen
                                                ? 'bg-green-500 border-green-500'
                                                : 'bg-white border-slate-300 hover:border-slate-400'
                                        }`}
                                    >
                                        {abt.darfRechnungenGenehmigen && <Check className="w-full h-full text-white p-0.5" />}
                                    </button>
                                    <div>
                                        <span className="text-sm font-medium text-slate-900">Darf Rechnungen genehmigen</span>
                                        <p className="text-xs text-slate-500">Sieht alle Eingangsrechnungen und kann diese genehmigen</p>
                                    </div>
                                </label>
                                <label className="flex items-center gap-3 cursor-pointer">
                                    <button
                                        onClick={() => handleToggleRechnungsFlag(abt.abteilungId, 'darfRechnungenSehen')}
                                        className={`w-6 h-6 rounded-md border-2 transition-colors flex-shrink-0 ${
                                            abt.darfRechnungenSehen
                                                ? 'bg-green-500 border-green-500'
                                                : 'bg-white border-slate-300 hover:border-slate-400'
                                        }`}
                                    >
                                        {abt.darfRechnungenSehen && <Check className="w-full h-full text-white p-0.5" />}
                                    </button>
                                    <div>
                                        <span className="text-sm font-medium text-slate-900">Darf genehmigte Rechnungen sehen</span>
                                        <p className="text-xs text-slate-500">Sieht nur bereits genehmigte Eingangsrechnungen (Buchhaltung)</p>
                                    </div>
                                </label>
                            </div>
                        </div>

                        {/* Push-Benachrichtigungen */}
                        <div className="mt-6 pt-4 border-t border-slate-200">
                            <div className="flex items-center gap-2 mb-3">
                                <Bell className="w-4 h-4 text-slate-600" />
                                <h3 className="font-semibold text-slate-700">Push-Benachrichtigungen</h3>
                            </div>
                            <div className="space-y-3">
                                <label className="flex items-center gap-3 cursor-pointer">
                                    <button
                                        onClick={() => handleTogglePushFlag(abt.abteilungId, 'darfFreigabeAnnahmePushen')}
                                        className={`w-6 h-6 rounded-md border-2 transition-colors flex-shrink-0 ${
                                            abt.darfFreigabeAnnahmePushen
                                                ? 'bg-green-500 border-green-500'
                                                : 'bg-white border-slate-300 hover:border-slate-400'
                                        }`}
                                    >
                                        {abt.darfFreigabeAnnahmePushen && <Check className="w-full h-full text-white p-0.5" />}
                                    </button>
                                    <div>
                                        <span className="text-sm font-medium text-slate-900">Kunde hat angenommen</span>
                                        <p className="text-xs text-slate-500">
                                            Push aufs Handy, sobald ein Kunde ein Angebot oder eine Auftragsbestätigung digital annimmt.
                                        </p>
                                    </div>
                                </label>
                                <label className="flex items-center gap-3 cursor-pointer">
                                    <button
                                        onClick={() => handleTogglePushFlag(abt.abteilungId, 'darfWebseitenAnfragenPushen')}
                                        className={`w-6 h-6 rounded-md border-2 transition-colors flex-shrink-0 ${
                                            abt.darfWebseitenAnfragenPushen
                                                ? 'bg-green-500 border-green-500'
                                                : 'bg-white border-slate-300 hover:border-slate-400'
                                        }`}
                                    >
                                        {abt.darfWebseitenAnfragenPushen && <Check className="w-full h-full text-white p-0.5" />}
                                    </button>
                                    <div>
                                        <span className="text-sm font-medium text-slate-900">Neue Anfrage über Webseite</span>
                                        <p className="text-xs text-slate-500">
                                            Push aufs Handy-Sperrbildschirm, sobald über das Webseiten-Formular eine neue Anfrage eingeht.
                                        </p>
                                    </div>
                                </label>
                            </div>
                        </div>
                    </Card>
                ))}

                {!loadError && berechtigungen.length === 0 && (
                    <Card className="p-12 text-center">
                        <Users className="w-12 h-12 mx-auto text-slate-300 mb-4" />
                        <p className="text-slate-500">Noch keine Abteilungen angelegt.</p>
                        <p className="text-slate-400 text-sm mt-1">
                            Legen Sie oben eine Abteilung an, um Berechtigungen zu verwalten.
                        </p>
                    </Card>
                )}
            </div>
        </PageLayout>
    );
}
