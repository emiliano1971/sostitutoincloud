-- 009_tenant_settings_bollo_default.sql
-- Default per i parametri bollo su tenant_settings (marca da bollo €2,00 oltre €77,47).
ALTER TABLE tenant_settings
    ALTER COLUMN bollo_importo
    SET DEFAULT 2.00;

ALTER TABLE tenant_settings
    ALTER COLUMN bollo_soglia
    SET DEFAULT 77.47;
