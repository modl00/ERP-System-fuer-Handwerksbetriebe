import { useEffect, useMemo, useRef } from 'react';
import { AlertTriangle, CalendarClock, Check, Pin, Plus, Gem, Timer } from 'lucide-react';
import { cn } from '../../lib/utils';
import type { Ampel, Auftrag, Vorgang } from './plantafelApi';
import { TAG_BREITE, anzeigeDatum, anzeigeKurz, balken, berechneAchse, formatDatum, istWochenende, monatskoepfe, plusTage, wochenenden, xVon } from './zeitachse';
import { verschobenerStart } from './verschiebung';
import { useZiehen } from './ziehen';

const KOPF_H = 52;
const AUFTRAG_H = 48;
const VORGANG_H = 34;
const LINKS_B = 288;

/** „ +3 Tage" hinter der Ampel, wenn die Lieferzeit nach dem Zieltermin liegt. */
const verspaetungText = (tage?: number | null) => (tage ? ` +${tage} ${tage === 1 ? 'Tag' : 'Tage'}` : '');

const AMPEL: Record<Ampel, { text: string; klasse: string }> = {
    OK: { text: 'Im Plan', klasse: 'bg-emerald-100 text-emerald-800' },
    KNAPP: { text: 'Knapp', klasse: 'bg-amber-100 text-amber-800' },
    VERSPAETET: { text: 'Verspätet', klasse: 'bg-red-100 text-red-800' },
    OHNE_ZIEL: { text: 'Kein Zieltermin', klasse: 'bg-slate-100 text-slate-600' },
};

type Zeile = { art: 'auftrag'; auftrag: Auftrag; oben: number } | { art: 'vorgang'; auftrag: Auftrag; vorgang: Vorgang; oben: number };

interface Props {
    auftraege: Auftrag[];
    heute?: Date;
    onVorgang: (auftrag: Auftrag, vorgang: Vorgang) => void;
    onNeuerVorgang: (auftrag: Auftrag) => void;
    onTermine: (auftrag: Auftrag) => void;
    /** Optional: die KI erklärt, wie die Lieferzeit des Auftrags zustande kommt. */
    onErklaeren?: (auftrag: Auftrag) => void;
    /** Optional: schlägt die Dauern der Vorgänge aus der Arbeitszeit des Angebots vor. */
    onDauern?: (auftrag: Auftrag) => void;
    /** Optional: Ein Balken wurde mit der Maus auf einen anderen Starttag gezogen (macht den Start fest). Ohne diesen Rückruf lassen sich die Balken nicht ziehen. */
    onVerschieben?: (auftrag: Auftrag, vorgang: Vorgang, neuerStart: string) => void;
    /** Arbeitswoche als ISO-Zahlen (1 = Montag); ohne Angabe Montag bis Freitag. */
    arbeitstage?: readonly number[];
}

function vorgangBeschreibung(v: Vorgang): string {
    const teile = [`${v.bezeichnung}, ${anzeigeDatum(v.start)} bis ${anzeigeDatum(v.ende)}, ${v.dauerTage === 0 ? 'Meilenstein' : `${v.dauerTage} Arbeitstage`}`];
    if (v.dauerHerkunft === 'GESCHAETZT') teile.push('Dauer geschätzt');
    if (v.festerStart) teile.push('fester Start');
    if (v.konflikt) teile.push('Konflikt: Die Vorgänger sind erst nach dem festen Start fertig');
    if (v.status === 'FERTIG') teile.push('fertig');
    return teile.join(', ');
}

/**
 * Das Gantt-Bild der Plantafel: links die Namen (bleiben beim Scrollen stehen), rechts die Zeitachse mit Balken, Pfeilen für die Abhängigkeiten,
 * Wochenenden, der Heute-Linie und dem Zieltermin. Die Balken lassen sich mit der Maus auf einen anderen Starttag ziehen; gespeichert wird über die Rückrufe.
 */
