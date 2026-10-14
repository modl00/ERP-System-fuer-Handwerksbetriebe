import { Settings2 } from 'lucide-react';
import { Button } from '../ui/button';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '../ui/dialog';
import { InfoButton, InfoAbschnitt } from '../ui/info-button';
import type { FirmaBriefpapierInfo } from './vorlagenEinstellungen';

interface LayoutEinstellungenDialogProps {
    firmaInfo: FirmaBriefpapierInfo;
    onFirmaInfoAendern: (info: FirmaBriefpapierInfo) => void;
    onSchliessen: () => void;
}

/**
 * Dialog für Layout-Einstellungen im Formular-Editor:
 * zeigt den Status des firmenweiten Briefpapiers und erlaubt es,
 * `briefpapierEnthaeltKopf` umzuschalten.
 *
 * Das Briefpapier selbst wird in den Firmen-Einstellungen hochgeladen
 * (Firmendaten → Firmenweites Briefpapier).
 */
export default function LayoutEinstellungenDialog({
    firmaInfo,
    onFirmaInfoAendern,
    onSchliessen,
}: LayoutEinstellungenDialogProps) {
    const hatBriefpapier = !!firmaInfo.briefpapierDateiname;

    return (
        <Dialog open onOpenChange={open => { if (!open) onSchliessen(); }}>
            <DialogContent className="max-w-md">
                <DialogHeader>
                    <DialogTitle className="flex items-center gap-2">
                        <Settings2 aria-hidden="true" className="h-4 w-4 text-rose-600" />
                        Layout-Einstellungen
                    </DialogTitle>
                </DialogHeader>

                <div className="space-y-5 py-1">
                    {/* Briefpapier-Status */}
                    <div>
                        <div className="flex items-center gap-1.5 mb-1">
                            <p className="text-sm font-medium text-slate-800">Firmenweites Briefpapier</p>
                            <InfoButton titel="Firmenweites Briefpapier">
                                <InfoAbschnitt titel="Was passiert im Hintergrund?">
                                    <p>Das Briefpapier-Bild aus den Firmendaten wird als Seitenhintergrund hinter alle Blöcke gelegt – für jede Vorlage automatisch.</p>
                                </InfoAbschnitt>
                                <InfoAbschnitt titel="Was bewirkt das?">
                                    <p>Vorlagen, die kein eigenes Briefpapier haben, zeigen dieses Bild als Seitenhintergrund. Vorlagen mit eigenem Briefpapier behalten ihres.</p>
                                </InfoAbschnitt>
                            </InfoButton>
                        </div>
                        {hatBriefpapier ? (
                            <p className="text-sm text-emerald-700 bg-emerald-50 rounded-lg px-3 py-2">
                                Briefpapier hinterlegt – wird als Seitenhintergrund verwendet.
                            </p>
                        ) : (
                            <p className="text-sm text-slate-500 bg-slate-50 rounded-lg px-3 py-2">
                                Kein firmenweites Briefpapier hinterlegt. Hochladen unter
                                <span className="font-medium"> Firmendaten → Firmenweites Briefpapier</span>.
                            </p>
                        )}
                    </div>

                    {/* Kopf-Option */}
                    {hatBriefpapier && (
                        <div>
                            <div className="flex items-center gap-1.5 mb-1">
                                <p className="text-sm font-medium text-slate-800">Briefpapier enthält Kopfbereich</p>
                                <InfoButton titel="Kopfbereich im Briefpapier">
                                    <InfoAbschnitt titel="Was passiert im Hintergrund?">
                                        <p>Der Formular-Editor blendet den Logo-Baustein aus der Vorschau aus, wenn das Briefpapier bereits ein Logo enthält.</p>
                                    </InfoAbschnitt>
                                    <InfoAbschnitt titel="Was bewirkt das?">
                                        <p>Verhindert, dass Logo und Firmendaten doppelt erscheinen. Der Logo-Baustein bleibt in der Vorlage gespeichert und kann jederzeit wieder aktiviert werden.</p>
                                    </InfoAbschnitt>
                                </InfoButton>
                            </div>
                            <label className="flex items-start gap-2.5 cursor-pointer select-none rounded-lg border border-slate-200 px-3 py-2.5 hover:bg-slate-50 transition-colors">
                                <input
                                    type="checkbox"
                                    className="mt-0.5 accent-rose-600"
                                    checked={firmaInfo.briefpapierEnthaeltKopf}
                                    onChange={e =>
                                        onFirmaInfoAendern({ ...firmaInfo, briefpapierEnthaeltKopf: e.target.checked })
                                    }
                                />
                                <span className="text-sm text-slate-700">
                                    Briefpapier enthält schon Briefkopf und Fußzeile
                                    <span className="block text-xs text-slate-500 mt-0.5">
                                        Logo-Baustein wird in der Vorschau ausgeblendet
                                    </span>
                                </span>
                            </label>
                        </div>
                    )}
                </div>

                <DialogFooter>
                    <Button variant="outline" onClick={onSchliessen}>Schließen</Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
