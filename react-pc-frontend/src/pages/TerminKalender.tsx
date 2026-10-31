import { useState, useEffect, useMemo, useRef, useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
    Briefcase, Calendar, CalendarClock, CalendarDays, ChevronLeft, ChevronRight,
    Clock, LayoutGrid, List, Loader2, Plus, RefreshCw, AlertTriangle, Truck, User, Users,
} from 'lucide-react';
import { Button } from '../components/ui/button';
import { useToast } from '../components/ui/toast';
import { leseFehlermeldung } from '../lib/apiFehler';
import { cn } from '../lib/utils';
import { TerminModal } from '../features/kalender/TerminModal';
import {
    addTage, anzeigeSpanne, formatLocalDate, jahreImZeitraum, montagDerWoche, parseLocalDate,
    sichtbarerZeitraum, sortiereTermine, stundenBereich, tageImZeitraum, verteileUeberlappungen, wechsleMonat,
    zeitInMinuten, type KalenderAnsicht,
} from './terminKalenderLogik';

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
    projektId: number | null;
    projektName: string | null;
    kundeId: number | null;
    kundeName: string | null;
    lieferantId: number | null;
    lieferantName: string | null;
    anfrageId: number | null;
    anfrageBetreff: string | null;
    erstellerId: number | null;
    erstellerName: string | null;
    teilnehmer: Teilnehmer[];
    /** Vom Backend: darf der angemeldete Benutzer den Termin ändern und löschen? */
    darfBearbeiten?: boolean;
}

// Team-Abwesenheiten (Urlaub, Krankheit, Fortbildung). „ABWESEND" liefert der Server statt
// „Krankheit", wenn der Betrachter weder Admin noch Geschäftsführung ist (Datenschutz, #268).
type AbwesenheitTyp = 'URLAUB' | 'KRANKHEIT' | 'ABWESEND' | 'FORTBILDUNG' | 'ZEITAUSGLEICH';

interface TeamAbwesenheit {
    id: number;
    datum: string;
    typ: AbwesenheitTyp;
    stunden: number;
    mitarbeiterId: number;
    mitarbeiterName: string;
}

interface Feiertag {
    datum: string;
    bezeichnung: string;
}

interface FarbStil {
    bg: string;
    border: string;
    text: string;
}

/**
 * Feiertage und Abwesenheiten sind keine Termine: Sie werden nur angezeigt und
 * öffnen keinen Termin-Dialog. Deshalb ein eigener Typ statt eines Termins mit erfundener ID.
 */
interface Hinweis {
    key: string;
    art: 'feiertag' | 'abwesenheit';
    titel: string;
    untertitel: string;
    stil: FarbStil;
}

interface TagesInhalt {
    termine: KalenderEintrag[];
    abwesenheiten: Hinweis[];
    feiertage: Hinweis[];
}

// Farben für Abwesenheitstypen (minimalistisch ohne Emojis)
const ABWESENHEIT_FARBEN: Record<string, FarbStil & { label: string; hex: string }> = {
    URLAUB: { bg: 'bg-sky-100', border: 'border-sky-500', text: 'text-sky-700', label: 'Urlaub', hex: '#0ea5e9' },
    KRANKHEIT: { bg: 'bg-slate-100', border: 'border-slate-400', text: 'text-slate-600', label: 'Krankheit', hex: '#64748b' },
    ABWESEND: { bg: 'bg-slate-100', border: 'border-slate-400', text: 'text-slate-600', label: 'Abwesend', hex: '#64748b' },
    FORTBILDUNG: { bg: 'bg-emerald-100', border: 'border-emerald-500', text: 'text-emerald-700', label: 'Fortbildung', hex: '#10b981' },
    ZEITAUSGLEICH: { bg: 'bg-amber-100', border: 'border-amber-500', text: 'text-amber-700', label: 'Zeitausgleich', hex: '#f59e0b' },
    FEIERTAG: { bg: 'bg-rose-100', border: 'border-rose-400', text: 'text-rose-600', label: 'Feiertag', hex: '#f43f5e' },
};

const WOCHENTAGE = ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'];
const WOCHENTAGE_LANG = ['Sonntag', 'Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag'];
const MONATE = ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni',
    'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember'];

// Farboptionen für Termine
const FARB_OPTIONEN = [
    { value: '#dc2626', label: 'Rot', bg: 'bg-red-100', border: 'border-red-500', text: 'text-red-700' },
    { value: '#ea580c', label: 'Orange', bg: 'bg-orange-100', border: 'border-orange-500', text: 'text-orange-700' },
    { value: '#ca8a04', label: 'Gelb', bg: 'bg-yellow-100', border: 'border-yellow-500', text: 'text-yellow-700' },
    { value: '#16a34a', label: 'Grün', bg: 'bg-green-100', border: 'border-green-500', text: 'text-green-700' },
    { value: '#0891b2', label: 'Türkis', bg: 'bg-cyan-100', border: 'border-cyan-500', text: 'text-cyan-700' },
    { value: '#2563eb', label: 'Blau', bg: 'bg-blue-100', border: 'border-blue-500', text: 'text-blue-700' },
    { value: '#7c3aed', label: 'Violett', bg: 'bg-violet-100', border: 'border-violet-500', text: 'text-violet-700' },
    { value: '#db2777', label: 'Pink', bg: 'bg-pink-100', border: 'border-pink-500', text: 'text-pink-700' },
];
const STANDARD_FARBE = '#7c3aed';

