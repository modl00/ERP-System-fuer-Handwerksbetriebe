import { useState } from 'react';
import { Loader2, RotateCcw } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Label } from '../../components/ui/label';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';

interface Props {
    open: boolean;
    laeuft: boolean;
    onOpenChange: (open: boolean) => void;
    /** Der Grund ist freiwillig, kommt aber mit Name und Zeit in den Verlauf des Protokolls. */
    onBestaetigen: (grund: string) => void;
}

/** Wieder öffnen, um einen fertigen Wartungsbericht nachträglich zu ändern (#238). Der Grund steht später im Verlauf. */
export function WiederOeffnenDialog({ open, laeuft, onOpenChange, onBestaetigen }: Props) {
    return (
        <Dialog open={open} onOpenChange={(o) => { if (!laeuft) onOpenChange(o); }}>
            <DialogContent className="sm:max-w-[480px]">
                <Formular laeuft={laeuft} onAbbrechen={() => onOpenChange(false)} onBestaetigen={onBestaetigen} />
            </DialogContent>
        </Dialog>
    );
}

/** Eigener Zustand: Beim nächsten Öffnen ist das Feld wieder leer, weil der Inhalt nur bei geöffnetem Dialog existiert. */
function Formular({ laeuft, onAbbrechen, onBestaetigen }: { laeuft: boolean; onAbbrechen: () => void; onBestaetigen: (grund: string) => void }) {
    const [grund, setGrund] = useState('');
    return (
        <>
                <DialogHeader>
                    <DialogTitle>Wartungsbericht ändern?</DialogTitle>
                    <DialogDescription>
                        Das Protokoll gilt dann nicht mehr als abgeschlossen und lässt sich wieder ändern. Danach schließen Sie es erneut ab; der Bericht im Projekt wird dabei ersetzt.
                        Wer wann geöffnet hat, steht im Verlauf.
                    </DialogDescription>
                </DialogHeader>
                <div>
                    <Label htmlFor="wiederoeffnen-grund">Was soll geändert werden? (freiwillig)</Label>
                    <textarea
                        id="wiederoeffnen-grund"
                        className="w-full min-h-[80px] p-3 rounded-lg border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500"
                        value={grund}
                        maxLength={500}
                        placeholder="z. B. Messwert falsch eingetragen"
                        onChange={(e) => setGrund(e.target.value)}
                    />
                </div>
                <DialogFooter>
                    <Button variant="outline" onClick={onAbbrechen} disabled={laeuft}>Abbrechen</Button>
                    <Button className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700" disabled={laeuft} onClick={() => onBestaetigen(grund)}>
                        {laeuft ? <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> : <RotateCcw className="h-4 w-4" aria-hidden="true" />} Wieder öffnen
                    </Button>
                </DialogFooter>
        </>
    );
}
