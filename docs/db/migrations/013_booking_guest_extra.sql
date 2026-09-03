-- 013_booking_guest_extra.sql
-- Indirizzo e telefono ospite sul booking: nuovi campi mappabili dal file ospiti
-- in fase di import e modificabili dal dialog anagrafica ospite.
ALTER TABLE booking
    ADD COLUMN IF NOT EXISTS guest_address VARCHAR(200),
    ADD COLUMN IF NOT EXISTS guest_phone   VARCHAR(30);
