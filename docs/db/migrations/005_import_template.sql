-- 005_import_template.sql
-- Template di mapping colonne per l'import prenotazioni.
-- Salva nome colonne file → campi di sistema per non rimappare ogni volta.
-- NB: la FK punta a tenant(id) (tabella singolare, come da schema-target.sql).

CREATE TABLE IF NOT EXISTS import_template (
    id              SERIAL PRIMARY KEY,
    fk_tenant_id    INTEGER NOT NULL
                        REFERENCES tenant(id)
                        ON DELETE CASCADE,
    nome            VARCHAR(100) NOT NULL,
    descrizione     VARCHAR(255),
    header_row      INTEGER NOT NULL DEFAULT 0,
    booking_mapping JSONB NOT NULL DEFAULT '{}',
    guest_mapping   JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_import_template_nome
        UNIQUE (fk_tenant_id, nome)
);

DROP TRIGGER IF EXISTS import_template_updated_at ON import_template;
CREATE TRIGGER import_template_updated_at
    BEFORE UPDATE ON import_template
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX IF NOT EXISTS
    idx_import_template_tenant
    ON import_template(fk_tenant_id);
