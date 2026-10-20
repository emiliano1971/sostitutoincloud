-- ============================================
-- Migration 033: invio SDI manuale come default
-- ============================================
--
-- Nuovi tenant: l'auto-invio della fattura PM allo SDI parte disattivato e si abilita
-- dalle impostazioni del tenant. Allineato ai default applicativi (TenantService /
-- TenantSettingsService.defaultSettings(): sdiAutoSend=false). I tenant esistenti
-- mantengono il valore attuale.
--
-- Esecuzione:
--     psql -h localhost -U sostitutoincloud -d sostitutoincloud \
--          -f docs/db/migrations/033_sdi_auto_send_default_false.sql

ALTER TABLE tenant_settings
    ALTER COLUMN sdi_auto_send SET DEFAULT FALSE;
