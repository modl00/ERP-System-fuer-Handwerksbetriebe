import { stripHtmlTags } from '../../lib/htmlSanitizer';

/**
 * Ein Rich-Text ohne sichtbaren Inhalt gilt als leer und wird als `null` gesendet - sonst bleibt ein geleertes Beschreibungsfeld
 * "gepflegt", weil Tiptap beim Leeren typischerweise "<p></p>" liefert statt eines leeren Strings. Zaehlt nicht als Inhalt: Tags,
 * "&nbsp;" (als Entity oder echtes Zeichen) und gewoehnliche Leerzeichen.
 */
export function richtextOderNull(html: string): string | null {
    // U+00A0 NO-BREAK SPACE: Tiptap schreibt es manchmal direkt statt als &nbsp;
    const NBSP = String.fromCharCode(160);
    const sichtbarerText = stripHtmlTags(html)
        .replace(/&nbsp;/gi, ' ')
        .replaceAll(NBSP, ' ')
        .trim();
    return sichtbarerText === '' ? null : html;
}
