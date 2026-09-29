-- ============================================
-- Migration 022: booking_split_economico — riordino colonne
-- importo_originale_file (021) e imponibile (020), aggiunte in fondo con ALTER TABLE,
-- vanno prima di importo: valore dal file → netto → lordo, nell'ordine in cui si leggono.
-- ============================================
--
-- PostgreSQL non riordina le colonne con ALTER TABLE: la tabella va ricreata.
-- Nessun codice dipende dall'ordine fisico (tutte le query nominano le colonne),
-- quindi la migration è solo strutturale: dati, id e vincoli restano identici.
--
-- Oggetti che dipendono da booking_split_economico (verificati su
-- information_schema.referential_constraints, pg_indexes, pg_trigger, pg_depend):
--   FK entranti : nessuna
--   viste       : nessuna
--   FK uscenti  : fk_booking_id → booking (CASCADE), fk_tenant_id → tenant (RESTRICT),
--                 fk_property_contract_rule_id → property_contract_rule (SET NULL)
--   sequenza    : booking_split_economico_id_seq (OWNED BY id — va staccata prima del DROP,
--                 altrimenti DROP TABLE booking_split_economico_old la cancellerebbe)
--   indici      : pkey, idx_bse_booking_id (parziale), idx_bse_tenant_id
--   trigger     : trg_bse_updated_at
--   commenti    : tabella + tipo_voce, source, imponibile, importo_originale_file
--
-- Esecuzione (tutto in una transazione, già dentro il file):
--     psql -h localhost -U sostitutoincloud -d sostitutoincloud \
--          -v ON_ERROR_STOP=1 -f docs/db/migrations/022_booking_split_reorder_columns.sql
--   oppure da DBeaver con "Esegui script" (Alt+X), non la singola istruzione.
-- Prova a vuoto: sostituire il COMMIT finale con ROLLBACK.
--
-- Niente meta-comandi psql (\set ...): DBeaver li manda al server come SQL e fallisce.

BEGIN;

-- 1. Rinomina: la tabella vecchia resta come sorgente dei dati (indici e vincoli la seguono).
ALTER TABLE booking_split_economico RENAME TO booking_split_economico_old;

-- 2. Nuova tabella con l'ordine desiderato. Vincoli e indici con nome si creano dopo il
--    DROP della vecchia (punto 5), perché la _old ne conserva i nomi.
CREATE TABLE booking_split_economico (
    id                           INTEGER       NOT NULL DEFAULT nextval('booking_split_economico_id_seq'::regclass),
    fk_booking_id                INTEGER       NOT NULL,
    fk_tenant_id                 INTEGER       NOT NULL,
    fk_property_contract_rule_id INTEGER,
    tipo_voce                    VARCHAR(50)   NOT NULL,
    descrizione                  VARCHAR(255)  NOT NULL,
    importo_originale_file       DECIMAL(10,2) DEFAULT NULL,
    imponibile                   DECIMAL(10,2) DEFAULT NULL,
    importo                      DECIMAL(10,2) NOT NULL,
    aliquota_iva                 DECIMAL(5,2)  NOT NULL DEFAULT 0,
    include_in_fattura_pm        BOOLEAN       NOT NULL DEFAULT TRUE,
    ordinamento                  SMALLINT      NOT NULL DEFAULT 0,
    source                       VARCHAR(20)   NOT NULL DEFAULT 'calcolato',
    deleted_at                   TIMESTAMP     DEFAULT NULL,
    created_at                   TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at                   TIMESTAMP     NOT NULL DEFAULT NOW(),
    created_by                   INTEGER,
    updated_by                   INTEGER
);

-- 2b. Stesso proprietario della tabella originale (vedi migration 019): altrimenti,
--     eseguita con un altro ruolo, l'ALTER SEQUENCE ... OWNED BY qui sotto fallirebbe.
DO $$
BEGIN
    EXECUTE format('ALTER TABLE booking_split_economico OWNER TO %I',
        (SELECT pg_get_userbyid(relowner) FROM pg_class WHERE oid = 'booking_split_economico_old'::regclass));
END $$;

-- 2c. Trasferisci la sequenza alla nuova tabella: il DROP del punto 5 non la cancella.
ALTER SEQUENCE booking_split_economico_id_seq OWNED BY booking_split_economico.id;

-- 3. Copia dei dati, id compresi. Il trigger updated_at non esiste ancora sulla nuova
--    tabella (ed è BEFORE UPDATE): updated_at resta quello originale.
INSERT INTO booking_split_economico (
    id, fk_booking_id, fk_tenant_id, fk_property_contract_rule_id, tipo_voce, descrizione,
    importo_originale_file, imponibile, importo, aliquota_iva, include_in_fattura_pm,
    ordinamento, source, deleted_at, created_at, updated_at, created_by, updated_by)
