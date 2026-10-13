import { useState } from 'react';
import { ImageOff } from 'lucide-react';
import { sichereBildQuelle } from './bildQuelle';

interface LogoBildProps {
    /** Base64-Daten-URL oder HTTP-URL. */
    quelle: string;
    className?: string;
}

/**
 * Zeigt ein Logobild sicher an und blendet bei Fehler einen Platzhalter ein.
 * Gemeinsam von BlockInhalt und anderen Formularwesen-Komponenten genutzt.
 */
export function LogoBild({ quelle, className = 'h-full w-full object-contain' }: LogoBildProps) {
    const [fehler, setFehler] = useState(false);
    const sicher = sichereBildQuelle(quelle);
    if (!sicher || fehler) {
        return (
            <div role="img" aria-label="Bild nicht gefunden" className={`flex items-center justify-center rounded bg-slate-100 ${className}`}>
                <ImageOff aria-hidden="true" className="h-6 w-6 text-slate-400" />
            </div>
        );
    }
    return <img src={sicher} alt="Logo" draggable={false} onError={() => setFehler(true)} className={className} />;
}
