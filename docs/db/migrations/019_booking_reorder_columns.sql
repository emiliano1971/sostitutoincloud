-- ============================================
-- Migration 019: booking — riordino colonne
-- Gli importi dello split economico (ora storici: il dettaglio vive in
-- booking_split_economico, migration 018) vanno in fondo alla tabella,
-- dopo tutti i campi operativi.
-- ============================================
--
-- PostgreSQL non riordina le colonne con ALTER TABLE: la tabella va ricreata.
-- Nessun codice dipende dall'ordine fisico (tutte le query nominano le colonne),
-- quindi la migration è solo strutturale: dati, id e vincoli restano identici.
--
-- Oggetti che dipendono da booking e vanno ricreati (verificati su pg_constraint,
-- pg_indexes, pg_trigger, pg_depend):
--   FK entranti : fiscal_document, settlement_booking, withholding_ledger (RESTRICT),
--                 booking_split_economico (CASCADE)
--   vista       : v_ricavi_mensili
--   sequenza    : booking_id_seq (OWNED BY booking.id — va staccata prima del DROP,
--                 altrimenti DROP TABLE booking_old la cancellerebbe)
--   indici      : 9 idx_booking_* + pkey + uq_external_booking
--   trigger     : trg_booking_updated_at
--   commenti    : tabella + booking.total_costi_pm
--
-- Esecuzione (tutto in una transazione, già dentro il file):
--     psql -h localhost -U sostitutoincloud -d sostitutoincloud \
--          -v ON_ERROR_STOP=1 -f docs/db/migrations/019_booking_reorder_columns.sql
--   oppure da DBeaver con "Esegui script" (Alt+X), non la singola istruzione.
-- Prova a vuoto: sostituire il COMMIT finale con ROLLBACK.
--
-- Niente meta-comandi psql (\set ...): DBeaver li manda al server come SQL e fallisce.
-- Non servono: con BEGIN/COMMIT espliciti un errore lascia la transazione abortita e il
-- COMMIT finale diventa un ROLLBACK, sia in psql sia in DBeaver.

BEGIN;

-- 1. Rinomina: la tabella vecchia resta come sorgente dei dati.
--    Gli oggetti collegati (FK entranti, vista, indici) la seguono nel rename.
ALTER TABLE booking RENAME TO booking_old;

-- 2. Nuova tabella con l'ordine desiderato: campi operativi, poi importi.
--    Vincoli e indici con nome si creano dopo il DROP di booking_old (punto 5),
--    perché booking_old ne conserva i nomi (booking_pkey, uq_external_booking, idx_booking_*).
CREATE TABLE booking (
    id                              INTEGER                 NOT NULL DEFAULT nextval('booking_id_seq'::regclass),
    fk_tenant_id                    INTEGER                 NOT NULL,
    fk_property_id                  INTEGER                 NOT NULL,
    fk_owner_id                     INTEGER,
    fk_canale_ota_id                INTEGER,
    fk_regime_fiscale_id            INTEGER,
    external_booking_id             VARCHAR(100),
    guest_name                      VARCHAR(150)            NOT NULL,
    guest_tax_code                  VARCHAR(20),
    guest_birth_date                DATE,
    guest_sesso                     CHAR(1),
    guest_birth_place               VARCHAR(100),
    guest_birth_belfiore            CHAR(4),
    guest_doc_type                  VARCHAR(30),
    guest_doc_number                VARCHAR(30),
    guest_country                   VARCHAR(50),
    guest_address                   VARCHAR(200),
    guest_phone                     VARCHAR(30),
    checkin_date                    DATE                    NOT NULL,
    checkout_date                   DATE                    NOT NULL,
    nights                          SMALLINT                NOT NULL,
    guests                          SMALLINT                NOT NULL,
    fk_stato_prenotazione_id        INTEGER                 NOT NULL DEFAULT 1,
    payment_status                  payment_status          NOT NULL DEFAULT 'pending',
    settlement_status               settlement_status       NOT NULL DEFAULT 'pending',
    -- importi dello split economico (storici)
    tourist_tax_amount              DECIMAL(10,2)           NOT NULL DEFAULT 0,
    tourist_tax_included_in_gross   BOOLEAN                 NOT NULL DEFAULT FALSE,
    tourist_tax_collection          tourist_tax_collection  NOT NULL DEFAULT 'contanti',
    gross_amount                    DECIMAL(10,2)           NOT NULL,
    ota_commission_amount           DECIMAL(10,2)           NOT NULL DEFAULT 0,
    cleaning_amount                 DECIMAL(10,2)           NOT NULL DEFAULT 0,
    pm_fee_amount                   DECIMAL(10,2)           NOT NULL DEFAULT 0,
    owner_net_amount                DECIMAL(10,2)           NOT NULL,
    withholding_amount              DECIMAL(10,2)           NOT NULL DEFAULT 0,
    aliquota_ritenuta               DECIMAL(5,2)            NOT NULL DEFAULT 21.00,
    total_costi_pm                  DECIMAL(10,2)           DEFAULT 0,
    created_at                      TIMESTAMP               NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMP               NOT NULL DEFAULT NOW()
);