SELECT
    id, fk_booking_id, fk_tenant_id, fk_property_contract_rule_id, tipo_voce, descrizione,
    importo_originale_file, imponibile, importo, aliquota_iva, include_in_fattura_pm,
    ordinamento, source, deleted_at, created_at, updated_at, created_by, updated_by
FROM booking_split_economico_old;

-- 3b. Controllo: stessi record della tabella vecchia, altrimenti si annulla tutto.
DO $$
DECLARE
    n_old INTEGER; n_new INTEGER;
    s_old NUMERIC; s_new NUMERIC;
    m_old INTEGER; m_new INTEGER;
BEGIN
    SELECT count(*), coalesce(sum(importo), 0), coalesce(max(id), 0) INTO n_old, s_old, m_old FROM booking_split_economico_old;
    SELECT count(*), coalesce(sum(importo), 0), coalesce(max(id), 0) INTO n_new, s_new, m_new FROM booking_split_economico;
    IF n_old <> n_new OR s_old <> s_new OR m_old <> m_new THEN
        RAISE EXCEPTION 'Copia booking_split_economico incoerente: righe %/%, importo %/%, max id %/%',
            n_old, n_new, s_old, s_new, m_old, m_new;
    END IF;
    RAISE NOTICE 'Copia booking_split_economico OK: % righe, importo totale %, max id %', n_new, s_new, m_new;
END $$;

-- 4. Nessuna FK entrante né vista da staccare (vedi intestazione).

-- 5. Via la tabella vecchia (con i suoi indici, vincoli e trigger).
DROP TABLE booking_split_economico_old;

-- 5b. La sequenza è sopravvissuta al DROP ed è ancora al valore di prima.
SELECT last_value FROM booking_split_economico_id_seq;

-- 6. Vincoli (stessi nomi e definizioni di prima)
ALTER TABLE booking_split_economico ADD CONSTRAINT booking_split_economico_pkey PRIMARY KEY (id);
ALTER TABLE booking_split_economico ADD CONSTRAINT booking_split_economico_fk_booking_id_fkey
    FOREIGN KEY (fk_booking_id) REFERENCES booking(id) ON DELETE CASCADE;
ALTER TABLE booking_split_economico ADD CONSTRAINT booking_split_economico_fk_tenant_id_fkey
    FOREIGN KEY (fk_tenant_id) REFERENCES tenant(id) ON DELETE RESTRICT;
ALTER TABLE booking_split_economico ADD CONSTRAINT booking_split_economico_fk_property_contract_rule_id_fkey
    FOREIGN KEY (fk_property_contract_rule_id) REFERENCES property_contract_rule(id) ON DELETE SET NULL;

-- 7. Indici
-- Indice principale per recupero righe per booking (esclude deleted)
CREATE INDEX idx_bse_booking_id
    ON booking_split_economico(fk_booking_id)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_bse_tenant_id
    ON booking_split_economico(fk_tenant_id);

-- 8. Trigger updated_at
CREATE TRIGGER trg_bse_updated_at
    BEFORE UPDATE ON booking_split_economico
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- 9. Commenti (stessi testi delle migration 018, 020, 021)
COMMENT ON TABLE booking_split_economico IS
    'Righe di costo dello split economico '
    'per ogni prenotazione. Le voci con '
    'include_in_fattura_pm=true entrano '
    'nella fattura PM. Le voci con '
    'deleted_at valorizzato sono eliminate '
    'logicamente. source indica come è '
    'nata la riga: calcolato=da regole '
    'contratto, manuale=inserita dal PM, '
    'import=dal file di importazione.';

COMMENT ON COLUMN booking_split_economico.tipo_voce IS
    'commissione_ota | pulizie | '
    'cambio_biancheria | commissione_pm | '
    'extra | tassa_soggiorno';

COMMENT ON COLUMN booking_split_economico.source IS
    'calcolato | manuale | import';

COMMENT ON COLUMN booking_split_economico.imponibile IS
    'Importo netto senza IVA (valore dalla '
    'regola contratto o inserito dal PM). '
    'importo = imponibile × (1 + aliquota_iva/100). '
    'NULL per righe create prima della '
    'migration 020.';

COMMENT ON COLUMN booking_split_economico.importo_originale_file IS
    'Valore grezzo della commissione OTA '
    'dal file di import, prima di qualsiasi '
    'trasformazione IVA. NULL per righe non '
    'da import o per voci non OTA. '
    'Utile per riconciliazione e debug.';

COMMIT;
