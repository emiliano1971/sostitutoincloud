-- 006_comune_italiano.sql
-- Tabella comuni italiani (fonte ISTAT) per calcolo automatico del codice fiscale.
-- Il seed è in docs/db/seed-comuni.sql (7.894 comuni).

CREATE TABLE IF NOT EXISTS comune_italiano (
    id               SERIAL PRIMARY KEY,
    nome             VARCHAR(150) NOT NULL,
    sigla_provincia  CHAR(2)      NOT NULL,
    regione          VARCHAR(100) NOT NULL,
    codice_belfiore  CHAR(4)      NOT NULL UNIQUE,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_comune_nome
    ON comune_italiano (LOWER(nome), sigla_provincia);

CREATE INDEX IF NOT EXISTS idx_comune_belfiore
    ON comune_italiano (codice_belfiore);
