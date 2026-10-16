import { stripHtmlTags } from '../../lib/htmlSanitizer';

/**
 * Ein Rich-Text ohne sichtbaren Inhalt gilt als leer und wird als `null` gesendet - sonst bleibt ein geleertes Beschreibungsfeld
 * "gepflegt", weil Tiptap beim Leeren typischerweise "<p></p>" liefert statt eines leeren Strings. Zählt nicht als Inhalt: Tags,
 * "&nbsp;" (als Entity oder echtes Zeichen) und gewöhnliche Leerzeichen.
 */
export function richtextOderNull(html: string): string | null {
    const sichtbarerText = stripHtmlTags(html)
        .replace(/&nbsp;/gi, ' ')
        .replace(/ /g, ' ')
        .trim();
    return sichtbarerText === '' ? null : html;
}
