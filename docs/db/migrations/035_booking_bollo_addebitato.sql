-- ============================================
-- Migration 035: bollo addebitato al proprietario, fotografato sul booking
-- ============================================
--
-- Riepilogo sul booking della voce 'bollo' dello split economico (migration 034), così la
-- liquidazione distingue il bollo addebitato (già dedotto dal canone tramite la fattura PM)
-- da quello solo informativo, senza rileggere lo split.
-- Entrambe le colonne sono riscritte da BookingService.popolaSplitEconomico() a ogni calcolo.
--
-- Esecuzione:
--     psql -h localhost -U sostitutoincloud -d sostitutoincloud \
--          -f docs/db/migrations/035_booking_bollo_addebitato.sql

ALTER TABLE booking
    ADD COLUMN IF NOT EXISTS bollo_addebitato BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE booking
    ADD COLUMN IF NOT EXISTS bollo_importo NUMERIC(10,2) NOT NULL DEFAULT 0;

COMMENT ON COLUMN booking.bollo_addebitato IS
    'True se il bollo è stato addebitato al proprietario come voce dello split economico '
    '(fuori campo art. 15 in fattura PM). Aggiornato da popolaSplitEconomico.';

COMMENT ON COLUMN booking.bollo_importo IS
    'Importo del bollo addebitato in euro. 0 se non addebitato. Aggiornato da popolaSplitEconomico.';
