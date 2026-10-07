-- ============================================
-- Migration 028: dimensione pagina delle liste paginate per tenant
-- ============================================
--
-- Usata dalla lista documenti fiscali (GET /api/documents con size=0 o assente):
-- paginazione lato server con totale elementi e pagine (DocumentPageDTO).
-- Modificabile dalle impostazioni tenant, scheda "Configurazione" (10..200).
--
-- Esecuzione:
--     psql -h localhost -U sostitutoincloud -d sostitutoincloud \
--          -f docs/db/migrations/028_tenant_page_size.sql

ALTER TABLE tenant_settings
    ADD COLUMN IF NOT EXISTS page_size INTEGER NOT NULL DEFAULT 50;

COMMENT ON COLUMN tenant_settings.page_size IS
    'Dimensione pagina per le liste paginate (documenti, booking ecc.). Default 50.';
