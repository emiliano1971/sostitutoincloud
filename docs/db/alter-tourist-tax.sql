-- ============================================================
-- ALTER TABLE regola_tassa_soggiorno
-- Aggiunge colonne mancanti rispetto al mock
-- ============================================================

ALTER TABLE regola_tassa_soggiorno
    ADD COLUMN IF NOT EXISTS region           VARCHAR(100),
    ADD COLUMN IF NOT EXISTS max_amount_per_person DECIMAL(5,2),
    ADD COLUMN IF NOT EXISTS exemptions       TEXT,
    ADD COLUMN IF NOT EXISTS notes            TEXT,
    ADD COLUMN IF NOT EXISTS fk_tenant_id     INTEGER
        REFERENCES tenant(id) ON DELETE CASCADE;

-- Rinomina colonne esistenti per allinearle al mock
-- (solo se vuoi uniformare i nomi)
-- ALTER TABLE regola_tassa_soggiorno
--   RENAME COLUMN comune TO municipality;
-- Lasciamo i nomi italiani per coerenza col resto del progetto

-- ============================================================
-- NUOVE TABELLE FIGLIE
-- ============================================================

CREATE TABLE IF NOT EXISTS tassa_fascia_eta (
                                                id              SERIAL PRIMARY KEY,
                                                fk_regola_id    INTEGER NOT NULL
                                                    REFERENCES regola_tassa_soggiorno(id)
                                                        ON DELETE CASCADE,
                                                label           VARCHAR(50) NOT NULL,
                                                min_age         SMALLINT NOT NULL,
                                                max_age         SMALLINT NOT NULL,
                                                reduction_pct   SMALLINT NOT NULL DEFAULT 0,
                                                created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS tassa_stagione (
                                              id              SERIAL PRIMARY KEY,
                                              fk_regola_id    INTEGER NOT NULL
                                                  REFERENCES regola_tassa_soggiorno(id)
                                                      ON DELETE CASCADE,
                                              label           VARCHAR(50) NOT NULL,
                                              start_month     SMALLINT NOT NULL,
                                              start_day       SMALLINT NOT NULL,
                                              end_month       SMALLINT NOT NULL,
                                              end_day         SMALLINT NOT NULL,
                                              reduction_pct   SMALLINT NOT NULL DEFAULT 0,
                                              created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS tassa_zona (
                                          id              SERIAL PRIMARY KEY,
                                          fk_regola_id    INTEGER NOT NULL
                                              REFERENCES regola_tassa_soggiorno(id)
                                                  ON DELETE CASCADE,
                                          label           VARCHAR(100) NOT NULL,
                                          reduction_pct   SMALLINT NOT NULL DEFAULT 0,
                                          created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indici sulle tabelle figlie
CREATE INDEX IF NOT EXISTS idx_fascia_eta_regola
    ON tassa_fascia_eta(fk_regola_id);
CREATE INDEX IF NOT EXISTS idx_stagione_regola
    ON tassa_stagione(fk_regola_id);
CREATE INDEX IF NOT EXISTS idx_zona_regola
    ON tassa_zona(fk_regola_id);