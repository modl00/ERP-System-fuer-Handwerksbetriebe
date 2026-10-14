import { useState } from 'react';
import { ImageOff } from 'lucide-react';

interface BriefpapierPdfProps {
    /** Ladeversion / Cache-Buster (erhöhen nach Upload). */
    version?: number;
    className?: string;
}

/**
 * Zeigt das firmenweite Briefpapier (`/api/firma/briefpapier`) als Hintergrundbild.
 * Wird in EditorCanvas und PreviewModal als Alternative zum vorlageninternen
 * Briefpapier (Base64-Meta) verwendet.
 */
export function BriefpapierPdf({ version = 0, className = '' }: BriefpapierPdfProps) {
    const [fehler, setFehler] = useState(false);

    if (fehler) {
        return (
            <div
                role="img"
                aria-label="Briefpapier nicht verfügbar"
                className={`flex items-center justify-center bg-slate-50 ${className}`}
            >
                <ImageOff aria-hidden="true" className="h-6 w-6 text-slate-300" />
            </div>
        );
    }

    return (
        <img
            src={`/api/firma/briefpapier?v=${version}`}
            alt="Firmenbriefpapier"
            draggable={false}
            onError={() => setFehler(true)}
            className={`object-cover ${className}`}
        />
    );
}
