-- ============================================
-- Migration 025: note di credito (NDC) sulle fatture PM
-- ============================================
--
-- La NDC è un fiscal_document di tipo 'nota_credito' (già presente in tipo_documento)
-- collegato alla fattura originale tramite fk_documento_collegato_id.
-- Numero documento NC-YYYY-NNNN generato come FT/RIC (FiscalDocumentDAO.generateDocumentNumber,
-- progressivo per tenant + tipo + anno): nessun contatore dedicato in tenant_settings.
-- Importi della NDC negativi nel DB (total_amount, vat_amount, imponibile), positivi nelle righe.
--
-- Esecuzione:
--     psql -h localhost -U sostitutoincloud -d sostitutoincloud \
--          -v ON_ERROR_STOP=1 -f docs/db/migrations/025_note_di_credito.sql

BEGIN;

CREATE TABLE IF NOT EXISTS fiscal_document_riga_ndc (
    id                      SERIAL          PRIMARY KEY,
    fk_fiscal_document_id   INTEGER         NOT NULL REFERENCES fiscal_document(id) ON DELETE CASCADE,
    fk_tenant_id            INTEGER         NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    fk_split_economico_id   INTEGER         REFERENCES booking_split_economico(id) ON DELETE SET NULL,
    descrizione             VARCHAR(255)    NOT NULL,
    importo_stornato        DECIMAL(10,2)   NOT NULL,
    imponibile_stornato     DECIMAL(10,2),
    aliquota_iva            DECIMAL(5,2)    NOT NULL DEFAULT 0,
    ordinamento             SMALLINT        NOT NULL DEFAULT 0,
    created_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    created_by              INTEGER
);

COMMENT ON TABLE fiscal_document_riga_ndc IS
    'Righe della nota di credito. '
    'importo_stornato è positivo nel DB e negativo nel documento fiscale. '
    'fk_split_economico_id indica la riga di booking_split_economico stornata, '
    'NULL per storno totale o riga libera.';

CREATE INDEX IF NOT EXISTS idx_fdrndc_document ON fiscal_document_riga_ndc(fk_fiscal_document_id);
CREATE INDEX IF NOT EXISTS idx_fdrndc_tenant   ON fiscal_document_riga_ndc(fk_tenant_id);

DROP TRIGGER IF EXISTS trg_fdrndc_updated_at ON fiscal_document_riga_ndc;
CREATE TRIGGER trg_fdrndc_updated_at
    BEFORE UPDATE ON fiscal_document_riga_ndc
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Stato documento per la NDC annullata prima dell'invio SDI (lookup, nessun enum)
INSERT INTO stato_documento (codice, descrizione, is_error, finale)
VALUES ('annullata', 'Annullato', FALSE, TRUE)
ON CONFLICT (codice) DO NOTHING;

-- Stato prenotazione dopo una NDC che storna l'intera fattura PM
-- (con NDC parziale il booking resta doc_issued e resta liquidabile)
INSERT INTO stato_prenotazione (codice, descrizione, finale)
VALUES ('stornata', 'Fattura PM stornata con nota di credito', FALSE)
ON CONFLICT (codice) DO NOTHING;

COMMIT;
