-- 008_tenant_settings_pm_anagrafica.sql
-- Dati anagrafici di nascita del PM (sostituto d'imposta), necessari per il modello F24.
-- NB: codice fiscale e denominazione del PM restano su tenant (tax_code, legal_name),
--     qui si aggiungono solo i dati di nascita non presenti altrove.
ALTER TABLE tenant_settings
    ADD COLUMN IF NOT EXISTS data_nascita      DATE,
    ADD COLUMN IF NOT EXISTS sesso             CHAR(1),
    ADD COLUMN IF NOT EXISTS comune_nascita    VARCHAR(100),
    ADD COLUMN IF NOT EXISTS provincia_nascita CHAR(2);
