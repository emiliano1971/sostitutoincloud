-- 015_sdi_progressivo_alfanumerico.sql
-- Il ProgressivoInvio SDI passa da numerico zero-padded (00001) ad alfanumerico
-- base-26 di 5 caratteri A-Z (EAAAA), con un tetto massimo configurabile per riga.
--
-- ultimo_valore_alfa significa "ultimo progressivo EMESSO", quindi il seme è il
-- valore immediatamente precedente al primo che si vuole emettere: da 'DZZZZ' il
-- primo incremento produce 'EAAAA'. Così la logica del DAO ha un solo ramo
-- (incrementa sempre) e anche le righe già esistenti partono da 'EAAAA'.
--
-- Le tre colonne sono NOT NULL: su applicazione è necessario perché una colonna
-- nullable renderebbe inefficace il vincolo unique qui sotto (in PostgreSQL i NULL
-- non collidono mai), e sulle due CHAR(5) perché un NULL passerebbe il CHECK.
ALTER TABLE sdi_progressivo
    ADD COLUMN IF NOT EXISTS
        ultimo_valore_alfa CHAR(5) NOT NULL DEFAULT 'DZZZZ'
        CHECK (ultimo_valore_alfa ~ '^[A-Z]{5}$'),
    ADD COLUMN IF NOT EXISTS
        valore_massimo CHAR(5) NOT NULL DEFAULT 'HZZZZ'
        CHECK (valore_massimo ~ '^[A-Z]{5}$'),
    ADD COLUMN IF NOT EXISTS
        applicazione VARCHAR(50) NOT NULL DEFAULT 'SDI';

-- ultimo_valore resta per compatibilità (storico dei progressivi numerici già
-- emessi) ma non viene più usato per generare il progressivo.

-- Il vincolo unique include l'applicazione, per riusare la tabella come
-- contatore anche per applicazioni diverse da SDI.
ALTER TABLE sdi_progressivo
    DROP CONSTRAINT IF EXISTS uq_sdi_progressivo;

ALTER TABLE sdi_progressivo
    ADD CONSTRAINT uq_sdi_progressivo
        UNIQUE (fk_tenant_id, anno, applicazione);