-- 2b. Stesso proprietario della tabella originale. Se lo script gira con un altro ruolo
--     (es. postgres da DBeaver) la nuova booking nascerebbe sua: l'ALTER SEQUENCE ... OWNED BY
--     qui sotto fallirebbe ("la sequenza deve avere lo stesso proprietario della tabella a cui
--     è collegata") e l'applicazione perderebbe la proprietà della tabella.
DO $$
BEGIN
    EXECUTE format('ALTER TABLE booking OWNER TO %I',
        (SELECT pg_get_userbyid(relowner) FROM pg_class WHERE oid = 'booking_old'::regclass));
END $$;

-- 2c. Trasferisci la sequenza alla nuova tabella: così il DROP TABLE booking_old
--     (punto 5) non la cancella insieme alla colonna id vecchia.
ALTER SEQUENCE booking_id_seq OWNED BY booking.id;

-- 3. Copia dei dati, id compresi. Nessun trigger su booking: updated_at resta quello originale.
INSERT INTO booking (
    id, fk_tenant_id, fk_property_id, fk_owner_id, fk_canale_ota_id, fk_regime_fiscale_id,
    external_booking_id, guest_name, guest_tax_code,
    guest_birth_date, guest_sesso, guest_birth_place, guest_birth_belfiore,
    guest_doc_type, guest_doc_number, guest_country, guest_address, guest_phone,
    checkin_date, checkout_date, nights, guests,
    fk_stato_prenotazione_id, payment_status, settlement_status, created_at, updated_at,
    gross_amount, ota_commission_amount, cleaning_amount, pm_fee_amount, owner_net_amount,
    withholding_amount, aliquota_ritenuta, tourist_tax_amount, tourist_tax_included_in_gross,
    tourist_tax_collection, total_costi_pm)
SELECT
    id, fk_tenant_id, fk_property_id, fk_owner_id, fk_canale_ota_id, fk_regime_fiscale_id,
    external_booking_id, guest_name, guest_tax_code,
    guest_birth_date, guest_sesso, guest_birth_place, guest_birth_belfiore,
    guest_doc_type, guest_doc_number, guest_country, guest_address, guest_phone,
    checkin_date, checkout_date, nights, guests,
    fk_stato_prenotazione_id, payment_status, settlement_status, created_at, updated_at,
    gross_amount, ota_commission_amount, cleaning_amount, pm_fee_amount, owner_net_amount,
    withholding_amount, aliquota_ritenuta, tourist_tax_amount, tourist_tax_included_in_gross,
    tourist_tax_collection, total_costi_pm
FROM booking_old;

-- 3b. Controllo: stessi record della tabella vecchia, altrimenti si annulla tutto.
DO $$
DECLARE
    n_old INTEGER; n_new INTEGER;
    s_old NUMERIC; s_new NUMERIC;
    m_old INTEGER; m_new INTEGER;
BEGIN
    SELECT count(*), coalesce(sum(gross_amount), 0), coalesce(max(id), 0) INTO n_old, s_old, m_old FROM booking_old;
    SELECT count(*), coalesce(sum(gross_amount), 0), coalesce(max(id), 0) INTO n_new, s_new, m_new FROM booking;
    IF n_old <> n_new OR s_old <> s_new OR m_old <> m_new THEN
        RAISE EXCEPTION 'Copia booking incoerente: righe %/%, lordo %/%, max id %/%',
            n_old, n_new, s_old, s_new, m_old, m_new;
    END IF;
    RAISE NOTICE 'Copia booking OK: % righe, lordo totale %, max id %', n_new, s_new, m_new;
END $$;

-- 4. Stacca da booking_old ciò che la bloccherebbe o sparirebbe con il DROP.
DROP VIEW v_ricavi_mensili;
ALTER TABLE fiscal_document         DROP CONSTRAINT fiscal_document_fk_booking_id_fkey;
ALTER TABLE settlement_booking      DROP CONSTRAINT settlement_booking_fk_booking_id_fkey;
ALTER TABLE withholding_ledger      DROP CONSTRAINT withholding_ledger_fk_booking_id_fkey;
ALTER TABLE booking_split_economico DROP CONSTRAINT booking_split_economico_fk_booking_id_fkey;

-- 5. Via la tabella vecchia (con i suoi indici e vincoli).
DROP TABLE booking_old;

-- 5b. La sequenza è sopravvissuta al DROP ed è ancora al valore di prima.
SELECT last_value FROM booking_id_seq;

-- 6. Vincoli della tabella (stessi nomi e definizioni di prima).
ALTER TABLE booking ADD CONSTRAINT booking_pkey PRIMARY KEY (id);
ALTER TABLE booking ADD CONSTRAINT booking_fk_tenant_id_fkey
    FOREIGN KEY (fk_tenant_id) REFERENCES tenant(id) ON DELETE RESTRICT;
ALTER TABLE booking ADD CONSTRAINT booking_fk_property_id_fkey
    FOREIGN KEY (fk_property_id) REFERENCES property(id) ON DELETE RESTRICT;
ALTER TABLE booking ADD CONSTRAINT booking_fk_owner_id_fkey
    FOREIGN KEY (fk_owner_id) REFERENCES owner_profile(id) ON DELETE RESTRICT;
ALTER TABLE booking ADD CONSTRAINT booking_fk_canale_ota_id_fkey
    FOREIGN KEY (fk_canale_ota_id) REFERENCES canale_ota(id) ON DELETE SET NULL;
ALTER TABLE booking ADD CONSTRAINT booking_fk_regime_fiscale_id_fkey
    FOREIGN KEY (fk_regime_fiscale_id) REFERENCES regime_fiscale(id) ON DELETE SET NULL;
ALTER TABLE booking ADD CONSTRAINT booking_fk_stato_prenotazione_id_fkey
    FOREIGN KEY (fk_stato_prenotazione_id) REFERENCES stato_prenotazione(id) ON DELETE RESTRICT;
ALTER TABLE booking ADD CONSTRAINT chk_checkout_after_checkin CHECK (checkout_date > checkin_date);
ALTER TABLE booking ADD CONSTRAINT chk_nights_positive        CHECK (nights > 0);
ALTER TABLE booking ADD CONSTRAINT chk_guests_positive        CHECK (guests > 0);
ALTER TABLE booking ADD CONSTRAINT uq_external_booking
    UNIQUE (fk_tenant_id, fk_canale_ota_id, external_booking_id);

-- 7. Indici
CREATE INDEX idx_booking_fk_tenant_id             ON booking(fk_tenant_id);
CREATE INDEX idx_booking_fk_property_id           ON booking(fk_property_id);
CREATE INDEX idx_booking_fk_owner_id              ON booking(fk_tenant_id, fk_owner_id);
CREATE INDEX idx_booking_checkout_date            ON booking(fk_tenant_id, checkout_date DESC);
CREATE INDEX idx_booking_fk_stato_prenotazione_id ON booking(fk_tenant_id, fk_stato_prenotazione_id);
CREATE INDEX idx_booking_settlement_status        ON booking(fk_tenant_id, settlement_status);
CREATE INDEX idx_booking_guest_tax_code           ON booking(guest_tax_code);
CREATE INDEX idx_booking_checkin_checkout         ON booking(fk_property_id, checkin_date, checkout_date);
CREATE INDEX idx_booking_fk_regime_fiscale_id     ON booking(fk_regime_fiscale_id);

-- 8. FK entranti (stessi nomi e regole ON DELETE di prima)
ALTER TABLE fiscal_document ADD CONSTRAINT fiscal_document_fk_booking_id_fkey
    FOREIGN KEY (fk_booking_id) REFERENCES booking(id) ON DELETE RESTRICT;
ALTER TABLE settlement_booking ADD CONSTRAINT settlement_booking_fk_booking_id_fkey
    FOREIGN KEY (fk_booking_id) REFERENCES booking(id) ON DELETE RESTRICT;
ALTER TABLE withholding_ledger ADD CONSTRAINT withholding_ledger_fk_booking_id_fkey
    FOREIGN KEY (fk_booking_id) REFERENCES booking(id) ON DELETE RESTRICT;
ALTER TABLE booking_split_economico ADD CONSTRAINT booking_split_economico_fk_booking_id_fkey
    FOREIGN KEY (fk_booking_id) REFERENCES booking(id) ON DELETE CASCADE;

-- 9. Trigger updated_at
CREATE TRIGGER trg_booking_updated_at
    BEFORE UPDATE ON booking
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 10. Vista ricavi mensili (definizione invariata)
CREATE VIEW v_ricavi_mensili AS
SELECT
    b.fk_tenant_id,
    DATE_TRUNC('month', b.checkout_date)  AS mese,
    SUM(b.pm_fee_amount)                  AS ricavi_pm,
    SUM(b.owner_net_amount)               AS ricavi_ow,
    SUM(b.ota_commission_amount)          AS commissioni,
    SUM(b.withholding_amount)             AS ritenute
FROM booking b
JOIN stato_prenotazione sp ON sp.id = b.fk_stato_prenotazione_id
WHERE sp.codice NOT IN ('cancelled')
GROUP BY b.fk_tenant_id, DATE_TRUNC('month', b.checkout_date);

-- 10b. Anche la vista allo stesso proprietario della tabella (vedi 2b).
DO $$
BEGIN
    EXECUTE format('ALTER VIEW v_ricavi_mensili OWNER TO %I',
        (SELECT pg_get_userbyid(relowner) FROM pg_class WHERE oid = 'booking'::regclass));
END $$;

-- 11. Commenti
COMMENT ON TABLE booking IS
    'Prenotazioni importate dai canali OTA o inserite manualmente. '
    'Contiene tutti i dati finanziari (lordo, commissioni, netto, ritenute, tassa soggiorno). '
    'fk_stato_prenotazione_id referenzia la tabella lookup; '
    'lo stato documento è derivato a runtime dai fiscal_document associati, non persistito qui; '
    'payment_status e settlement_status restano enum.';
COMMENT ON COLUMN booking.total_costi_pm IS
    'Somma delle voci booking_split_economico '
    'con include_in_fattura_pm=true. '
    'Aggiornato dal service dopo ogni '
    'modifica alle righe split.';
COMMENT ON VIEW v_ricavi_mensili IS
    'Aggregazione mensile dei ricavi per tenant. '
    'Sostituisce il mockRevenueData del frontend con dati reali da booking.';

-- 12. Verifica finale (dentro la transazione)
SELECT column_name, ordinal_position
FROM information_schema.columns
WHERE table_name = 'booking'
ORDER BY ordinal_position;

COMMIT;
