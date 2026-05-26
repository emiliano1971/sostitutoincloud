## COMADI FATTI DB

Nel file docs/db/schema-target.sql converti i seguenti
enum in tabelle lookup. Per ognuno:
1. Elimina il CREATE TYPE corrispondente
2. Crea la tabella lookup nella sezione TABELLE LOOKUP
3. Sostituisci le colonne che usavano l'enum con FK
   verso la nuova tabella (prefisso fk_)
4. Aggiungi i dati iniziali con INSERT
5. Aggiungi gli indici nella sezione INDICI CONSIGLIATI
6. Aggiorna i commenti delle tabelle impattate

--- CONVERSIONE 1 ---
Elimina: CREATE TYPE booking_status
Crea tabella:
CREATE TABLE stato_prenotazione (
id          SERIAL          PRIMARY KEY,
codice      VARCHAR(30)     NOT NULL UNIQUE,
descrizione VARCHAR(150)    NOT NULL,
finale      BOOLEAN         NOT NULL DEFAULT FALSE, -- TRUE per stati terminali (settled, cancelled)
attivo      BOOLEAN         NOT NULL DEFAULT TRUE,
created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
updated_at  TIMESTAMP       NOT NULL DEFAULT NOW()
);
Dati iniziali:
('imported',  'Importata da OTA, dati grezzi',              FALSE)
('enriched',  'Dati arricchiti (CF ospite, tassa soggiorno)', FALSE)
('ready',     'Pronta per emissione documento',              FALSE)
('doc_issued','Documento fiscale emesso',                    FALSE)
('settled',   'Liquidata al proprietario',                   TRUE)
('cancelled', 'Annullata',                                   TRUE)
In booking: rinomina booking_status → fk_stato_prenotazione_id
INTEGER NOT NULL REFERENCES stato_prenotazione(id) ON DELETE RESTRICT
DEFAULT = id del record 'imported'

--- CONVERSIONE 2 ---
Elimina: CREATE TYPE document_status
Crea tabella:
CREATE TABLE stato_documento (
id          SERIAL          PRIMARY KEY,
codice      VARCHAR(30)     NOT NULL UNIQUE,
descrizione VARCHAR(150)    NOT NULL,
is_error    BOOLEAN         NOT NULL DEFAULT FALSE, -- TRUE per stati di errore
finale      BOOLEAN         NOT NULL DEFAULT FALSE, -- TRUE per stati terminali
attivo      BOOLEAN         NOT NULL DEFAULT TRUE,
created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
updated_at  TIMESTAMP       NOT NULL DEFAULT NOW()
);
Dati iniziali:
('draft',     'Bozza',                          FALSE, FALSE)
('ready',     'Pronto per invio SDI',           FALSE, FALSE)
('sent_sdi',  'Inviato a SDI',                  FALSE, FALSE)
('accepted',  'Accettato da SDI',               FALSE, TRUE)
('rejected',  'Rifiutato da SDI',               TRUE,  TRUE)
('error',     'Errore generico',                TRUE,  FALSE)
In booking: rinomina document_status → fk_stato_documento_id
INTEGER NOT NULL REFERENCES stato_documento(id) ON DELETE RESTRICT
In fiscal_document: rinomina stato → fk_stato_documento_id
INTEGER NOT NULL REFERENCES stato_documento(id) ON DELETE RESTRICT

--- CONVERSIONE 3 ---
Elimina: CREATE TYPE fiscal_regime
Crea tabella:
CREATE TABLE regime_fiscale (
id          SERIAL          PRIMARY KEY,
codice      VARCHAR(30)     NOT NULL UNIQUE,
descrizione VARCHAR(150)    NOT NULL,
attivo      BOOLEAN         NOT NULL DEFAULT TRUE,
created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
updated_at  TIMESTAMP       NOT NULL DEFAULT NOW()
);
Dati iniziali:
('cedolare_secca', 'Cedolare secca')
('iva_10',         'IVA 10%')
('ordinario',      'Regime ordinario')
In owner_profile: rinomina fiscal_regime → fk_regime_fiscale_id
INTEGER NOT NULL REFERENCES regime_fiscale(id) ON DELETE RESTRICT

NON modificare nient'altro.
Aggiungere trigger set_updated_at su tutte e tre le nuove tabelle.

### Nel file docs/db/schema-target.sql modifica ONLY audit_log:
- Rimuovi il campo updated_at
- Aggiorna il commento: aggiungi che è immutabile come settlement_booking
  Non toccare nient'altro.

