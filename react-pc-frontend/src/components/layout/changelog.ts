export interface ChangelogEintrag {
    version: string;
    datum: string;
    eintraege: string[];
}

export const CHANGELOG: ChangelogEintrag[] = [
    {
        version: '2026.10.23',
        datum: '23. Oktober 2026',
        eintraege: [
            'Bestellübersicht: Lager-Vorschau beim Einbuchen – jede Position zeigt ob der Artikel erkannt wurde oder noch angelegt werden muss',
            'Artikel anlegen direkt aus dem Lager-Dialog mit der gleichen Maske wie im Artikelstamm, vorausgefüllt mit Bezeichnung und Lieferanten-Nummer',
            'Bestellübersicht: Neuer Reiter „Privat" – Rechnungen die nicht zur Firma gehören können markiert und jederzeit wieder in den normalen Workflow zurückgeholt werden',
            'Erledigt-Tab: Zuordnung nachträglich bearbeiten wieder möglich',
        ],
    },
    {
        version: '2026.10.20',
        datum: '20. Oktober 2026',
        eintraege: [
            'Bestellscheine: neuer Tab „Versandbereit" zeigt offene Entwürfe nach Lieferant gruppiert',
            'Menü Einkauf: „Bedarf" in Bestellscheine integriert – ein Menüpunkt statt zwei',
            'KI-Belegerkennung: Fehlermeldung nennt jetzt den genauen Grund (z. B. Modell kann keine PDFs lesen)',
            'Bestellübersicht: direkt neue Bestellung anlegen ohne Umweg',
        ],
    },
    {
        version: '2026.10.18',
        datum: '18. Oktober 2026',
        eintraege: [
            'Plantafel: Ressourcenansicht nach Mitarbeiter und Maschinen',
            'Schnellzugriff-Leiste oben im Menü konfigurierbar',
            'Programmfarbe angepasst',
            'Kalender und Plantafel sind jetzt verknüpft',
        ],
    },
    {
        version: '2026.10.10',
        datum: '10. Oktober 2026',
        eintraege: [
            'Artikel: Gewicht (Verzinken) und Fläche (Pulverbeschichten) hinterlegbar',
            'Formel-Kalkulation bei Arbeitsgängen: variable Eingaben direkt im Auftrag',
            'KI: Belege im Chat-Fenster direkt auslesen und zuordnen',
            'SMB-Abgleich: Netzwerkordner synchronisieren',
        ],
    },
];

export const AKTUELLE_VERSION = CHANGELOG[0].version;
