-- ============================================
-- Migration 027: compensazione dei crediti d'imposta da NDC nel modello F24
-- ============================================
--
-- I crediti nascono da NdcService (migration 026): riga withholding_ledger
-- stato 'credito_imposta', ritenuta_amount negativo.
-- La compensazione in un F24 (F24Service.applicaCrediti):
--   - totale: la riga diventa 'compensato' con fk_f24_record_id = F24;
--   - parziale: la riga resta 'credito_imposta' con il residuo e si crea una nuova riga
--     'compensato' per l'importo usato, con fk_ledger_origine_id = riga di credito.
-- Le righe 'compensato' NON entrano nel totale ritenute dell'F24: il credito è in
-- f24_record.importo_credito e il versamento effettivo è f24_record.saldo_netto.
--
-- Esecuzione:
--     psql -h localhost -U sostitutoincloud -d sostitutoincloud \
--          -v ON_ERROR_STOP=1 -f docs/db/migrations/027_f24_crediti_imposta.sql

BEGIN;

-- withholding_ledger: riga di origine dello spezzamento
ALTER TABLE withholding_ledger
    ADD COLUMN IF NOT EXISTS fk_ledger_origine_id INTEGER
        REFERENCES withholding_ledger(id) ON DELETE SET NULL;

COMMENT ON COLUMN withholding_ledger.fk_ledger_origine_id IS
    'Se valorizzato, questa riga è stata creata per compensazione parziale di '
    'fk_ledger_origine_id. La riga origine viene aggiornata con il residuo.';

COMMENT ON COLUMN withholding_ledger.stato IS
    'da_versare / versata (inclusa in un F24) / stornata (annullata da NDC) / '
    'credito_imposta (credito da NDC su ritenuta già versata) / '
    'compensato (credito usato nell F24 fk_f24_record_id)';

-- Una sola ritenuta per documento fiscale, escluse le righe nate da uno spezzamento
-- (stesso documento NDC della riga di credito da cui derivano)
ALTER TABLE withholding_ledger DROP CONSTRAINT IF EXISTS uq_withholding_per_document;
CREATE UNIQUE INDEX IF NOT EXISTS uq_withholding_per_document
    ON withholding_ledger(fk_fiscal_document_id)
    WHERE fk_ledger_origine_id IS NULL;

-- f24_record: crediti compensati e saldo da versare
ALTER TABLE f24_record ADD COLUMN IF NOT EXISTS importo_credito        DECIMAL(10,2) DEFAULT 0;
ALTER TABLE f24_record ADD COLUMN IF NOT EXISTS codice_tributo_credito VARCHAR(10);
ALTER TABLE f24_record ADD COLUMN IF NOT EXISTS anno_credito           INTEGER;
ALTER TABLE f24_record ADD COLUMN IF NOT EXISTS saldo_netto            DECIMAL(10,2);

COMMENT ON COLUMN f24_record.importo_credito IS
    'Totale crediti compensati in questo F24.';
COMMENT ON COLUMN f24_record.codice_tributo_credito IS
    'Codice tributo della riga a credito (da tenant_settings.codice_tributo_credito).';
COMMENT ON COLUMN f24_record.anno_credito IS
    'Anno di riferimento della riga a credito: anno della NDC più recente compensata.';
COMMENT ON COLUMN f24_record.saldo_netto IS
    'total_amount - importo_credito. Importo effettivamente da versare.';

-- tenant_settings: codice tributo dei crediti da NDC
ALTER TABLE tenant_settings
    ADD COLUMN IF NOT EXISTS codice_tributo_credito VARCHAR(10) NOT NULL DEFAULT '6782';

COMMENT ON COLUMN tenant_settings.codice_tributo_credito IS
    'Codice tributo per crediti da NDC nel modello F24. Default 6782 '
    '(recupero eccedenze locazioni brevi).';

COMMIT;
