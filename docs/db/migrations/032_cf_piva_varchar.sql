-- ============================================
-- Migration 032: codice fiscale e P.IVA a lunghezza variabile
-- ============================================
--
-- tax_code CHAR(16) completava con spazi i codici fiscali numerici a 11 cifre
-- (società, ditte individuali: CF = P.IVA). Con VARCHAR il valore resta quello inserito.
-- Validazione applicativa (CodiceFiscalePivaValidator):
--   - tenant: P.IVA obbligatoria (11 cifre); CF a 16 caratteri o 11 cifre, se vuoto = P.IVA;
--   - owner_profile: persona_fisica → CF a 16 caratteri obbligatorio;
--                    piva/societa  → P.IVA obbligatoria, CF a 16 caratteri o 11 cifre (vuoto = P.IVA).
-- Il cast da CHAR a VARCHAR rimuove gli spazi di riempimento dei valori esistenti.
--
-- Esecuzione:
--     psql -h localhost -U sostitutoincloud -d sostitutoincloud \
--          -f docs/db/migrations/032_cf_piva_varchar.sql

ALTER TABLE tenant
    ALTER COLUMN tax_code   TYPE VARCHAR(16),
    ALTER COLUMN vat_number TYPE VARCHAR(11);

ALTER TABLE owner_profile
    ALTER COLUMN tax_code   TYPE VARCHAR(16),
    ALTER COLUMN vat_number TYPE VARCHAR(11);
