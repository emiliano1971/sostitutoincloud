-- ============================================
-- Migration 038: annullo dell'invio manuale allo SDI
-- ============================================
--
-- SdiManualeAnnulloService.annulla() riporta a 'ready' una fattura PM o una nota di credito
-- marcata come inviata manualmente per errore (sent_sdi senza progressivo né file) e
-- valorizza questa colonna con NOW(). Resta valorizzata anche dopo un nuovo invio: serve
-- all'avviso SDI_MANUALE_ANNULLATO dell'eliminazione documento. Lo storico completo resta in
-- audit_log (azione sdi.manuale.annulla).
--
-- Esecuzione:
--     psql -h localhost -U sostitutoincloud -d sostitutoincloud \
--          -f docs/db/migrations/038_fiscal_document_sdi_manuale_annullato.sql

ALTER TABLE fiscal_document
    ADD COLUMN IF NOT EXISTS sdi_manuale_annullato_at TIMESTAMP;

COMMENT ON COLUMN fiscal_document.sdi_manuale_annullato_at IS
    'Data dell ultimo annullo di un invio manuale allo SDI; resta anche dopo un nuovo invio. '
    'NULL se mai annullato.';
