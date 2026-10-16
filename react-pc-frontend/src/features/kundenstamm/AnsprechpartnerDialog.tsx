import { useEffect, useState } from 'react';
import { Loader2 } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Input } from '../../components/ui/input';
import { Label } from '../../components/ui/label';
import { PhoneInput } from '../../components/PhoneInput';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { useToast } from '../../components/ui/toast';
import { aendereAnsprechpartner, legeAnsprechpartnerAn } from './api';
import type { Ansprechpartner, AnsprechpartnerEingabe } from './types';

interface AnsprechpartnerDialogProps {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    kundeId: number;
    /** Gesetzt = Ansprechpartner ändern, sonst neu anlegen. */
    ansprechpartner?: Ansprechpartner;
    /** Ist es der erste Ansprechpartner, wird er automatisch zum Hauptansprechpartner. */
    istErster: boolean;
    onGespeichert: () => void;
}

const LEER: AnsprechpartnerEingabe = { name: '', funktion: '', telefon: '', mobil: '', email: '', notizen: '', haupt: false };

// Bewusst einfach und ohne verschachtelte Wiederholungen (kein ReDoS): etwas@etwas.etwas
const EMAIL_MUSTER = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function ausAnsprechpartner(a: Ansprechpartner | undefined): AnsprechpartnerEingabe {
    if (!a) return LEER;
    return {
        name: a.name, funktion: a.funktion ?? '', telefon: a.telefon ?? '', mobil: a.mobil ?? '',
        email: a.email ?? '', notizen: a.notizen ?? '', haupt: a.haupt,
    };
}

