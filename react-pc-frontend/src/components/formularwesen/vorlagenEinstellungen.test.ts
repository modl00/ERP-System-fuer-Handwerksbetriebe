import { describe, expect, it } from 'vitest';
import { leseVorlagenEinstellungen, vorlagenEinstellungenMeta, type VorlagenEinstellungen } from './vorlagenEinstellungen';

const BEISPIEL = { schriftart: 'TIMES', betreff1Praefix: 'Objekt: "Süd"', falzmarken: false } as unknown as VorlagenEinstellungen;

describe('Vorlagen-Einstellungen im Vorlagen-HTML', () => {
    it('schreibt und liest die Einstellungen, auch mit Sonderzeichen und Anführungszeichen', () => {
        const html = `<meta name="page-format" content="A4" />${vorlagenEinstellungenMeta(BEISPIEL)}<div class="page"></div>`;
        expect(leseVorlagenEinstellungen(html)).toEqual(BEISPIEL);
    });

    it('liefert ohne Einstellungen nichts: die Vorlage behält ihre bisherige Darstellung', () => {
        expect(vorlagenEinstellungenMeta(null)).toBe('');
        expect(leseVorlagenEinstellungen('<meta name="page-format" content="A4" />')).toBeNull();
        expect(leseVorlagenEinstellungen(null)).toBeNull();
        expect(leseVorlagenEinstellungen('')).toBeNull();
    });

    it('ignoriert unlesbare Einstellungen, statt zu scheitern', () => {
        expect(leseVorlagenEinstellungen('<meta name="vorlage-einstellungen" content="%7Bkaputt" />')).toBeNull();
        expect(leseVorlagenEinstellungen('<meta name="vorlage-einstellungen" content="%22text%22" />')).toBeNull();
    });
});
