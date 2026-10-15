import { useCallback, useEffect, useRef, useState } from 'react';
import { Eraser, Loader2, PenLine, Trash2 } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Input } from '../../components/ui/input';
import { useConfirm } from '../../components/ui/confirm-dialog';
import { cn } from '../../lib/utils';
import { istPngDataUrl } from './format';

interface UnterschriftenfeldProps {
    titel: string;
    /** Vorhandene Unterschrift als PNG-Data-URL. */
    bild: string | null;
    name: string | null;
    /** Name, der beim ersten Unterschreiben vorgeschlagen wird (zum Beispiel der Techniker). */
    vorschlagName?: string;
    bearbeitbar: boolean;
    onSpeichern: (bild: string, name: string) => Promise<void>;
    onLoeschen: () => Promise<void>;
    /** Nur für die eigene Unterschrift des Mitarbeiters: Eine gespeicherte Unterschrift steht zur Verfügung. */
    gespeicherteVorhanden?: boolean;
    /** Setzt die gespeicherte Unterschrift ein (als Kopie). */
    onGespeicherteVerwenden?: () => Promise<void>;
    /** Merkt die frisch gezeichnete Unterschrift für künftige Feldeinsätze. */
    onMerken?: (bild: string, name: string) => Promise<void>;
}

const FLAECHE_HOEHE = 176;

/**
 * Zeichenfläche für eine Unterschrift. Funktioniert mit Maus, Finger und Stift (Pointer Events).
 * Die Fläche verhindert das Mitscrollen der Seite, solange gezeichnet wird.
 */
