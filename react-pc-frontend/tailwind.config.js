/**
 * Die Programmfarbe: „rose“ liest seine Stufen aus den Variablen --farbe-50 … --farbe-950 (Format „r g b“),
 * die in den Einstellungen (Darstellung) gesetzt werden. Ohne Variable gilt der Rückfallwert, das mitgelieferte Tailwind-Rosé.
 */
const farbe = (stufe, rueckfall) => `rgb(var(--farbe-${stufe}, ${rueckfall}) / <alpha-value>)`;

/** @type {import('tailwindcss').Config} */
export default {
    content: [
        "./index.html",
        "./src/**/*.{js,ts,jsx,tsx}",
    ],
    theme: {
        extend: {
            colors: {
                rose: {
                    50: farbe(50, '255 241 242'),
                    100: farbe(100, '255 228 230'),
                    200: farbe(200, '254 205 211'),
                    300: farbe(300, '253 164 175'),
                    400: farbe(400, '251 113 133'),
                    500: farbe(500, '244 63 94'),
                    600: farbe(600, '225 29 72'),
                    700: farbe(700, '190 18 60'),
                    800: farbe(800, '159 18 57'),
                    900: farbe(900, '136 19 55'),
                    950: farbe(950, '76 5 25'),
                },
            },
        },
    },
    plugins: [],
}