/** Farbe eines Termins – genau die gewählte; unbekannte oder fehlende Werte werden Violett. */
function terminFarbe(farbe: string | null): FarbStil {
    return FARB_OPTIONEN.find(f => f.value === farbe) ?? FARB_OPTIONEN.find(f => f.value === STANDARD_FARBE)!;
}

// Hilfsfunktion: Liest die mitarbeiterId des aktuellen Benutzers aus localStorage
const getCurrentUserMitarbeiterId = (): number | null => {
    try {
        const stored = localStorage.getItem('frontendUserSelection');
        if (stored) {
            const parsed = JSON.parse(stored);
            if (parsed.mitarbeiterId) {
                return parsed.mitarbeiterId;
            }
        }
    } catch { /* ignore */ }
    return null;
};

function getWeekNumber(d: Date): number {
    const date = new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()));
    const dayNum = date.getUTCDay() || 7;
    date.setUTCDate(date.getUTCDate() + 4 - dayNum);
    const yearStart = new Date(Date.UTC(date.getUTCFullYear(), 0, 1));
    return Math.ceil((((date.getTime() - yearStart.getTime()) / 86400000) + 1) / 7);
}

function datumLang(datum: string): string {
    return parseLocalDate(datum).toLocaleDateString('de-DE', { weekday: 'long', day: '2-digit', month: 'long', year: 'numeric' });
}

function uhrzeitText(eintrag: KalenderEintrag): string | null {
    if (eintrag.ganztaegig || !eintrag.startZeit) return null;
    return `${eintrag.startZeit.substring(0, 5)}${eintrag.endeZeit ? ` – ${eintrag.endeZeit.substring(0, 5)}` : ''}`;
}

/** Nur-Lese-Eintrag (Feiertag, Abwesenheit): zeigt Namen und Art im Tooltip, öffnet nichts. */
function HinweisEintrag({ hinweis, kompakt }: { hinweis: Hinweis; kompakt?: boolean }) {
    return (
        <div
            data-art={hinweis.art}
            // Im Monatsraster darf der Name gekürzt sein – der volle Text steht im Tooltip.
            data-kuerzung-erlaubt={kompakt ? '' : undefined}
            title={`${hinweis.titel} – ${hinweis.untertitel}`}
            // Klick soll weder einen Termin öffnen noch über den Tag einen neuen anlegen.
            onClick={(e) => e.stopPropagation()}
            className={cn(
                'border-l-2 cursor-default',
                hinweis.stil.bg, hinweis.stil.border, hinweis.stil.text,
                kompakt ? 'text-xs px-1.5 py-1 rounded truncate' : 'p-3 rounded-lg border-l-4',
            )}
        >
            {kompakt ? hinweis.titel : (
                <>
                    <p className="font-semibold">{hinweis.titel}</p>
                    <p className="text-xs text-slate-500 mt-0.5">{hinweis.untertitel}</p>
                </>
            )}
        </div>
    );
}

/** Datum aus dem Deep-Link ?date=2026-03-06, oder null, wenn keins oder ungültig. */
function deepLinkDatumAus(searchParams: URLSearchParams): Date | null {
    const dateParam = searchParams.get('date');
    if (!dateParam) return null;
    if (!/^\d{4}-\d{2}-\d{2}$/.test(dateParam)) return null;
    const parsed = parseLocalDate(dateParam);
    return isNaN(parsed.getTime()) ? null : parsed;
}

