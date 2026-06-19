-- ============================================================
-- schema-target.sql
-- Schema PostgreSQL 18 — sostitutoincloud PMS fiscale
-- Generato da: docs/db/analisi-mock.md
-- ============================================================

-- ============================================================
-- TIPI ENUM
-- Solo per stati e ruoli con valori fissi e definitivi.
-- Valori suscettibili di crescita o con attributi aggiuntivi
-- sono gestiti tramite tabelle lookup (vedi sezione seguente).
-- ============================================================

CREATE TYPE user_role AS ENUM (
    'super_admin',
    'tenant_admin',
    'pm_user',
    'owner_user'
);

CREATE TYPE tenant_status AS ENUM (
    'draft',
    'active',
    'suspended',
    'closed'
);

CREATE TYPE owner_type AS ENUM (
    'persona_fisica',
    'piva',
    'societa'
);


CREATE TYPE payment_status AS ENUM (
    'pending',
    'received',
    'failed'
);

CREATE TYPE settlement_status AS ENUM (
    'pending',
    'calculated',
    'approved',
    'paid'
);

CREATE TYPE f24_status AS ENUM (
    'draft',
    'ready',
    'sent',
    'paid',
    'error'
);

CREATE TYPE cu_status AS ENUM (
    'draft',
    'generated',
    'sent',
    'delivered'
);

CREATE TYPE tourist_tax_collection AS ENUM (
    'contanti',
    'payment_link',
    'altro'
);


-- ============================================================
-- FUNZIONE updated_at
-- Applica il trigger su ogni tabella che necessita
-- di aggiornamento automatico del campo updated_at.
-- ============================================================

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;


-- ============================================================
-- TABELLE LOOKUP
-- Valori che possono crescere nel tempo o che hanno
-- attributi propri (commissioni, descrizioni, flag).
-- ============================================================

