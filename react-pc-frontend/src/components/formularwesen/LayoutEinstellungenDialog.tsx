import { useEffect, useState } from 'react';
import { Loader2, RotateCcw } from 'lucide-react';

import { Button } from '../ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../ui/dialog';
import { Input } from '../ui/input';
import { Label } from '../ui/label';
import { Select } from '../ui/select-custom';
import type { VorlagenEinstellungen } from './vorlagenEinstellungen';

interface Props {
    offen: boolean;
    /** Die Einstellungen der Vorlage; leer = noch keine (Standardwerte werden geladen) */
    einstellungen: VorlagenEinstellungen | null;
    /** Beim Lieferschein gelten andere Standardwerte (ohne Preise, mit Artikelnummer) */
    lieferschein: boolean;
    onSchliessen: () => void;
    /** Das neue Seitenlayout (Vorlagen-HTML mit den Einstellungen darin) */
    onUebernehmen: (html: string, einstellungen: VorlagenEinstellungen) => void;
}

const SCHRIFTEN = [
    { value: 'HELVETICA', label: 'Arial / Calibri (serifenlos)' },
    { value: 'TIMES', label: 'Times (mit Serifen)' },
    { value: 'COURIER', label: 'Courier (Schreibmaschine)' },
];
const BETREFF1 = [
    { value: 'BAUVORHABEN', label: 'Bauvorhaben mit Anschrift' },
    { value: 'BETREFF', label: 'Betreff des Dokuments' },
];
const FUSSZEILEN = [
    { value: 'DOKUMENT_FIRMA_SEITE', label: 'Dokument-Nr. / Firma / Seite' },
    { value: 'FIRMENDATEN', label: 'Bank / Steuer / Register (drei Spalten)' },
    { value: 'KEINE', label: 'Keine Fußzeile' },
];
const TABELLEN = [
    { value: 'GRAU', label: 'Grau hinterlegt (Kopfzeile und Gruppen)' },
    { value: 'SCHLICHT', label: 'Schlicht (mit Linien)' },
];

function Gruppe({ titel, children }: { titel: string; children: React.ReactNode }) {
    return (
        <fieldset className="space-y-3">
            <legend className="text-sm font-semibold text-slate-900 pb-1">{titel}</legend>
            {children}
        </fieldset>
    );
}

function Haken({ id, label, wert, onChange, hilfe }: { id: string; label: string; wert: boolean; onChange: (v: boolean) => void; hilfe?: string }) {
    return (
        <div>
            <label htmlFor={id} className="flex items-center gap-2 text-sm text-slate-700 cursor-pointer">
                <input id={id} type="checkbox" checked={wert} onChange={e => onChange(e.target.checked)} className="h-4 w-4 rounded border-slate-300 accent-rose-600" />
                {label}
            </label>
            {hilfe && <p className="ml-6 text-xs text-slate-500">{hilfe}</p>}
        </div>
    );
}

function Zahl({ id, label, wert, onChange, einheit = 'mm', min = 0, max = 300 }: { id: string; label: string; wert: number; onChange: (v: number) => void; einheit?: string; min?: number; max?: number }) {
    return (
        <div>
            <Label htmlFor={id}>{label} ({einheit})</Label>
            <Input id={id} type="number" inputMode="numeric" min={min} max={max} value={Number.isFinite(wert) ? wert : ''}
                onChange={e => onChange(e.target.value === '' ? NaN : Number(e.target.value))} />
        </div>
    );
}

