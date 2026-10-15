import { useEffect, useRef, useState } from 'react'
import { Html5Qrcode, Html5QrcodeSupportedFormats } from 'html5-qrcode'
import { Keyboard, Loader2, X } from 'lucide-react'

const BEREICH = 'lager-scanner'

const FORMATE = [
    Html5QrcodeSupportedFormats.EAN_13,
    Html5QrcodeSupportedFormats.EAN_8,
    Html5QrcodeSupportedFormats.UPC_A,
    Html5QrcodeSupportedFormats.UPC_E,
    Html5QrcodeSupportedFormats.CODE_128,
    Html5QrcodeSupportedFormats.CODE_39,
    Html5QrcodeSupportedFormats.ITF,
]

interface Props {
    /** Wird einmal mit dem erkannten oder eingetippten Code aufgerufen; danach schließt der Scanner. */
    onCode: (code: string) => void
    onClose: () => void
}

/**
 * Strichcode-Scanner mit der Kamera. Erkannt wird der erste Code, dann stoppt die Kamera sofort (kein offener Stream, auf dem iPhone sonst
 * dauerhaft der Kamera-Punkt). Ohne Kamera-Erlaubnis oder ohne sichere Verbindung (https) erklärt der Scanner, was zu tun ist, und lässt
 * den Code eintippen. Das Eintippen gibt es immer, auch bei verschmutzten oder beschädigten Etiketten.
 */
export default function BarcodeScanner({ onCode, onClose }: Props) {
    const scannerRef = useRef<Html5Qrcode | null>(null)
    const fertigRef = useRef(false)
    const [startet, setStartet] = useState(true)
    const [fehler, setFehler] = useState<string | null>(null)
    const [eingabe, setEingabe] = useState('')

    useEffect(() => {
        let abgebrochen = false
        const scanner = new Html5Qrcode(BEREICH, { formatsToSupport: FORMATE, verbose: false })
        scannerRef.current = scanner
        scanner.start(
            { facingMode: 'environment' },
            { fps: 10, qrbox: { width: 280, height: 120 } },
            (text) => {
                if (fertigRef.current) return
                fertigRef.current = true
                onCode(text.trim())
            },
            () => { /* einzelne Bilder ohne Code: normal */ },
        ).then(() => { if (!abgebrochen) setStartet(false) })
            .catch((e: unknown) => {
                if (abgebrochen) return
                setStartet(false)
                const text = String(e instanceof Error ? e.message : e)
                setFehler(window.isSecureContext === false
                    ? 'Die Kamera braucht eine sichere Verbindung (https). Tippe die Nummer stattdessen ein.'
                    : /permission|denied|notallowed/i.test(text)
                        ? 'Die Kamera ist nicht erlaubt. Erlaube sie unter Einstellungen oder tippe die Nummer ein.'
                        : 'Die Kamera konnte nicht gestartet werden. Tippe die Nummer ein.')
            })
        return () => {
            abgebrochen = true
            // Kamera freigeben, auch wenn der Start noch läuft.
            const s = scannerRef.current
            scannerRef.current = null
            if (s) {
                void (async () => {
                    try {
                        const zustand = s.getState()
                        if (zustand === 2 || zustand === 3) await s.stop()
                        s.clear()
                    } catch { /* beim Aufräumen egal */ }
                })()
            }
        }
        // onCode bleibt absichtlich außen vor: Der Scanner startet nur einmal.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    const senden = () => {
        const code = eingabe.replace(/\s+/g, '')
        if (!code || fertigRef.current) return
        fertigRef.current = true
        onCode(code)
    }

    return (
        <div role="dialog" aria-label="Strichcode scannen" className="fixed inset-0 z-50 flex flex-col bg-slate-900/95 p-4 safe-area-top">
            <div className="flex items-center justify-between text-white">
                <h2 className="text-lg font-semibold">Strichcode scannen</h2>
                <button onClick={onClose} aria-label="Schließen" className="rounded-full p-2 hover:bg-white/10"><X className="h-6 w-6" /></button>
            </div>

            <div className="mt-4 flex-1 overflow-hidden rounded-2xl bg-black">
                <div id={BEREICH} className="h-full w-full" />
                {startet && !fehler && (
                    <div className="flex items-center justify-center gap-2 py-6 text-sm text-slate-300"><Loader2 className="h-5 w-5 animate-spin" /> Kamera startet …</div>
                )}
            </div>

            {fehler && <p role="alert" className="mt-3 rounded-xl bg-amber-50 p-3 text-sm text-amber-900">{fehler}</p>}

            <div className="mt-4 rounded-2xl bg-white p-3">
                <label htmlFor="lager-code-eingabe" className="mb-1 flex items-center gap-2 text-sm font-medium text-slate-700">
                    <Keyboard className="h-4 w-4" aria-hidden="true" /> Nummer eintippen
                </label>
                <div className="flex gap-2">
                    <input id="lager-code-eingabe" inputMode="numeric" autoComplete="off" value={eingabe} onChange={e => setEingabe(e.target.value)}
                        onKeyDown={e => { if (e.key === 'Enter') senden() }} placeholder="4006381333931"
                        className="min-h-12 flex-1 rounded-lg border border-slate-200 px-3 text-base outline-none focus:ring-2 focus:ring-rose-500" />
                    <button onClick={senden} disabled={!eingabe.trim()}
                        className="min-h-12 rounded-lg bg-rose-600 px-4 font-medium text-white disabled:bg-slate-300">Suchen</button>
                </div>
            </div>
        </div>
    )
}
