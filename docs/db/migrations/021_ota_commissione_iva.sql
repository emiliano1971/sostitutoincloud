-- ============================================
-- Migration 021: commissione OTA lorda/netta per canale
-- + valore originale della commissione dal file di import
-- ============================================

-- Campo sul canale OTA
ALTER TABLE canale_ota
    ADD COLUMN IF NOT EXISTS commissione_ivata BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN canale_ota.commissione_ivata IS
    'true = commissione nel file di import '
    'è lorda (IVA inclusa, es. Booking.com). '
    'false = commissione nel file è netta, '
    'il sistema aggiunge IVA 22% '
    '(es. Airbnb). '
    'Default true per retrocompatibilità.';

-- Campo su booking_split_economico
ALTER TABLE booking_split_economico
    ADD COLUMN IF NOT EXISTS importo_originale_file DECIMAL(10,2) DEFAULT NULL;

COMMENT ON COLUMN booking_split_economico.importo_originale_file IS
    'Valore grezzo della commissione OTA '
    'dal file di import, prima di qualsiasi '
    'trasformazione IVA. NULL per righe non '
    'da import o per voci non OTA. '
    'Utile per riconciliazione e debug.';

-- Airbnb: commissione nel file netta (il sistema aggiunge l'IVA)
UPDATE canale_ota
   SET commissione_ivata = FALSE
 WHERE nome ILIKE '%airbnb%';
