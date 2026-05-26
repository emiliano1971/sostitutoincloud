Leggi il file docs/db/analisi-mock.md e genera il file 
docs/db/schema-target.sql con le seguenti regole:

- Database: PostgreSQL 18
- Tutti i nomi di tabelle e colonne in snake_case
- Ogni tabella ha sempre: id SERIAL PRIMARY KEY, 
  created_at TIMESTAMP DEFAULT NOW(), 
  updated_at TIMESTAMP DEFAULT NOW()
- Usa VARCHAR con lunghezza appropriata al contesto,
  mai TEXT salvo campi descrittivi lunghi
- Codice fiscale: CHAR(16)
- Partita IVA: CHAR(11)
- Importi e valori monetari: DECIMAL(10,2)
- Date: DATE, timestamp: TIMESTAMP
- Booleani: BOOLEAN DEFAULT FALSE
- Enum ricorrenti (stati, ruoli) come tipo PostgreSQL CREATE TYPE
- Chiavi esterne con FK esplicite e ON DELETE appropriato al contesto
- Aggiungi un commento SQL su ogni tabella che spiega cosa contiene
- Alla fine del file aggiungi una sezione commentata con 
  gli indici consigliati per le query più frequenti
  
  - NON usare CREATE TYPE enum per valori che potrebbero 
  crescere nel tempo o avere attributi aggiuntivi — 
  creare invece tabelle lookup (es. tipi_documento, 
  canali_ota, codici_tributo, regole_tassa_soggiorno).
  Usare enum PostgreSQL solo per stati e ruoli 
  con valori fissi e definitivi.
