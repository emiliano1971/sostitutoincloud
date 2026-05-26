Leggi il file frontend/src/data/mock-data.ts e genera 
il file docs/db/seed-data.sql.

Regole:
- Commento in testa: "-- SEED DATA — solo per test, NON eseguire in prod"
- Ordine FK: lookup già presenti in schema-target.sql, 
  quindi inizia da tenant
- id hardcoded, niente subquery
- password_hash = '{CHANGE_ME}'
- Inserisci SOLO:
  * 1 tenant
  * 2 utenti (1 admin, 1 owner)
  * 1 owner_profile
  * 2 property
  * 6 booking (1 per stato: imported, enriched, ready, doc_issued, settled, cancelled)
  * 2 fiscal_document collegati ai booking in stato doc_issued e settled
- Nient'altro — niente settlement, f24, cu, audit_log
