-- ============================================
-- Migration 018: booking_split_economico
-- Righe di costo dello split economico
-- per ogni prenotazione
-- ============================================

CREATE TABLE booking_split_economico (
    id                           SERIAL        PRIMARY KEY,
    fk_booking_id                INTEGER       NOT NULL REFERENCES booking(id) ON DELETE CASCADE,
    fk_tenant_id                 INTEGER       NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    fk_property_contract_rule_id INTEGER       REFERENCES property_contract_rule(id) ON DELETE SET NULL,
    tipo_voce                    VARCHAR(50)   NOT NULL,
    -- 'commissione_ota' | 'pulizie' | 'cambio_biancheria' |
    -- 'commissione_pm' | 'extra' | 'tassa_soggiorno'
    descrizione                  VARCHAR(255)  NOT NULL,
    importo                      DECIMAL(10,2) NOT NULL,
    aliquota_iva                 DECIMAL(5,2)  NOT NULL DEFAULT 0,
    include_in_fattura_pm        BOOLEAN       NOT NULL DEFAULT TRUE,
    ordinamento                  SMALLINT      NOT NULL DEFAULT 0,
    source                       VARCHAR(20)   NOT NULL DEFAULT 'calcolato',
    -- 'calcolato' | 'manuale' | 'import'
    deleted_at                   TIMESTAMP     DEFAULT NULL,
    created_at                   TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at                   TIMESTAMP     NOT NULL DEFAULT NOW(),
    created_by                   INTEGER,
    updated_by                   INTEGER
);

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

-- Indice principale per recupero righe per booking (esclude deleted)
CREATE INDEX idx_bse_booking_id
    ON booking_split_economico(fk_booking_id)
    WHERE deleted_at IS NULL;

-- Indice per tenant
CREATE INDEX idx_bse_tenant_id
    ON booking_split_economico(fk_tenant_id);

-- Trigger updated_at (funzione comune dello schema: set_updated_at)
CREATE TRIGGER trg_bse_updated_at
    BEFORE UPDATE ON booking_split_economico
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- ============================================
-- Aggiungi total_costi_pm al booking
-- Somma delle voci split con
-- include_in_fattura_pm = true
-- Aggiornato a programma dal service,
-- non da trigger
-- ============================================

ALTER TABLE booking
    ADD COLUMN IF NOT EXISTS total_costi_pm DECIMAL(10,2) DEFAULT 0;

COMMENT ON COLUMN booking.total_costi_pm IS
    'Somma delle voci booking_split_economico '
    'con include_in_fattura_pm=true. '
    'Aggiornato dal service dopo ogni '
    'modifica alle righe split.';
