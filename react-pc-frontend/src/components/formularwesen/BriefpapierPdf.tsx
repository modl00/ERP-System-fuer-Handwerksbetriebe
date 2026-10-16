import { useEffect, useRef, useState } from 'react';

/* eslint-disable @typescript-eslint/no-explicit-any */

interface Props {
    /** Adresse des PDF-Briefpapiers */
    url: string;
    className?: string;
}

/**
 * Zeigt die erste Seite eines PDF-Briefpapiers als Hintergrund, auf die ganze Fläche gezogen (wie im fertigen Dokument, wo es immer auf ganz A4 liegt).
 * Nutzt PDF.js, das die Anwendung schon für die PDF-Vorschau lädt. Steht PDF.js nicht zur Verfügung oder lässt sich die Datei nicht lesen,
 * erscheint ein kurzer Hinweis, das Briefpapier selbst erscheint dann erst im fertigen Dokument.
 */
export default function BriefpapierPdf({ url, className }: Props) {
    const leinwand = useRef<HTMLCanvasElement>(null);
    const [fehler, setFehler] = useState(false);

    useEffect(() => {
        const lib = (window as any).pdfjsLib;
        if (!lib || typeof lib.getDocument !== 'function') { setFehler(true); return; }
        let abgebrochen = false;
        setFehler(false);
        (async () => {
            try {
                const antwort = await fetch(url);
                if (!antwort.ok) throw new Error('Briefpapier nicht ladbar');
                const pdf = await lib.getDocument({ data: await antwort.arrayBuffer() }).promise;
                const seite = await pdf.getPage(1);
                if (abgebrochen || !leinwand.current) return;
                const ansicht = seite.getViewport({ scale: 2 });
                const canvas = leinwand.current;
                canvas.width = ansicht.width;
                canvas.height = ansicht.height;
                const kontext = canvas.getContext('2d');
                if (!kontext) throw new Error('Keine Zeichenfläche');
                await seite.render({ canvasContext: kontext, viewport: ansicht }).promise;
            } catch {
                if (!abgebrochen) setFehler(true);
            }
        })();
        return () => { abgebrochen = true; };
    }, [url]);

    if (fehler) {
        return (
            <p className="absolute bottom-2 left-2 right-2 text-center text-[10px] text-slate-400" style={{ zIndex: 0 }}>
                Ihr Briefpapier (PDF) erscheint im fertigen Dokument hinter dem Text.
            </p>
        );
    }
    return <canvas ref={leinwand} aria-hidden="true" className={className ?? 'absolute inset-0 w-full h-full pointer-events-none'} style={{ zIndex: 0 }} />;
}
