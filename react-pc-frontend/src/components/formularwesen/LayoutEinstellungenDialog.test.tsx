import { render, screen, fireEvent } from '@testing-library/react';
import LayoutEinstellungenDialog from './LayoutEinstellungenDialog';

const mitBriefpapier = { briefpapierDateiname: 'bp.png', briefpapierEnthaeltKopf: false };
const ohneBriefpapier = { briefpapierDateiname: null, briefpapierEnthaeltKopf: false };

describe('LayoutEinstellungenDialog', () => {
    it('zeigt Hinweis, wenn kein Briefpapier hinterlegt', () => {
        render(<LayoutEinstellungenDialog firmaInfo={ohneBriefpapier} onFirmaInfoAendern={vi.fn()} onSchliessen={vi.fn()} />);
        expect(screen.getByText(/Kein firmenweites Briefpapier/)).toBeInTheDocument();
    });

    it('zeigt Erfolgsmeldung, wenn Briefpapier vorhanden', () => {
        render(<LayoutEinstellungenDialog firmaInfo={mitBriefpapier} onFirmaInfoAendern={vi.fn()} onSchliessen={vi.fn()} />);
        expect(screen.getByText(/Briefpapier hinterlegt/)).toBeInTheDocument();
    });

    it('zeigt Kopf-Checkbox nur wenn Briefpapier vorhanden', () => {
        render(<LayoutEinstellungenDialog firmaInfo={ohneBriefpapier} onFirmaInfoAendern={vi.fn()} onSchliessen={vi.fn()} />);
        expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
    });

    it('ruft onFirmaInfoAendern beim Umschalten der Checkbox auf', () => {
        const onChange = vi.fn();
        render(<LayoutEinstellungenDialog firmaInfo={mitBriefpapier} onFirmaInfoAendern={onChange} onSchliessen={vi.fn()} />);
        fireEvent.click(screen.getByRole('checkbox'));
        expect(onChange).toHaveBeenCalledWith({ ...mitBriefpapier, briefpapierEnthaeltKopf: true });
    });

    it('ruft onSchliessen beim Klick auf Schließen auf', () => {
        const onSchliessen = vi.fn();
        render(<LayoutEinstellungenDialog firmaInfo={ohneBriefpapier} onFirmaInfoAendern={vi.fn()} onSchliessen={onSchliessen} />);
        // Footer-Schließen-Button (nicht der Dialog-X-Button)
        const buttons = screen.getAllByRole('button', { name: /Schließen/i });
        fireEvent.click(buttons[buttons.length - 1]);
        expect(onSchliessen).toHaveBeenCalled();
    });
});
