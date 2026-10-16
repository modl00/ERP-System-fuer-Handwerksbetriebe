import { render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import BriefpapierPdf from './BriefpapierPdf';

/* eslint-disable @typescript-eslint/no-explicit-any */

describe('BriefpapierPdf', () => {
    beforeEach(() => {
        vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, arrayBuffer: async () => new ArrayBuffer(8) })));
        HTMLCanvasElement.prototype.getContext = vi.fn(() => ({})) as any;
    });
    afterEach(() => {
        vi.unstubAllGlobals();
        delete (window as any).pdfjsLib;
    });

    it('zeichnet die erste Seite des PDF-Briefpapiers auf die Zeichenfläche', async () => {
        const render1 = vi.fn(() => ({ promise: Promise.resolve() }));
        const getPage = vi.fn(async () => ({ getViewport: () => ({ width: 1190, height: 1684 }), render: render1 }));
        (window as any).pdfjsLib = { getDocument: vi.fn(() => ({ promise: Promise.resolve({ getPage }) })) };

        const { container } = render(<BriefpapierPdf url="/api/firma/briefpapier?v=x.pdf" />);

        await waitFor(() => expect(render1).toHaveBeenCalledTimes(1));
        expect(getPage).toHaveBeenCalledWith(1);
        const canvas = container.querySelector('canvas') as HTMLCanvasElement;
        expect(canvas.width).toBe(1190);
        expect(canvas.height).toBe(1684);
        expect(screen.queryByText(/erscheint im fertigen Dokument/)).toBeNull();
    });

    it('erklärt statt zu scheitern, wenn PDF.js fehlt', () => {
        render(<BriefpapierPdf url="/api/firma/briefpapier?v=x.pdf" />);
        expect(screen.getByText(/Briefpapier \(PDF\) erscheint im fertigen Dokument/)).toBeVisible();
    });

    it('erklärt auch, wenn sich die Datei nicht laden lässt', async () => {
        (window as any).pdfjsLib = { getDocument: vi.fn(() => ({ promise: Promise.reject(new Error('kaputt')) })) };
        render(<BriefpapierPdf url="/api/firma/briefpapier?v=x.pdf" />);
        expect(await screen.findByText(/Briefpapier \(PDF\) erscheint im fertigen Dokument/)).toBeVisible();
    });
});
