import { formatDecimalInput } from '../lib/numberInput';

/** Minuten als Text, zum Beispiel "30" oder "6,5". */
export const minutenText = (wert: number | null | undefined) => (wert == null ? '' : formatDecimalInput(wert));
