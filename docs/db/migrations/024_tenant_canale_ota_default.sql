-- ============================================
-- Migration 024: canale OTA di default del tenant
-- Usato dall'importazione massiva proprietari/immobili (OwnerBulkImportService)
-- per creare la regola commissione_ota degli immobili importati.
-- ============================================
--
-- Esecuzione:
--     psql -h localhost -U sostitutoincloud -d sostitutoincloud \
--          -v ON_ERROR_STOP=1 -f docs/db/migrations/024_tenant_canale_ota_default.sql

BEGIN;

ALTER TABLE tenant_settings
    ADD COLUMN IF NOT EXISTS fk_canale_ota_default_id INTEGER
        REFERENCES canale_ota(id) ON DELETE SET NULL;

COMMENT ON COLUMN tenant_settings.fk_canale_ota_default_id IS
    'Canale OTA di default usato per le regole commissione_ota '
    'nell import massivo proprietari. '
    'NULL = nessun canale default configurato.';

COMMIT;
