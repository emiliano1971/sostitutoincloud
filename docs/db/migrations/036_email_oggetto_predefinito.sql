-- ============================================
-- Migration 036: oggetto predefinito dell'email di invio documenti
-- ============================================
--
-- Affianca email_testo_predefinito (migration 031): oggetto proposto nel box "Invia Documenti"
-- del dettaglio prenotazione e nel dialog di invio massivo. Se NULL o vuoto il frontend usa
-- "Documenti fiscali soggiorno". Nessun default a DB: in creazione tenant resta NULL.
--
-- Esecuzione:
--     psql -h localhost -U sostitutoincloud -d sostitutoincloud \
--          -f docs/db/migrations/036_email_oggetto_predefinito.sql

ALTER TABLE tenant_settings
    ADD COLUMN IF NOT EXISTS email_oggetto_predefinito VARCHAR(200);

COMMENT ON COLUMN tenant_settings.email_oggetto_predefinito IS
    'Oggetto predefinito per l invio email dei documenti fiscali. '
    'Se NULL o vuoto il frontend usa "Documenti fiscali soggiorno". '
    'Editabile prima di ogni invio.';
