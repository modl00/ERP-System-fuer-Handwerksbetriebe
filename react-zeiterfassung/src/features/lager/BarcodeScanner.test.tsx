import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

const kamera = vi.hoisted(() => ({
    start: vi.fn(),
    stop: vi.fn(async () => undefined),
    clear: vi.fn(),
    getState: vi.fn(() => 2),
    beiErkennung: null as null | ((text: string) => void),
}))

vi.mock('html5-qrcode', () => ({
    Html5Qrcode: class {
        start = kamera.start
        stop = kamera.stop
        clear = kamera.clear
        getState = kamera.getState
    },
    Html5QrcodeSupportedFormats: { EAN_13: 1, EAN_8: 2, UPC_A: 3, UPC_E: 4, CODE_128: 5, CODE_39: 6, ITF: 7 },
}))

import BarcodeScanner from './BarcodeScanner'

beforeEach(() => {
    kamera.start.mockReset()
    kamera.start.mockImplementation(async (_cam: unknown, _cfg: unknown, ok: (t: string) => void) => { kamera.beiErkennung = ok })
    kamera.stop.mockClear()
    kamera.clear.mockClear()
})
afterEach(() => { kamera.beiErkennung = null })

describe('BarcodeScanner', () => {
    it('startet die Rückkamera für Strichcodes und meldet den ersten erkannten Code genau einmal', async () => {
        const onCode = vi.fn()
        render(<BarcodeScanner onCode={onCode} onClose={vi.fn()} />)

        await waitFor(() => expect(kamera.start).toHaveBeenCalled())
        expect(kamera.start.mock.calls[0][0]).toEqual({ facingMode: 'environment' })
        kamera.beiErkennung?.(' 4006381333931 ')
        kamera.beiErkennung?.('4012345678901')

        expect(onCode).toHaveBeenCalledTimes(1)
        expect(onCode).toHaveBeenCalledWith('4006381333931')
    })

    it('gibt die Kamera beim Schließen frei', async () => {
        const { unmount } = render(<BarcodeScanner onCode={vi.fn()} onClose={vi.fn()} />)
        await waitFor(() => expect(kamera.start).toHaveBeenCalled())

        unmount()

        await waitFor(() => expect(kamera.stop).toHaveBeenCalled())
        expect(kamera.clear).toHaveBeenCalled()
    })

    it('ohne Kamera-Erlaubnis erklärt der Scanner, was zu tun ist, und lässt den Code eintippen', async () => {
        kamera.start.mockRejectedValueOnce(new Error('NotAllowedError: Permission denied'))
        const onCode = vi.fn()
        const user = userEvent.setup()
        render(<BarcodeScanner onCode={onCode} onClose={vi.fn()} />)

        expect(await screen.findByRole('alert')).toHaveTextContent(/nicht erlaubt/)
        await user.type(screen.getByLabelText(/Nummer eintippen/), '4006 381 333931{enter}')

        expect(onCode).toHaveBeenCalledWith('4006381333931')
    })

    it('ohne sichere Verbindung (http) nennt er https als Grund', async () => {
        kamera.start.mockRejectedValueOnce(new Error('getUserMedia is not defined'))
        const alt = Object.getOwnPropertyDescriptor(window, 'isSecureContext')
        Object.defineProperty(window, 'isSecureContext', { value: false, configurable: true })
        try {
            render(<BarcodeScanner onCode={vi.fn()} onClose={vi.fn()} />)
            expect(await screen.findByRole('alert')).toHaveTextContent(/https/)
        } finally {
            if (alt) Object.defineProperty(window, 'isSecureContext', alt)
            else Reflect.deleteProperty(window, 'isSecureContext')
        }
    })

    it('eine andere Störung der Kamera führt ebenfalls zum Eintippen; leere Eingabe sendet nichts', async () => {
        kamera.start.mockRejectedValueOnce(new Error('Kamera belegt'))
        const onCode = vi.fn()
        const user = userEvent.setup()
        render(<BarcodeScanner onCode={onCode} onClose={vi.fn()} />)

        expect(await screen.findByRole('alert')).toHaveTextContent(/nicht gestartet/)
        expect(screen.getByRole('button', { name: 'Suchen' })).toBeDisabled()
        await user.type(screen.getByLabelText(/Nummer eintippen/), '   ')
        expect(onCode).not.toHaveBeenCalled()
    })

    it('Schließen meldet sich bei der Seite', async () => {
        const onClose = vi.fn()
        render(<BarcodeScanner onCode={vi.fn()} onClose={onClose} />)

        await userEvent.click(screen.getByRole('button', { name: 'Schließen' }))

        expect(onClose).toHaveBeenCalled()
    })
})
