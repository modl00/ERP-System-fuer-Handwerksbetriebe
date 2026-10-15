import { useRef, useState } from 'react';

/** So weit (in Pixeln) muss die Maus wandern, bis aus einem Klick ein Ziehen wird. */
const SCHWELLE = 5;

export interface Ziehen {
    /** Verschiebung während des Ziehens (für die Vorschau), sonst {@code null}. */
    stand: { dx: number; dy: number } | null;
    /** An das Element hängen, das gezogen werden soll. */
    bindung: {
        onPointerDown: (e: React.PointerEvent<HTMLElement>) => void;
        onPointerMove: (e: React.PointerEvent<HTMLElement>) => void;
        onPointerUp: (e: React.PointerEvent<HTMLElement>) => void;
        onPointerCancel: () => void;
        onClick: (e: React.MouseEvent<HTMLElement>) => void;
    };
}

/**
 * Macht ein Element mit der Maus (oder dem Finger) verschiebbar. Ein Klick ohne Bewegung bleibt ein Klick ({@code onKlick}, auch per Tastatur);
 * nach dem Ziehen kommt {@code onEnde} mit der Strecke und der Stelle, an der losgelassen wurde.
 */
export function useZiehen({ erlaubt, onKlick, onEnde }: {
    erlaubt: boolean;
    onKlick?: () => void;
    onEnde: (dx: number, dy: number, x: number, y: number) => void;
}): Ziehen {
    const start = useRef<{ id: number; x: number; y: number } | null>(null);
    const bewegt = useRef(false);
    const [stand, setStand] = useState<{ dx: number; dy: number } | null>(null);

    return {
        stand,
        bindung: {
            onPointerDown: e => {
                bewegt.current = false;
                if (!erlaubt || e.button !== 0) return;
                start.current = { id: e.pointerId, x: e.clientX, y: e.clientY };
                e.currentTarget.setPointerCapture?.(e.pointerId);
            },
            onPointerMove: e => {
                const s = start.current;
                if (!s) return;
                const dx = e.clientX - s.x;
                const dy = e.clientY - s.y;
                if (!bewegt.current && Math.hypot(dx, dy) < SCHWELLE) return;
                bewegt.current = true;
                setStand({ dx, dy });
            },
            onPointerUp: e => {
                const s = start.current;
                start.current = null;
                if (!s) return;
                e.currentTarget.releasePointerCapture?.(s.id);
                if (bewegt.current) {
                    setStand(null);
                    onEnde(e.clientX - s.x, e.clientY - s.y, e.clientX, e.clientY);
                }
            },
            onPointerCancel: () => {
                start.current = null;
                bewegt.current = false;
                setStand(null);
            },
            onClick: e => {
                if (bewegt.current) {
                    // Das Loslassen nach dem Ziehen löst noch einen Klick aus: Der soll nichts öffnen
                    bewegt.current = false;
                    e.preventDefault();
                    return;
                }
                onKlick?.();
            },
        },
    };
}
