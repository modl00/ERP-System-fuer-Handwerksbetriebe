import { render, screen, waitFor } from '@testing-library/react';
import { expect, it } from 'vitest';
import LogoBild from './LogoBild';
import PreviewModal from './PreviewModal';
import { FIRMEN_SAMPLE_DATA, SAMPLE_DATA, istFirmenlogoInhalt, istNurFirmenangaben, logoQuelle, type FirmenPapier } from './constants';
import type { FormBlock } from '../../types';

const block = (aenderung: Partial<FormBlock> & Pick<FormBlock, 'id' | 'type'>): FormBlock =>
    ({ page: 1, x: 10, y: 10, z: 1, width: 150, height: 50, ...aenderung });

it('Logo-Blöcke zeigen das Firmenlogo, auch beim alten Platzhalter', () => {
    for (const inhalt of [undefined, '', '  ', '{{FIRMENLOGO}}', '/image001.png']) {
        expect(istFirmenlogoInhalt(inhalt)).toBe(true);
        expect(logoQuelle(inhalt)).toBe('/api/firma/logo');
    }
    expect(istFirmenlogoInhalt('data:image/png;base64,AAAA')).toBe(false);
    expect(logoQuelle('data:image/png;base64,AAAA')).toBe('data:image/png;base64,AAAA');
});

it('erkennt Briefkopf- und Fußzeilenblöcke an den Firmen-Platzhaltern', () => {
    expect(istNurFirmenangaben('{{FIRMENNAME}}')).toBe(true);
    expect(istNurFirmenangaben('{{FIRMENADRESSE}} · {{FIRMENKONTAKT}}')).toBe(true);
    expect(istNurFirmenangaben('{{ABSENDERZEILE}}')).toBe(true);
    expect(istNurFirmenangaben('{{FIRMENNAME}}  ·  {{DOKUMENTTYP}} {{DOKUMENTNUMMER}}')).toBe(false);
    expect(istNurFirmenangaben('{{KUNDENADRESSE}}')).toBe(false);
    expect(istNurFirmenangaben('Tel. {{FIRMENTELEFON}}')).toBe(false);
    expect(istNurFirmenangaben('')).toBe(false);
    expect(istNurFirmenangaben(undefined)).toBe(false);
});

it('ein fehlendes Logo wird als Hinweis angezeigt statt als kaputtes Bild', async () => {
    render(<LogoBild content="{{FIRMENLOGO}}" />);

    const bild = screen.getByAltText('Logo');
    expect(bild).toHaveAttribute('src', '/api/firma/logo');
    bild.dispatchEvent(new Event('error'));

    expect(await screen.findByText('Kein Logo hinterlegt')).toBeVisible();
});

const items: FormBlock[] = [
    block({ id: 'logo', type: 'logo', content: '{{FIRMENLOGO}}' }),
    block({ id: 'name', type: 'text', content: '{{FIRMENNAME}}', y: 80 }),
    block({ id: 'bank', type: 'text', content: '{{FIRMENBANK}}', y: 700 }),
    block({ id: 'adresse', type: 'text', content: '{{KUNDENADRESSE}}', y: 140 }),
];
const liveData = { ...SAMPLE_DATA, ...FIRMEN_SAMPLE_DATA };

const zeige = (papier: FirmenPapier | null, hintergrund: string | null = null) =>
    render(<PreviewModal items={items} backgroundImage={hintergrund} backgroundImagePage2={null} firmenPapier={papier} liveData={liveData} onClose={() => {}} />);

it('die Vorschau zeigt das Briefpapier als Hintergrund und alle Blöcke, wenn der Kopf nicht darin steckt', async () => {
    const { container } = zeige({ url: '/api/firma/briefpapier?v=1', istPdf: false, enthaeltKopf: false });

    expect(container.querySelector('img[src="/api/firma/briefpapier?v=1"]')).not.toBeNull();
    expect(container.querySelector('img[src="/api/firma/briefpapier?v=1"]')!.className).toContain('object-fill');
    expect(screen.getByText('Muster Metallbau GmbH')).toBeVisible();
    expect(screen.getByAltText('Logo')).toBeVisible();
    await waitFor(() => expect(screen.getByText(/Beispiel GmbH/)).toBeVisible());
});

it('steckt der Kopf schon im Briefpapier, entfallen Logo, Firmenname und Bank in der Vorschau, die Kundenadresse bleibt', () => {
    zeige({ url: '/api/firma/briefpapier?v=1', istPdf: false, enthaeltKopf: true });

    expect(screen.queryByAltText('Logo')).not.toBeInTheDocument();
    expect(screen.queryByText('Muster Metallbau GmbH')).not.toBeInTheDocument();
    expect(screen.queryByText(/IBAN/)).not.toBeInTheDocument();
    expect(screen.getByText(/Beispiel GmbH/)).toBeVisible();
});

it('ein eigener Hintergrund der Vorlage hat Vorrang vor dem Briefpapier und hebt das Weglassen auf', () => {
    const { container } = zeige({ url: '/api/firma/briefpapier?v=1', istPdf: false, enthaeltKopf: true }, 'data:image/png;base64,AAAA');

    expect(container.querySelector('img[src="/api/firma/briefpapier?v=1"]')).toBeNull();
    expect(container.querySelector('img[src="data:image/png;base64,AAAA"]')).not.toBeNull();
    expect(screen.getByText('Muster Metallbau GmbH')).toBeVisible();
});

it('ein PDF-Briefpapier wird in der Vorschau erklärt statt als kaputtes Bild gezeigt', () => {
    zeige({ url: null, istPdf: true, enthaeltKopf: false });

    expect(screen.getByText(/Briefpapier \(PDF\) erscheint im fertigen Dokument/)).toBeVisible();
});
