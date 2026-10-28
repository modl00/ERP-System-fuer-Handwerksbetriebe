import { useCallback } from 'react';
import { useConfirm } from '../../components/ui/confirm-dialog';
import { useToast } from '../../components/ui/toast';
import { aendereVorgang, setzeFruehestens, type Auftrag, type Probe } from './plantafelApi';
import { anzeigeKurz } from './zeitachse';

/** Fragt vor dem Speichern nach (Vorschau „was ändert sich"); {@code false} = der Anwender will es nicht. */
export type VorschauFrage = (vorgangId: number, probe: Probe, titel: string) => Promise<boolean>;

/**
 * Setzt einen Vorgang per Maus auf einen neuen Starttag. Das ist „frühestens ab" und kein Fixtermin: Der Vorgang beginnt nicht früher, rückt aber mit der Kette nach hinten,
 * wenn Vorgänger später fertig werden. Ein Fixtermin entsteht nur durch den Pin im Vorgang; hat der Vorgang schon einen, fragt das Programm vorher nach.
 * Vor dem Speichern zeigt die Vorschau, was sich verschiebt. {@code stand} ist der Stand des Vorgangs, den der Anwender sieht (Schutz vor gleichzeitigem Bearbeiten).
 * Rückgabe: {@code true}, wenn gespeichert wurde.
 */
export function useVorgangVerschieben(onAuftrag: (a: Auftrag) => void, vorschauFrage?: VorschauFrage) {
    const confirm = useConfirm();
    const toast = useToast();
    return useCallback(async (vorgang: { id: number; titel: string; fest: boolean; stand?: number }, neuerStart: string): Promise<boolean> => {
        if (vorgang.fest) {
            const ja = await confirm({
                title: 'Fixtermin verschieben?',
                message: `„${vorgang.titel}" hat einen festen Start. Wirklich auf den ${anzeigeKurz(neuerStart)} verschieben?`,
                confirmLabel: 'Verschieben',
                variant: 'warning',
            });
            if (!ja) return false;
        }
        const probe: Probe = vorgang.fest ? { festerStart: neuerStart, festerStartSetzen: true } : { fruehestensAb: neuerStart, fruehestensAbSetzen: true };
        if (vorschauFrage && !await vorschauFrage(vorgang.id, probe, `„${vorgang.titel}" ${vorgang.fest ? 'beginnt fest am' : 'beginnt frühestens am'} ${anzeigeKurz(neuerStart)}.`)) return false;
        try {
            if (vorgang.fest) {
                onAuftrag(await aendereVorgang(vorgang.id, { festerStart: neuerStart, stand: vorgang.stand }));
                toast.success(`„${vorgang.titel}" beginnt jetzt am ${anzeigeKurz(neuerStart)} (fester Start).`);
            } else {
                onAuftrag(await setzeFruehestens(vorgang.id, neuerStart, vorgang.stand));
                toast.success(`„${vorgang.titel}" beginnt frühestens am ${anzeigeKurz(neuerStart)}.`);
            }
            return true;
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Der Vorgang konnte nicht verschoben werden.');
            return false;
        }
    }, [confirm, toast, onAuftrag, vorschauFrage]);
}
