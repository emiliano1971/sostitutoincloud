-- ============================================
-- Migration 031: email ospite e testo predefinito per l'invio documenti
-- ============================================
--
-- Invio via email di fattura PM + ricevuta owner dal dettaglio prenotazione
-- (POST /api/bookings/{id}/invia-documenti):
--   - booking.guest_email: destinatari precompilati, importati dal file prenotazioni
--     (colonna "Email") e/o dal file ospiti, oppure inseriti dal dialog ospite;
--   - tenant_settings.email_testo_predefinito: corpo dell'email, modificabile
--     dalle impostazioni tenant (scheda "Configurazione") e prima di ogni invio.
--
-- Esecuzione:
--     psql -h localhost -U sostitutoincloud -d sostitutoincloud \
--          -f docs/db/migrations/031_email_documenti.sql

-- Email ospite (più indirizzi separati da ;)
ALTER TABLE booking
    ADD COLUMN IF NOT EXISTS guest_email VARCHAR(500);

COMMENT ON COLUMN booking.guest_email IS
    'Email ospite, più indirizzi separati da punto e virgola. '
    'Importata dal file prenotazioni o inserita manualmente.';

-- Testo predefinito email documenti
ALTER TABLE tenant_settings
    ADD COLUMN IF NOT EXISTS email_testo_predefinito TEXT;

COMMENT ON COLUMN tenant_settings.email_testo_predefinito IS
    'Testo predefinito per l email di invio documenti fiscali '
    '(fattura PM e ricevuta owner). Editabile prima dell invio.';
