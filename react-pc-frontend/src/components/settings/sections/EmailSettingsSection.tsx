import { PostfachSettings } from './postfach/PostfachSettings';
import { MailOrdnerAuswahl } from './MailOrdnerAuswahl';
import { ImapOrdnerVerknuepfung } from './ImapOrdnerVerknuepfung';

/**
 * Einstellungen -> E-Mail.
 */
export function EmailSettingsSection({ onSaved }: { onSaved?: () => void }) {
    return (
        <div className="space-y-6">
            <PostfachSettings onSaved={onSaved} />
            <MailOrdnerAuswahl />
            <ImapOrdnerVerknuepfung />
        </div>
    );
}
