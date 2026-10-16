import { useEffect, useState } from 'react';

/** Das mitgelieferte Programmlogo: gilt nur, solange keine Firma ein eigenes Logo hinterlegt hat. */
const STANDARD_LOGO = '/firmenlogo_icon.png';

/** Ereignis, das die Firmenseite nach dem Hochladen oder Löschen des Logos sendet, damit die Kopfleiste sofort das neue Logo zeigt. */
export const FIRMENLOGO_GEAENDERT = 'firmenlogo-geaendert';

/**
 * Das Logo oben links (#209): das **Firmenlogo** aus Einstellungen → Firma, solange eines hinterlegt ist, sonst das mitgelieferte Programmlogo. Dasselbe Logo steht auch als
 * Symbol im Browser-Tab. Gibt es kein Firmenlogo (404) oder ist der Server nicht erreichbar, bleibt es beim Programmlogo.
 */
export function FirmenLogo({ className = 'h-14 w-auto object-contain' }: { className?: string }) {
    const [version, setVersion] = useState(0);
    const [vorhanden, setVorhanden] = useState(true);

    useEffect(() => {
        const neu = () => { setVorhanden(true); setVersion(v => v + 1); };
        window.addEventListener(FIRMENLOGO_GEAENDERT, neu);
        return () => window.removeEventListener(FIRMENLOGO_GEAENDERT, neu);
    }, []);

    const quelle = vorhanden ? `/api/firma/logo?v=${version}` : STANDARD_LOGO;

    // Das Symbol im Browser-Tab folgt dem Logo (und kehrt beim Verlassen zum mitgelieferten zurück)
    useEffect(() => {
        const link = document.querySelector<HTMLLinkElement>('link[rel~="icon"]');
        if (!link) return;
        const alt = link.getAttribute('href');
        link.setAttribute('href', quelle);
        return () => { if (alt) link.setAttribute('href', alt); };
    }, [quelle]);

    return <img src={quelle} alt="Firmenlogo" className={className} onError={() => setVorhanden(false)} />;
}
