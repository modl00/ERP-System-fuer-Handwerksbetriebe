-- Portiert von db/migration/V943__kostenstelle_jahres_soll.sql (MySQL) nach PostgreSQL.
-- Geschätzter Jahresbetrag (Soll) je Kostenstelle für die Gemeinkosten der Kostenrechnung (#237): Bei einer frischen Installation gibt es noch keine Belege, trotzdem soll ein Satz entstehen.
-- Für das laufende und spätere Jahre gilt je Kostenstelle der höhere Wert aus echten Kosten (Belege) und Soll. Leer = kein Soll. Idempotent, keine Enum-Spalte.
ALTER TABLE firma_kostenstelle ADD COLUMN IF NOT EXISTS jahres_soll DECIMAL(14,2) NULL;
