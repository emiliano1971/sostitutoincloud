-- ============================================
-- Migration 030: F24 integrativi per periodi già pagati
-- ============================================
--
-- Ritenute 'da_versare' di un periodo il cui F24 è già pagato (es. ricevute emesse dopo il
-- pagamento) finiscono in un F24 integrativo dello stesso periodo: l'AdE somma i versamenti.
-- Il vincolo "un F24 per tenant/periodo/codice tributo" resta solo sul principale.
--
-- Esecuzione:
--     psql -h localhost -U sostitutoincloud -d sostitutoincloud \
--          -f docs/db/migrations/030_f24_integrativo.sql

ALTER TABLE f24_record
    ADD COLUMN IF NOT EXISTS integrativo BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE f24_record
    ADD COLUMN IF NOT EXISTS fk_f24_principale_id INTEGER REFERENCES f24_record(id) ON DELETE SET NULL;

COMMENT ON COLUMN f24_record.integrativo IS
    'TRUE se è un F24 integrativo per un periodo già pagato. L AdE somma i versamenti per lo stesso periodo.';
COMMENT ON COLUMN f24_record.fk_f24_principale_id IS
    'FK al primo F24 del periodo (quello principale). NULL se questo è il principale.';

-- Unicità solo sul principale: gli integrativi condividono periodo e codice tributo
ALTER TABLE f24_record DROP CONSTRAINT IF EXISTS uq_f24_tenant_period_tributo;
CREATE UNIQUE INDEX IF NOT EXISTS uq_f24_principale_tenant_period_tributo
    ON f24_record (fk_tenant_id, period, fk_codice_tributo_id)
    WHERE integrativo = FALSE;
