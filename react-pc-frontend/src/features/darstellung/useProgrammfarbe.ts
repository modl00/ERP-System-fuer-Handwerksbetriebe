import { useEffect } from 'react';
import { useAuth } from '../../auth/AuthContext';
import { synchronisiereFarbe } from './darstellung';

/** Holt nach der Anmeldung die Programmfarbe der Firma und färbt die Oberfläche. */
export function useProgrammfarbe(): void {
    const { user } = useAuth();
    const angemeldet = user !== null && user !== undefined;
    useEffect(() => {
        if (angemeldet) void synchronisiereFarbe();
    }, [angemeldet]);
}