export function Gantt({ auftraege, heute = new Date(), onVorgang, onNeuerVorgang, onTermine, onErklaeren, onDauern, onVerschieben, arbeitstage }: Props) {
    const scroll = useRef<HTMLDivElement>(null);
    const heuteTag = useMemo(() => new Date(heute.getFullYear(), heute.getMonth(), heute.getDate()), [heute]);

    const achse = useMemo(() => berechneAchse(auftraege.flatMap(a => [a.startDatum, a.zielDatum, a.liefertermin, ...a.vorgaenge.flatMap(v => [v.start, v.ende])]), heuteTag), [auftraege, heuteTag]);
    const breite = achse.tage * TAG_BREITE;

    const { zeilen, hoehe } = useMemo(() => {
        const liste: Zeile[] = [];
        let y = 0;
        for (const a of auftraege) {
            liste.push({ art: 'auftrag', auftrag: a, oben: y });
            y += AUFTRAG_H;
            for (const v of a.vorgaenge) {
                liste.push({ art: 'vorgang', auftrag: a, vorgang: v, oben: y });
                y += VORGANG_H;
            }
        }
        return { zeilen: liste, hoehe: y };
    }, [auftraege]);

    const heuteX = xVon(achse, formatDatum(heuteTag)) + TAG_BREITE / 2;
    useEffect(() => {
        if (scroll.current) scroll.current.scrollLeft = Math.max(0, heuteX - 160);
    }, [heuteX]);

    const tageKopf = useMemo(() => Array.from({ length: achse.tage }, (_, i) => plusTage(achse.start, i)), [achse]);

    // Pfeile: vom Ende des Vorgängers zum Anfang des Nachfolgers
    const pfeile = useMemo(() => {
        const ergebnis: { schluessel: string; d: string; konflikt: boolean }[] = [];
        for (const z of zeilen) {
            if (z.art !== 'vorgang' || !z.vorgang.start || !z.vorgang.ende) continue;
            for (const dep of z.vorgang.vorgaenger) {
                const vor = zeilen.find((x): x is Extract<Zeile, { art: 'vorgang' }> => x.art === 'vorgang' && x.vorgang.id === dep.vorgaengerId);
                if (!vor?.vorgang.start || !vor.vorgang.ende) continue;
                const von = balken(achse, vor.vorgang.start, vor.vorgang.ende);
                const nach = balken(achse, z.vorgang.start, z.vorgang.ende);
                const x1 = von.x + von.breite;
                const y1 = vor.oben + VORGANG_H / 2;
                const y2 = z.oben + VORGANG_H / 2;
                ergebnis.push({ schluessel: `${z.vorgang.id}-${dep.vorgaengerId}`, d: `M${x1} ${y1} H${x1 + 6} V${y2} H${nach.x - 2}`, konflikt: z.vorgang.konflikt });
            }
        }
        return ergebnis;
    }, [zeilen, achse]);

    if (auftraege.length === 0) return null;

    return (
        <div className="flex overflow-hidden rounded-xl border border-slate-200 bg-white" data-testid="gantt">
            {/* Linke Spalte: Namen */}
            <div className="shrink-0 border-r border-slate-200 bg-white" style={{ width: LINKS_B }}>
                <div className="border-b border-slate-200 bg-slate-50 px-3 text-xs font-semibold uppercase tracking-wide text-slate-500" style={{ height: KOPF_H, lineHeight: `${KOPF_H}px` }}>Auftrag und Vorgänge</div>
                {zeilen.map(z => z.art === 'auftrag' ? (
                    <div key={`a${z.auftrag.projektId}`} className="flex items-center gap-2 border-b border-slate-200 bg-slate-50 px-3" style={{ height: AUFTRAG_H }} data-testid={`auftrag-${z.auftrag.projektId}`}>
                        <div className="min-w-0 flex-1">
                            <p className="truncate text-sm font-semibold text-slate-900" title={z.auftrag.bezeichnung}>{z.auftrag.bezeichnung}</p>
                            <p className="flex items-center gap-1.5 text-xs text-slate-500">
                                <span className={cn('shrink-0 whitespace-nowrap rounded-full px-1.5 py-px font-medium', AMPEL[z.auftrag.ampel].klasse)}>{AMPEL[z.auftrag.ampel].text}{verspaetungText(z.auftrag.verspaetungTage)}</span>
                                <span className="truncate" title={`Lieferzeit ${anzeigeDatum(z.auftrag.liefertermin)}`}>Lieferzeit {anzeigeKurz(z.auftrag.liefertermin)}</span>
                            </p>
                        </div>
                        {onDauern && (
                            <button type="button" onClick={() => onDauern(z.auftrag)} aria-label={`Dauern von ${z.auftrag.bezeichnung} aus dem Angebot vorschlagen`} title="Dauern aus der Arbeitszeit des Angebots vorschlagen"
                                className="rounded p-1.5 text-slate-500 hover:bg-white hover:text-rose-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-300">
                                <Timer className="h-4 w-4" aria-hidden="true" />
                            </button>
                        )}
                        {onErklaeren && (
                            <button type="button" onClick={() => onErklaeren(z.auftrag)} aria-label={`Termin von ${z.auftrag.bezeichnung} erklären`} title="Die KI erklärt, wie die Lieferzeit zustande kommt"
                                className="rounded p-1.5 text-slate-500 hover:bg-white hover:text-rose-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-300">
                                <Gem className="h-4 w-4" aria-hidden="true" />
                            </button>
                        )}
                        <button type="button" onClick={() => onTermine(z.auftrag)} aria-label={`Termine von ${z.auftrag.bezeichnung} ändern`} title="Start und Zieltermin ändern"
                            className="rounded p-1.5 text-slate-500 hover:bg-white hover:text-rose-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-300">
                            <CalendarClock className="h-4 w-4" aria-hidden="true" />
                        </button>
                        <button type="button" onClick={() => onNeuerVorgang(z.auftrag)} aria-label={`Vorgang zu ${z.auftrag.bezeichnung} hinzufügen`} title="Vorgang hinzufügen"
                            className="rounded p-1.5 text-slate-500 hover:bg-white hover:text-rose-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-300">
                            <Plus className="h-4 w-4" aria-hidden="true" />
                        </button>
                    </div>
                ) : (
                    <button key={`v${z.vorgang.id}`} type="button" onClick={() => onVorgang(z.auftrag, z.vorgang)}
                        className="flex w-full items-center gap-2 border-b border-slate-100 px-3 pl-6 text-left hover:bg-slate-50 focus:outline-none focus-visible:bg-rose-50" style={{ height: VORGANG_H }}>
                        <span className="h-2.5 w-2.5 shrink-0 rounded-full" style={{ backgroundColor: z.vorgang.farbe }} aria-hidden="true" />
                        <span className="min-w-0 flex-1 truncate text-sm text-slate-800">{z.vorgang.bezeichnung}</span>
                        <span className="shrink-0 text-xs text-slate-500">{z.vorgang.dauerTage === 0 ? 'Termin' : `${z.vorgang.dauerTage} Tg.`}{z.vorgang.dauerHerkunft === 'GESCHAETZT' ? ' (geschätzt)' : ''}</span>
                    </button>
                ))}
            </div>

            {/* Rechts: Zeitachse */}
            <div ref={scroll} className="min-w-0 flex-1 overflow-x-auto" role="region" aria-label="Zeitachse der Plantafel" tabIndex={0}>
                <div className="relative" style={{ width: breite }}>
                    <div className="sticky top-0 z-10 border-b border-slate-200 bg-slate-50" style={{ height: KOPF_H }}>
                        <div className="relative h-6 border-b border-slate-200">
                            {monatskoepfe(achse).map(m => (
                                <span key={m.x} className="absolute top-0 flex h-6 items-center border-l border-slate-200 px-2 text-xs font-semibold text-slate-600" style={{ left: m.x, width: m.breite }}>{m.text}</span>
                            ))}
                        </div>
                        <div className="relative flex" style={{ height: KOPF_H - 24 }}>
                            {tageKopf.map((d, i) => {
                                const wochenende = istWochenende(d, arbeitstage);
                                const istHeute = formatDatum(d) === formatDatum(heuteTag);
                                return (
                                    <span key={i} className={cn('flex shrink-0 flex-col items-center justify-center text-[10px] leading-tight', wochenende ? 'text-slate-400' : 'text-slate-600', istHeute && 'font-bold text-rose-700')} style={{ width: TAG_BREITE }}>
                                        <span>{['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa'][d.getDay()]}</span>
                                        <span>{d.getDate()}</span>
                                    </span>
                                );
                            })}
                        </div>
                    </div>

                    <div className="relative" style={{ height: hoehe }}>
                        {wochenenden(achse, arbeitstage).map(w => <div key={w.x} className="absolute top-0 bg-slate-100/70" style={{ left: w.x, width: w.breite, height: hoehe }} aria-hidden="true" />)}
                        {zeilen.map(z => <div key={z.art === 'auftrag' ? `l${z.auftrag.projektId}` : `l${z.vorgang.id}`} className={cn('absolute left-0 right-0 border-b', z.art === 'auftrag' ? 'border-slate-200 bg-slate-50/60' : 'border-slate-100')} style={{ top: z.oben, height: z.art === 'auftrag' ? AUFTRAG_H : VORGANG_H, width: breite }} aria-hidden="true" />)}
                        <div className="absolute top-0 w-px bg-rose-500" style={{ left: heuteX, height: hoehe }} aria-hidden="true" title="Heute" />

                        {zeilen.map(z => {
                            if (z.art === 'auftrag') {
                                const a = z.auftrag;
                                const starts = a.vorgaenge.map(v => v.start).filter((s): s is string => !!s).sort();
                                const enden = a.vorgaenge.map(v => v.ende).filter((s): s is string => !!s).sort();
                                const zusammen = starts.length && enden.length ? balken(achse, starts[0], enden[enden.length - 1]) : null;
                                return (
                                    <div key={`ab${a.projektId}`}>
                                        {zusammen && <div className="absolute rounded-full bg-slate-700" style={{ left: zusammen.x, width: zusammen.breite, top: z.oben + AUFTRAG_H / 2 - 3, height: 6 }} aria-hidden="true" />}
                                        {a.liefertermin && (
                                            <div className="absolute h-3.5 w-3.5 rotate-45 border border-white bg-rose-600" style={{ left: xVon(achse, a.liefertermin) + TAG_BREITE - 7, top: z.oben + AUFTRAG_H / 2 - 7 }}
                                                title={`Lieferzeit ${anzeigeDatum(a.liefertermin)}`} aria-hidden="true" />
                                        )}
                                        {a.zielDatum && (
                                            <div className="absolute border-l-2 border-dashed border-slate-500" style={{ left: xVon(achse, a.zielDatum) + TAG_BREITE, top: z.oben, height: AUFTRAG_H + a.vorgaenge.length * VORGANG_H }}
                                                title={`Zieltermin ${anzeigeDatum(a.zielDatum)}`} aria-hidden="true" />
                                        )}
                                    </div>
                                );
                            }
                            const v = z.vorgang;
                            if (!v.start || !v.ende) return null;
                            const b = balken(achse, v.start, v.ende);
                            return <VorgangBalken key={`b${v.id}`} arbeitstage={arbeitstage} vorgang={v} x={b.x} breite={b.breite} oben={z.oben + 6} onKlick={() => onVorgang(z.auftrag, v)}
                                onVerschieben={onVerschieben ? neuerStart => onVerschieben(z.auftrag, v, neuerStart) : undefined} />;
                        })}

                        <svg className="pointer-events-none absolute left-0 top-0" width={breite} height={hoehe} aria-hidden="true">
                            <defs>
                                <marker id="plan-pfeil" viewBox="0 0 8 8" refX="7" refY="4" markerWidth="6" markerHeight="6" orient="auto"><path d="M0 0 L8 4 L0 8 z" fill="#64748b" /></marker>
                                <marker id="plan-pfeil-rot" viewBox="0 0 8 8" refX="7" refY="4" markerWidth="6" markerHeight="6" orient="auto"><path d="M0 0 L8 4 L0 8 z" fill="#dc2626" /></marker>
                            </defs>
                            {pfeile.map(p => <path key={p.schluessel} d={p.d} fill="none" stroke={p.konflikt ? '#dc2626' : '#64748b'} strokeWidth="1.5" markerEnd={`url(#${p.konflikt ? 'plan-pfeil-rot' : 'plan-pfeil'})`} />)}
                        </svg>
                    </div>
                </div>
            </div>
        </div>
    );
}


