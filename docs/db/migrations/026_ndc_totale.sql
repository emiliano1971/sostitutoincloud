-- ============================================
-- Migration 026: NDC solo totale — copia booking e storno ritenuta
-- ============================================
--
-- booking.fk_booking_origine_id: prenotazione copiata da un booking 'stornata'
-- (BookingService.copiaBooking), per il collegamento bidirezionale nel dettaglio.
--
-- withholding_ledger.fk_ndc_id: nota di credito che ha stornato la ritenuta.
-- Nuovi valori di withholding_ledger.stato (VARCHAR, nessun enum da modificare):
--   'stornata'        ritenuta annullata dalla NDC prima del pagamento dell'F24
--   'credito_imposta' riga di credito (ritenuta_amount negativo) per una ritenuta
--                     già versata con F24 'paid'/'sent'
-- Le righe con fk_ndc_id valorizzato sono escluse da CU e liquidazioni.
--
-- Esecuzione:
--     psql -h localhost -U sostitutoincloud -d sostitutoincloud \
--          -v ON_ERROR_STOP=1 -f docs/db/migrations/026_ndc_totale.sql

BEGIN;

ALTER TABLE booking
    ADD COLUMN IF NOT EXISTS fk_booking_origine_id INTEGER
        REFERENCES booking(id) ON DELETE SET NULL;

COMMENT ON COLUMN booking.fk_booking_origine_id IS
    'Booking di origine da cui questa prenotazione è stata copiata. '
    'Valorizzato quando lo stato del booking originale era stornata.';

ALTER TABLE withholding_ledger
    ADD COLUMN IF NOT EXISTS fk_ndc_id INTEGER
        REFERENCES fiscal_document(id) ON DELETE SET NULL;

COMMENT ON COLUMN withholding_ledger.fk_ndc_id IS
    'Nota di credito che ha stornato la ritenuta (stato stornata) o che ha generato '
    'la riga di credito d imposta (stato credito_imposta). NULL = ritenuta ordinaria.';

COMMENT ON COLUMN withholding_ledger.stato IS
    'da_versare / versata (inclusa in un F24) / stornata (annullata da NDC) / '
    'credito_imposta (riga di credito da NDC su ritenuta già versata)';

COMMIT;
