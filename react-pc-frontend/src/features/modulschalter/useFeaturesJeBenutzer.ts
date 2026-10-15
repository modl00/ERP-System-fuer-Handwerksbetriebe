import { useEffect } from 'react';
import { useAuth } from '../../auth/AuthContext';
import { setzeFunktionsrechteZurueck } from '../funktionsrechte/useFunktionsrechte';
import { setzeFeaturesZurueck } from './useFeatures';

/**
 * Lädt den Modul-Stand nach einem Benutzerwechsel neu. Der zuletzt gesehene Benutzer steht bewusst außerhalb
 * der Komponente: Beim Abmelden hängt das Layout aus und beim Anmelden wird es neu gebaut.
 */
let zuletztGesehen: number | null | undefined;

export function useFeaturesJeBenutzer(): void {
    const { user } = useAuth();
    const benutzerId = user?.id ?? null;
    useEffect(() => {
        if (zuletztGesehen !== undefined && zuletztGesehen !== benutzerId) { setzeFeaturesZurueck(); setzeFunktionsrechteZurueck(); }
        zuletztGesehen = benutzerId;
    }, [benutzerId]);
}