function VorgangBalken({ vorgang: v, x, breite, oben, onKlick, onVerschieben, arbeitstage }: { arbeitstage?: readonly number[]; vorgang: Vorgang; x: number; breite: number; oben: number; onKlick: () => void; onVerschieben?: (neuerStart: string) => void }) {
    const ziehen = useZiehen({
        erlaubt: onVerschieben != null && v.start != null,
        onKlick,
        onEnde: dx => {
            const neuerStart = v.start ? verschobenerStart(v.start, dx / TAG_BREITE, arbeitstage) : null;
            if (neuerStart) onVerschieben?.(neuerStart);
        },
    });
    const beschreibung = vorgangBeschreibung(v);
    return (
        <button type="button" aria-label={beschreibung} title={onVerschieben ? `${beschreibung}. Mit der Maus ziehen verschiebt den Start.` : beschreibung} {...ziehen.bindung}
            className={cn('absolute flex items-center gap-1 overflow-hidden rounded-md px-1.5 text-left text-xs font-medium text-white shadow-sm focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-400',
                onVerschieben && 'cursor-grab active:cursor-grabbing', ziehen.stand && 'z-30 opacity-90 shadow-lg', v.konflikt && 'ring-2 ring-red-500', v.status === 'FERTIG' && 'opacity-60')}
            style={{
                left: x + 1, width: breite - 2, top: oben, height: VORGANG_H - 12, backgroundColor: v.farbe, touchAction: 'none',
                transform: ziehen.stand ? `translate(${Math.round(ziehen.stand.dx / TAG_BREITE) * TAG_BREITE}px, 0)` : undefined,
                backgroundImage: v.dauerHerkunft === 'GESCHAETZT' ? 'repeating-linear-gradient(135deg, rgba(255,255,255,.28) 0 5px, transparent 5px 10px)' : undefined,
            }}>
            {v.konflikt && <AlertTriangle className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />}
            {v.festerStart && <Pin className="h-3 w-3 shrink-0" aria-hidden="true" />}
            {v.status === 'FERTIG' && <Check className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />}
            <span className="truncate">{v.bezeichnung}</span>
        </button>
    );
}
