import { TerminModal, type TerminModalProps } from '../kalender/TerminModal';

export type { TerminModalProps as TerminDialogProps };

/**
 * Termin in der Plantafel anlegen, ändern oder löschen.
 * Delegiert an das gemeinsame TerminModal (Kalender und Plantafel teilen sich ein Modal).
 */
export function TerminDialog(props: TerminModalProps) {
    return <TerminModal {...props} />;
}
