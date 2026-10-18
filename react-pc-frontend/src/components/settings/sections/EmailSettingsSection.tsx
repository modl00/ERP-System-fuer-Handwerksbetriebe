import { PostfachSettings } from './postfach/PostfachSettings';
import { MailOrdnerAuswahl } from './MailOrdnerAuswahl';

/**
 * Einstellungen -> E-Mail.
 */
export function EmailSettingsSection({ onSaved }: { onSaved?: () => void }) {
    return (
        <div className="space-y-6">
            <PostfachSettings onSaved={onSaved} />
            <MailOrdnerAuswahl />
        </div>
    );
}