export default function TerminKalender() {
    const toast = useToast();
    const [searchParams, setSearchParams] = useSearchParams();
    // Ein Stichtag für alle Ansichten: Monat, Woche und Tag zeigen immer den Zeitraum um diesen Tag.
    // So bleiben Monats-Auswahl, Jahr, Woche und Tag zueinander passend.
    const [stichtag, setStichtag] = useState<Date>(() => {
        const heute = new Date();
        return new Date(heute.getFullYear(), heute.getMonth(), heute.getDate());
    });
    const [ansicht, setAnsicht] = useState<KalenderAnsicht>('monat');
    const [eintraege, setEintraege] = useState<KalenderEintrag[]>([]);
    const [teamAbwesenheiten, setTeamAbwesenheiten] = useState<TeamAbwesenheit[]>([]);
    const [feiertage, setFeiertage] = useState<Feiertag[]>([]);
    const [loading, setLoading] = useState(true);
    const [ladeFehler, setLadeFehler] = useState<string | null>(null);
    const [neuLadenZaehler, setNeuLadenZaehler] = useState(0);
    const [showMonthPicker, setShowMonthPicker] = useState(false);
    const ladeNummer = useRef(0);

    // Stammdaten für Teilnehmer-Auswahl
    // Modal-State
    const [showModal, setShowModal] = useState(false);
    const [editingEintrag, setEditingEintrag] = useState<KalenderEintrag | null>(null);
    const [selectedDate, setSelectedDate] = useState<string | null>(null);

    const zeitraum = useMemo(() => sichtbarerZeitraum(ansicht, stichtag), [ansicht, stichtag]);
    const { von, bis } = zeitraum;
    const heuteStr = formatLocalDate(new Date());

    // Anderer Zeitraum oder „Neu laden": Ladeanzeige an und alten Fehler weg, bevor der Effekt lädt.
    const [ladeAnlass, setLadeAnlass] = useState({ von, bis, neuLadenZaehler });
    if (ladeAnlass.von !== von || ladeAnlass.bis !== bis || ladeAnlass.neuLadenZaehler !== neuLadenZaehler) {
        setLadeAnlass({ von, bis, neuLadenZaehler });
        setLoading(true);
        setLadeFehler(null);
    }

    // Lädt Termine, Abwesenheiten und Feiertage genau für den sichtbaren Zeitraum.
    // Promise-Kette: Zustand wird erst gesetzt, wenn die Antworten da sind.
    const laden = useCallback((zeitraumVon: string, zeitraumBis: string) => {
        const nummer = ++ladeNummer.current;
        const mitarbeiterId = getCurrentUserMitarbeiterId();
        const params = new URLSearchParams({ von: zeitraumVon, bis: zeitraumBis });
        if (mitarbeiterId) params.set('mitarbeiterId', String(mitarbeiterId));
        const jahre = jahreImZeitraum({ von: zeitraumVon, bis: zeitraumBis });
        return Promise.all([
            fetch(`/api/kalender?${params.toString()}`),
            fetch(`/api/abwesenheit/team?von=${zeitraumVon}&bis=${zeitraumBis}`).catch(() => null),
            ...jahre.map(jahr => fetch(`/api/zeiterfassung/feiertage?jahr=${jahr}`).catch(() => null)),
        ])
            .then(async ([kalenderRes, abwesenheitRes, ...feiertagRes]) => {
                if (!kalenderRes.ok) {
                    throw new Error(await leseFehlermeldung(kalenderRes, 'Die Termine konnten nicht geladen werden.'));
                }
                const kalenderDaten = await kalenderRes.json();
                const abwesenheitDaten = abwesenheitRes?.ok ? await abwesenheitRes.json().catch(() => []) : [];
                const feiertagDaten = await Promise.all(feiertagRes.map(res => res?.ok ? res.json().catch(() => []) : []));
                if (nummer !== ladeNummer.current) return; // Inzwischen wurde weitergeblättert.
                setEintraege(Array.isArray(kalenderDaten) ? kalenderDaten : []);
                setTeamAbwesenheiten(Array.isArray(abwesenheitDaten) ? abwesenheitDaten : []);
                setFeiertage(feiertagDaten.flatMap(daten => Array.isArray(daten) ? daten as Feiertag[] : []));
            })
            .catch((err) => {
                if (nummer !== ladeNummer.current) return;
                // fetch wirft TypeError, wenn der Server nicht erreichbar ist.
                const text = err instanceof TypeError || !(err instanceof Error) || !err.message
                    ? 'Die Termine konnten nicht geladen werden. Bitte die Verbindung prüfen.'
                    : err.message;
                setEintraege([]);
                setTeamAbwesenheiten([]);
                setLadeFehler(text);
                toast.error(text);
            })
            .finally(() => {
                if (nummer === ladeNummer.current) setLoading(false);
            });
    }, [toast]);

    useEffect(() => {
        void laden(von, bis);
    }, [laden, von, bis, neuLadenZaehler]);

    // Deep-link: navigate to specific date from URL param ?date=2026-03-06
    // Gültiges Datum beim Rendern übernehmen; die Adresszeile räumt der Effekt danach auf.
    const [ausgewerteteParams, setAusgewerteteParams] = useState<URLSearchParams | null>(null);
    if (ausgewerteteParams !== searchParams) {
        setAusgewerteteParams(searchParams);
        const datum = deepLinkDatumAus(searchParams);
        if (datum) {
            setStichtag(datum);
            setAnsicht('tag');
        }
    }
    useEffect(() => {
        if (deepLinkDatumAus(searchParams)) setSearchParams({}, { replace: true });
    }, [searchParams, setSearchParams]);

    // Inhalte je Tag: echte Termine, Abwesenheiten und Feiertage getrennt.
    const inhaltJeTag = useMemo(() => {
        const karte = new Map<string, TagesInhalt>();
        const holen = (datum: string) => {
            let inhalt = karte.get(datum);
            if (!inhalt) {
                inhalt = { termine: [], abwesenheiten: [], feiertage: [] };
                karte.set(datum, inhalt);
            }
            return inhalt;
        };
        for (const eintrag of eintraege) holen(eintrag.datum).termine.push(eintrag);
        for (const inhalt of karte.values()) inhalt.termine = sortiereTermine(inhalt.termine);
        for (const a of teamAbwesenheiten) {
            const farbInfo = ABWESENHEIT_FARBEN[a.typ] || ABWESENHEIT_FARBEN.URLAUB;
            holen(a.datum).abwesenheiten.push({
                key: `abwesenheit-${a.id}`,
                art: 'abwesenheit',
                titel: a.mitarbeiterName,
                untertitel: farbInfo.label,
                stil: farbInfo,
            });
        }
        feiertage.forEach((f, index) => {
            holen(f.datum).feiertage.push({
                key: `feiertag-${f.datum}-${index}`,
                art: 'feiertag',
                titel: f.bezeichnung,
                untertitel: 'Gesetzlicher Feiertag',
                stil: ABWESENHEIT_FARBEN.FEIERTAG,
            });
        });
        return karte;
    }, [eintraege, teamAbwesenheiten, feiertage]);

    const inhaltAm = (datum: string): TagesInhalt => inhaltJeTag.get(datum) ?? { termine: [], abwesenheiten: [], feiertage: [] };

    // Event Handlers
    const handleDayClick = (datum: string) => {
        setSelectedDate(datum);
        setEditingEintrag(null);
        setShowModal(true);
    };

    const handleEventClick = (eintrag: KalenderEintrag, e: React.MouseEvent) => {
        e.stopPropagation();
        setEditingEintrag(eintrag);
        setSelectedDate(eintrag.datum);
        setShowModal(true);
    };

    const handleCloseModal = () => {
        setShowModal(false);
        setEditingEintrag(null);
        setSelectedDate(null);
    };

    const handleSave = () => {
        setNeuLadenZaehler(n => n + 1);
        handleCloseModal();
    };

    const zeigeTag = (datum: string) => {
        setStichtag(parseLocalDate(datum));
        setAnsicht('tag');
    };

    const montag = montagDerWoche(stichtag);
    const wochenNummer = getWeekNumber(montag);
    const monatsSchluessel = formatLocalDate(stichtag).slice(0, 7);

    return (
        <div className="p-6 max-w-7xl mx-auto">
            {/* Header */}
            <div className="flex flex-col md:flex-row justify-between gap-4 md:items-end mb-8">
                <div>
                    <p className="text-sm font-semibold text-rose-600 uppercase tracking-wide">
                        Projektmanagement
                    </p>
                    <h1 className="text-3xl font-bold text-slate-900">
                        TERMINKALENDER
                    </h1>
                    <p className="text-slate-500 mt-1">
                        Planen Sie Termine und verknüpfen Sie diese mit Projekten, Kunden und Lieferanten
                    </p>
                </div>
                <div>
                    <Button
                        onClick={() => { setSelectedDate(formatLocalDate(new Date())); setEditingEintrag(null); setShowModal(true); }}
                        className="bg-rose-600 text-white hover:bg-rose-700"
                    >
                        <Plus className="w-4 h-4 mr-2" />
                        Neuer Termin
                    </Button>
                </div>
            </div>

            {/* Controls */}
            <div className="flex flex-wrap items-center gap-4 bg-white p-4 rounded-lg border border-slate-200 shadow-sm mb-6">
                {/* Month Picker */}
                <div className="relative">
                    <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">Monat</label>
                    <button
                        type="button"
                        aria-label={`Monat: ${MONATE[stichtag.getMonth()]}`}
                        aria-expanded={showMonthPicker}
                        onClick={() => setShowMonthPicker(!showMonthPicker)}
                        className="flex items-center justify-between w-44 px-3 py-2 bg-white border border-slate-300 rounded-md hover:bg-slate-50 transition-colors cursor-pointer"
                    >
                        <span className="font-medium">{MONATE[stichtag.getMonth()]}</span>
                        <Calendar className="w-4 h-4 text-slate-400" />
                    </button>

                    {showMonthPicker && (
                        <div className="absolute top-full left-0 mt-2 w-64 bg-white rounded-lg shadow-xl border border-slate-200 z-50 p-4 animate-in fade-in zoom-in-95 duration-200">
                            <div className="grid grid-cols-3 gap-2">
                                {MONATE.map((m, idx) => (
                                    <button
                                        type="button"
                                        key={m}
                                        onClick={() => { setStichtag(wechsleMonat(stichtag, stichtag.getFullYear(), idx)); setShowMonthPicker(false); }}
                                        className={`p-2 text-sm rounded-md transition-colors cursor-pointer ${stichtag.getMonth() === idx
                                            ? 'bg-rose-100 text-rose-700 font-bold'
                                            : 'hover:bg-slate-100 text-slate-700'
                                            }`}
                                    >
                                        {m}
                                    </button>
                                ))}
                            </div>
                        </div>
                    )}
                </div>

                {/* Year Switcher */}
                <div>
                    <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">Jahr</label>
                    <div className="flex items-center bg-white border border-slate-300 rounded-md">
                        <button type="button" aria-label="Vorheriges Jahr" className="p-2 hover:bg-slate-50 text-slate-600 cursor-pointer transition-colors"
                            onClick={() => setStichtag(wechsleMonat(stichtag, stichtag.getFullYear() - 1, stichtag.getMonth()))}>
                            <ChevronLeft className="w-4 h-4" />
                        </button>
                        <span className="w-16 text-center font-bold text-slate-800">{stichtag.getFullYear()}</span>
                        <button type="button" aria-label="Nächstes Jahr" className="p-2 hover:bg-slate-50 text-slate-600 cursor-pointer transition-colors"
                            onClick={() => setStichtag(wechsleMonat(stichtag, stichtag.getFullYear() + 1, stichtag.getMonth()))}>
                            <ChevronRight className="w-4 h-4" />
                        </button>
                    </div>
                </div>

                {/* Ansicht-Toggle */}
                <div>
                    <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">Ansicht</label>
                    <div className="flex bg-slate-100 rounded-md p-0.5">
                        {([
                            { wert: 'monat', label: 'Monat', icon: LayoutGrid },
                            { wert: 'woche', label: 'Woche', icon: List },
                            { wert: 'tag', label: 'Tag', icon: CalendarClock },
                        ] as const).map(({ wert, label, icon: Icon }) => (
                            <button
                                type="button"
                                key={wert}
                                aria-pressed={ansicht === wert}
                                onClick={() => setAnsicht(wert)}
                                className={`px-3 py-1.5 text-sm font-medium rounded transition-all flex items-center gap-1 cursor-pointer ${ansicht === wert ? 'bg-white text-rose-700 shadow-sm' : 'text-slate-600 hover:text-slate-800'}`}
                            >
                                <Icon className="w-3.5 h-3.5" /> {label}
                            </button>
                        ))}
                    </div>
                </div>

                {/* Heute-Button */}
                <div className="ml-auto">
                    <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">&nbsp;</label>
                    <Button
                        variant="outline"
                        size="sm"
                        onClick={() => setStichtag(parseLocalDate(formatLocalDate(new Date())))}
                        className="border-rose-200 text-rose-700 hover:bg-rose-50"
                    >
                        <CalendarDays className="w-4 h-4 mr-1" />
                        Heute
                    </Button>
                </div>
            </div>

            {/* Wochennavigation (nur bei Wochenansicht) */}
            {ansicht === 'woche' && (
                <div className="flex items-center justify-between bg-white p-3 rounded-lg border border-slate-200 shadow-sm mb-4">
                    <button
                        type="button"
                        aria-label="Vorherige Woche"
                        onClick={() => setStichtag(addTage(stichtag, -7))}
                        className="p-2 hover:bg-slate-100 rounded-lg transition-colors cursor-pointer"
                    >
                        <ChevronLeft className="w-5 h-5 text-slate-600" />
                    </button>
                    <div className="text-center">
                        <p className="text-lg font-bold text-slate-900">
                            KW {wochenNummer}
                        </p>
                        <p className="text-sm text-slate-500">
                            {montag.toLocaleDateString('de-DE', { day: '2-digit', month: 'short' })} – {addTage(montag, 6).toLocaleDateString('de-DE', { day: '2-digit', month: 'short', year: 'numeric' })}
                        </p>
                    </div>
                    <button
                        type="button"
                        aria-label="Nächste Woche"
                        onClick={() => setStichtag(addTage(stichtag, 7))}
                        className="p-2 hover:bg-slate-100 rounded-lg transition-colors cursor-pointer"
                    >
                        <ChevronRight className="w-5 h-5 text-slate-600" />
                    </button>
                </div>
            )}

            {/* Legende */}
            <div className="flex flex-wrap gap-4 bg-white p-3 rounded-lg border border-slate-200 shadow-sm mb-4">
                <span className="text-xs font-semibold text-slate-500 uppercase self-center mr-2">Legende:</span>
                {/* Termine erscheinen in ihrer gewählten Farbe */}
                <div className="flex items-center gap-1.5" title="Termine erscheinen in der Farbe, die beim Termin gewählt ist.">
                    <span className="flex -space-x-1" aria-hidden="true">
                        {FARB_OPTIONEN.map(f => (
                            <span key={f.value} className="w-3 h-3 rounded-full ring-1 ring-white" style={{ backgroundColor: f.value }} />
                        ))}
                    </span>
                    <span className="text-xs text-slate-600 font-medium">Termin (gewählte Farbe)</span>
                </div>
                {/* Abwesenheitstypen */}
                {Object.entries(ABWESENHEIT_FARBEN).map(([key, val]) => (
                    <div key={key} className="flex items-center gap-1.5">
                        <span
                            className="w-3 h-3 rounded-full"
                            style={{ backgroundColor: val.hex }}
                        ></span>
                        <span className="text-xs text-slate-600 font-medium">{val.label}</span>
                    </div>
                ))}
            </div>

            {ladeFehler && (
                <div role="alert" className="flex flex-wrap items-center gap-3 mb-4 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">
                    <AlertTriangle className="w-4 h-4 shrink-0" aria-hidden="true" />
                    <p className="flex-1 min-w-0">{ladeFehler}</p>
                    <Button variant="outline" size="sm" onClick={() => setNeuLadenZaehler(n => n + 1)}>
                        <RefreshCw className="w-4 h-4 mr-1" /> Erneut versuchen
                    </Button>
                </div>
            )}

            {/* Kalender Grid */}
            {loading ? (
                <div className="flex justify-center py-24 bg-white rounded-lg border border-slate-200" aria-label="Termine werden geladen">
                    <Loader2 className="w-10 h-10 motion-safe:animate-spin text-rose-600" />
                </div>
            ) : ansicht === 'tag' ? (
                <TagesAnsicht
                    datum={formatLocalDate(stichtag)}
                    inhalt={inhaltAm(formatLocalDate(stichtag))}
                    onVorher={() => setStichtag(addTage(stichtag, -1))}
                    onNachher={() => setStichtag(addTage(stichtag, 1))}
                    onNeu={handleDayClick}
                    onTermin={handleEventClick}
                />
            ) : ansicht === 'woche' ? (
                /* ========== WOCHENANSICHT ========== */
                <div className="bg-white rounded-lg border border-slate-200 shadow-sm overflow-hidden">
                    <div className="divide-y divide-slate-200">
                        {tageImZeitraum(zeitraum).map(tagStr => {
                            const datum = parseLocalDate(tagStr);
                            const isWeekend = datum.getDay() === 0 || datum.getDay() === 6;
                            const isToday = tagStr === heuteStr;
                            const inhalt = inhaltAm(tagStr);
                            const leer = inhalt.termine.length + inhalt.abwesenheiten.length + inhalt.feiertage.length === 0;

                            return (
                                <div
                                    key={tagStr}
                                    className={`flex gap-4 p-4 transition-all duration-150 cursor-pointer hover:bg-rose-50/30 ${isWeekend ? 'bg-slate-50/50' : ''
                                        } ${isToday ? 'bg-rose-50/50 border-l-4 border-l-rose-500' : ''}`}
                                    onClick={() => handleDayClick(tagStr)}
                                >
                                    {/* Datum-Spalte */}
                                    <div className="w-24 shrink-0">
                                        <p className="text-xs font-medium text-slate-500 uppercase">{WOCHENTAGE_LANG[datum.getDay()]}</p>
                                        <p className={`text-2xl font-bold ${isToday ? 'text-rose-600' : 'text-slate-900'}`}>
                                            {datum.getDate()}
                                        </p>
                                        <p className="text-xs text-slate-400">
                                            {datum.toLocaleDateString('de-DE', { month: 'short' })}
                                        </p>
                                    </div>

                                    {/* Einträge */}
                                    <div className="flex-1 space-y-2">
                                        {leer ? (
                                            <p className="text-sm text-slate-400 italic py-2">Keine Termine</p>
                                        ) : (
                                            <>
                                                {inhalt.termine.map(eintrag => (
                                                    <TerminKarte key={eintrag.id} eintrag={eintrag} onClick={(e) => handleEventClick(eintrag, e)} />
                                                ))}
                                                {[...inhalt.abwesenheiten, ...inhalt.feiertage].map(h => <HinweisEintrag key={h.key} hinweis={h} />)}
                                            </>
                                        )}
                                    </div>

                                    {/* Plus Button */}
                                    <button
                                        type="button"
                                        aria-label={`Neuer Termin am ${datum.toLocaleDateString('de-DE')}`}
                                        onClick={(e) => { e.stopPropagation(); handleDayClick(tagStr); }}
                                        className="p-2 h-fit text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded-lg transition-colors cursor-pointer"
                                    >
                                        <Plus className="w-5 h-5" />
                                    </button>
                                </div>
                            );
                        })}
                    </div>
                </div>
            ) : (
                /* ========== MONATSANSICHT ========== */
                <div className="bg-white rounded-lg border border-slate-200 shadow-sm overflow-hidden">
                    {/* Wochentag Header */}
                    <div className="grid grid-cols-7 bg-slate-50 border-b border-slate-200">
                        {WOCHENTAGE.map(tag => (
                            <div key={tag} className="p-3 text-center text-xs font-bold text-slate-500 uppercase tracking-widest">
                                {tag}
                            </div>
                        ))}
                    </div>

                    {/* Tage Grid – inklusive der angeschnittenen Tage aus Vor- und Folgemonat */}
                    <div className="grid grid-cols-7 bg-slate-200 gap-px">
                        {tageImZeitraum(zeitraum).map(tagStr => {
                            const datum = parseLocalDate(tagStr);
                            const imMonat = tagStr.startsWith(monatsSchluessel);
                            const isWeekend = datum.getDay() === 0 || datum.getDay() === 6;
                            const isToday = tagStr === heuteStr;
                            const inhalt = inhaltAm(tagStr);
                            // Echte Termine zuerst, damit Abwesenheiten sie nicht verdrängen.
                            // Feiertage stehen als Name neben der Tageszahl.
                            const eintraegeDesTages: ({ art: 'termin'; eintrag: KalenderEintrag } | { art: 'hinweis'; hinweis: Hinweis })[] = [
                                ...inhalt.termine.map(eintrag => ({ art: 'termin' as const, eintrag })),
                                ...inhalt.abwesenheiten.map(hinweis => ({ art: 'hinweis' as const, hinweis })),
                            ];
                            const weitere = eintraegeDesTages.length - 3;

                            return (
                                <div
                                    key={tagStr}
                                    data-datum={tagStr}
                                    className={cn(
                                        'min-h-32 p-2 transition-all duration-150 cursor-pointer group relative hover:bg-rose-50/40',
                                        !imMonat ? 'bg-slate-50' : isWeekend ? 'bg-slate-50/50' : 'bg-white',
                                    )}
                                    onClick={() => handleDayClick(tagStr)}
                                >
                                    <div className="flex justify-between items-start gap-1 mb-2">
                                        <span className={cn(
                                            'text-sm font-bold w-7 h-7 shrink-0 flex items-center justify-center rounded-full',
                                            isToday ? 'bg-rose-600 text-white' : imMonat ? 'text-slate-700 group-hover:bg-slate-200' : 'text-slate-400',
                                        )}>
                                            {datum.getDate()}
                                        </span>
                                        {inhalt.feiertage.length > 0 && (
                                            <span
                                                data-art="feiertag"
                                                title={inhalt.feiertage.map(f => `${f.titel} – ${f.untertitel}`).join('\n')}
                                                onClick={(e) => e.stopPropagation()}
                                                className="min-w-0 pt-1 text-right text-[11px] font-semibold leading-tight text-rose-600 cursor-default"
                                            >
                                                {inhalt.feiertage.map(f => f.titel).join(', ')}
                                            </span>
                                        )}
                                    </div>

                                    {/* Einträge */}
                                    <div className="space-y-1">
                                        {eintraegeDesTages.slice(0, 3).map(item => {
                                            if (item.art === 'hinweis') return <HinweisEintrag key={item.hinweis.key} hinweis={item.hinweis} kompakt />;
                                            const { eintrag } = item;
                                            const farbStyle = terminFarbe(eintrag.farbe);
                                            return (
                                                <button
                                                    type="button"
                                                    key={eintrag.id}
                                                    onClick={(e) => handleEventClick(eintrag, e)}
                                                    className={cn(
                                                        'block w-full text-left text-xs px-1.5 py-1 rounded truncate border-l-2 cursor-pointer hover:opacity-80 transition-opacity focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-rose-500',
                                                        farbStyle.bg, farbStyle.border, farbStyle.text,
                                                    )}
                                                    title={eintrag.titel}
                                                    data-kuerzung-erlaubt=""
                                                >
                                                    {eintrag.startZeit && !eintrag.ganztaegig && (
                                                        <span className="font-medium mr-1">{eintrag.startZeit.substring(0, 5)}</span>
                                                    )}
                                                    {eintrag.titel}
                                                </button>
                                            );
                                        })}
                                        {weitere > 0 && (
                                            <button
                                                type="button"
                                                onClick={(e) => { e.stopPropagation(); zeigeTag(tagStr); }}
                                                title={`Alle Einträge am ${datum.toLocaleDateString('de-DE')} in der Tagesansicht zeigen`}
                                                className="w-full rounded text-xs text-center text-slate-500 font-medium hover:bg-rose-100 hover:text-rose-700 cursor-pointer py-0.5 focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-rose-500"
                                            >
                                                +{weitere} weitere
                                            </button>
                                        )}
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                </div>
            )}

            <TerminModal
                offen={showModal}
                terminId={editingEintrag?.id ?? null}
                vorgabe={{ von: selectedDate ?? '', bis: selectedDate ?? '', mitarbeiterId: getCurrentUserMitarbeiterId() }}
                eintragHinweis={editingEintrag}
                onSchliessen={handleCloseModal}
                onGeaendert={handleSave}
            />
        </div>
    );
}

// =========================================================================
// TERMIN-KARTE (Woche / ganztägig in der Tagesansicht)
// =========================================================================

function TerminKarte({ eintrag, onClick }: { eintrag: KalenderEintrag; onClick: (e: React.MouseEvent) => void }) {
    const farbStyle = terminFarbe(eintrag.farbe);
    const zeit = uhrzeitText(eintrag);
    return (
        <button
            type="button"
            onClick={onClick}
            className={cn(
                'block w-full text-left p-3 rounded-lg border-l-4 transition-all hover:shadow-md cursor-pointer focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500',
                farbStyle.bg, farbStyle.border,
            )}
        >
            <div className="flex items-start justify-between gap-2">
                <div>
                    <p className={`font-semibold ${farbStyle.text}`}>{eintrag.titel}</p>
                    {zeit ? (
                        <p className="text-xs text-slate-600 flex items-center gap-1 mt-0.5">
                            <Clock className="w-3 h-3" /> {zeit}
                        </p>
                    ) : (
                        <p className="text-xs text-slate-500 mt-0.5">Ganztägig</p>
                    )}
                </div>
                {/* Verknüpfungs-Badge */}
                <div className="flex gap-1 shrink-0">
                    {eintrag.projektName && (
                        <span className="px-1.5 py-0.5 bg-rose-100 text-rose-700 text-xs rounded flex items-center gap-0.5">
                            <Briefcase className="w-3 h-3" />
                        </span>
                    )}
                    {eintrag.kundeName && (
                        <span className="px-1.5 py-0.5 bg-blue-100 text-blue-700 text-xs rounded flex items-center gap-0.5">
                            <User className="w-3 h-3" />
                        </span>
                    )}
                    {eintrag.lieferantName && (
                        <span className="px-1.5 py-0.5 bg-orange-100 text-orange-700 text-xs rounded flex items-center gap-0.5">
                            <Truck className="w-3 h-3" />
                        </span>
                    )}
                    {eintrag.teilnehmer && eintrag.teilnehmer.length > 0 && (
                        <span className="px-1.5 py-0.5 bg-slate-100 text-slate-600 text-xs rounded flex items-center gap-0.5">
                            <Users className="w-3 h-3" /> {eintrag.teilnehmer.length}
                        </span>
                    )}
                </div>
            </div>
            {eintrag.beschreibung && (
                <p className="text-xs text-slate-600 mt-1 line-clamp-2">{eintrag.beschreibung}</p>
            )}
            {(eintrag.projektName || eintrag.kundeName || eintrag.lieferantName) && (
                <div className="flex flex-wrap gap-3 mt-2 text-xs text-slate-500">
                    {eintrag.projektName && (
                        <span className="flex items-center gap-1">
                            <Briefcase className="w-3 h-3 text-rose-500" /> {eintrag.projektName}
                        </span>
                    )}
                    {eintrag.kundeName && (
                        <span className="flex items-center gap-1">
                            <User className="w-3 h-3 text-blue-500" /> {eintrag.kundeName}
                        </span>
                    )}
                    {eintrag.lieferantName && (
                        <span className="flex items-center gap-1">
                            <Truck className="w-3 h-3 text-orange-500" /> {eintrag.lieferantName}
                        </span>
                    )}
                </div>
            )}
        </button>
    );
}

// =========================================================================
// TAGESANSICHT
// =========================================================================

const STUNDEN_HOEHE = 80; // px pro Stunde
const ZEITSPALTE = '5rem'; // Breite der Uhrzeit-Spalte links
const RAND_RECHTS = '1rem';

interface TagesAnsichtProps {
    datum: string;
    inhalt: TagesInhalt;
    onVorher: () => void;
    onNachher: () => void;
    onNeu: (datum: string) => void;
    onTermin: (eintrag: KalenderEintrag, e: React.MouseEvent) => void;
}

function TagesAnsicht({ datum, inhalt, onVorher, onNachher, onNeu, onTermin }: TagesAnsichtProps) {
    const ganztaegige = inhalt.termine.filter(e => e.ganztaegig || zeitInMinuten(e.startZeit) === null);
    const zeitbasierte = inhalt.termine.filter(e => !e.ganztaegig && zeitInMinuten(e.startZeit) !== null);
    const bereich = stundenBereich(zeitbasierte);
    const plaetze = verteileUeberlappungen(zeitbasierte);
    const stunden = Array.from({ length: bereich.bis - bereich.von }, (_, i) => i + bereich.von);
    const hinweise = [...inhalt.feiertage, ...inhalt.abwesenheiten];

    return (
        <div className="bg-white rounded-lg border border-slate-200 shadow-sm overflow-hidden">
            {/* Tag-Navigation */}
            <div className="flex items-center justify-between p-4 border-b border-slate-200 bg-slate-50">
                <button type="button" aria-label="Vorheriger Tag" onClick={onVorher} className="p-2 hover:bg-slate-200 rounded-lg transition-colors cursor-pointer">
                    <ChevronLeft className="w-5 h-5 text-slate-600" />
                </button>
                <p className="text-lg font-bold text-slate-900 text-center">{datumLang(datum)}</p>
                <button type="button" aria-label="Nächster Tag" onClick={onNachher} className="p-2 hover:bg-slate-200 rounded-lg transition-colors cursor-pointer">
                    <ChevronRight className="w-5 h-5 text-slate-600" />
                </button>
            </div>

            {/* Ganztägige Einträge oben */}
            {(ganztaegige.length > 0 || hinweise.length > 0) && (
                <div className="border-b border-slate-200 p-4 space-y-2">
                    {ganztaegige.map(eintrag => (
                        <TerminKarte key={eintrag.id} eintrag={eintrag} onClick={(e) => onTermin(eintrag, e)} />
                    ))}
                    {hinweise.map(h => <HinweisEintrag key={h.key} hinweis={h} />)}
                </div>
            )}

            {/* Zeitraster mit absolut positionierten Terminen */}
            <div className="relative" style={{ height: stunden.length * STUNDEN_HOEHE }}>
                {stunden.map(stunde => (
                    <div
                        key={stunde}
                        className="absolute left-0 right-0 border-b border-slate-100 hover:bg-rose-50/30 cursor-pointer transition-colors"
                        style={{ top: (stunde - bereich.von) * STUNDEN_HOEHE, height: STUNDEN_HOEHE }}
                        onClick={() => onNeu(datum)}
                    >
                        <div className="w-16 text-sm font-medium text-slate-400 pl-4 pt-2">
                            {`${stunde.toString().padStart(2, '0')}:00`}
                        </div>
                    </div>
                ))}

                {zeitbasierte.map(eintrag => {
                    const spanne = anzeigeSpanne(eintrag);
                    if (!spanne) return null;
                    const platz = plaetze.get(eintrag.id) ?? { spalte: 0, spalten: 1 };
                    const farbStyle = terminFarbe(eintrag.farbe);
                    const breite = `(100% - ${ZEITSPALTE} - ${RAND_RECHTS}) / ${platz.spalten}`;
                    return (
                        <button
                            type="button"
                            key={eintrag.id}
                            data-termin-id={eintrag.id}
                            className={cn(
                                'absolute text-left p-2 rounded-lg border-l-4 cursor-pointer hover:shadow-md transition-all overflow-hidden focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-rose-500',
                                farbStyle.bg, farbStyle.border,
                            )}
                            style={{
                                top: (spanne.start - bereich.von * 60) * (STUNDEN_HOEHE / 60),
                                height: (spanne.ende - spanne.start) * (STUNDEN_HOEHE / 60),
                                left: `calc(${ZEITSPALTE} + ${breite} * ${platz.spalte})`,
                                width: `calc(${breite} - 4px)`,
                                zIndex: 10,
                            }}
                            onClick={(e) => onTermin(eintrag, e)}
                        >
                            <div className="flex items-start justify-between gap-2">
                                <div className="min-w-0">
                                    <p className={`font-semibold truncate ${farbStyle.text}`}>{eintrag.titel}</p>
                                    <p className="text-xs text-slate-600 flex items-center gap-1 mt-0.5">
                                        <Clock className="w-3 h-3 shrink-0" /> {uhrzeitText(eintrag)}
                                    </p>
                                    {eintrag.beschreibung && (
                                        <p className="text-xs text-slate-600 mt-1 line-clamp-2">{eintrag.beschreibung}</p>
                                    )}
                                </div>
                                {eintrag.teilnehmer && eintrag.teilnehmer.length > 0 && (
                                    <span className="px-1.5 py-0.5 bg-slate-100 text-slate-600 text-xs rounded flex items-center gap-0.5 shrink-0">
                                        <Users className="w-3 h-3" /> {eintrag.teilnehmer.length}
                                    </span>
                                )}
                            </div>
                        </button>
                    );
                })}
            </div>
        </div>
    );
}

