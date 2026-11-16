-- ============================================
-- Migration 037: traccia dell'ultimo invio email di un documento fiscale
-- ============================================
--
-- EmailService.inviaDocumenti() le valorizza dopo un invio riuscito, solo per i documenti
-- effettivamente allegati. Un nuovo invio sovrascrive i valori: lo storico completo resta in
-- audit_log (azione email.documenti). I documenti esistenti restano NULL: gli invii precedenti
-- non sono ricostruibili.
--
-- Esecuzione:
--     psql -h localhost -U sostitutoincloud -d sostitutoincloud \
--          -f docs/db/migrations/037_fiscal_document_email_inviata.sql

ALTER TABLE fiscal_document
    ADD COLUMN IF NOT EXISTS email_inviata_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS email_inviata_a  VARCHAR(500);

COMMENT ON COLUMN fiscal_document.email_inviata_at IS
    'Ultimo invio email del documento accettato dal server SMTP (non conferma la consegna). '
    'NULL se mai inviato da questa funzione.';

COMMENT ON COLUMN fiscal_document.email_inviata_a IS
    'Destinatari dell ultimo invio email, separati da punto e virgola.';