-- Canali OTA (Airbnb, Booking.com, Vrbo, ecc.)
CREATE TABLE canale_ota (
    id                      SERIAL          PRIMARY KEY,
    codice                  VARCHAR(30)     NOT NULL UNIQUE,   -- chiave applicativa es. 'airbnb'
    nome                    VARCHAR(100)    NOT NULL,
    commissione_default_pct DECIMAL(5,2)    NOT NULL DEFAULT 0,
    tassa_soggiorno_inclusa BOOLEAN         NOT NULL DEFAULT FALSE,
    attivo                  BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP       NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE canale_ota IS
    'Canali OTA supportati dalla piattaforma. '
    'Gestito come lookup per consentire aggiunta di nuovi canali senza modificare il codice.';

CREATE TRIGGER trg_canale_ota_updated_at
    BEFORE UPDATE ON canale_ota
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- Tipologie di immobile (LT, B&B, Affittacamere, ecc.)
CREATE TABLE tipo_immobile (
    id          SERIAL          PRIMARY KEY,
    codice      VARCHAR(10)     NOT NULL UNIQUE,
    descrizione VARCHAR(100)    NOT NULL,
    attivo      BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP       NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE tipo_immobile IS
    'Tipologie catastali/fiscali di immobile (LT = Locazione Turistica, B&B, ecc.). '
    'Lookup per supportare futuri tipi senza modifiche allo schema.';

CREATE TRIGGER trg_tipo_immobile_updated_at
    BEFORE UPDATE ON tipo_immobile
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- Tipologie di documento fiscale
CREATE TABLE tipo_documento (
    id              SERIAL          PRIMARY KEY,
    codice          VARCHAR(20)     NOT NULL UNIQUE,   -- es. 'fattura', 'ricevuta', 'nota_credito'
    descrizione     VARCHAR(100)    NOT NULL,
    richiede_iva    BOOLEAN         NOT NULL DEFAULT FALSE,
    trasmesso_sdi   BOOLEAN         NOT NULL DEFAULT FALSE,
    attivo          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE tipo_documento IS
    'Tipologie di documento fiscale emettibile (fattura elettronica, ricevuta, nota di credito). '
    'Lookup per consentire nuovi tipi (es. fattura PA) senza modificare gli enum.';

CREATE TRIGGER trg_tipo_documento_updated_at
    BEFORE UPDATE ON tipo_documento
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- Codici tributo F24
CREATE TABLE codice_tributo (
    id          SERIAL          PRIMARY KEY,
    codice      CHAR(4)         NOT NULL UNIQUE,   -- es. '1919'
    descrizione VARCHAR(250)    NOT NULL,
    attivo      BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP       NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE codice_tributo IS
    'Codici tributo per modello F24 (es. 1919 = ritenute locazioni brevi ex art. 4 DL 50/2017). '
    'Lookup per supportare futuri codici tributo senza modifiche allo schema.';

CREATE TRIGGER trg_codice_tributo_updated_at
    BEFORE UPDATE ON codice_tributo
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- Esiti SDI (Sistema di Interscambio)
CREATE TABLE sdi_esito (
    id          SERIAL          PRIMARY KEY,
    codice      CHAR(2)         NOT NULL UNIQUE,   -- RC, MC, NS, DT, AT
    descrizione VARCHAR(150)    NOT NULL,
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP       NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE sdi_esito IS
    'Codici esito restituiti dal SDI per fatture elettroniche. '
    'Lookup perché l Agenzia delle Entrate può introdurre nuovi codici.';

CREATE TRIGGER trg_sdi_esito_updated_at
    BEFORE UPDATE ON sdi_esito
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- Regole tassa di soggiorno per comune
CREATE TABLE regola_tassa_soggiorno (
    id                      SERIAL          PRIMARY KEY,
    comune                  VARCHAR(100)    NOT NULL,
    provincia               CHAR(2)         NOT NULL,
    importo_per_notte       DECIMAL(5,2)    NOT NULL,
    max_notti               SMALLINT        NOT NULL DEFAULT 7,
    eta_esenzione           SMALLINT,                   -- NULL = nessuna esenzione per età
                                                        -- valore N = ospiti sotto N anni sono esenti
    valida_dal              DATE            NOT NULL,
    valida_al               DATE,                       -- NULL = ancora in vigore
    attivo                  BOOLEAN         NOT NULL DEFAULT TRUE,
    region                  VARCHAR(100),
    max_amount_per_person   DECIMAL(5,2),
    exemptions              TEXT,
    notes                   TEXT,
    fk_tenant_id            INTEGER         REFERENCES tenant(id) ON DELETE CASCADE,
    created_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP       NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE regola_tassa_soggiorno IS
    'Regole comunali per il calcolo della tassa di soggiorno. '
    'eta_esenzione: ospiti con età strettamente inferiore a questo valore sono esenti (NULL = nessuna esenzione). '
    'Lookup perché le tariffe variano per delibera comunale e cambiano nel tempo.';

CREATE TRIGGER trg_regola_tassa_soggiorno_updated_at
    BEFORE UPDATE ON regola_tassa_soggiorno
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- Fasce di età della tassa di soggiorno (figlia di regola_tassa_soggiorno)
CREATE TABLE tassa_fascia_eta (
    id              SERIAL          PRIMARY KEY,
    fk_regola_id    INTEGER         NOT NULL REFERENCES regola_tassa_soggiorno(id) ON DELETE CASCADE,
    label           VARCHAR(50)     NOT NULL,
    min_age         SMALLINT        NOT NULL,
    max_age         SMALLINT        NOT NULL,
    reduction_pct   SMALLINT        NOT NULL DEFAULT 0,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Stagioni della tassa di soggiorno (figlia di regola_tassa_soggiorno)
CREATE TABLE tassa_stagione (
    id              SERIAL          PRIMARY KEY,
    fk_regola_id    INTEGER         NOT NULL REFERENCES regola_tassa_soggiorno(id) ON DELETE CASCADE,
    label           VARCHAR(50)     NOT NULL,
    start_month     SMALLINT        NOT NULL,
    start_day       SMALLINT        NOT NULL,
    end_month       SMALLINT        NOT NULL,
    end_day         SMALLINT        NOT NULL,
    reduction_pct   SMALLINT        NOT NULL DEFAULT 0,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Zone della tassa di soggiorno (figlia di regola_tassa_soggiorno)
CREATE TABLE tassa_zona (
    id              SERIAL          PRIMARY KEY,
    fk_regola_id    INTEGER         NOT NULL REFERENCES regola_tassa_soggiorno(id) ON DELETE CASCADE,
    label           VARCHAR(100)    NOT NULL,
    reduction_pct   SMALLINT        NOT NULL DEFAULT 0,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Indici sulle tabelle figlie
CREATE INDEX IF NOT EXISTS idx_fascia_eta_regola
    ON tassa_fascia_eta(fk_regola_id);
CREATE INDEX IF NOT EXISTS idx_stagione_regola
    ON tassa_stagione(fk_regola_id);
CREATE INDEX IF NOT EXISTS idx_zona_regola
    ON tassa_zona(fk_regola_id);


-- Scenari fiscali applicabili alle prenotazioni
CREATE TABLE scenario_fiscale (
    id          SERIAL          PRIMARY KEY,
    codice      VARCHAR(30)     NOT NULL UNIQUE,
    descrizione VARCHAR(200)    NOT NULL,
    attivo      BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP       NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE scenario_fiscale IS
    'Scenari fiscali che determinano come vengono emessi i documenti per una prenotazione '
    '(es. quale documento emette il PM, quale l owner, aliquote applicabili). '
    'Lookup per consentire nuovi scenari senza modifiche allo schema.';

CREATE TRIGGER trg_scenario_fiscale_updated_at
    BEFORE UPDATE ON scenario_fiscale
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- Stati del workflow prenotazione
CREATE TABLE stato_prenotazione (
    id          SERIAL          PRIMARY KEY,
    codice      VARCHAR(30)     NOT NULL UNIQUE,
    descrizione VARCHAR(150)    NOT NULL,
    finale      BOOLEAN         NOT NULL DEFAULT FALSE,   -- TRUE per stati terminali (settled, cancelled)
    attivo      BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP       NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE stato_prenotazione IS
    'Stati del workflow di una prenotazione (imported → enriched → ready → doc_issued → settled / cancelled). '
    'finale = TRUE indica uno stato terminale oltre il quale il workflow non prosegue. '
    'Lookup per consentire nuovi stati senza modificare il codice.';

CREATE TRIGGER trg_stato_prenotazione_updated_at
    BEFORE UPDATE ON stato_prenotazione
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- Stati del workflow documento fiscale
CREATE TABLE stato_documento (
    id          SERIAL          PRIMARY KEY,
    codice      VARCHAR(30)     NOT NULL UNIQUE,
    descrizione VARCHAR(150)    NOT NULL,
    is_error    BOOLEAN         NOT NULL DEFAULT FALSE,   -- TRUE per stati di errore
    finale      BOOLEAN         NOT NULL DEFAULT FALSE,   -- TRUE per stati terminali
    attivo      BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP       NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE stato_documento IS
    'Stati del ciclo di vita di un documento fiscale (draft → ready → sent_sdi → accepted / rejected). '
    'is_error = TRUE identifica stati anomali che richiedono intervento manuale. '
    'finale = TRUE indica uno stato terminale. '
    'Lookup per consentire nuovi stati (es. nuovi esiti SDI) senza modificare il codice.';

CREATE TRIGGER trg_stato_documento_updated_at
    BEFORE UPDATE ON stato_documento
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- Regimi fiscali applicabili ai proprietari
CREATE TABLE regime_fiscale (
    id          SERIAL          PRIMARY KEY,
    codice      VARCHAR(30)     NOT NULL UNIQUE,
    descrizione VARCHAR(150)    NOT NULL,
    attivo      BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP       NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE regime_fiscale IS
    'Regimi fiscali applicabili ai proprietari (cedolare secca, IVA 10%, ordinario). '
    'Lookup per supportare nuovi regimi fiscali senza modificare lo schema.';

CREATE TRIGGER trg_regime_fiscale_updated_at
    BEFORE UPDATE ON regime_fiscale
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ============================================================
-- TABELLE PRINCIPALI
-- Ordine di creazione rispetta le dipendenze FK.
-- Convenzione: tutte le colonne chiave esterna hanno prefisso fk_.
-- ============================================================

-- Tenant (Agenzie / Property Manager clienti della piattaforma)
CREATE TABLE tenant (
    id                      SERIAL          PRIMARY KEY,
    legal_name              VARCHAR(200)    NOT NULL,
    display_name            VARCHAR(100)    NOT NULL,
    tax_code                CHAR(16)        NOT NULL UNIQUE,
    vat_number              CHAR(11)        UNIQUE,           -- solo 11 cifre, senza prefisso IT
    stato                   tenant_status   NOT NULL DEFAULT 'draft',
    administrative_email    VARCHAR(150)    NOT NULL,
    pec                     VARCHAR(150),
    phone                   VARCHAR(20),
    legal_address           VARCHAR(300)    NOT NULL,
    activated_at            DATE,
    created_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP       NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE tenant IS
    'Agenzie o Property Manager clienti della piattaforma. '
    'Ogni tenant è un soggetto fiscale autonomo che gestisce i propri owner, immobili e prenotazioni.';

CREATE TRIGGER trg_tenant_updated_at
    BEFORE UPDATE ON tenant
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- Utenti della piattaforma
-- fk_owner_id è aggiunto con ALTER TABLE dopo la creazione di owner_profile
-- per evitare dipendenza circolare tenant → utente → owner_profile → tenant.
CREATE TABLE utente (
    id              SERIAL          PRIMARY KEY,
    fk_tenant_id    INTEGER         REFERENCES tenant(id) ON DELETE SET NULL,   -- NULL per super_admin
    email           VARCHAR(150)    NOT NULL UNIQUE,
    first_name      VARCHAR(80)     NOT NULL,
    last_name       VARCHAR(80)     NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL,
    ruolo           user_role       NOT NULL,
    attivo          BOOLEAN         NOT NULL DEFAULT TRUE,
    last_login      TIMESTAMP,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE utente IS
    'Utenti autenticati della piattaforma. '
    'Il ruolo determina sezioni e operazioni accessibili. '
    'Il super_admin non è vincolato a nessun tenant (fk_tenant_id = NULL).';

CREATE TRIGGER trg_utente_updated_at
    BEFORE UPDATE ON utente
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- Proprietari degli immobili
CREATE TABLE owner_profile (
    id              SERIAL          PRIMARY KEY,
    fk_tenant_id    INTEGER         NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    owner_type      owner_type      NOT NULL DEFAULT 'persona_fisica',
    first_name      VARCHAR(80),                -- compilato per owner_type = persona_fisica
    last_name       VARCHAR(80),                -- compilato per owner_type = persona_fisica
    legal_name      VARCHAR(200),               -- compilato per owner_type = piva / societa
    tax_code        CHAR(16)        NOT NULL,
    vat_number      CHAR(11),                   -- solo per piva / societa
    fk_regime_fiscale_id INTEGER         NOT NULL REFERENCES regime_fiscale(id) ON DELETE RESTRICT DEFAULT 1,  -- 1 = 'cedolare_secca'
    email           VARCHAR(150),
    phone           VARCHAR(20),
    iban            VARCHAR(34),                -- IBAN per accredito liquidazioni (max 34 per standard internazionale)
    attivo          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_owner_tax_code_per_tenant UNIQUE (fk_tenant_id, tax_code)
);
COMMENT ON TABLE owner_profile IS
    'Proprietari degli immobili gestiti da un tenant. '
    'Può essere persona fisica (first_name/last_name + tax_code) o soggetto con P.IVA (legal_name + vat_number). '
    'fk_regime_fiscale_id referenzia la tabella lookup regime_fiscale.';

CREATE TRIGGER trg_owner_profile_updated_at
    BEFORE UPDATE ON owner_profile
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Collegamento utente ↔ owner_profile (aggiunto dopo owner_profile per evitare dipendenza circolare)
ALTER TABLE utente
    ADD COLUMN fk_owner_id INTEGER REFERENCES owner_profile(id) ON DELETE SET NULL;
COMMENT ON COLUMN utente.fk_owner_id IS
    'Popolato solo per ruolo = owner_user. Collega l utente al profilo proprietario.';


-- Immobili
CREATE TABLE property (
    id                  SERIAL          PRIMARY KEY,
    fk_tenant_id        INTEGER         NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    fk_owner_id         INTEGER         NOT NULL REFERENCES owner_profile(id) ON DELETE RESTRICT,
    fk_pm_user_id       INTEGER         REFERENCES utente(id) ON DELETE SET NULL,       -- property manager assegnato
    fk_tipo_immobile_id INTEGER         REFERENCES tipo_immobile(id) ON DELETE SET NULL,
    internal_code       VARCHAR(20)     NOT NULL,
    display_name        VARCHAR(150)    NOT NULL,
    address             VARCHAR(200)    NOT NULL,
    city                VARCHAR(80)     NOT NULL,
    region              VARCHAR(80)     NOT NULL,
    cin_code            VARCHAR(25),              -- Codice Identificativo Nazionale (formato IT + 6 + 1 + 9 = 18 char)
    attivo              BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_property_code_per_tenant UNIQUE (fk_tenant_id, internal_code)
);
COMMENT ON TABLE property IS
    'Immobili gestiti da un tenant per conto di un owner. '
    'Il CIN (Codice Identificativo Nazionale) è obbligatorio per locazioni turistiche dal 2024.';

CREATE TRIGGER trg_property_updated_at
    BEFORE UPDATE ON property
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- Codici OTA per immobile (sostituisce l oggetto ota_codes annidato nel frontend)
CREATE TABLE property_ota_code (
    id              SERIAL          PRIMARY KEY,
    fk_property_id  INTEGER         NOT NULL REFERENCES property(id) ON DELETE CASCADE,
    fk_canale_ota_id INTEGER        NOT NULL REFERENCES canale_ota(id) ON DELETE RESTRICT,
    external_id     VARCHAR(60)     NOT NULL,   -- ID assegnato dall OTA alla struttura
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_property_per_canale UNIQUE (fk_property_id, fk_canale_ota_id)
);
COMMENT ON TABLE property_ota_code IS
    'Associazione tra immobile e canale OTA con il codice identificativo esterno. '
    'Normalizza l oggetto ota_codes del frontend in righe separate, una per OTA.';

CREATE TRIGGER trg_property_ota_code_updated_at
    BEFORE UPDATE ON property_ota_code
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- Prenotazioni
CREATE TABLE booking (
    id                              SERIAL                  PRIMARY KEY,
    fk_tenant_id                    INTEGER                 NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    fk_property_id                  INTEGER                 NOT NULL REFERENCES property(id) ON DELETE RESTRICT,
    fk_canale_ota_id                INTEGER                 REFERENCES canale_ota(id) ON DELETE SET NULL,
    fk_scenario_fiscale_id          INTEGER                 REFERENCES scenario_fiscale(id) ON DELETE SET NULL,
    external_booking_id             VARCHAR(100),           -- ID prenotazione sul canale OTA
    guest_name                      VARCHAR(150)            NOT NULL,
    guest_tax_code                  VARCHAR(20),            -- VARCHAR perché può essere codice fiscale estero
    checkin_date                    DATE                    NOT NULL,
    checkout_date                   DATE                    NOT NULL,
    nights                          SMALLINT                NOT NULL,
    guests                          SMALLINT                NOT NULL,
    gross_amount                    DECIMAL(10,2)           NOT NULL,
    ota_commission_amount           DECIMAL(10,2)           NOT NULL DEFAULT 0,
    cleaning_amount                 DECIMAL(10,2)           NOT NULL DEFAULT 0,
    pm_fee_amount                   DECIMAL(10,2)           NOT NULL DEFAULT 0,
    owner_net_amount                DECIMAL(10,2)           NOT NULL,
    withholding_amount              DECIMAL(10,2)           NOT NULL DEFAULT 0,   -- ritenuta 21%
    tourist_tax_amount              DECIMAL(10,2)           NOT NULL DEFAULT 0,
    tourist_tax_included_in_gross   BOOLEAN                 NOT NULL DEFAULT FALSE,
    tourist_tax_collection          tourist_tax_collection  NOT NULL DEFAULT 'contanti',
    fk_stato_prenotazione_id        INTEGER                 NOT NULL REFERENCES stato_prenotazione(id) ON DELETE RESTRICT DEFAULT 1,  -- 1 = 'imported'
    payment_status                  payment_status          NOT NULL DEFAULT 'pending',
    fk_stato_documento_id           INTEGER                 NOT NULL REFERENCES stato_documento(id) ON DELETE RESTRICT DEFAULT 1,          -- 1 = 'draft'
    settlement_status               settlement_status       NOT NULL DEFAULT 'pending',
    created_at                      TIMESTAMP               NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMP               NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_checkout_after_checkin   CHECK (checkout_date > checkin_date),
    CONSTRAINT chk_nights_positive          CHECK (nights > 0),
    CONSTRAINT chk_guests_positive          CHECK (guests > 0),
    CONSTRAINT uq_external_booking          UNIQUE (fk_canale_ota_id, external_booking_id)
);
COMMENT ON TABLE booking IS
    'Prenotazioni importate dai canali OTA o inserite manualmente. '
    'Contiene tutti i dati finanziari (lordo, commissioni, netto, ritenute, tassa soggiorno). '
    'fk_stato_prenotazione_id e fk_stato_documento_id referenziano le tabelle lookup; '
    'payment_status e settlement_status restano enum.';

CREATE TRIGGER trg_booking_updated_at
    BEFORE UPDATE ON booking
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- Documenti fiscali (fatture, ricevute, note di credito)
CREATE TABLE fiscal_document (
    id                      SERIAL          PRIMARY KEY,
    fk_tenant_id            INTEGER         NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    fk_booking_id           INTEGER         NOT NULL REFERENCES booking(id) ON DELETE RESTRICT,
    fk_tipo_documento_id    INTEGER         NOT NULL REFERENCES tipo_documento(id) ON DELETE RESTRICT,
    fk_sdi_esito_id         INTEGER         REFERENCES sdi_esito(id) ON DELETE SET NULL,
    document_number         VARCHAR(30)     NOT NULL,           -- es. 'FT-2025-0001', 'RIC-2025-0001'
    issue_date              DATE            NOT NULL,
    recipient_name          VARCHAR(150)    NOT NULL,
    recipient_tax_code      VARCHAR(20),                        -- CF destinatario, opzionale per stranieri
    total_amount            DECIMAL(10,2)   NOT NULL,
    vat_amount              DECIMAL(10,2)   NOT NULL DEFAULT 0, -- 0 per ricevute fuori campo IVA
    imponibile              DECIMAL(10,2),                      -- base imponibile (canone per ricevuta, riaddebiti+provvigione per fattura)
    ritenuta_amount         DECIMAL(10,2),                      -- ritenuta d'acconto 21% (cedolare/sostituto d'imposta)
    bollo_amount            DECIMAL(10,2),                      -- imposta di bollo €2,00 se importo > €77,47
    iva_amount              DECIMAL(10,2),                      -- IVA del documento (0 per ricevute fuori campo IVA)
    fk_stato_documento_id   INTEGER         NOT NULL REFERENCES stato_documento(id) ON DELETE RESTRICT DEFAULT 1,  -- 1 = 'draft'
    sdi_identifier          VARCHAR(50),                        -- identificativo assegnato da SDI
    created_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_document_number_per_tenant UNIQUE (fk_tenant_id, document_number)
);
COMMENT ON TABLE fiscal_document IS
    'Documenti fiscali emessi per ogni prenotazione. '
    'Di norma ogni booking genera due documenti: fattura PM → ospite (con IVA) '
    'e ricevuta owner → ospite (fuori campo IVA, cedolare secca). '
    'fk_stato_documento_id referenzia la tabella lookup stato_documento.';

CREATE TRIGGER trg_fiscal_document_updated_at
    BEFORE UPDATE ON fiscal_document
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- Liquidazioni periodiche proprietari
CREATE TABLE settlement (
    id                  SERIAL              PRIMARY KEY,
    fk_tenant_id        INTEGER             NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    fk_owner_id         INTEGER             NOT NULL REFERENCES owner_profile(id) ON DELETE RESTRICT,
    period              CHAR(7)             NOT NULL,   -- formato YYYY-MM
    total_amount        DECIMAL(10,2)       NOT NULL,
    withholding_amount  DECIMAL(10,2)       NOT NULL DEFAULT 0,
    net_amount          DECIMAL(10,2)       NOT NULL,   -- total_amount - withholding_amount
    stato               settlement_status   NOT NULL DEFAULT 'pending',
    payment_date        DATE,                           -- NULL finché non liquidata
    created_at          TIMESTAMP           NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP           NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_settlement_owner_period   UNIQUE (fk_owner_id, period),
    CONSTRAINT chk_settlement_period_fmt    CHECK (period ~ '^\d{4}-(0[1-9]|1[0-2])$')
);
COMMENT ON TABLE settlement IS
    'Liquidazioni mensili per proprietario: consolida netto e ritenute '
    'di tutte le prenotazioni del periodo. Un solo record per (owner, mese).';

CREATE TRIGGER trg_settlement_updated_at
    BEFORE UPDATE ON settlement
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- Associazione prenotazioni ↔ liquidazione
-- Tabella di join immutabile: niente updated_at (le righe non vengono mai modificate).
CREATE TABLE settlement_booking (
    id                  SERIAL      PRIMARY KEY,
    fk_settlement_id    INTEGER     NOT NULL REFERENCES settlement(id) ON DELETE CASCADE,
    fk_booking_id       INTEGER     NOT NULL REFERENCES booking(id) ON DELETE RESTRICT,
    created_at          TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_settlement_booking UNIQUE (fk_settlement_id, fk_booking_id)
);
COMMENT ON TABLE settlement_booking IS
    'Tabella di associazione N:N tra settlement e booking. '
    'Traccia esattamente quali prenotazioni compongono una liquidazione. '
    'Immutabile: le righe vengono solo inserite o cancellate, mai aggiornate.';


-- Versamenti F24 ritenute
CREATE TABLE f24_record (
    id                  SERIAL          PRIMARY KEY,
    fk_tenant_id        INTEGER         NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    fk_codice_tributo_id INTEGER        NOT NULL REFERENCES codice_tributo(id) ON DELETE RESTRICT,
    period              CHAR(7)         NOT NULL,   -- formato YYYY-MM
    total_amount        DECIMAL(10,2)   NOT NULL,
    withholdings_count  SMALLINT        NOT NULL DEFAULT 0,   -- numero di ritenute aggregate nel versamento
    stato               f24_status      NOT NULL DEFAULT 'draft',
    deadline_date       DATE            NOT NULL,
    payment_date        DATE,                       -- NULL finché non pagato
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_f24_tenant_period_tributo UNIQUE (fk_tenant_id, period, fk_codice_tributo_id),
    CONSTRAINT chk_f24_period_fmt           CHECK (period ~ '^\d{4}-(0[1-9]|1[0-2])$')
);
COMMENT ON TABLE f24_record IS
    'Versamenti periodici delle ritenute operate tramite modello F24. '
    'Un record per mese per codice tributo per tenant. '
    'Codice tributo 1919: ritenute sui corrispettivi da locazioni brevi (art. 4 DL 50/2017).';

CREATE TRIGGER trg_f24_record_updated_at
    BEFORE UPDATE ON f24_record
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- Certificazioni Uniche annuali
CREATE TABLE cu_record (
    id              SERIAL          PRIMARY KEY,
    fk_tenant_id    INTEGER         NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    fk_owner_id     INTEGER         NOT NULL REFERENCES owner_profile(id) ON DELETE RESTRICT,
    tax_year        SMALLINT        NOT NULL,
    total_compensi  DECIMAL(10,2)   NOT NULL DEFAULT 0,
    total_ritenute  DECIMAL(10,2)   NOT NULL DEFAULT 0,
    stato           cu_status       NOT NULL DEFAULT 'draft',
    generated_at    TIMESTAMP,                  -- NULL finché non generata
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_cu_owner_year UNIQUE (fk_owner_id, tax_year)
);
COMMENT ON TABLE cu_record IS
    'Certificazioni Uniche (CU) annuali per proprietario. '
    'Attesta i compensi erogati e le ritenute operate nell anno fiscale. '
    'Un solo record per (owner, anno fiscale).';

CREATE TRIGGER trg_cu_record_updated_at
    BEFORE UPDATE ON cu_record
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- Log di audit
CREATE TABLE audit_log (
    id              SERIAL          PRIMARY KEY,
    fk_tenant_id    INTEGER         REFERENCES tenant(id) ON DELETE SET NULL,   -- NULL per azioni super_admin cross-tenant
    fk_utente_id    INTEGER         REFERENCES utente(id) ON DELETE SET NULL,
    user_email      VARCHAR(150)    NOT NULL,   -- denormalizzato: persiste la traccia anche se l utente viene eliminato
    action          VARCHAR(100)    NOT NULL,   -- es. 'booking.import', 'document.issue', 'tenant.suspend'
    entity_type     VARCHAR(50)     NOT NULL,   -- es. 'Booking', 'FiscalDocument', 'Tenant'
    entity_id       INTEGER,                    -- ID dell entità coinvolta (NULL per azioni senza entità specifica)
    details         TEXT,                       -- descrizione libera — TEXT giustificato per contenuto non predicibile
    ip_address      VARCHAR(45)     NOT NULL,   -- IPv4 max 15 char, IPv6 max 39, totale max 45
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE audit_log IS
    'Log immutabile delle operazioni significative eseguite dagli utenti. '
    'user_email è denormalizzato per mantenere la traccia storica anche dopo eliminazione utente. '
    'Le righe non devono essere mai modificate o cancellate in produzione. '
    'Immutabile come settlement_booking: niente updated_at, niente trigger.';


-- ============================================================
-- DATI INIZIALI — TABELLE LOOKUP
-- ============================================================

INSERT INTO stato_prenotazione (codice, descrizione, finale) VALUES
    ('imported',   'Importata da OTA, dati grezzi',                FALSE),
    ('enriched',   'Dati arricchiti (CF ospite, tassa soggiorno)', FALSE),
    ('ready',      'Pronta per emissione documento',               FALSE),
    ('doc_issued', 'Documento fiscale emesso',                     FALSE),
    ('settled',    'Liquidata al proprietario',                    TRUE),
    ('cancelled',  'Annullata',                                    TRUE);

INSERT INTO stato_documento (codice, descrizione, is_error, finale) VALUES
    ('draft',     'Bozza',                    FALSE, FALSE),
    ('ready',     'Pronto per invio SDI',     FALSE, FALSE),
    ('sent_sdi',  'Inviato a SDI',            FALSE, FALSE),
    ('accepted',  'Accettato da SDI',         FALSE, TRUE),
    ('rejected',  'Rifiutato da SDI',         TRUE,  TRUE),
    ('error',     'Errore generico',          TRUE,  FALSE);

INSERT INTO regime_fiscale (codice, descrizione) VALUES
    ('cedolare_secca', 'Cedolare secca'),
    ('iva_10',         'IVA 10%'),
    ('ordinario',      'Regime ordinario');

INSERT INTO canale_ota (codice, nome, commissione_default_pct, tassa_soggiorno_inclusa) VALUES
    ('airbnb',          'Airbnb',           15.00, TRUE),
    ('booking',         'Booking.com',      18.00, FALSE),
    ('vrbo',            'Vrbo',              8.00, FALSE),
    ('expedia',         'Expedia',          15.00, FALSE),
    ('tripadvisor',     'TripAdvisor',       3.00, FALSE),
    ('google_travel',   'Google Travel',     0.00, FALSE),
    ('diretto',         'Diretto',           0.00, FALSE);

INSERT INTO tipo_immobile (codice, descrizione) VALUES
    ('LT',   'Locazione Turistica'),
    ('BB',   'Bed & Breakfast'),
    ('AC',   'Affittacamere'),
    ('CV',   'Casa Vacanze'),
    ('AGR',  'Agriturismo');

INSERT INTO tipo_documento (codice, descrizione, richiede_iva, trasmesso_sdi) VALUES
    ('fattura',         'Fattura elettronica',                      TRUE,  TRUE),
    ('ricevuta',        'Ricevuta semplice (fuori campo IVA)',       FALSE, FALSE),
    ('nota_credito',    'Nota di credito elettronica',              TRUE,  TRUE);

INSERT INTO codice_tributo (codice, descrizione) VALUES
    ('1919', 'Ritenute operate sui corrispettivi dovuti dai condomini — locazioni brevi art. 4 DL 50/2017');

INSERT INTO sdi_esito (codice, descrizione) VALUES
    ('RC', 'Ricevuta di Consegna — fattura consegnata al destinatario'),
    ('MC', 'Mancata Consegna — impossibile recapitare, disponibile in area riservata SDI'),
    ('NS', 'Notifica Scarto — fattura rifiutata per errori formali'),
    ('DT', 'Decorrenza Termini — non ritirata dal destinatario entro 15 giorni'),
    ('AT', 'Attestazione di Trasmissione — per PA: attestato di invio senza esito di consegna');

-- Tasse di soggiorno — fonte: frontend/src/data/tourist-tax.ts
-- eta_esenzione = soglia esclusiva: ospiti con età < eta_esenzione sono esenti
INSERT INTO regola_tassa_soggiorno
    (comune, provincia, importo_per_notte, max_notti, eta_esenzione, valida_dal) VALUES
    ('Venezia', 'VE', 5.00, 5,  10, '2025-04-01'),   -- DCC 77/2024; bassa stagione (gen) -30%
    ('Roma',    'RM', 3.50, 10, 10, '2024-01-01'),   -- cap €35 per persona per soggiorno
    ('Firenze', 'FI', 5.50, 7,  12, '2024-01-01'),
    ('Napoli',  'NA', 3.00, 14, 14, '2024-01-01'),
    ('Genova',  'GE', 2.50, 5,  12, '2024-01-01');   -- max 5 pernottamenti consecutivi

-- Scenari fiscali: nessun dato iniziale predefinito.
-- Definire gli scenari in base all analisi funzionale (es. scenario_A, scenario_B).


-- ============================================================
-- VISTE UTILI
-- ============================================================

-- Vista ricavi mensili (sostituisce mockRevenueData nel frontend)
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
COMMENT ON VIEW v_ricavi_mensili IS
    'Aggregazione mensile dei ricavi per tenant. '
    'Sostituisce il mockRevenueData del frontend con dati reali da booking.';


-- ============================================================
-- INDICI CONSIGLIATI
-- Raggruppati per tabella, con nota sulla query che li giustifica.
-- ============================================================

-- tenant
CREATE INDEX idx_tenant_stato
    ON tenant(stato);
    -- ricerca tenant attivi / sospesi in lista admin

-- utente
CREATE INDEX idx_utente_fk_tenant_id
    ON utente(fk_tenant_id);
    -- caricamento utenti per tenant
CREATE INDEX idx_utente_ruolo
    ON utente(ruolo);
    -- filtro per ruolo in gestione utenti

-- stato_prenotazione / stato_documento / regime_fiscale
CREATE INDEX idx_stato_prenotazione_attivo
    ON stato_prenotazione(attivo);
    -- lista stati attivi per UI (selezione stato)
CREATE INDEX idx_stato_documento_attivo
    ON stato_documento(attivo);
    -- lista stati attivi per UI (selezione stato)
CREATE INDEX idx_regime_fiscale_attivo
    ON regime_fiscale(attivo);
    -- lista regimi attivi per UI (selezione regime)

-- owner_profile
CREATE INDEX idx_owner_fk_tenant_id
    ON owner_profile(fk_tenant_id);
    -- lista proprietari di un tenant
CREATE INDEX idx_owner_tax_code
    ON owner_profile(tax_code);
    -- ricerca proprietario per codice fiscale
CREATE INDEX idx_owner_attivo
    ON owner_profile(fk_tenant_id, attivo);
    -- lista proprietari attivi per tenant (query frequente da UI)
CREATE INDEX idx_owner_fk_regime_fiscale_id
    ON owner_profile(fk_regime_fiscale_id);
    -- filtro proprietari per regime fiscale

-- property
CREATE INDEX idx_property_fk_tenant_id
    ON property(fk_tenant_id);
    -- lista immobili di un tenant
CREATE INDEX idx_property_fk_owner_id
    ON property(fk_owner_id);
    -- immobili di un proprietario
CREATE INDEX idx_property_attivo
    ON property(fk_tenant_id, attivo);
    -- lista immobili attivi per tenant

-- property_ota_code
CREATE INDEX idx_property_ota_fk_property_id
    ON property_ota_code(fk_property_id);
    -- codici OTA di un immobile (JOIN frequente)
CREATE INDEX idx_property_ota_fk_canale_id
    ON property_ota_code(fk_canale_ota_id, external_id);
    -- ricerca immobile da ID esterno OTA (import prenotazioni)

-- booking (tabella più interrogata del sistema)
CREATE INDEX idx_booking_fk_tenant_id
    ON booking(fk_tenant_id);
    -- tutti i booking di un tenant
CREATE INDEX idx_booking_fk_property_id
    ON booking(fk_property_id);
    -- booking di un immobile
CREATE INDEX idx_booking_checkout_date
    ON booking(fk_tenant_id, checkout_date DESC);
    -- lista booking ordinata per checkout (dashboard e report)
CREATE INDEX idx_booking_fk_stato_prenotazione_id
    ON booking(fk_tenant_id, fk_stato_prenotazione_id);
    -- filtro per stato workflow (es. tutti i 'ready' da processare)
CREATE INDEX idx_booking_fk_stato_documento_id
    ON booking(fk_tenant_id, fk_stato_documento_id);
    -- filtro documenti da emettere / in errore SDI
CREATE INDEX idx_booking_settlement_status
    ON booking(fk_tenant_id, settlement_status);
    -- filtro per liquidazioni da calcolare
CREATE INDEX idx_booking_guest_tax_code
    ON booking(guest_tax_code);
    -- ricerca per codice fiscale ospite (accesso guest portal)
CREATE INDEX idx_booking_checkin_checkout
    ON booking(fk_property_id, checkin_date, checkout_date);
    -- verifica disponibilità e overlap date
CREATE INDEX idx_booking_fk_scenario_fiscale_id
    ON booking(fk_scenario_fiscale_id);
    -- filtro prenotazioni per scenario fiscale applicato

-- fiscal_document
CREATE INDEX idx_fiscal_doc_fk_tenant_id
    ON fiscal_document(fk_tenant_id);
    -- lista documenti di un tenant
CREATE INDEX idx_fiscal_doc_fk_booking_id
    ON fiscal_document(fk_booking_id);
    -- documenti di una prenotazione (tipicamente 2)
CREATE INDEX idx_fiscal_doc_fk_stato_documento_id
    ON fiscal_document(fk_tenant_id, fk_stato_documento_id);
    -- documenti in lavorazione / errore SDI
CREATE INDEX idx_fiscal_doc_issue_date
    ON fiscal_document(fk_tenant_id, issue_date DESC);
    -- lista documenti per data emissione

-- settlement
CREATE INDEX idx_settlement_fk_tenant_id
    ON settlement(fk_tenant_id);
CREATE INDEX idx_settlement_fk_owner_id
    ON settlement(fk_owner_id);
    -- liquidazioni di un proprietario
CREATE INDEX idx_settlement_period
    ON settlement(fk_tenant_id, period);
    -- liquidazioni di un periodo (generazione F24)
CREATE INDEX idx_settlement_stato
    ON settlement(fk_tenant_id, stato);
    -- liquidazioni da approvare / pagare

-- settlement_booking
CREATE INDEX idx_settlement_booking_fk_settlement_id
    ON settlement_booking(fk_settlement_id);
    -- prenotazioni di una liquidazione
CREATE INDEX idx_settlement_booking_fk_booking_id
    ON settlement_booking(fk_booking_id);
    -- liquidazione a cui appartiene un booking

-- f24_record
CREATE INDEX idx_f24_fk_tenant_period
    ON f24_record(fk_tenant_id, period);
    -- F24 di un periodo per tenant
CREATE INDEX idx_f24_stato
    ON f24_record(fk_tenant_id, stato);
    -- F24 da trasmettere / in scadenza

-- cu_record
CREATE INDEX idx_cu_fk_owner_id
    ON cu_record(fk_owner_id);
    -- CU di un proprietario
CREATE INDEX idx_cu_fk_tenant_year
    ON cu_record(fk_tenant_id, tax_year);
    -- tutte le CU di un tenant per anno

-- audit_log
CREATE INDEX idx_audit_fk_tenant_id
    ON audit_log(fk_tenant_id);
CREATE INDEX idx_audit_action
    ON audit_log(action);
    -- filtro per tipo di azione
CREATE INDEX idx_audit_entity
    ON audit_log(entity_type, entity_id);
    -- storia di una specifica entità
CREATE INDEX idx_audit_created_at
    ON audit_log(created_at DESC);
    -- log in ordine cronologico inverso (più recenti prima)

-- regola_tassa_soggiorno
CREATE INDEX idx_tassa_soggiorno_comune
    ON regola_tassa_soggiorno(comune, provincia);
    -- ricerca regola per comune durante calcolo tassa soggiorno
CREATE INDEX idx_tassa_soggiorno_attivo
    ON regola_tassa_soggiorno(attivo, valida_dal, valida_al);
    -- regole attualmente in vigore

-- ============================================================
-- TENANT SETTINGS
-- ============================================================
CREATE TABLE tenant_settings (
    id                          SERIAL          PRIMARY KEY,
    fk_tenant_id                INTEGER         NOT NULL UNIQUE
                                REFERENCES tenant(id) ON DELETE CASCADE,
    -- Parametri fiscali
    withholding_rate_primary    DECIMAL(5,2)    NOT NULL DEFAULT 21.00,
    withholding_rate_secondary  DECIMAL(5,2)    NOT NULL DEFAULT 26.00,
    codice_tributo_f24          VARCHAR(10)     NOT NULL DEFAULT '1919',
    document_window_days        SMALLINT        NOT NULL DEFAULT 14,
    cedolare_secca_enabled      BOOLEAN         NOT NULL DEFAULT TRUE,
    -- Policy documentali
    sdi_auto_send               BOOLEAN         NOT NULL DEFAULT TRUE,
    deroga_ricevuta_enabled     BOOLEAN         NOT NULL DEFAULT FALSE,
    numerazione_automatica      BOOLEAN         NOT NULL DEFAULT TRUE,
    -- Notifiche
    alert_scadenze_documenti    BOOLEAN         NOT NULL DEFAULT TRUE,
    alert_scadenze_f24          BOOLEAN         NOT NULL DEFAULT TRUE,
    notifiche_email             BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMP       NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE tenant_settings IS
  'Parametri fiscali, policy documentali e notifiche per tenant';

CREATE TRIGGER trg_tenant_settings_updated_at
    BEFORE UPDATE ON tenant_settings
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============================================================
-- AGGIORNAMENTI SUCCESSIVI
-- ============================================================

-- Aggiunta tourist_tax_collection a canale_ota
ALTER TABLE canale_ota
  ADD COLUMN IF NOT EXISTS tourist_tax_collection
  VARCHAR(20) DEFAULT 'contanti';

COMMENT ON COLUMN canale_ota.tourist_tax_collection IS
  'Modalità default riscossione tassa soggiorno per questo canale';
