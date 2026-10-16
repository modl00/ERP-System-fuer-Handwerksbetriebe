/** Ein Ansprechpartner eines Kunden. */
export interface Ansprechpartner {
    id: number;
    name: string;
    funktion: string | null;
    telefon: string | null;
    mobil: string | null;
    email: string | null;
    notizen: string | null;
    haupt: boolean;
}

export interface AnsprechpartnerEingabe {
    name: string;
    funktion: string;
    telefon: string;
    mobil: string;
    email: string;
    notizen: string;
    haupt: boolean;
}

/** Ein Objekt (Liegenschaft, Gebäude oder Standort) eines Kunden. */
export interface KundeObjekt {
    id: number;
    bezeichnung: string;
    strasse: string | null;
    plz: string | null;
    ort: string | null;
    ansprechpartnerName: string | null;
    ansprechpartnerTelefon: string | null;
    notizen: string | null;
    aktiv: boolean;
    anzahlWartungen: number;
}

export interface KundeObjektEingabe {
    bezeichnung: string;
    strasse: string;
    plz: string;
    ort: string;
    ansprechpartnerName: string;
    ansprechpartnerTelefon: string;
    notizen: string;
}
