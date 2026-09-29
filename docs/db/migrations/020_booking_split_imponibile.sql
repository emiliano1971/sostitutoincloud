-- ============================================
-- Migration 020: booking_split_economico.imponibile
-- Le regole contratto esprimono importi NETTI: il sistema aggiunge l'IVA
-- (22% in RF01, 0 in RF19) per ottenere il lordo che va in fattura PM.
-- Le righe esistenti restano con imponibile NULL (nessun backfill):
-- il frontend lo ricava da importo / (1 + aliquota_iva/100) finché non si fa Ricalcola.
-- ============================================

ALTER TABLE booking_split_economico
    ADD COLUMN IF NOT EXISTS imponibile DECIMAL(10,2) DEFAULT NULL;

COMMENT ON COLUMN booking_split_economico.imponibile IS
    'Importo netto senza IVA (valore dalla '
    'regola contratto o inserito dal PM). '
    'importo = imponibile × (1 + aliquota_iva/100). '
    'NULL per righe create prima della '
    'migration 020.';
