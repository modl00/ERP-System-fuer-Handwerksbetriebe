import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { AlertTriangle, ChevronLeft, ChevronRight, History, Loader2, Minus, Pencil, Pin, Plus, Undo2, Wrench } from 'lucide-react';
import { Link, useInRouterContext } from 'react-router-dom';
import { Button } from '../../components/ui/button';
import { useToast } from '../../components/ui/toast';
import {
    aendereVorgang, ladeAenderungen, ladeBelegung, nimmZurueck, verschiebeTermin, weiseZu,
    type Aenderung, type Auftrag, type Belegung as BelegungDaten, type BelegungEintrag, type BelegungZeile, type FreierTag,
} from './plantafelApi';
import { platziere, type Platziert } from './belegungLayout';
import {
    BREITE_MIT_NAMEN, BREITE_MIT_ZAHL, BREITE_TERMINE_EINZELN, MAX_FENSTER, ZEITSTUFEN, arbeitstageDelta, auftragsZeile, breiteJeTag, fortschritt, monatsBaender, neueGroesse, ordneAuftraege, pfeile, spalteVon, verdichteTermine,
    type Sortierung,
} from './ansicht';
import { neueZuweisung, verschobenerStart, verschobenerTermin, type Zuweisungsstand } from './verschiebung';
import { useZiehen } from './ziehen';
import { useVorgangVerschieben } from './useVorgangVerschieben';
import { useVorschauFrage } from './VorschauDialog';
import { TerminDialog } from './TerminDialog';
import { anzeigeKurz, formatDatum, istWochenende, parseDatum, plusTage, tageZwischen } from './zeitachse';

interface Props {
    /** Ein Klick auf einen Vorgang öffnet ihn zum Bearbeiten. */
    onVorgang: (projektId: number, vorgangId: number) => void;
    /** Erhöht sich nach jeder Änderung an einem Vorgang, damit die Ansicht neu lädt. */
    aktualisierung: number;
    /** Wird mit dem neu gerechneten Auftrag aufgerufen, wenn ein Vorgang verschoben oder neu zugewiesen wurde (damit die Ansicht „Aufträge" mitzieht). */
    onAuftrag?: (auftrag: Auftrag) => void;
    /** Arbeitswoche als ISO-Zahlen (1 = Montag); ohne Angabe Montag bis Freitag. */
    arbeitstage?: readonly number[];
    /** Ein Klick auf den Auftrag (Name in der Auftragszeile) öffnet ihn zum Bearbeiten. */
    onAuftragOeffnen?: (projektId: number) => void;
    /** Die Aufträge der Plantafel: Jeder steht als eigene Zeile in der Gruppe „Allgemein". */
    auftraege?: readonly Auftrag[];
    /** Bearbeiten-Modus: Nur dann lässt sich ziehen und per Klick auf einen freien Tag ein Termin anlegen (Schutz vor Versehen, auch am Tablet). */
    bearbeiten?: boolean;
    onBearbeiten?: (an: boolean) => void;
}

const WOCHENTAGE = ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa'];
const MONATE = ['Jan.', 'Feb.', 'März', 'Apr.', 'Mai', 'Juni', 'Juli', 'Aug.', 'Sep.', 'Okt.', 'Nov.', 'Dez.'];
const GRUPPEN: { gruppe: BelegungZeile['gruppe']; text: string }[] = [
    { gruppe: 'ALLGEMEIN', text: 'Allgemein' },
    { gruppe: 'MITARBEITER', text: 'Mitarbeiter' },
    { gruppe: 'RESSOURCE', text: 'Ressourcen' },
];
const NAMEN_B = 230;
/** Beim Verschieben nachgeladene Tage, so nah (in Tagen) am Rand wird nachgeladen, und so viele Tage hält die Ansicht höchstens vor. */
const NACHLADEN = 28;
const RAND = 10;
const START_TAGE = 84;
const MAX_TAGE = MAX_FENSTER;
const AMPEL: Record<Auftrag['ampel'], { text: string; klasse: string }> = {
    OK: { text: 'Im Plan', klasse: 'bg-emerald-100 text-emerald-800' },
    KNAPP: { text: 'Knapp', klasse: 'bg-amber-100 text-amber-900' },
    VERSPAETET: { text: 'Verspätet', klasse: 'bg-rose-100 text-rose-800' },
    OHNE_ZIEL: { text: 'Kein Zieltermin', klasse: 'bg-slate-100 text-slate-700' },
};
/** Hellgraue Linien bei 1/3 und 2/3 des Tages (die Stunden 11 und 14), ohne je Tag weitere Elemente. */
const STUNDENLINIEN = 'linear-gradient(to right, transparent calc(33.333% - 1px), #f1f5f9 calc(33.333% - 1px), #f1f5f9 33.333%, transparent 33.333%, transparent calc(66.667% - 1px), #f1f5f9 calc(66.667% - 1px), #f1f5f9 66.667%, transparent 66.667%)';
const KARTE_H = 66;
/** Stunden, die in der Kopfzeile unter jedem Tag stehen (der Tag reicht von 8 bis 17 Uhr). */
const STUNDEN = ['08', '11', '14'];

const montagVon = (d: Date) => plusTage(d, -((d.getDay() + 6) % 7));
const zeit = (z: string | null) => (z ? z.slice(0, 5) : '');

/** Kalenderwoche nach ISO 8601. */
function kalenderwoche(d: Date): number {
    const t = new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()));
    t.setUTCDate(t.getUTCDate() + 4 - (t.getUTCDay() || 7));
    return Math.ceil(((t.getTime() - Date.UTC(t.getUTCFullYear(), 0, 1)) / 86_400_000 + 1) / 7);
}

/** "12. – 18. Okt. 2026" */
function zeitraumTitel(von: Date, bis: Date): string {
    if (von.getFullYear() !== bis.getFullYear()) return `${von.getDate()}. ${MONATE[von.getMonth()]} ${von.getFullYear()} – ${bis.getDate()}. ${MONATE[bis.getMonth()]} ${bis.getFullYear()}`;
    if (von.getMonth() !== bis.getMonth()) return `${von.getDate()}. ${MONATE[von.getMonth()]} – ${bis.getDate()}. ${MONATE[bis.getMonth()]} ${bis.getFullYear()}`;
    return `${von.getDate()}. – ${bis.getDate()}. ${MONATE[bis.getMonth()]} ${bis.getFullYear()}`;
}

/** Wer den Vorgang heute macht: Mitarbeiter und Ressource, abgelesen an den Zeilen, in denen er steht. */
function aktuelleZuweisung(daten: BelegungDaten, id: string): Zuweisungsstand {
    const stand: Zuweisungsstand = { mitarbeiterIds: [], ressourceId: null };
    for (const z of daten.zeilen) {
        if (!z.eintraege.some(e => e.id === id)) continue;
        if (z.schluessel[0] === 'M') stand.mitarbeiterIds.push(Number(z.schluessel.slice(1)));
        if (z.schluessel[0] === 'R') stand.ressourceId = Number(z.schluessel.slice(1));
    }
    return stand;
}

