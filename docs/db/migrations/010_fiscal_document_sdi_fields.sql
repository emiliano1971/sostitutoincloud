-- ============================================================================
-- 010 — Campi SDI su fiscal_document + progressivi invio SDI per tenant/anno
-- ============================================================================
-- NB: lo stato del documento NON è una colonna varchar "stato_documento" ma la
-- FK fk_stato_documento_id verso la lookup stato_documento, che contiene già i
-- codici usati dal flusso SDI: ready (2), sent_sdi (3), accepted (4),
-- rejected (5), error (6). Gli UPDATE di stato risolvono quindi l'id per codice.
-- ============================================================================

ALTER TABLE fiscal_document
    ADD COLUMN IF NOT EXISTS sdi_progressivo VARCHAR(20),
    ADD COLUMN IF NOT EXISTS sdi_file_path   VARCHAR(500),
    ADD COLUMN IF NOT EXISTS sdi_sent_at     TIMESTAMP,
    ADD COLUMN IF NOT EXISTS sdi_error_msg   VARCHAR(500);

-- Progressivo invio SDI: univoco per tenant + anno, incrementato atomicamente
-- con INSERT ... ON CONFLICT DO UPDATE ... RETURNING (vedi SdiProgressivoDAO).
CREATE TABLE IF NOT EXISTS sdi_progressivo (
    id              SERIAL PRIMARY KEY,
    fk_tenant_id    INTEGER NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    anno            INTEGER NOT NULL,
    ultimo_valore   INTEGER NOT NULL DEFAULT 0,
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_sdi_progressivo UNIQUE (fk_tenant_id, anno)
);