export function Unterschriftenfeld({ titel, bild, name, vorschlagName, bearbeitbar, onSpeichern, onLoeschen, gespeicherteVorhanden, onGespeicherteVerwenden, onMerken }: UnterschriftenfeldProps) {
    const confirm = useConfirm();
    const leinwand = useRef<HTMLCanvasElement | null>(null);
    const zeichnet = useRef(false);
    const [hatStriche, setHatStriche] = useState(false);
    const hatStricheRef = useRef(false);
    const letzteBreite = useRef(0);
    const [eingabeName, setEingabeName] = useState(name ?? vorschlagName ?? '');
    const [neuZeichnen, setNeuZeichnen] = useState(false);
    const [speichert, setSpeichert] = useState(false);
    const [versucht, setVersucht] = useState(false);
    const [merken, setMerken] = useState(false);

    const vorhanden = istPngDataUrl(bild) && !neuZeichnen;
    const nameId = `unterschrift-name-${titel.replace(/\s+/g, '-').toLowerCase()}`;

    useEffect(() => { setEingabeName(name ?? vorschlagName ?? ''); }, [name, vorschlagName]);

    /** Passt die Auflösung an die Anzeigegröße und den Bildschirm an, damit die Linie scharf bleibt. */
    const richteLeinwandEin = useCallback(() => {
        const c = leinwand.current;
        if (!c) return;
        const faktor = window.devicePixelRatio || 1;
        const breite = c.clientWidth || 320;
        letzteBreite.current = breite;
        c.width = Math.round(breite * faktor);
        c.height = Math.round(FLAECHE_HOEHE * faktor);
        const ctx = c.getContext('2d');
        if (!ctx) return;
        ctx.scale(faktor, faktor);
        ctx.lineWidth = 2.5;
        ctx.lineCap = 'round';
        ctx.lineJoin = 'round';
        ctx.strokeStyle = '#0f172a';
        hatStricheRef.current = false;
        setHatStriche(false);
    }, []);

    useEffect(() => {
        if (vorhanden) return;
        richteLeinwandEin();
        // Die eingeblendete Tastatur löst auf dem Handy ein resize aus, ohne dass sich die Breite ändert.
        // Eine schon gezeichnete Unterschrift darf dabei nicht verschwinden.
        const beiGroessenaenderung = () => {
            const c = leinwand.current;
            if (!c || hatStricheRef.current || c.clientWidth === letzteBreite.current) return;
            richteLeinwandEin();
        };
        window.addEventListener('resize', beiGroessenaenderung);
        return () => window.removeEventListener('resize', beiGroessenaenderung);
    }, [vorhanden, richteLeinwandEin]);

    const punkt = (e: React.PointerEvent<HTMLCanvasElement>) => {
        const rand = e.currentTarget.getBoundingClientRect();
        return { x: e.clientX - rand.left, y: e.clientY - rand.top };
    };

    const beginne = (e: React.PointerEvent<HTMLCanvasElement>) => {
        if (!bearbeitbar) return;
        const ctx = e.currentTarget.getContext('2d');
        if (!ctx) return;
        e.currentTarget.setPointerCapture?.(e.pointerId);
        zeichnet.current = true;
        const p = punkt(e);
        ctx.beginPath();
        ctx.moveTo(p.x, p.y);
        // Ein einzelner Tipp soll einen Punkt hinterlassen.
        ctx.lineTo(p.x + 0.01, p.y + 0.01);
        ctx.stroke();
        hatStricheRef.current = true;
        setHatStriche(true);
    };

    const zeichne = (e: React.PointerEvent<HTMLCanvasElement>) => {
        if (!zeichnet.current) return;
        const ctx = e.currentTarget.getContext('2d');
        if (!ctx) return;
        const p = punkt(e);
        ctx.lineTo(p.x, p.y);
        ctx.stroke();
    };

    const beende = () => { zeichnet.current = false; };

    const leeren = () => richteLeinwandEin();

    const uebernehmen = async () => {
        setVersucht(true);
        const c = leinwand.current;
        if (!c || !hatStriche || eingabeName.trim() === '') return;
        setSpeichert(true);
        try {
            const bildNeu = c.toDataURL('image/png');
            await onSpeichern(bildNeu, eingabeName.trim());
            if (merken && onMerken) {
                // Das Merken ist ein Zusatz: Scheitert es, bleibt die Unterschrift im Feldeinsatz trotzdem gespeichert
                try {
                    await onMerken(bildNeu, eingabeName.trim());
                } catch {
                    // die Meldung zeigt der Aufrufer
                }
            }
            setNeuZeichnen(false);
            setMerken(false);
            setVersucht(false);
        } finally {
            setSpeichert(false);
        }
    };

    const verwendeGespeicherte = async () => {
        if (!onGespeicherteVerwenden) return;
        setSpeichert(true);
        try {
            await onGespeicherteVerwenden();
            setNeuZeichnen(false);
        } finally {
            setSpeichert(false);
        }
    };

    const entfernen = async () => {
        const ja = await confirm({
            title: 'Unterschrift löschen?',
            message: `Die Unterschrift „${titel}“ wird entfernt. Sie lässt sich danach neu zeichnen.`,
            confirmLabel: 'Löschen',
            variant: 'danger',
        });
        if (!ja) return;
        setSpeichert(true);
        try {
            await onLoeschen();
        } finally {
            setSpeichert(false);
        }
    };

    return (
        <section aria-label={`Unterschrift ${titel}`} className="rounded-lg border border-slate-200 bg-white p-4">
            <h4 className="text-sm font-semibold text-slate-900 mb-3">{titel}</h4>

            {vorhanden ? (
                <>
                    <div className="rounded-lg border border-slate-200 bg-white p-2">
                        <img src={bild!} alt={`Unterschrift von ${name ?? titel}`} className="mx-auto h-40 w-full object-contain" />
                    </div>
                    <p className="mt-2 text-sm text-slate-600">{name}</p>
                    {bearbeitbar && (
                        <div className="mt-3 flex flex-wrap gap-2">
                            <Button variant="outline" size="sm" onClick={() => setNeuZeichnen(true)} disabled={speichert}>
                                <PenLine className="h-4 w-4" aria-hidden="true" /> Neu unterschreiben
                            </Button>
                            <Button variant="ghost" size="sm" onClick={entfernen} disabled={speichert}>
                                {speichert ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> : <Trash2 className="h-4 w-4" aria-hidden="true" />}
                                Löschen
                            </Button>
                        </div>
                    )}
                </>
            ) : bearbeitbar ? (
                <>
                    <div className="relative">
                        <canvas
                            ref={leinwand}
                            role="img"
                            aria-label={`Zeichenfläche für die Unterschrift ${titel}`}
                            onPointerDown={beginne}
                            onPointerMove={zeichne}
                            onPointerUp={beende}
                            onPointerCancel={beende}
                            onPointerLeave={beende}
                            style={{ height: FLAECHE_HOEHE, touchAction: 'none' }}
                            className={cn(
                                'w-full rounded-lg border-2 border-dashed bg-white cursor-crosshair',
                                versucht && !hatStriche ? 'border-rose-300' : 'border-slate-300',
                            )}
                        />
                        {!hatStriche && (
                            <span className="pointer-events-none absolute inset-0 flex items-center justify-center text-sm text-slate-400">
                                Hier unterschreiben
                            </span>
                        )}
                    </div>
                    {versucht && !hatStriche && <p role="alert" className="mt-1 text-sm text-rose-700">Bitte zuerst in das Feld unterschreiben.</p>}

                    {gespeicherteVorhanden && onGespeicherteVerwenden && (
                        <Button variant="outline" size="sm" className="mt-3 min-h-11 w-full sm:w-auto" onClick={verwendeGespeicherte} disabled={speichert}>
                            <PenLine className="h-4 w-4" aria-hidden="true" /> Meine gespeicherte Unterschrift verwenden
                        </Button>
                    )}

                    <div className="mt-3">
                        <label htmlFor={nameId} className="block text-sm text-slate-800 mb-1.5">Name</label>
                        <Input
                            id={nameId}
                            value={eingabeName}
                            maxLength={200}
                            placeholder="Vor- und Nachname"
                            aria-invalid={versucht && eingabeName.trim() === '' ? true : undefined}
                            onChange={(e) => setEingabeName(e.target.value)}
                        />
                        {versucht && eingabeName.trim() === '' && <p role="alert" className="mt-1 text-sm text-rose-700">Bitte den Namen eintragen.</p>}
                    </div>

                    {onMerken && (
                        <label className="mt-3 flex cursor-pointer items-start gap-2 text-sm text-slate-700">
                            <input type="checkbox" className="mt-0.5 h-4 w-4 rounded border-slate-300 accent-rose-600" checked={merken} onChange={(e) => setMerken(e.target.checked)} />
                            <span>
                                <span className="font-medium">Für künftige Feldeinsätze merken</span>
                                <span className="block text-slate-500">Beim nächsten Feldeinsatz steht sie automatisch bereit. Bestehende Protokolle ändern sich dadurch nie.</span>
                            </span>
                        </label>
                    )}

                    <div className="mt-3 flex flex-wrap gap-2">
                        <Button size="sm" className="min-h-11" onClick={uebernehmen} disabled={speichert}>
                            {speichert && <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />}
                            Unterschrift übernehmen
                        </Button>
                        <Button variant="outline" size="sm" className="min-h-11" onClick={leeren} disabled={speichert || !hatStriche}
                            title={hatStriche ? undefined : 'Es ist noch nichts gezeichnet'}>
                            <Eraser className="h-4 w-4" aria-hidden="true" /> Neu beginnen
                        </Button>
                        {neuZeichnen && (
                            <Button variant="ghost" size="sm" onClick={() => setNeuZeichnen(false)} disabled={speichert}>Abbrechen</Button>
                        )}
                    </div>
                </>
            ) : (
                <p className="text-sm text-slate-500">Noch nicht unterschrieben.</p>
            )}
        </section>
    );
}