/**
 * Plantafel, Ansicht nach Mitarbeitern und Ressourcen (#323): Wer ist wann womit belegt? Die Zeitachse ist endlos: Beim Verschieben lädt die Ansicht Wochen nach.
 * Ein Klick auf einen freien Tag legt einen Termin an, ein Aufziehen über mehrere Tage einen durchgehenden Termin. Zeigt die Vorgänge der Plantafel, die Kalendertermine und die Abwesenheiten.
 * Vorgänge und Termine lassen sich mit der Maus auf einen anderen Tag und in die Zeile eines anderen Mitarbeiters oder einer anderen Ressource ziehen.
 * Doppelte Belegung steht als Hinweis am Eintrag; das Programm verschiebt nie von selbst.
 */
export function Belegung({ onVorgang, aktualisierung, onAuftrag, onAuftragOeffnen, arbeitstage, auftraege = [], bearbeiten = false, onBearbeiten }: Props) {
    const toast = useToast();
    const vorschau = useVorschauFrage();
    const verschiebeVorgang = useVorgangVerschieben(onAuftrag ?? (() => undefined), vorschau.frage);
    const [aenderungen, setAenderungen] = useState<Aenderung[]>([]);
    const [listeOffen, setListeOffen] = useState(false);
    const [arbeitet, setArbeitet] = useState(false);
    // Die Ansicht ist ein endloser Streifen: Ein Fenster aus Tagen wird geladen und beim Verschieben nach links oder rechts erweitert
    const [fenster, setFenster] = useState(() => ({ start: plusTage(montagVon(new Date()), -NACHLADEN), tage: START_TAGE }));
    const [zoom, setZoom] = useState(7);
    const [stand, setStand] = useState<{ schluessel: string; daten: BelegungDaten | null; fehler: string } | null>(null);
    const [version, setVersion] = useState(0);
    const [zugeklappt, setZugeklappt] = useState<Set<string>>(new Set());
    const [sortierung, setSortierung] = useState<Sortierung>('LIEFERZEIT');
    const [nurVerspaetete, setNurVerspaetete] = useState(false);
    const [erledigteZeigen, setErledigteZeigen] = useState(false);
    const [zeigeTermine, setZeigeTermine] = useState(true);
    const [zeigeAbwesenheit, setZeigeAbwesenheit] = useState(true);
    const [zeigeWartung, setZeigeWartung] = useState(true);
    const [ersterTag, setErsterTag] = useState(NACHLADEN);
    const [terminDialog, setTerminDialog] = useState<{ terminId: number | null; von: string; bis: string; mitarbeiterId: number | null } | null>(null);
    const gitter = useRef<HTMLDivElement>(null);
    const tafel = useRef<HTMLDivElement>(null);
    const gescrollt = useRef(false);
    const verschieben = useRef<{ x: number; links: number; id: number; bewegt: boolean } | null>(null);
    const [greift, setGreift] = useState(false);
    const korrektur = useRef(0);
    const ziel = useRef<Date | null>(null);
    const anker = useRef<number | null>(null);
    const { start, tage } = fenster;
    const schluessel = `${formatDatum(start)}|${tage}|${aktualisierung}|${version}`;

    useEffect(() => {
        let aktuell = true;
        ladeBelegung(formatDatum(start), formatDatum(plusTage(start, tage - 1))).then(
            daten => { if (aktuell) setStand({ schluessel, daten, fehler: '' }); },
            e => { if (aktuell) setStand(alt => ({ schluessel, daten: alt?.daten ?? null, fehler: e instanceof Error ? e.message : 'Die Belegung konnte nicht geladen werden.' })); },
        );
        return () => { aktuell = false; };
    }, [start, tage, schluessel]);

    // Die Liste der letzten Änderungen (für „Rückgängig") lädt mit jeder Änderung neu
    useEffect(() => {
        let aktuell = true;
        ladeAenderungen(10).then(l => { if (aktuell && Array.isArray(l)) setAenderungen(l); }, () => undefined);
        return () => { aktuell = false; };
    }, [aktualisierung, version]);

    const laedt = stand?.schluessel !== schluessel;
    const heute = formatDatum(new Date());
    const spalten = useMemo(() => Array.from({ length: tage }, (_, i) => plusTage(start, i)), [start, tage]);
    const daten = stand?.daten ?? null;
    const mitStunden = zoom <= 14;
    const tagBreite = breiteJeTag(zoom);
    const freieTage = useMemo(() => new Map<string, FreierTag>((daten?.freieTage ?? []).map(t => [t.datum, t])), [daten]);
    const baender = useMemo(() => (tagBreite < BREITE_MIT_NAMEN ? monatsBaender(start, tage) : []), [start, tage, tagBreite]);
    const auftragsZeilen = useMemo(() => ordneAuftraege(auftraege, sortierung, nurVerspaetete, erledigteZeigen).map(a => ({ auftrag: a, zeile: auftragsZeile(a) })), [auftraege, sortierung, nurVerspaetete, erledigteZeigen]);

    /** Wie viele Tage gerade sichtbar sind (ohne Messung, etwa im Test, so viele wie eingestellt). */
    const sichtbareTage = () => {
        const breite = tafel.current?.clientWidth ?? 0;
        return breite > NAMEN_B ? Math.max(1, Math.round((breite - NAMEN_B) / tagBreite)) : zoom;
    };
    const scrolleNach = (links: number, glatt: boolean) => {
        const el = tafel.current;
        if (!el) return;
        const x = Math.max(0, links);
        if (typeof el.scrollTo === 'function') el.scrollTo({ left: x, behavior: glatt ? 'smooth' : 'auto' });
        else el.scrollLeft = x;
    };

    // Erstes Anzeigen: heute (einen Tag davor) an den linken Rand
    useLayoutEffect(() => {
        if (!daten || gescrollt.current || !tafel.current) return;
        gescrollt.current = true;
        const idx = Math.max(0, tageZwischen(start, new Date(new Date().getFullYear(), new Date().getMonth(), new Date().getDate())) - 1);
        scrolleNach(idx * tagBreite, false);
        setErsterTag(idx);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [daten]);
    // Nach dem Erweitern nach links bleibt die Ansicht an derselben Stelle; ein Sprung außerhalb des Fensters landet am Zieltag
    useLayoutEffect(() => {
        const el = tafel.current;
        if (!el) return;
        if (ziel.current) {
            const idx = Math.max(0, tageZwischen(fenster.start, ziel.current) - 1);
            ziel.current = null;
            korrektur.current = 0;
            scrolleNach(idx * tagBreite, false);
            setErsterTag(idx);
        } else if (korrektur.current !== 0) {
            el.scrollLeft += korrektur.current;
            korrektur.current = 0;
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [fenster]);
    // Anderer Zoom: derselbe Tag bleibt links stehen
    useLayoutEffect(() => {
        if (anker.current == null) return;
        const tag = anker.current;
        anker.current = null;
        scrolleNach(tag * tagBreite, false);
         
    }, [tagBreite]);

    /** Am Rand des Fensters werden 4 Wochen nachgeladen; das Fenster bleibt höchstens ein Jahr breit. */
    const beiScroll = () => {
        const el = tafel.current;
        if (!el) return;
        const idx = Math.floor(el.scrollLeft / tagBreite);
        setErsterTag(alt => (alt === idx ? alt : idx));
        if (el.scrollLeft < RAND * tagBreite && el.scrollWidth > el.clientWidth) {
            korrektur.current += NACHLADEN * tagBreite;
            setFenster(f => ({ start: plusTage(f.start, -NACHLADEN), tage: Math.min(f.tage + NACHLADEN, MAX_TAGE) }));
        } else if (el.scrollLeft + el.clientWidth > el.scrollWidth - RAND * tagBreite && el.scrollWidth > el.clientWidth) {
            const zuviel = Math.max(0, tage + NACHLADEN - MAX_TAGE);
            korrektur.current -= zuviel * tagBreite;
            setFenster(f => ({ start: plusTage(f.start, zuviel), tage: Math.min(f.tage + NACHLADEN, MAX_TAGE) }));
        }
    };

    /** Zu einem Tag springen; liegt er außerhalb des geladenen Fensters, wird ein neues Fenster um ihn geladen. */
    const springeZu = (tag: Date, glatt = true) => {
        const idx = tageZwischen(start, tag);
        if (idx >= 0 && idx < tage) scrolleNach(Math.max(0, idx - 1) * tagBreite, glatt);
        else {
            ziel.current = tag;
            setFenster({ start: plusTage(montagVon(tag), -NACHLADEN), tage: START_TAGE });
        }
    };
    const schiebe = (richtung: 1 | -1) => scrolleNach((tafel.current?.scrollLeft ?? 0) + richtung * zoom * tagBreite, true);

    /** In welcher Zeile liegt die Höhe {@code y} (Bildschirm)? */
    const zeileBei = (y: number): string | null => {
        for (const el of Array.from(tafel.current?.querySelectorAll<HTMLElement>('[data-zeile]') ?? [])) {
            const r = el.getBoundingClientRect();
            if (y >= r.top && y < r.bottom) return el.dataset.zeile ?? null;
        }
        return null;
    };

    /** Der Stand des Vorgangs, den die Ansicht gerade zeigt (Schutz vor gleichzeitigem Bearbeiten). */
    const vorgangVon = (e: BelegungEintrag) => auftraege.find(a => a.projektId === e.projektId)?.vorgaenge.find(v => v.id === Number(e.id.slice(1)));

    /** Ziehen an einem Rand: links = „frühestens ab", rechts = Dauer (Wartevorgang) oder Aufwand (Arbeitsvorgang) ändern. Beides mit Vorschau. */
    const randZiehen = async (e: BelegungEintrag, seite: 'links' | 'rechts', dxTage: number) => {
        const vorgang = vorgangVon(e);
        const nummer = Number(e.id.slice(1));
        try {
            if (seite === 'links') {
                const neuerStart = verschobenerStart(e.von, dxTage, arbeitstage);
                if (neuerStart && await verschiebeVorgang({ id: nummer, titel: e.titel, fest: e.fest, stand: vorgang?.stand }, neuerStart)) setVersion(v => v + 1);
                return;
            }
            if (!vorgang) return;
            const groesse = neueGroesse(vorgang, arbeitstageDelta(e.bis, dxTage, arbeitstage));
            if (!groesse) return;
            if (!await vorschau.frage(nummer, groesse.probe, `„${e.titel}": ${groesse.text}.`)) return;
            onAuftrag?.(await aendereVorgang(nummer, { ...groesse.aenderung, stand: vorgang.stand }));
            toast.success(`„${e.titel}": ${groesse.text}.`);
            setVersion(v => v + 1);
        } catch (fehler) {
            toast.error(fehler instanceof Error ? fehler.message : 'Das Ändern hat nicht geklappt.');
        }
    };

    const ruecknahme = aenderungen.find(a => a.rueckgaengigMoeglich) ?? null;
    const nimmLetzteZurueck = async () => {
        if (!ruecknahme) return;
        setArbeitet(true);
        try {
            const auftrag = await nimmZurueck(ruecknahme.id);
            onAuftrag?.(auftrag);
            toast.success(`Zurückgenommen: ${ruecknahme.beschreibung}`);
            setVersion(v => v + 1);
        } catch (fehler) {
            toast.error(fehler instanceof Error ? fehler.message : 'Die Änderung konnte nicht zurückgenommen werden.');
        } finally {
            setArbeitet(false);
        }
    };

    const ziehe = async (e: BelegungEintrag, quelle: string, dx: number, y: number) => {
        if (!daten) return;
        const breite = gitter.current?.getBoundingClientRect().width || 0;
        const dxTage = dx / (breite > 0 ? breite / tage : tagBreite);
        const ziel = zeileBei(y) ?? quelle;
        const nummer = Number(e.id.slice(1));
        let geaendert = false;
        try {
            if (e.quelle === 'VORGANG') {
                const neuerStart = verschobenerStart(e.von, dxTage, arbeitstage);
                const zuweisung = neueZuweisung(quelle, ziel, aktuelleZuweisung(daten, e.id));
                if (neuerStart && !(await verschiebeVorgang({ id: nummer, titel: e.titel, fest: e.fest, stand: vorgangVon(e)?.stand }, neuerStart))) return;
                geaendert = neuerStart != null;
                if (zuweisung) {
                    const auftrag = await weiseZu(nummer, { ...zuweisung, prioritaet: null }, neuerStart ? undefined : vorgangVon(e)?.stand);
                    onAuftrag?.(auftrag);
                    toast.success(`„${e.titel}" steht jetzt in der Zeile ${daten.zeilen.find(z => z.schluessel === ziel)?.name ?? ''}.`);
                    geaendert = true;
                }
            } else if (e.quelle === 'TERMIN') {
                const neu = verschobenerTermin(e, dxTage);
                const personWechsel = quelle[0] === 'M' && ziel[0] === 'M' && quelle !== ziel;
                if (!neu && !personWechsel) return;
                await verschiebeTermin(nummer, {
                    datum: neu?.datum ?? e.von, von: neu ? neu.von : e.vonZeit, bis: neu ? neu.bis : e.bisZeit,
                    vonMitarbeiterId: personWechsel ? Number(quelle.slice(1)) : null, nachMitarbeiterId: personWechsel ? Number(ziel.slice(1)) : null,
                });
                toast.success(`„${e.titel}" ist verschoben.`);
                geaendert = true;
            }
        } catch (fehler) {
            toast.error(fehler instanceof Error ? fehler.message : 'Das Verschieben hat nicht geklappt.');
        }
        if (geaendert) setVersion(v => v + 1);
    };

    const klappe = (gruppe: string) => setZugeklappt(alt => {
        const neu = new Set(alt);
        if (!neu.delete(gruppe)) neu.add(gruppe);
        return neu;
    });

    /** Neuer Termin für die gewählten Tage in einer Zeile (Mitarbeiter oder Firma). */
    const neuerTermin = (zeile: BelegungZeile, von: string, bis: string) =>
        setTerminDialog({ terminId: null, von, bis, mitarbeiterId: zeile.schluessel[0] === 'M' ? Number(zeile.schluessel.slice(1)) : null });

    const sichtbarBis = Math.min(tage - 1, ersterTag + sichtbareTage() - 1);

    return (
        <section aria-label="Belegung nach Mitarbeitern und Ressourcen" className="space-y-3">
            <div className="flex flex-wrap items-center gap-2">
                <select aria-label="Zeitraum" value={zoom} onChange={e => {
                    const neu = Number(e.target.value);
                    anker.current = ersterTag;
                    // Das Fenster muss den sichtbaren Zeitraum plus Reserve enthalten, sonst lädt die Ansicht beim Scrollen endlos nach
                    setFenster(f => (f.tage >= Math.min(MAX_TAGE, neu + 2 * NACHLADEN) ? f : { start: f.start, tage: Math.min(MAX_TAGE, neu + 2 * NACHLADEN) }));
                    setZoom(neu);
                }} className="rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-sm">
                    {ZEITSTUFEN.map(z => <option key={z.tage} value={z.tage}>{z.text}</option>)}
                </select>
                <Button variant="outline" size="sm" aria-label="Zeitraum zurück" onClick={() => schiebe(-1)}><ChevronLeft className="h-4 w-4" aria-hidden="true" /></Button>
                <Button variant="outline" size="sm" onClick={() => springeZu(new Date())}>Heute</Button>
                <Button variant="outline" size="sm" aria-label="Zeitraum vor" onClick={() => schiebe(1)}><ChevronRight className="h-4 w-4" aria-hidden="true" /></Button>
                <input type="date" aria-label="Zu Datum springen" value={formatDatum(spalten[Math.min(ersterTag, tage - 1)] ?? start)} onChange={e => { if (e.target.value) springeZu(parseDatum(e.target.value)); }}
                    className="rounded-lg border border-slate-300 bg-white px-2 py-1.5 text-sm" />
                <h2 className="flex-1 text-center text-lg font-semibold text-slate-900">{zeitraumTitel(spalten[Math.min(ersterTag, tage - 1)] ?? start, spalten[Math.max(0, sichtbarBis)] ?? start)}</h2>
                <Button variant={bearbeiten ? 'default' : 'outline'} size="sm" aria-pressed={bearbeiten} onClick={() => onBearbeiten?.(!bearbeiten)}
                    className={bearbeiten ? 'bg-rose-600 text-white border border-rose-600 hover:bg-rose-700' : ''}
                    title={bearbeiten ? 'Bearbeiten beenden' : 'Bearbeiten einschalten: Dann lässt sich ziehen und per Klick ein Termin anlegen'}>
                    <Pencil className="mr-1 h-4 w-4" aria-hidden="true" /> {bearbeiten ? 'Bearbeiten ist an' : 'Bearbeiten'}
                </Button>
                <Button variant="outline" size="sm" onClick={() => void nimmLetzteZurueck()} disabled={!ruecknahme || arbeitet}
                    title={ruecknahme ? `Zurücknehmen: ${ruecknahme.beschreibung}` : 'Es gibt nichts zurückzunehmen'}>
                    <Undo2 className="mr-1 h-4 w-4" aria-hidden="true" /> Rückgängig
                </Button>
                <div className="relative">
                    <Button variant="outline" size="sm" aria-expanded={listeOffen} onClick={() => setListeOffen(o => !o)} title="Die letzten Änderungen von Hand"><History className="mr-1 h-4 w-4" aria-hidden="true" /> Änderungen</Button>
                    {listeOffen && (
                        <div className="absolute right-0 z-40 mt-1 w-96 max-w-[90vw] rounded-lg border border-slate-200 bg-white p-2 shadow-lg" data-testid="aenderungsliste">
                            <p className="px-2 pb-1 text-xs font-semibold text-slate-700">Letzte Änderungen</p>
                            {aenderungen.length === 0 && <p className="px-2 py-1 text-xs text-slate-500">Noch keine Änderung von Hand.</p>}
                            <ul className="max-h-72 divide-y divide-slate-100 overflow-y-auto text-xs">
                                {aenderungen.map(a => (
                                    <li key={a.id} className={`px-2 py-1.5 ${a.zurueckgenommen ? 'text-slate-400 line-through' : 'text-slate-800'}`}>
                                        <p>{a.beschreibung}</p>
                                        <p className="text-[11px] text-slate-500 no-underline">{new Date(a.zeitpunkt).toLocaleString('de-DE', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })} Uhr · {a.benutzer ?? 'unbekannt'}{a.zurueckgenommen ? ' · zurückgenommen' : ''}</p>
                                    </li>
                                ))}
                            </ul>
                        </div>
                    )}
                </div>
                <Button variant="outline" size="sm" onClick={() => setTerminDialog({ terminId: null, von: heute, bis: heute, mitarbeiterId: null })}><Plus className="mr-1 h-4 w-4" aria-hidden="true" /> Termin</Button>
                {laedt && daten && <Loader2 className="h-4 w-4 text-slate-500 motion-safe:animate-spin" role="status" aria-label="Wird aktualisiert" />}
            </div>

            <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-600" role="group" aria-label="Filter und Sortierung">
                <label className="flex items-center gap-1.5">Aufträge sortieren nach
                    <select aria-label="Aufträge sortieren nach" value={sortierung} onChange={e => setSortierung(e.target.value as Sortierung)} className="rounded border border-slate-300 bg-white px-2 py-1 text-xs">
                        <option value="LIEFERZEIT">Lieferzeit</option>
                        <option value="VERSPAETUNG">Verspätung</option>
                        <option value="WICHTIGKEIT">Wichtigkeit</option>
                    </select>
                </label>
                <label className="flex items-center gap-1.5"><input type="checkbox" className="accent-rose-600" checked={nurVerspaetete} onChange={e => setNurVerspaetete(e.target.checked)} /> nur verspätete</label>
                <label className="flex items-center gap-1.5"><input type="checkbox" className="accent-rose-600" checked={erledigteZeigen} onChange={e => setErledigteZeigen(e.target.checked)} /> erledigte Aufträge</label>
                <span className="text-slate-400" aria-hidden="true">|</span>
                <label className="flex items-center gap-1.5"><input type="checkbox" className="accent-rose-600" checked={zeigeTermine} onChange={e => setZeigeTermine(e.target.checked)} /> Termine</label>
                <label className="flex items-center gap-1.5"><input type="checkbox" className="accent-rose-600" checked={zeigeAbwesenheit} onChange={e => setZeigeAbwesenheit(e.target.checked)} /> Urlaub und Abwesenheit</label>
                <label className="flex items-center gap-1.5"><input type="checkbox" className="accent-rose-600" checked={zeigeWartung} onChange={e => setZeigeWartung(e.target.checked)} /> fällige Wartungen</label>
            </div>

            {stand?.fehler && !laedt && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{stand.fehler}</p>}
            {laedt && !daten && <p role="status" className="flex items-center gap-2 text-sm text-slate-600"><Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> Die Belegung wird geladen …</p>}

            {daten && (
                <div ref={tafel} onScroll={beiScroll} data-testid="belegung"
                    className={`overflow-x-auto rounded-xl border border-slate-200 bg-white ${greift ? 'cursor-grabbing select-none' : ''}`}
                    // Mit gedrückter Maus auf einer freien Stelle ziehen verschiebt die Ansicht (nicht auf Balken, Knöpfen und Eingaben)
                    onPointerDown={e => {
                        if (e.button !== 0 || e.pointerType === 'touch' || (e.target as HTMLElement).closest('button, a, input, select, textarea, [data-nopan]')) return;
                        verschieben.current = { x: e.clientX, links: tafel.current?.scrollLeft ?? 0, id: e.pointerId, bewegt: false };
                    }}
                    onPointerMove={e => {
                        const v = verschieben.current;
                        if (!v || !tafel.current) return;
                        const dx = e.clientX - v.x;
                        if (!v.bewegt && Math.abs(dx) < 4) return;
                        if (!v.bewegt) { v.bewegt = true; setGreift(true); tafel.current.setPointerCapture?.(v.id); }
                        tafel.current.scrollLeft = v.links - dx;
                    }}
                    onPointerUp={e => { const v = verschieben.current; verschieben.current = null; if (v?.bewegt) { tafel.current?.releasePointerCapture?.(v.id); setGreift(false); } else if (v) e.stopPropagation(); }}
                    onPointerCancel={() => { verschieben.current = null; setGreift(false); }}>
                    <div style={{ width: NAMEN_B + tage * tagBreite }}>
                        <div className="flex border-b border-slate-300 bg-white text-xs text-slate-700">
                            <div className="sticky left-0 z-20 flex shrink-0 items-center border-r border-slate-200 bg-white px-3 font-semibold" style={{ width: NAMEN_B }}>Ressourcen</div>
                            <div ref={gitter} data-testid="belegung-gitter" className="shrink-0" style={{ width: tage * tagBreite }}>
                              {baender.length > 0 && (
                                <div className="flex border-b border-slate-200" data-testid="monatsbaender">
                                    {baender.map(b => (
                                        <div key={b.von} className="overflow-hidden whitespace-nowrap border-l border-slate-200 px-1 py-1 font-semibold" style={{ width: b.anzahl * tagBreite }} title={b.text}>
                                            {b.anzahl * tagBreite >= 90 ? b.text : b.anzahl * tagBreite >= 40 ? b.text.slice(0, 3) : ''}
                                        </div>
                                    ))}
                                </div>
                              )}
                              <div className="flex">
                                {spalten.map(d => {
                                    const iso = formatDatum(d);
                                    const frei = freieTage.get(iso);
                                    const rand = tagBreite >= BREITE_MIT_ZAHL || d.getDay() === 1 ? 'border-l border-slate-200' : '';
                                    return (
                                        <div key={iso} title={frei ? frei.name : undefined} className={`flex-1 ${rand} ${istWochenende(d, arbeitstage) ? 'bg-slate-100' : ''} ${frei ? (frei.art === 'FEIERTAG' ? 'bg-amber-100' : 'bg-orange-100') : ''} ${iso === heute ? 'bg-yellow-50' : ''}`}>
                                            {tagBreite >= BREITE_MIT_ZAHL && (
                                            <p className={`px-1 py-1.5 text-center font-semibold ${iso === heute ? 'text-slate-900' : ''}`}>
                                                {mitStunden ? `${WOCHENTAGE[d.getDay()]}., ${d.getDate()}.${d.getMonth() + 1}. KW ${kalenderwoche(d)}`
                                                    : tagBreite >= BREITE_MIT_NAMEN ? `${WOCHENTAGE[d.getDay()]} ${d.getDate()}.${d.getMonth() + 1}.` : d.getDate()}
                                            </p>
                                            )}
                                            {mitStunden && frei && tagBreite >= 100 && <p className="truncate px-1 pb-0.5 text-center text-[10px] font-medium text-amber-900">{frei.name}</p>}
                                            {mitStunden && (
                                                <div className="flex border-t border-slate-200 text-center">
                                                    {STUNDEN.map((s, i) => <span key={s} className={`flex-1 py-1 font-semibold ${i > 0 ? 'border-l border-slate-200' : ''}`}>{s}</span>)}
                                                </div>
                                            )}
                                        </div>
                                    );
                                })}
                              </div>
                            </div>
                        </div>
                        {GRUPPEN.map(g => {
                            const eigene = daten.zeilen.filter(z => z.gruppe === g.gruppe);
                            const zeilen = g.gruppe === 'ALLGEMEIN' ? [...auftragsZeilen.map(x => x.zeile), ...eigene] : eigene;
                            if (zeilen.length === 0) return null;
                            const offen = !zugeklappt.has(g.gruppe);
                            return (
                                <div key={g.gruppe}>
                                    {/* Der Knopf ist nur so breit wie die Namensspalte: Ein klebender Knopf in voller Breite könnte beim Scrollen nicht mitwandern und wäre dann unsichtbar */}
                                    <div className="border-b border-slate-200 bg-slate-100">
                                        <button type="button" aria-expanded={offen} onClick={() => klappe(g.gruppe)} style={{ width: NAMEN_B }}
                                            className="sticky left-0 flex items-center gap-2 bg-slate-100 px-3 py-1.5 text-left text-sm font-semibold text-slate-800 hover:bg-slate-200 focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-rose-400">
                                            <span className="flex h-4 w-4 items-center justify-center rounded-sm border border-slate-500 bg-white" aria-hidden="true">
                                                {offen ? <Minus className="h-3 w-3" /> : <Plus className="h-3 w-3" />}
                                            </span>
                                            {g.text}
                                        </button>
                                    </div>
                                    {offen && zeilen.map(z => (
                                        <ZeileAnsicht key={z.schluessel} zeile={z} auftrag={auftragsZeilen.find(x => x.zeile.schluessel === z.schluessel)?.auftrag} start={start} spalten={spalten} heute={heute}
                                            mitStunden={mitStunden} arbeitstage={arbeitstage} tagBreite={tagBreite} freieTage={freieTage} bearbeiten={bearbeiten}
                                            zeige={{ termine: zeigeTermine, abwesenheit: zeigeAbwesenheit, wartung: zeigeWartung }}
                                            onVorgang={onVorgang} onTermin={id => setTerminDialog({ terminId: id, von: heute, bis: heute, mitarbeiterId: null })} onTage={neuerTermin} onZiehe={ziehe} onRand={randZiehen} onAuftragOeffnen={onAuftragOeffnen}
                                            onWoche={montag => { setZoom(7); springeZu(parseDatum(montag)); }} />
                                    ))}
                                </div>
                            );
                        })}
                    </div>
                </div>
            )}
            <ul className="flex flex-wrap gap-x-5 gap-y-1 text-xs text-slate-500" aria-label="Zeichenerklärung">
                <li>Nach links und rechts verschieben: Scrollleiste, Mausrad mit Umschalttaste oder die Pfeile oben (es geht endlos weiter)</li>
                <li>Zeile mit Auftragsnamen: alle Vorgänge eines Auftrags. Raute = Lieferzeit, gestrichelte Linie = Zieltermin</li>
                <li>Farbiger Balken: Vorgang der Plantafel (Klick zeigt die Einzelheiten; zum Ändern „Bearbeiten" einschalten)</li>
                <li>Gelb getönter Tag: Feiertag. Orange: Betriebsurlaub</li>
                <li>Türkiser Kasten: fällige Wartung, je Objekt zusammengefasst (Klick öffnet die Anlage bzw. die Wartungsübersicht)</li>
                <li>Pfeile in den Auftragszeilen: Der Vorgang am Pfeilende beginnt nach dem Ende des Vorgangs am Pfeilanfang</li>
                <li>Ansicht verschieben: mit gedrückter Maus auf eine freie Stelle ziehen (im Bearbeiten-Modus in den Kopfzeilen und Auftragszeilen)</li>
                <li>Heller Kasten: Kalendertermin (Klick öffnet ihn; über mehrere Tage = durchgehender Termin)</li>
                <li>Grauer Balken: Urlaub, Krank und Ähnliches</li>
                <li>Warnzeichen: doppelt belegt oder in einer Abwesenheit eingeplant</li>
                <li>Pin: fester Start</li>
                <li>Mit eingeschaltetem „Bearbeiten": Auf einen freien Tag klicken legt einen Termin an, über mehrere Tage ziehen einen Termin über alle gewählten Tage (nicht bei Ressourcen).</li>
                <li>Mit eingeschaltetem „Bearbeiten": Am linken Rand eines Vorgangs ziehen = frühestens ab, am rechten Rand ziehen = länger oder kürzer (Aufwand bzw. Dauer). Vor dem Speichern zeigt das Programm, was sich verschiebt; „Rückgängig" nimmt die letzte Änderung zurück.</li>
                <li>Mit eingeschaltetem „Bearbeiten": Einträge mit der Maus ziehen (anderer Tag, andere Uhrzeit oder andere Zeile). Ein gezogener Vorgang beginnt „frühestens" an dem Tag und rückt mit der Kette nach hinten, wenn Vorgänger später fertig werden.</li>
            </ul>
            {vorschau.dialog}
            {terminDialog && (
                <TerminDialog offen terminId={terminDialog.terminId} vorgabe={{ von: terminDialog.von, bis: terminDialog.bis, mitarbeiterId: terminDialog.mitarbeiterId }}
                    onSchliessen={() => setTerminDialog(null)} onGeaendert={() => setVersion(v => v + 1)} />
            )}
        </section>
    );
}

/** Die Pfeile zwischen den Vorgängen eines Auftrags (wie im Gantt-Diagramm): vom Ende des Vorgängers zum Beginn des Nachfolgers. */
function AbhaengigkeitsPfeile({ platziert, breiteJeTag: breite, tage }: { platziert: Platziert[]; breiteJeTag: number; tage: number }) {
    const { liste, hoehe } = useMemo(() => {
        const balken = new Map<number, { x1: number; x2: number; y: number }>();
        const abh = new Map<number, number[]>();
        for (const p of platziert) {
            if (p.eintrag.quelle !== 'VORGANG') continue;
            const id = Number(p.eintrag.id.slice(1));
            balken.set(id, { x1: p.von * breite + 1, x2: p.bis * breite - 1, y: 4 + p.bahn * KARTE_H + (KARTE_H - 4) / 2 });
            if (p.eintrag.vorgaengerIds?.length) abh.set(id, p.eintrag.vorgaengerIds);
        }
        return { liste: pfeile(balken, abh), hoehe: Math.max(1, ...platziert.map(p => p.bahn + 1)) * KARTE_H + 8 };
    }, [platziert, breite]);
    if (liste.length === 0) return null;
    return (
        <svg className="pointer-events-none absolute left-0 top-0" width={tage * breite} height={hoehe} aria-hidden="true" data-testid="pfeile">
            <defs>
                <marker id="pfeilspitze" viewBox="0 0 8 8" refX="7" refY="4" markerWidth="7" markerHeight="7" orient="auto"><path d="M0,0 L8,4 L0,8 z" fill="#475569" /></marker>
            </defs>
            {liste.map(p => <path key={`${p.von}-${p.nach}`} d={p.pfad} fill="none" stroke="#475569" strokeWidth="1.5" markerEnd="url(#pfeilspitze)" data-testid={`pfeil-${p.von}-${p.nach}`} />)}
        </svg>
    );
}

function WartungLink({ id, objekt, klasse, stil, beschreibung, kinder }: { id: number; objekt: boolean; klasse: string; stil: React.CSSProperties; beschreibung: string; kinder: React.ReactNode }) {
    const imRouter = useInRouterContext();
    const ziel = objekt ? '/wartung' : `/wartung/anlagen/${id}`;
    return imRouter
        ? <Link to={ziel} aria-label={beschreibung} title={beschreibung} style={stil} className={klasse}>{kinder}</Link>
        : <a href={ziel} aria-label={beschreibung} title={beschreibung} style={stil} className={klasse}>{kinder}</a>;
}

interface Sichtbar { termine: boolean; abwesenheit: boolean; wartung: boolean }

function ZeileAnsicht({ zeile, auftrag, start, spalten, heute, mitStunden, arbeitstage, tagBreite, freieTage, bearbeiten, zeige, onVorgang, onTermin, onTage, onZiehe, onRand, onAuftragOeffnen, onWoche }: {
    zeile: BelegungZeile; auftrag?: Auftrag; start: Date; spalten: Date[]; heute: string; mitStunden: boolean; arbeitstage?: readonly number[]; tagBreite: number; freieTage: Map<string, FreierTag>;
    bearbeiten: boolean; zeige: Sichtbar; onVorgang: Props['onVorgang'];
    onTermin: (terminId: number) => void; onTage: (zeile: BelegungZeile, von: string, bis: string) => void;
    onZiehe: (e: BelegungEintrag, quelle: string, dx: number, y: number) => void; onRand: (e: BelegungEintrag, seite: 'links' | 'rechts', dxTage: number) => void; onAuftragOeffnen?: (projektId: number) => void; onWoche: (montag: string) => void;
}) {
    const sichtbar = useMemo(() => {
        const gefiltert = zeile.eintraege.filter(e => (e.quelle !== 'TERMIN' || zeige.termine) && (e.quelle !== 'ABWESENHEIT' || zeige.abwesenheit) && (e.quelle !== 'WARTUNG' || zeige.wartung));
        return tagBreite < BREITE_TERMINE_EINZELN ? verdichteTermine(gefiltert) : gefiltert;
    }, [zeile.eintraege, zeige, tagBreite]);
    const { platziert, bahnen } = useMemo(() => platziere(sichtbar, start, spalten.length), [sichtbar, start, spalten.length]);
    const kannTermin = bearbeiten && zeile.gruppe !== 'RESSOURCE' && zeile.schluessel[0] !== 'P' && zeile.schluessel !== 'W';
    const feld = useRef<HTMLDivElement>(null);
    const erster = useRef<number | null>(null);
    const [auswahl, setAuswahl] = useState<{ a: number; b: number } | null>(null);
    /** Welcher Tag liegt unter der Maus? */
    const tagBei = (x: number) => {
        const r = feld.current?.getBoundingClientRect();
        const breite = r && r.width > 0 ? r.width / spalten.length : tagBreite;
        return Math.min(spalten.length - 1, Math.max(0, Math.floor((x - (r?.left ?? 0)) / breite)));
    };
    const ende = () => { erster.current = null; setAuswahl(null); };
    const auswahlBindung = kannTermin ? {
        onPointerDown: (e: React.PointerEvent<HTMLElement>) => {
            if (e.button !== 0) return;
            e.stopPropagation(); // hier zieht man einen Termin auf, die Ansicht verschiebt sich nicht
            const i = tagBei(e.clientX);
            erster.current = i;
            setAuswahl({ a: i, b: i });
            e.currentTarget.setPointerCapture?.(e.pointerId);
        },
        onPointerMove: (e: React.PointerEvent<HTMLElement>) => {
            if (erster.current == null) return;
            const i = tagBei(e.clientX);
            setAuswahl(alt => (alt && alt.b !== i ? { ...alt, b: i } : alt));
        },
        onPointerUp: (e: React.PointerEvent<HTMLElement>) => {
            const a = erster.current;
            if (a == null) return;
            e.currentTarget.releasePointerCapture?.(e.pointerId);
            const b = tagBei(e.clientX);
            ende();
            onTage(zeile, formatDatum(spalten[Math.min(a, b)]), formatDatum(spalten[Math.max(a, b)]));
        },
        onPointerCancel: ende,
    } : {};
    const prozent = (spalte: number) => `${(spalte * 100) / spalten.length}%`;
    const lieferSpalte = auftrag?.liefertermin ? spalteVon(start, auftrag.liefertermin) + 1 : null;
    const zielSpalte = auftrag?.zielDatum ? spalteVon(start, auftrag.zielDatum) + 1 : null;
    const imFenster = (spalte: number | null) => spalte != null && spalte >= 0 && spalte <= spalten.length;
    const ampel = auftrag ? AMPEL[auftrag.ampel] : null;
    return (
        <div className="flex border-b border-slate-200" data-testid={`zeile-${zeile.schluessel}`} data-zeile={zeile.schluessel}>
            <div className="sticky left-0 z-20 shrink-0 border-r border-slate-200 bg-white px-3 py-2 pl-6 text-sm text-slate-900" style={{ width: NAMEN_B }}>
                {auftrag && onAuftragOeffnen ? (
                    <button type="button" onClick={() => onAuftragOeffnen(auftrag.projektId)} title="Den ganzen Auftrag öffnen: Termine, Wichtigkeit, Vorgänge, Entfernen" aria-label={`Auftrag ${zeile.name} öffnen`}
                        className="block w-full truncate rounded text-left font-medium hover:text-rose-700 hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-rose-600">
                        {zeile.name}{auftrag.auftragsnummer ? <span className="ml-1 text-xs font-normal text-slate-500">({auftrag.auftragsnummer})</span> : null}
                    </button>
                ) : <p className="truncate" title={zeile.name}>{zeile.name}{auftrag?.auftragsnummer ? <span className="ml-1 text-xs text-slate-500">({auftrag.auftragsnummer})</span> : null}</p>}
                {auftrag && ampel && (
                    <p className="mt-0.5 flex flex-wrap items-center gap-1 text-[11px] text-slate-600" data-testid={`auftrag-info-${auftrag.projektId}`}>
                        <span className={`rounded px-1.5 py-0.5 font-medium ${ampel.klasse}`}>{ampel.text}{auftrag.ampel === 'VERSPAETET' && auftrag.verspaetungTage ? ` +${auftrag.verspaetungTage} Tg.` : ''}</span>
                        <span>Lieferzeit {auftrag.liefertermin ? anzeigeKurz(auftrag.liefertermin) : '–'}</span>
                    </p>
                )}
            </div>
            <div ref={feld} data-testid={`feld-${zeile.schluessel}`} className="relative flex shrink-0" style={{ width: spalten.length * tagBreite, minHeight: bahnen * KARTE_H + 8 }}>
                <div className={`flex flex-1 ${kannTermin ? 'cursor-cell touch-none select-none' : ''}`} data-testid={`tage-${zeile.schluessel}`} title={kannTermin ? 'Klicken: Termin anlegen. Über mehrere Tage ziehen: Termin über alle gewählten Tage.' : undefined} {...auswahlBindung}>
                    {spalten.map(d => {
                        const iso = formatDatum(d);
                        const frei = freieTage.get(iso);
                        const rand = tagBreite >= BREITE_MIT_ZAHL || d.getDay() === 1 ? 'border-l border-slate-200' : '';
                        return (
                            <div key={iso} className={`flex-1 ${rand} ${istWochenende(d, arbeitstage) ? 'bg-slate-100' : ''} ${frei ? (frei.art === 'FEIERTAG' ? 'bg-amber-50' : 'bg-orange-50') : ''} ${iso === heute ? 'bg-yellow-50' : ''}`}
                                style={mitStunden ? { backgroundImage: STUNDENLINIEN } : undefined} aria-hidden="true" />
                        );
                    })}
                </div>
                {auswahl && (
                    <div className="pointer-events-none absolute inset-y-0 rounded-sm bg-rose-200/50 ring-1 ring-rose-400" data-testid="tage-auswahl"
                        style={{ left: `${(Math.min(auswahl.a, auswahl.b) * 100) / spalten.length}%`, width: `${((Math.abs(auswahl.b - auswahl.a) + 1) * 100) / spalten.length}%` }} />
                )}
                {auftrag && imFenster(zielSpalte) && (
                    <div className="pointer-events-none absolute inset-y-0 z-10 border-l-2 border-dashed border-rose-500" style={{ left: prozent(zielSpalte as number) }} data-testid={`ziel-${auftrag.projektId}`}
                        title={`Zieltermin ${anzeigeKurz(auftrag.zielDatum)}`} />
                )}
                {auftrag && <AbhaengigkeitsPfeile platziert={platziert} breiteJeTag={tagBreite} tage={spalten.length} />}
                {platziert.map(p => (
                    <EintragAnsicht key={`${p.eintrag.id}-${p.bahn}`} p={p} tage={spalten.length} quelle={zeile.schluessel} heuteSpalte={spalten.findIndex(d => formatDatum(d) === heute)} bearbeiten={bearbeiten} onVorgang={onVorgang} onTermin={onTermin} onZiehe={onZiehe} onWoche={onWoche}
                        onRand={(e, seite, dxPx) => {
                            const r = feld.current?.getBoundingClientRect();
                            onRand(e, seite, dxPx / (r && r.width > 0 ? r.width / spalten.length : tagBreite));
                        }} />
                ))}
                {auftrag && imFenster(lieferSpalte) && (
                    <div className={`pointer-events-none absolute z-10 h-3 w-3 -translate-x-1/2 rotate-45 ${auftrag.ampel === 'VERSPAETET' ? 'bg-rose-600' : 'bg-slate-800'}`} style={{ left: prozent(lieferSpalte as number), bottom: 3 }}
                        data-testid={`lieferzeit-${auftrag.projektId}`} title={`Lieferzeit ${anzeigeKurz(auftrag.liefertermin)}`} />
                )}
            </div>
        </div>
    );
}

/** Der Griff am linken oder rechten Rand eines Vorgangs: Ziehen ändert den Beginn („frühestens ab") oder die Größe (Dauer bzw. Aufwand). Ein Klick darauf öffnet nichts. */
function RandGriff({ seite, titel, onFertig }: { seite: 'links' | 'rechts'; titel: string; onFertig: (dxPx: number) => void }) {
    const start = useRef<number | null>(null);
    const [dx, setDx] = useState(0);
    return (
        <span role="presentation" title={titel} data-testid={`griff-${seite}`} style={dx !== 0 ? { transform: `translateX(${dx}px)`, backgroundColor: 'rgba(255,255,255,0.9)' } : undefined}
            className={`absolute inset-y-0 z-20 w-2 cursor-ew-resize touch-none ${seite === 'links' ? 'left-0 rounded-l-md' : 'right-0 rounded-r-md'} ${dx !== 0 ? '' : 'hover:bg-white/50'}`}
            onPointerDown={e => { e.stopPropagation(); if (e.button !== 0) return; start.current = e.clientX; e.currentTarget.setPointerCapture?.(e.pointerId); }}
            onPointerMove={e => { if (start.current != null) setDx(e.clientX - start.current); }}
            onPointerUp={e => { const s = start.current; start.current = null; e.stopPropagation(); e.currentTarget.releasePointerCapture?.(e.pointerId); setDx(0); if (s != null && Math.abs(e.clientX - s) >= 5) onFertig(e.clientX - s); }}
            onPointerCancel={() => { start.current = null; setDx(0); }}
            onClick={e => e.stopPropagation()} />
    );
}

function EintragAnsicht({ p, tage, quelle, heuteSpalte, bearbeiten, onVorgang, onTermin, onZiehe, onRand, onWoche }: {
    p: Platziert; tage: number; quelle: string; heuteSpalte: number; bearbeiten: boolean; onVorgang: Props['onVorgang']; onTermin: (terminId: number) => void;
    onZiehe: (e: BelegungEintrag, quelle: string, dx: number, y: number) => void; onRand: (e: BelegungEintrag, seite: 'links' | 'rechts', dxPx: number) => void; onWoche: (montag: string) => void;
}) {
    const e = p.eintrag;
    const bewegbar = bearbeiten && (e.quelle === 'VORGANG' || e.quelle === 'TERMIN');
    const ziehen = useZiehen({
        erlaubt: bewegbar,
        onKlick: () => {
            if (e.quelle === 'VORGANG' && e.projektId != null) onVorgang(e.projektId, Number(e.id.slice(1)));
            else if (e.quelle === 'TERMIN') onTermin(Number(e.id.slice(1)));
            else if (e.quelle === 'TERMIN_SAMMEL') onWoche(e.von);
        },
        onEnde: (dx, _dy, _x, y) => onZiehe(e, quelle, dx, y),
    });
    const breite = 100 / tage;
    const stil: React.CSSProperties = {
        left: `calc(${p.von * breite}% + 1px)`, width: `calc(${(p.bis - p.von) * breite}% - 2px)`, top: 4 + p.bahn * KARTE_H, height: KARTE_H - 4,
        ...(ziehen.stand ? { transform: `translate(${ziehen.stand.dx}px, ${ziehen.stand.dy}px)`, zIndex: 30, touchAction: 'none' } : { touchAction: bewegbar ? 'none' : 'auto' }),
    };
    const uhr = e.vonZeit ? `${zeit(e.vonZeit)}${e.bisZeit ? ` bis ${zeit(e.bisZeit)}` : ''}` : '';
    const zeitraum = `${anzeigeKurz(e.von)}${e.bis !== e.von ? ` bis ${anzeigeKurz(e.bis)}` : ''}`;
    const beschreibung = `${e.titel}${e.untertitel ? `, ${e.untertitel}` : ''}, ${zeitraum}${uhr ? `, ${uhr}` : ''}${e.hinweis ? `. Achtung: ${e.hinweis}` : ''}`;
    const warnung = e.hinweis ? <AlertTriangle className="h-3.5 w-3.5 shrink-0 text-amber-300" aria-hidden="true" /> : null;
    const wirdGezogen = ziehen.stand != null;
    const gemeinsam = `absolute flex flex-col justify-start gap-px overflow-hidden rounded-md px-2 py-1 text-left text-[11px] leading-tight focus-visible:outline focus-visible:outline-2 focus-visible:outline-rose-600 ${bewegbar ? 'cursor-grab active:cursor-grabbing' : ''} ${wirdGezogen ? 'shadow-lg opacity-90' : 'shadow-sm'}`;

    if (e.quelle === 'VORGANG') {
        return (
            <button type="button" aria-label={beschreibung} title={beschreibung} style={{ ...stil, backgroundColor: e.farbe }} {...ziehen.bindung}
                className={`${gemeinsam} text-white ${e.status === 'FERTIG' ? 'opacity-50' : ''} ${e.hinweis ? 'ring-2 ring-amber-400' : ''}`}>
                {/* Fortschritt: gestempelt = echt (dunkler gefüllt), bis heute ohne Stempelung = Schätzung (schraffiert) */}
                {fortschritt(e) != null && <span aria-hidden="true" data-testid={`fortschritt-${e.id}`} className="pointer-events-none absolute inset-y-0 left-0 bg-black/30" style={{ width: `${(fortschritt(e) as number) * 100}%` }} />}
                {e.status !== 'FERTIG' && p.von < heuteSpalte && p.bis > p.von && (
                    <span aria-hidden="true" data-testid={`schraffur-${e.id}`} className="pointer-events-none absolute inset-y-0"
                        style={{ left: `${(fortschritt(e) ?? 0) * 100}%`, width: `${Math.max(0, (Math.min(p.bis, heuteSpalte) - p.von) / (p.bis - p.von) * 100 - (fortschritt(e) ?? 0) * 100)}%`,
                            backgroundImage: 'repeating-linear-gradient(45deg, rgba(255,255,255,0.35) 0, rgba(255,255,255,0.35) 3px, transparent 3px, transparent 7px)' }} />
                )}
                <span className="relative flex items-center gap-1 font-semibold">{warnung}{e.fest && <Pin className="h-3 w-3 shrink-0" aria-hidden="true" />}<span className="truncate">{e.prioritaet === 'DRINGEND' ? '! ' : ''}{e.titel}</span></span>
                <span className="relative truncate" aria-hidden="true">{zeitraum}{e.istStunden != null && e.aufwandStunden ? ` · ${e.istStunden.toLocaleString('de-DE', { maximumFractionDigits: 1 })} von ${e.aufwandStunden.toLocaleString('de-DE', { maximumFractionDigits: 1 })} Std.` : ''}</span>
                {e.untertitel && <span className="relative truncate opacity-90" aria-hidden="true">{e.untertitel}</span>}
                {bearbeiten && e.status !== 'FERTIG' && (
                    <>
                        <RandGriff seite="links" titel="Ziehen: Der Vorgang beginnt frühestens an dem Tag" onFertig={dx => onRand(e, 'links', dx)} />
                        <RandGriff seite="rechts" titel="Ziehen: Der Vorgang wird länger oder kürzer (Dauer bzw. Aufwand)" onFertig={dx => onRand(e, 'rechts', dx)} />
                    </>
                )}
            </button>
        );
    }
    if (e.quelle === 'ABWESENHEIT') {
        return <div style={stil} title={beschreibung} role="img" aria-label={beschreibung} className={`${gemeinsam} justify-center bg-slate-300 text-xs font-medium text-slate-700`}>{e.titel}</div>;
    }
    if (e.quelle === 'WARTUNG') {
        return (
            <WartungLink id={Number(e.id.slice(1))} objekt={e.id.startsWith('O')} beschreibung={beschreibung} stil={stil} klasse={`${gemeinsam} bg-teal-600 text-white`}
                kinder={<><span className="flex items-center gap-1 font-semibold"><Wrench className="h-3 w-3 shrink-0" aria-hidden="true" /><span className="truncate">{e.titel}</span></span>{e.untertitel && <span className="truncate opacity-90" aria-hidden="true">{e.untertitel}</span>}</>} />
        );
    }
    if (e.quelle === 'TERMIN_SAMMEL') {
        return (
            <button type="button" aria-label={`${e.titel} in der Woche ab ${anzeigeKurz(e.von)}. Klicken: Woche anzeigen`} title={`${e.titel}. Klicken: Woche anzeigen`} style={stil} {...ziehen.bindung}
                className={`${gemeinsam} items-center justify-center border border-slate-300 bg-white font-semibold text-slate-700`}>
                <span className="truncate">{e.titel}</span>
            </button>
        );
    }
    return (
        <button type="button" aria-label={beschreibung} title={beschreibung} style={{ ...stil, borderLeftColor: e.farbe }} {...ziehen.bindung}
            className={`${gemeinsam} border border-slate-300 border-l-4 bg-white text-slate-800 ${e.hinweis ? 'ring-2 ring-amber-400' : ''}`}>
            <span className="flex items-center gap-1 font-semibold">{warnung}<span className="truncate">{e.titel}</span></span>
            <span className="truncate" aria-hidden="true">{uhr || 'ganztägig'}</span>
            {e.untertitel && <span className="truncate text-slate-600" aria-hidden="true">{e.untertitel}</span>}
        </button>
    );
}
