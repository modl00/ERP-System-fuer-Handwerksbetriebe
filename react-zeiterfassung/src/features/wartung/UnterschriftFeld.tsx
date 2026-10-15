import { useEffect, useRef, useState } from 'react'
import { Eraser } from 'lucide-react'

interface Props {
    /** Beschriftung, zum Beispiel „Unterschrift Kunde“ */
    label: string
    /** Wird bei jeder Änderung mit der PNG-Data-URL aufgerufen (leer = nichts gezeichnet). */
    onChange: (bild: string | null) => void
}

/**
 * Unterschriftsfeld für Finger oder Stift: Zeichnen mit Zeigerereignissen, „Löschen“ leert das Feld. Das Bild ist klein gehalten (Breite 600 px, nur
 * Linien), damit es unter der Grenze des Servers bleibt.
 */
export function UnterschriftFeld({ label, onChange }: Props) {
    const leinwand = useRef<HTMLCanvasElement>(null)
    const zeichnet = useRef(false)
    const [leer, setLeer] = useState(true)

    useEffect(() => {
        const c = leinwand.current
        const ctx = c?.getContext('2d')
        if (!c || !ctx) return
        ctx.lineWidth = 3
        ctx.lineCap = 'round'
        ctx.lineJoin = 'round'
        ctx.strokeStyle = '#0f172a'
    }, [])

    const punkt = (e: React.PointerEvent<HTMLCanvasElement>) => {
        const c = leinwand.current!
        const r = c.getBoundingClientRect()
        return { x: (e.clientX - r.left) * (c.width / r.width), y: (e.clientY - r.top) * (c.height / r.height) }
    }

    const beginn = (e: React.PointerEvent<HTMLCanvasElement>) => {
        const ctx = leinwand.current?.getContext('2d')
        if (!ctx) return
        e.currentTarget.setPointerCapture?.(e.pointerId)
        zeichnet.current = true
        const p = punkt(e)
        ctx.beginPath()
        ctx.moveTo(p.x, p.y)
        ctx.lineTo(p.x + 0.01, p.y + 0.01)
        ctx.stroke()
    }

    const weiter = (e: React.PointerEvent<HTMLCanvasElement>) => {
        if (!zeichnet.current) return
        const ctx = leinwand.current?.getContext('2d')
        if (!ctx) return
        const p = punkt(e)
        ctx.lineTo(p.x, p.y)
        ctx.stroke()
    }

    const ende = () => {
        if (!zeichnet.current) return
        zeichnet.current = false
        setLeer(false)
        onChange(leinwand.current!.toDataURL('image/png'))
    }

    const loeschen = () => {
        const c = leinwand.current
        c?.getContext('2d')?.clearRect(0, 0, c.width, c.height)
        setLeer(true)
        onChange(null)
    }

    return (
        <div>
            <div className="mb-1 flex items-center justify-between">
                <span className="text-sm font-medium text-slate-700">{label}</span>
                <button type="button" onClick={loeschen} disabled={leer} aria-label={`${label} löschen`}
                    className="inline-flex items-center gap-1 rounded-lg px-2 py-1 text-xs text-slate-600 hover:bg-slate-100 disabled:opacity-40">
                    <Eraser className="h-3.5 w-3.5" aria-hidden="true" /> Löschen
                </button>
            </div>
            <canvas ref={leinwand} width={600} height={200} role="img" aria-label={`${label}, hier mit dem Finger unterschreiben`}
                onPointerDown={beginn} onPointerMove={weiter} onPointerUp={ende} onPointerCancel={ende}
                className="h-40 w-full touch-none rounded-xl border border-slate-300 bg-white" />
        </div>
    )
}