/** Anlegen und Ändern eines Ansprechpartners eines Kunden. */
export function AnsprechpartnerDialog({ open, onOpenChange, kundeId, ansprechpartner, istErster, onGespeichert }: AnsprechpartnerDialogProps) {
    const toast = useToast();
    const [eingabe, setEingabe] = useState<AnsprechpartnerEingabe>(LEER);
    const [speichert, setSpeichert] = useState(false);
    const [fehler, setFehler] = useState<{ feld: 'name' | 'email'; text: string } | undefined>();

    useEffect(() => {
        if (open) {
            setEingabe(ausAnsprechpartner(ansprechpartner));
            setFehler(undefined);
        }
    }, [open, ansprechpartner]);

    const setze = (feld: keyof AnsprechpartnerEingabe) => (wert: string) => setEingabe((alt) => ({ ...alt, [feld]: wert }));
    const istHaupt = ansprechpartner?.haupt === true;

    const speichern = async () => {
        if (eingabe.name.trim() === '') {
            setFehler({ feld: 'name', text: 'Bitte einen Namen eintragen.' });
            return;
        }
        if (eingabe.email.trim() !== '' && !EMAIL_MUSTER.test(eingabe.email.trim())) {
            setFehler({ feld: 'email', text: 'Bitte eine gültige E-Mail-Adresse eintragen, zum Beispiel name@firma.de.' });
            return;
        }
        setSpeichert(true);
        try {
            if (ansprechpartner) await aendereAnsprechpartner(kundeId, ansprechpartner.id, eingabe);
            else await legeAnsprechpartnerAn(kundeId, eingabe);
            toast.success(ansprechpartner ? 'Ansprechpartner gespeichert.' : 'Ansprechpartner angelegt.');
            onGespeichert();
            onOpenChange(false);
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Der Ansprechpartner konnte nicht gespeichert werden.');
        } finally {
            setSpeichert(false);
        }
    };

    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent className="sm:max-w-[520px]">
                <DialogHeader>
                    <DialogTitle>{ansprechpartner ? 'Ansprechpartner bearbeiten' : 'Ansprechpartner hinzufügen'}</DialogTitle>
                    <DialogDescription>
                        Eine Person beim Kunden, die Sie erreichen können, zum Beispiel Geschäftsführung, Hausmeister oder Einkauf.
                    </DialogDescription>
                </DialogHeader>

                <div className="flex flex-col gap-4 py-2">
                    <div>
                        <Label htmlFor="ap-name">Name</Label>
                        <Input
                            id="ap-name"
                            value={eingabe.name}
                            maxLength={200}
                            placeholder="z. B. Hans Beispiel"
                            aria-invalid={fehler?.feld === 'name' ? true : undefined}
                            aria-describedby={fehler?.feld === 'name' ? 'ap-name-fehler' : undefined}
                            onChange={(e) => { setze('name')(e.target.value); setFehler(undefined); }}
                        />
                        {fehler?.feld === 'name' && <p id="ap-name-fehler" role="alert" className="mt-1 text-sm text-rose-700">{fehler.text}</p>}
                    </div>
                    <div>
                        <Label htmlFor="ap-funktion">Funktion</Label>
                        <Input id="ap-funktion" value={eingabe.funktion} maxLength={120} placeholder="z. B. Hausmeister, Einkauf" onChange={(e) => setze('funktion')(e.target.value)} />
                    </div>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                        <div>
                            <Label htmlFor="ap-telefon">Telefon</Label>
                            <PhoneInput id="ap-telefon" value={eingabe.telefon} maxLength={60} onChange={setze('telefon')} />
                        </div>
                        <div>
                            <Label htmlFor="ap-mobil">Mobil</Label>
                            <PhoneInput id="ap-mobil" variant="mobil" value={eingabe.mobil} maxLength={60} onChange={setze('mobil')} />
                        </div>
                    </div>
                    <div>
                        <Label htmlFor="ap-email">E-Mail</Label>
                        <Input
                            id="ap-email"
                            type="email"
                            value={eingabe.email}
                            maxLength={200}
                            placeholder="name@firma.de"
                            aria-invalid={fehler?.feld === 'email' ? true : undefined}
                            aria-describedby={fehler?.feld === 'email' ? 'ap-email-fehler' : undefined}
                            onChange={(e) => { setze('email')(e.target.value); setFehler(undefined); }}
                        />
                        {fehler?.feld === 'email' && <p id="ap-email-fehler" role="alert" className="mt-1 text-sm text-rose-700">{fehler.text}</p>}
                    </div>
                    <div>
                        <Label htmlFor="ap-notizen">Notiz</Label>
                        <textarea
                            id="ap-notizen"
                            className="w-full min-h-[80px] p-3 rounded-lg border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500"
                            value={eingabe.notizen}
                            maxLength={10000}
                            placeholder="z. B. nur vormittags erreichbar"
                            onChange={(e) => setze('notizen')(e.target.value)}
                        />
                    </div>
                    <label className="flex min-h-11 items-start gap-3 text-sm text-slate-700">
                        <input
                            type="checkbox"
                            className="mt-1 h-4 w-4 accent-rose-600"
                            checked={eingabe.haupt || istErster}
                            disabled={istHaupt || istErster}
                            onChange={(e) => setEingabe((alt) => ({ ...alt, haupt: e.target.checked }))}
                        />
                        <span>
                            <span className="font-medium">Hauptansprechpartner</span>
                            <span className="block text-slate-500">
                                {istErster || istHaupt
                                    ? 'Der Hauptansprechpartner steht in E-Mails, Mahnungen und Dokumenten. Um ihn zu wechseln, einen anderen zum Hauptansprechpartner machen.'
                                    : 'Er löst den bisherigen Hauptansprechpartner ab und steht künftig in E-Mails, Mahnungen und Dokumenten.'}
                            </span>
                        </span>
                    </label>
                </div>

                <DialogFooter>
                    <Button variant="outline" size="sm" onClick={() => onOpenChange(false)} disabled={speichert}>Abbrechen</Button>
                    <Button size="sm" onClick={speichern} disabled={speichert}>
                        {speichert && <Loader2 className="w-4 h-4 animate-spin" aria-hidden="true" />}
                        {speichert ? 'Speichert...' : 'Speichern'}
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
