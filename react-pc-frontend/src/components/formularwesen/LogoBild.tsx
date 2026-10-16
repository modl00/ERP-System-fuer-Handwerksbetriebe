import { useEffect, useState } from 'react';
import { ImageOff } from 'lucide-react';
import { logoQuelle } from './constants';

/** Das Logo eines Logo-Blocks: standardmäßig das Firmenlogo aus der Firma. Fehlt es, steht ein Hinweis statt eines kaputten Bildes. */
export default function LogoBild({ content, align = 'left' }: { content?: string; align?: string }) {
    const quelle = logoQuelle(content);
    const [fehlt, setFehlt] = useState(false);
    useEffect(() => { setFehlt(false); }, [quelle]);   // eslint-disable-line react-hooks/set-state-in-effect
    if (fehlt) {
        return (
            <div className="flex items-center gap-1 text-[10px] text-slate-400 leading-tight" title="Das Logo wird unter Firma hochgeladen">
                <ImageOff className="w-3.5 h-3.5 shrink-0" /> Kein Logo hinterlegt
            </div>
        );
    }
    // Wie im PDF: ins Feld eingepasst ohne Verzerrung, oben bündig, nach dem Stil des Blocks ausgerichtet
    const x = align === 'right' ? 'right' : align === 'center' ? 'center' : 'left';
    return <img src={quelle} alt="Logo" className="w-full h-full object-contain" style={{ objectPosition: `${x} top` }} onError={() => setFehlt(true)} />;
}
