-- 016_sdi_progressivo_globale.sql
-- Il ProgressivoInvio SDI diventa un contatore GLOBALE per applicazione: non più
-- per tenant e anno. Restano solo applicazione, ultimo_valore_alfa e valore_massimo.
--
-- ATTENZIONE prima di eseguirla in test/prod: il TRUNCATE azzera i contatori
-- esistenti e la numerazione riparte da 'EAAAA', quindi verificare che i
-- progressivi già presenti in fiscal_document.sdi_progressivo non vengano
-- riemessi — per la stessa partita IVA lo stesso ProgressivoInvio produce lo
-- stesso nome file, che l'AdE scarta come duplicato. Se necessario seminare
-- ultimo_valore_alfa al massimo già emesso invece di 'DZZZZ'.

-- Svuota la tabella e ridefiniscila
TRUNCATE TABLE sdi_progressivo;

ALTER TABLE sdi_progressivo
    DROP CONSTRAINT IF EXISTS uq_sdi_progressivo;

ALTER TABLE sdi_progressivo
    DROP COLUMN IF EXISTS fk_tenant_id;
ALTER TABLE sdi_progressivo
    DROP COLUMN IF EXISTS anno;
ALTER TABLE sdi_progressivo
    DROP COLUMN IF EXISTS ultimo_valore;

-- Aggiunge vincolo univoco per applicazione
ALTER TABLE sdi_progressivo
    ADD CONSTRAINT uq_sdi_applicazione
        UNIQUE (applicazione);

-- Inserisce il record iniziale per SDI.
-- 'DZZZZ' = nulla ancora emesso: il primo incremento produce 'EAAAA'.
INSERT INTO sdi_progressivo
    (applicazione, ultimo_valore_alfa, valore_massimo)
VALUES
    ('SDI', 'DZZZZ', 'HZZZZ');
