Leggi il file CLAUDE.md e docs/db/schema-target.sql
prima di procedere.

Trasforma la tabella regime_fiscale in una
tabella di transcodifica multi-purpose
aggiungendo il campo metadata.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. ALTER TABLE E SEED SUL DB
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Esegui sul DB:

psql -U sostitutoincloud -d sostitutoincloud \
-h localhost -c "
-- Aggiungi colonna metadata
ALTER TABLE regime_fiscale
ADD COLUMN IF NOT EXISTS metadata
VARCHAR(50) NOT NULL DEFAULT 'REGIME_FISCALE';

-- Aggiorna dati esistenti
UPDATE regime_fiscale
SET metadata = 'REGIME_FISCALE';

-- Aggiungi indice per ricerche per metadata
CREATE INDEX IF NOT EXISTS idx_regime_fiscale_metadata
ON regime_fiscale(metadata);

-- Inserisci codici NATURA_IVA
INSERT INTO regime_fiscale
(codice, descrizione, attivo, metadata)
VALUES
('N1',   'Escluse art.13 (forfettario)',          TRUE, 'NATURA_IVA'),
('N2.1', 'Fuori campo IVA art.4 D.L.50/2017',    TRUE, 'NATURA_IVA'),
('N2.2', 'Fuori campo IVA altri casi',            TRUE, 'NATURA_IVA'),
('N3.5', 'Non imponibili regime margine',         TRUE, 'NATURA_IVA'),
('N4',   'Esenti',                                TRUE, 'NATURA_IVA'),
('N6.1', 'Inversione contabile rottami',          TRUE, 'NATURA_IVA');

-- Inserisci codici ALIQUOTA_IVA
INSERT INTO regime_fiscale
(codice, descrizione, attivo, metadata)
VALUES
('0',  'Esente / Fuori campo IVA',  TRUE, 'ALIQUOTA_IVA'),
('10', 'IVA 10% (CAV)',             TRUE, 'ALIQUOTA_IVA'),
('22', 'IVA 22% (ordinaria)',       TRUE, 'ALIQUOTA_IVA');

-- Inserisci codici REGIME_FISCALE_PM
-- (i codici SDI ufficiali per il PM)
INSERT INTO regime_fiscale
(codice, descrizione, attivo, metadata)
VALUES
('RF01', 'Ordinario',                               TRUE, 'REGIME_FISCALE_PM'),
('RF19', 'Forfettario (art.1, commi 54-89, L.190/2014)', TRUE, 'REGIME_FISCALE_PM');
"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. AGGIORNA SCHEMA
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiorna docs/db/schema-target.sql:
- Aggiungi colonna metadata a regime_fiscale
- Aggiungi indice idx_regime_fiscale_metadata
- Aggiungi i nuovi INSERT nel seed

Aggiorna docs/db/seed-data.sql:
- Aggiungi tutti i nuovi record inseriti

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. AGGIORNA MODEL E DAO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiorna model/RegimeFiscale.java:
- Aggiungi campo String metadata

Aggiorna dao/mapper/RegimeFiscaleRowMapper.java:
- Aggiungi mapping rs.getString("metadata")

Aggiorna dao/RegimeFiscaleDAO.java:
- Aggiorna findAll() per includere metadata
- Aggiungi metodo:
  List<RegimeFiscale> findByMetadata(String metadata)
    - SELECT * FROM regime_fiscale
      WHERE metadata = ? AND attivo = TRUE
      ORDER BY codice

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. AGGIORNA LookupService e LookupCollectionDTO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiorna dto/lookup/LookupCollectionDTO.java:
- Aggiungi:
  List<LookupItemDTO> regimiFiscaliPm
  List<LookupItemDTO> naturaIva
  List<LookupItemDTO> aliquoteIva

Aggiorna service/LookupService.java:
- Nel metodo getAll() aggiungi:
    * regimiFiscaliPm =
      regimeFiscaleDAO.findByMetadata("REGIME_FISCALE_PM")
      mappati su LookupItemDTO
    * naturaIva =
      regimeFiscaleDAO.findByMetadata("NATURA_IVA")
      mappati su LookupItemDTO
    * aliquoteIva =
      regimeFiscaleDAO.findByMetadata("ALIQUOTA_IVA")
      mappati su LookupItemDTO
- La lista regimiFiscali esistente continua
  a caricare solo REGIME_FISCALE (per i proprietari)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. AGGIORNA FRONTEND — LookupContext
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiorna frontend/src/api/lookupApi.ts:
- Aggiungi a LookupCollection:
  regimiFiscaliPm: LookupItem[]
  naturaIva: LookupItem[]
  aliquoteIva: LookupItem[]

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. AGGIORNA TenantSettings.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In frontend/src/pages/tenant/TenantSettings.tsx
nella tab "Parametri Fiscali" sostituisci
i Select hardcoded con valori dinamici
dal LookupContext:

- Regime PM: usa lookups.regimiFiscaliPm
- Natura IVA: usa lookups.naturaIva

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7. BUILD E TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal clean package
cd frontend && npm run build

Verifica endpoint lookup:
curl -s http://localhost:8081/sostitutoincloud/\
api/public/lookup | python3 -m json.tool \
| grep -A 30 "regimiFiscaliPm\|naturaIva\|aliquoteIva"

Riporta output di entrambi.