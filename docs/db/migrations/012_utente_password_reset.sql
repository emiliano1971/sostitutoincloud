-- 012_utente_password_reset.sql
-- Recupero password via email + cambio password per utente autenticato.
-- reset_token: 64 caratteri esadecimali generati dal PasswordResetService.
-- must_change_password: predisposizione per forzare il cambio al primo accesso
--   (nessun flusso lo imposta a true al momento; updatePassword lo riporta a false).

ALTER TABLE utente
    ADD COLUMN IF NOT EXISTS reset_token VARCHAR(64),
    ADD COLUMN IF NOT EXISTS reset_token_expires_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT false;

-- Indice parziale: i token sono pochi e transitori, indicizzare solo le righe valorizzate.
CREATE INDEX IF NOT EXISTS idx_utente_reset_token
    ON utente(reset_token)
    WHERE reset_token IS NOT NULL;
