import { useEffect, useState } from 'react';
import { AuftragPlanenDialog } from './AuftragPlanenDialog';
import { AB_ERSTELLT_EREIGNIS, ladeAbFenster, ladeAufnahme } from './plantafelApi';

/**
 * Hört darauf, dass eine Auftragsbestätigung erstellt wurde, und fragt dann nach, was die Planung braucht (Issue #331, Abschnitt 5).
 * Das Fenster erscheint nur, wenn der Benutzer es nicht abgeschaltet hat und der Auftrag noch nicht in der Plantafel steht; es hält die Auftragsbestätigung nie auf.
 */
export function AuftragPlanenHost() {
    const [projektId, setProjektId] = useState<number | null>(null);
    useEffect(() => {
        const beiAb = (e: Event) => {
            const id = (e as CustomEvent<{ projektId?: number }>).detail?.projektId;
            if (typeof id !== 'number') return;
            ladeAbFenster().then(f => (f.aktiv ? ladeAufnahme(id) : null)).then(a => { if (a && !a.bereitsGeplant) setProjektId(id); }, () => undefined);
        };
        window.addEventListener(AB_ERSTELLT_EREIGNIS, beiAb);
        return () => window.removeEventListener(AB_ERSTELLT_EREIGNIS, beiAb);
    }, []);
    return projektId == null ? null : <AuftragPlanenDialog offen projektId={projektId} vonSelbst onSchliessen={() => setProjektId(null)} />;
}
