import type { CSSProperties } from 'react';
import type { FormBlockStyles } from '../../types';
import { zerlegeInfoblockZeilen } from './infoblockZeilen';

/**
 * Inhalt eines Textblocks. Zeilen der Form "Bezeichnung[Tab]Wert" (der Infoblock) stehen links die Bezeichnung und rechts bündig der Wert;
 * Zeilen mit leerem Wert entfallen, wie im PDF.
 */
export function TextBlockInhalt({ text, styles }: { text: string; styles: FormBlockStyles }) {
    const stil: CSSProperties = {
        fontSize: styles.fontSize,
        color: styles.color,
        fontWeight: styles.fontWeight as CSSProperties['fontWeight'],
        textAlign: styles.textAlign,
        whiteSpace: 'pre-line',
    };
    if (!text.includes('\t')) {
        return <div style={stil}>{text}</div>;
    }
    const zeilen = zerlegeInfoblockZeilen(text);
    return (
        <div style={{ ...stil, whiteSpace: 'normal', lineHeight: 1.4 }}>
            {zeilen.map((z, i) => (
                <div key={i} className="flex justify-between gap-3">
                    <span>{z.bezeichnung}</span>
                    {z.wert !== null && <span className="text-right">{z.wert}</span>}
                </div>
            ))}
        </div>
    );
}

/** Eine waagerechte Linie über die Breite des Blocks (Falzmarke, Trennstrich). */
export function LinieInhalt({ farbe }: { farbe?: string }) {
    return <div className="w-full" style={{ borderTop: `1px solid ${farbe || '#374151'}` }} role="presentation" />;
}