/** Einstellungen einer Dokumentvorlage: Seite, Adresszeile, Betreffzeilen, Infoblock, Fußzeile, Briefpapier, Positionen. */
export default function LayoutEinstellungenDialog({ offen, einstellungen, lieferschein, onSchliessen, onUebernehmen }: Props) {
    const [werte, setWerte] = useState<VorlagenEinstellungen | null>(einstellungen);
    const [fehler, setFehler] = useState('');
    const [arbeitet, setArbeitet] = useState(false);

    const ladeStandard = async () => {
        setFehler('');
        try {
            const res = await fetch(`/api/formulare/layout-einstellungen/standard?art=${lieferschein ? 'lieferschein' : 'standard'}`);
            if (!res.ok) throw new Error();
            setWerte(await res.json() as VorlagenEinstellungen);
        } catch {
            setFehler('Die Standardwerte konnten nicht geladen werden.');
        }
    };

    useEffect(() => {
        if (!offen) return;
        setFehler('');
        if (einstellungen) setWerte(einstellungen);
        else { setWerte(null); void ladeStandard(); }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [offen, einstellungen]);

    const setze = <K extends keyof VorlagenEinstellungen>(feld: K, wert: VorlagenEinstellungen[K]) =>
        setWerte(w => (w ? { ...w, [feld]: wert } : w));

    const uebernehmen = async () => {
        if (!werte) return;
        const zahlenfelder = (Object.entries(werte) as [string, unknown][]).filter(([, v]) => typeof v === 'number' && !Number.isFinite(v as number));
        if (zahlenfelder.length > 0) { setFehler('Bitte alle Zahlenfelder ausfüllen.'); return; }
        setArbeitet(true);
        setFehler('');
        try {
            const res = await fetch('/api/formulare/layout-aus-einstellungen', {
                method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(werte),
            });
            if (!res.ok) throw new Error();
            const antwort = await res.json() as { html: string; einstellungen: VorlagenEinstellungen };
            onUebernehmen(antwort.html, antwort.einstellungen);
            onSchliessen();
        } catch {
            setFehler('Das Layout konnte nicht erstellt werden.');
        } finally {
            setArbeitet(false);
        }
    };

    return (
        <Dialog open={offen} onOpenChange={(o) => { if (!o) onSchliessen(); }}>
            <DialogContent className="sm:max-w-[640px] max-h-[90vh] overflow-y-auto">
                <DialogHeader>
                    <DialogTitle>Seiten-Einstellungen</DialogTitle>
                    <DialogDescription>
                        Hier legen Sie fest, wo Adresse, Infoblock und Betreffzeilen stehen und wie die Positionen aussehen. „Layout neu aufbauen" setzt
                        die Blöcke danach neu; eigene Verschiebungen und zusätzliche Blöcke gehen dabei verloren. Gespeichert wird erst mit „Speichern".
                    </DialogDescription>
                </DialogHeader>

                {!werte ? (
                    <div className="flex items-center gap-2 text-slate-500 text-sm py-8" role="status">
                        <Loader2 className="w-4 h-4 animate-spin motion-reduce:animate-none" aria-hidden="true" /> Einstellungen werden geladen …
                    </div>
                ) : (
                    <div className="space-y-6">
                        <Gruppe titel="Schrift und Seite">
                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                                <div>
                                    <Label>Schriftart</Label>
                                    <Select value={werte.schriftart} onChange={v => setze('schriftart', v as VorlagenEinstellungen['schriftart'])} options={SCHRIFTEN} aria-label="Schriftart" />
                                </div>
                                <Zahl id="le-schriftgroesse" label="Schriftgröße" einheit="pt" min={6} max={14} wert={werte.schriftgroesse} onChange={v => setze('schriftgroesse', v)} />
                                <Zahl id="le-haupt-oben" label="Hauptblock: Abstand oben" wert={werte.hauptblockOben} min={40} max={200} onChange={v => setze('hauptblockOben', v)} />
                                <Zahl id="le-haupt-links" label="Hauptblock: Abstand links" wert={werte.hauptblockLinks} max={60} onChange={v => setze('hauptblockLinks', v)} />
                                <Zahl id="le-haupt-rechts" label="Hauptblock: Abstand rechts" wert={werte.hauptblockRechts} max={60} onChange={v => setze('hauptblockRechts', v)} />
                            </div>
                            <Haken id="le-falzmarken" label="Falzmarken auf der ersten Seite anzeigen" wert={werte.falzmarken} onChange={v => setze('falzmarken', v)} />
                        </Gruppe>

                        <Gruppe titel="Adresszeile (Absender über dem Empfänger)">
                            <Haken id="le-adresszeile" label="Adresszeile anzeigen" wert={werte.adresszeileAnzeigen} onChange={v => setze('adresszeileAnzeigen', v)} />
                            <div>
                                <Label htmlFor="le-adresszeile-text">Eigener Text (leer = Firmenname, Straße und Ort)</Label>
                                <Input id="le-adresszeile-text" value={werte.adresszeileText} maxLength={200} onChange={e => setze('adresszeileText', e.target.value)} placeholder="z. B. Muster GmbH | Weg 1 | 12345 Ort" />
                            </div>
                            <div className="grid grid-cols-2 gap-3">
                                <Zahl id="le-adr-oben" label="Abstand oben" wert={werte.adresszeileOben} min={5} max={150} onChange={v => setze('adresszeileOben', v)} />
                                <Zahl id="le-adr-links" label="Abstand links" wert={werte.adresszeileLinks} max={150} onChange={v => setze('adresszeileLinks', v)} />
                            </div>
                        </Gruppe>

                        <Gruppe titel="Betreffzeile 1 (Projektanschrift)">
                            <div className="flex flex-wrap gap-x-6 gap-y-2">
                                <Haken id="le-b1-an" label="Betreffzeile 1 anzeigen" wert={werte.betreff1Anzeigen} onChange={v => setze('betreff1Anzeigen', v)} />
                                <Haken id="le-b1-fett" label="Fett gedruckt" wert={werte.betreff1Fett} onChange={v => setze('betreff1Fett', v)} />
                            </div>
                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                                <div>
                                    <Label>Was soll hier stehen?</Label>
                                    <Select value={werte.betreff1Inhalt} onChange={v => setze('betreff1Inhalt', v as VorlagenEinstellungen['betreff1Inhalt'])} options={BETREFF1} aria-label="Inhalt der Betreffzeile 1" />
                                </div>
                                <Zahl id="le-b1-gr" label="Schriftgröße" einheit="pt" min={6} max={24} wert={werte.betreff1Schriftgroesse} onChange={v => setze('betreff1Schriftgroesse', v)} />
                            </div>
                            <div>
                                <Label htmlFor="le-b1-praefix">Präfix der Betreffzeile</Label>
                                <Input id="le-b1-praefix" value={werte.betreff1Praefix} maxLength={30} onChange={e => setze('betreff1Praefix', e.target.value)} placeholder="z. B. BV:" />
                            </div>
                        </Gruppe>

                        <Gruppe titel="Betreffzeile 2 (Titel mit Dokumentnummer)">
                            <div className="flex flex-wrap gap-x-6 gap-y-2">
                                <Haken id="le-b2-an" label="Betreffzeile 2 anzeigen" wert={werte.betreff2Anzeigen} onChange={v => setze('betreff2Anzeigen', v)} />
                                <Haken id="le-b2-fett" label="Fett gedruckt" wert={werte.betreff2Fett} onChange={v => setze('betreff2Fett', v)} />
                            </div>
                            <Zahl id="le-b2-gr" label="Schriftgröße" einheit="pt" min={6} max={28} wert={werte.betreff2Schriftgroesse} onChange={v => setze('betreff2Schriftgroesse', v)} />
                            <div>
                                <Label htmlFor="le-b2-text">Eigener Titel (leer = „Rechnung Nr. …" je nach Dokumentart)</Label>
                                <Input id="le-b2-text" value={werte.betreff2Text} maxLength={200} onChange={e => setze('betreff2Text', e.target.value)} placeholder="{{DOKUMENTTYP}} Nr. {{DOKUMENTNUMMER}}" />
                            </div>
                        </Gruppe>

                        <Gruppe titel="Dokumentinformationen und Kontaktdaten (Infoblock)">
                            <Haken id="le-info-an" label="Infoblock anzeigen" wert={werte.infoblockAnzeigen} onChange={v => setze('infoblockAnzeigen', v)} />
                            <div className="grid grid-cols-3 gap-3">
                                <Zahl id="le-info-oben" label="Abstand oben" wert={werte.infoblockOben} min={5} max={200} onChange={v => setze('infoblockOben', v)} />
                                <Zahl id="le-info-links" label="Abstand links" wert={werte.infoblockLinks} max={190} onChange={v => setze('infoblockLinks', v)} />
                                <Zahl id="le-info-breite" label="Breite" wert={werte.infoblockBreite} min={30} max={190} onChange={v => setze('infoblockBreite', v)} />
                            </div>
                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-2">
                                <Haken id="le-i-dok" label="Dokumentnummer" wert={werte.infoDokumentnummer} onChange={v => setze('infoDokumentnummer', v)} />
                                <Haken id="le-i-prj" label="Projektnummer" wert={werte.infoProjektnummer} onChange={v => setze('infoProjektnummer', v)} />
                                <Haken id="le-i-ref" label="Referenzdokument" wert={werte.infoReferenzdokument} onChange={v => setze('infoReferenzdokument', v)} />
                                <Haken id="le-i-kd" label="Kundennummer" wert={werte.infoKundennummer} onChange={v => setze('infoKundennummer', v)} />
                                <Haken id="le-i-dat" label="Datum" wert={werte.infoDatum} onChange={v => setze('infoDatum', v)} />
                                <Haken id="le-i-ans" label="Ansprechpartner (Name)" wert={werte.infoAnsprechpartner} onChange={v => setze('infoAnsprechpartner', v)} />
                                <Haken id="le-i-tel" label="Telefon" wert={werte.infoTelefon} onChange={v => setze('infoTelefon', v)} />
                                <Haken id="le-i-mob" label="Mobil" wert={werte.infoMobil} onChange={v => setze('infoMobil', v)} />
                                <Haken id="le-i-fax" label="Fax" wert={werte.infoFax} onChange={v => setze('infoFax', v)} />
                                <Haken id="le-i-mail" label="E-Mail" wert={werte.infoEmail} onChange={v => setze('infoEmail', v)} />
                            </div>
                            <p className="text-xs text-slate-500">Ansprechpartner ist der angemeldete Benutzer (Name, Telefon, Mobil und E-Mail aus seinem Mitarbeiter). Ohne Angaben gelten die der Firma; leere Zeilen entfallen.</p>
                        </Gruppe>

                        <Gruppe titel="Freitext-Infoblock">
                            <Haken id="le-ft-an" label="Freitext-Infoblock anzeigen" wert={werte.freitextAnzeigen} onChange={v => setze('freitextAnzeigen', v)} />
                            <div className="grid grid-cols-3 gap-3">
                                <Zahl id="le-ft-oben" label="Abstand oben" wert={werte.freitextOben} min={5} max={250} onChange={v => setze('freitextOben', v)} />
                                <Zahl id="le-ft-links" label="Abstand links" wert={werte.freitextLinks} max={190} onChange={v => setze('freitextLinks', v)} />
                                <Zahl id="le-ft-breite" label="Breite" wert={werte.freitextBreite} min={20} max={200} onChange={v => setze('freitextBreite', v)} />
                            </div>
                            <div>
                                <Label htmlFor="le-ft-inhalt">Inhalt</Label>
                                <textarea id="le-ft-inhalt" rows={3} maxLength={1000} value={werte.freitextInhalt} onChange={e => setze('freitextInhalt', e.target.value)}
                                    className="w-full px-3 py-2 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-rose-500/20 focus:border-rose-400" placeholder="z. B. Kostenvoranschlag" />
                            </div>
                        </Gruppe>

                        <Gruppe titel="Fußzeile">
                            <div>
                                <Label>Fußzeile</Label>
                                <Select value={werte.fusszeileTyp} onChange={v => setze('fusszeileTyp', v as VorlagenEinstellungen['fusszeileTyp'])} options={FUSSZEILEN} aria-label="Art der Fußzeile" />
                            </div>
                            <div className="grid grid-cols-2 gap-3">
                                <Zahl id="le-fuss-abstand" label="Abstand zum Hauptblock" wert={werte.fusszeileAbstand} max={40} onChange={v => setze('fusszeileAbstand', v)} />
                                <Zahl id="le-fuss-gr" label="Schriftgröße Fußzeile" einheit="pt" min={5} max={12} wert={werte.fusszeileSchriftgroesse} onChange={v => setze('fusszeileSchriftgroesse', v)} />
                            </div>
                        </Gruppe>

                        <Gruppe titel="Briefpapier">
                            <Haken id="le-papier" label="Briefpapier anzeigen" wert={werte.briefpapierAnzeigen} onChange={v => setze('briefpapierAnzeigen', v)}
                                hilfe="Das Briefpapier laden Sie unter Firma hoch, es gilt für alle Vorlagen. Ausgeschaltet bleibt es beim PDF weg, zum Beispiel für den Druck auf fertiges Papier." />
                        </Gruppe>

                        <Gruppe titel="Positionen">
                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-2">
                                <Haken id="le-pos-text" label="Positions-Beschreibung" wert={werte.positionsBeschreibung} onChange={v => setze('positionsBeschreibung', v)} />
                                <Haken id="le-pos-art" label="Artikelnummer anzeigen" wert={werte.artikelnummerAnzeigen} onChange={v => setze('artikelnummerAnzeigen', v)} />
                                <Haken id="le-pos-preis" label="Einzelpreise und Summen ausblenden" wert={werte.einzelpreiseAusblenden} onChange={v => setze('einzelpreiseAusblenden', v)}
                                    hilfe="Für Lieferscheine. Bei Rechnungen nicht empfehlenswert." />
                            </div>
                            <Zahl id="le-tabelle-gr" label="Schriftgröße der Tabelle" einheit="pt" min={7} max={14} wert={werte.tabellenSchriftgroesse ?? 10} onChange={v => setze('tabellenSchriftgroesse', v)} />
                            <div>
                                <Label>Darstellung der Tabelle</Label>
                                <Select value={werte.tabellenstil} onChange={v => setze('tabellenstil', v as VorlagenEinstellungen['tabellenstil'])} options={TABELLEN} aria-label="Darstellung der Tabelle" />
                            </div>
                        </Gruppe>
                    </div>
                )}

                {fehler && <div role="alert" className="p-3 bg-rose-50 border border-rose-200 text-sm text-rose-800 rounded-lg">{fehler}</div>}

                <DialogFooter className="gap-2">
                    <Button type="button" variant="ghost" size="sm" onClick={() => void ladeStandard()} className="mr-auto text-slate-600">
                        <RotateCcw className="w-3.5 h-3.5 mr-1" aria-hidden="true" /> Standardwerte
                    </Button>
                    <Button type="button" variant="outline" size="sm" onClick={onSchliessen}>Abbrechen</Button>
                    <Button type="button" size="sm" className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" onClick={() => void uebernehmen()} disabled={!werte || arbeitet}>
                        {arbeitet && <Loader2 className="w-4 h-4 animate-spin motion-reduce:animate-none mr-1" aria-hidden="true" />} Layout neu aufbauen
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
