-- IMAP-Ordner (Papierkorb, Spam) pro Postfach einstellbar (Issue #518)
ALTER TABLE email_absender
    ADD COLUMN IF NOT EXISTS imap_ordner_papierkorb VARCHAR(255),
    ADD COLUMN IF NOT EXISTS imap_ordner_spam       VARCHAR(255);
