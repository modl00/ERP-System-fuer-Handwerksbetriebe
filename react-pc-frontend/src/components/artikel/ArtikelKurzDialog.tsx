import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ExternalLink, Loader2, Save } from 'lucide-react';
import { Button } from '../ui/button';
import { Input } from '../ui/input';
import { Label } from '../ui/label';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../ui/dialog';
import { useToast } from '../ui/toast';
import { TiptapEditor } from '../TiptapEditor';
import { formatCurrency } from './formatCurrency';
import { richtextOderNull } from './richtext';
import type { ArtikelDetail } from '../../types';

interface Props {
    /** Der Artikel, der gezeigt wird; {@code null} = Dialog zu. */
    artikelId: number | null;
    onSchliessen: () => void;
    /** Nach dem Speichern, damit die aufrufende Seite neu laden kann. */
    onGespeichert?: () => void;
}

/**
 * Kurzansicht eines Artikels als Fenster (zum Beispiel aus einer Leistung): die wichtigsten Daten und die Texte für Angebote zum Ändern.
 * Alle Stammdaten ändert man auf der Artikelseite, dorthin führt der Link.
 */
export function ArtikelKurzDialog({ artikelId, onSchliessen, onGespeichert }: Props) {
    const toast = useToast();
    const [daten, setDaten] = useState<ArtikelDetail | null>(null);
    const [fehler, setFehler] = useState('');
    const [kurz, setKurz] = useState('');
    const [beschreibung, setBeschreibung] = useState('');
    const [speichert, setSpeichert] = useState(false);

    useEffect(() => {
        if (artikelId == null) return;
        let abgebrochen = false;
        setDaten(null); setFehler('');
        fetch(`/api/artikel/${encodeURIComponent(String(artikelId))}/detail`)
            .then(r => (r.ok ? r.json() : Promise.reject(new Error(r.status === 404 ? 'Dieser Artikel wurde nicht gefunden.' : 'Der Artikel konnte nicht geladen werden.'))))
            .then((d: ArtikelDetail) => {
                if (abgebrochen) return;
                setDaten(d);
                setKurz(d.artikel.kurzbeschreibung ?? '');
                setBeschreibung(d.artikel.beschreibung ?? '');
            })
            .catch(e => { if (!abgebrochen) setFehler(e instanceof Error ? e.message : 'Der Artikel konnte nicht geladen werden.'); });
        return () => { abgebrochen = true; };
    }, [artikelId]);

    const speichern = async () => {
        if (artikelId == null) return;
        setSpeichert(true);
        try {
            const res = await fetch(`/api/artikel/${encodeURIComponent(String(artikelId))}/dokumenttexte`, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                // Nur die zwei Texte: Der Aufschlag bleibt unberührt (Teil-Update)
                body: JSON.stringify({ kurzbeschreibung: kurz.trim() || null, beschreibung: richtextOderNull(beschreibung) }),
            });
            if (!res.ok) throw new Error(res.status === 400 ? 'Ein Text ist zu lang (Kurzbeschreibung höchstens 255, Beschreibung höchstens 10000 Zeichen).' : 'Die Texte konnten nicht gespeichert werden.');
            toast.success('Die Texte des Artikels sind gespeichert.');
            onGespeichert?.();
            onSchliessen();
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Die Texte konnten nicht gespeichert werden.');
        } finally {
            setSpeichert(false);
        }
    };

    const a = daten?.artikel;
    const einheit = typeof a?.verrechnungseinheit === 'string' ? a.verrechnungseinheit : a?.verrechnungseinheit?.anzeigename;

    return (
        <Dialog open={artikelId != null} onOpenChange={o => { if (!o) onSchliessen(); }}>
            <DialogContent className="sm:max-w-[640px] max-h-[90vh] overflow-y-auto">
                <DialogHeader>
                    <DialogTitle>{a?.produktname ?? 'Artikel'}</DialogTitle>
                    <DialogDescription>Hier ändern Sie die Texte, die in Angeboten und Rechnungen stehen. Alles andere auf der Artikelseite.</DialogDescription>
                </DialogHeader>

                {fehler ? (
                    <div role="alert" className="p-3 bg-rose-50 border border-rose-200 text-sm text-rose-800 rounded-lg">{fehler}</div>
                ) : !a ? (
                    <div className="flex items-center gap-2 text-slate-500 text-sm py-6"><Loader2 className="w-4 h-4 animate-spin motion-reduce:animate-none" aria-hidden="true" /> Artikel wird geladen …</div>
                ) : (
                    <div className="space-y-4">
                        <dl className="grid grid-cols-2 gap-x-6 gap-y-2 text-sm">
                            <Zeile label="Artikelnummer" wert={a.artikelnummer} />
                            <Zeile label="EAN / Strichcode" wert={a.ean} />
                            <Zeile label="Einheit" wert={einheit} />
                            <Zeile label="Günstigster Preis" wert={a.guenstigsterPreis != null ? formatCurrency(a.guenstigsterPreis) : undefined} />
                            <Zeile label="Werkstoff" wert={a.werkstoffName} />
                            <Zeile label="Abmessung" wert={a.abmessung} />
                        </dl>

                        <div className="space-y-1.5">
                            <Label htmlFor="kurz-text">Kurzbeschreibung (intern)</Label>
                            <Input id="kurz-text" value={kurz} maxLength={255} onChange={e => setKurz(e.target.value)} />
                        </div>
                        <div className="space-y-1.5">
                            <Label>Beschreibung für den Kunden</Label>
                            <TiptapEditor value={beschreibung} onChange={setBeschreibung} />
                        </div>
                    </div>
                )}

                <DialogFooter className="gap-2 sm:justify-between">
                    {artikelId != null && (
                        <Link to={`/artikel/${encodeURIComponent(String(artikelId))}`} className="inline-flex items-center gap-1.5 text-sm text-rose-700 hover:underline self-center">
                            <ExternalLink className="w-4 h-4" aria-hidden="true" /> Zur Artikelseite
                        </Link>
                    )}
                    <div className="flex gap-2">
                        <Button type="button" variant="outline" size="sm" onClick={onSchliessen}>Schließen</Button>
                        <Button type="button" size="sm" className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" onClick={speichern} disabled={!a || speichert}>
                            {speichert ? <Loader2 className="w-4 h-4 mr-2 animate-spin motion-reduce:animate-none" aria-hidden="true" /> : <Save className="w-4 h-4 mr-2" aria-hidden="true" />} Speichern
                        </Button>
                    </div>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}

function Zeile({ label, wert }: { label: string; wert?: string | null }) {
    return (
        <div>
            <dt className="text-xs uppercase tracking-wide text-slate-500">{label}</dt>
            <dd className="text-slate-900">{wert || '–'}</dd>
        </div>
    );
}
