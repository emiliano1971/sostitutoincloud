-- 007_booking_guest_anagrafica.sql
-- Campi anagrafici ospite sul booking, per calcolo CF automatico e dati fatturazione.
ALTER TABLE booking
    ADD COLUMN IF NOT EXISTS guest_birth_date     DATE,
    ADD COLUMN IF NOT EXISTS guest_sesso          CHAR(1),
    ADD COLUMN IF NOT EXISTS guest_birth_place    VARCHAR(100),
    ADD COLUMN IF NOT EXISTS guest_birth_belfiore CHAR(4),
    ADD COLUMN IF NOT EXISTS guest_doc_type       VARCHAR(30),
    ADD COLUMN IF NOT EXISTS guest_doc_number     VARCHAR(30),
    ADD COLUMN IF NOT EXISTS guest_country        VARCHAR(50);
