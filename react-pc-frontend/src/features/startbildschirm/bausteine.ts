import type { ReactNode } from 'react';
import { Aufgaben, Lager, Neues, PlantafelBaustein } from './BausteinInhalte';

/** Die Bausteine, die der Startbildschirm kennt. Neue Bausteine werden hier ergänzt (Kennung, Titel, Beschreibung, Inhalt aus BausteinInhalte.tsx). */
export interface Baustein {
    id: string;
    titel: string;
    beschreibung: string;
    Inhalt: () => ReactNode;
}

/** Aufbau, solange nichts gespeichert ist. */
export const STANDARD_BAUSTEINE = ['aufgaben', 'plantafel', 'lager', 'neues'];

export const BAUSTEINE: Baustein[] = [
    { id: 'aufgaben', titel: 'Das ist offen', beschreibung: 'Offene Aufgaben und Meldungen mit Anzahl, zum Anklicken', Inhalt: Aufgaben },
    { id: 'plantafel', titel: 'Lieferzeiten', beschreibung: 'Aufträge der Plantafel mit Lieferzeit und Ampel', Inhalt: PlantafelBaustein },
    { id: 'lager', titel: 'Lager', beschreibung: 'Artikel unter Mindestbestand und mit negativem Bestand', Inhalt: Lager },
    { id: 'neues', titel: 'Neueste Meldungen', beschreibung: 'Die letzten Meldungen (neue E-Mails, Anfragen …)', Inhalt: Neues },
];

export const baustein = (id: string) => BAUSTEINE.find(b => b.id === id);
