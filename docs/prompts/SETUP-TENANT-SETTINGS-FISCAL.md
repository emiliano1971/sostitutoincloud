Leggi il file CLAUDE.md e docs/db/schema-target.sql
prima di procedere.

Aggiungi parametri fiscali configurabili
a tenant_settings e aggiorna il sistema
di generazione documenti.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. ALTER TABLE sul DB
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Esegui sul DB:

psql -U sostitutoincloud -d sostitutoincloud \
-h localhost -c "
ALTER TABLE tenant_settings
ADD COLUMN IF NOT EXISTS bollo_importo
DECIMAL(5,2) NOT NULL DEFAULT 2.00,
ADD COLUMN IF NOT EXISTS bollo_soglia
DECIMAL(10,2) NOT NULL DEFAULT 77.47,
ADD COLUMN IF NOT EXISTS bollo_addebitato_cliente
BOOLEAN NOT NULL DEFAULT TRUE,
ADD COLUMN IF NOT EXISTS regime_fiscale_pm
VARCHAR(10) NOT NULL DEFAULT 'RF01',
ADD COLUMN IF NOT EXISTS natura_iva_esente
VARCHAR(10) NOT NULL DEFAULT 'N2.1',
ADD COLUMN IF NOT EXISTS aliquota_ritenuta_acconto
DECIMAL(5,2) NOT NULL DEFAULT 21.00;
"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. AGGIORNA SCHEMA E MODEL
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiorna docs/db/schema-target.sql:
- Aggiungi le 6 colonne a tenant_settings

Aggiorna model/TenantSettings.java:
- BigDecimal bolloImporto
- BigDecimal bolloSoglia
- Boolean bolloAddebitatoCliente
- String regimeFiscalePm
- String naturaIvaEsente
- BigDecimal aliquotaRitenutaAcconto

Aggiorna dao/mapper/TenantSettingsRowMapper.java:
- Aggiungi mapping per i 6 nuovi campi

Aggiorna dao/TenantSettingsDAO.java:
- Aggiungi i 6 campi nelle SELECT e nell'upsert

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. AGGIORNA DTO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiorna dto/settings/TenantSettingsDTO.java:
- Aggiungi i 6 nuovi campi

Aggiorna dto/settings/TenantSettingsUpdateDTO.java:
- Aggiungi i 6 nuovi campi (tutti opzionali)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. AGGIORNA TenantSettingsService
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In service/TenantSettingsService.java:

Nel metodo getSettings() dove crea i default
se non esistono, aggiungi i valori di default:
- bolloImporto = 2.00
- bolloSoglia = 77.47
- bolloAddebitatoCliente = true
- regimeFiscalePm = "RF01"
- naturaIvaEsente = "N2.1"
- aliquotaRitenutaAcconto = 21.00

Nel metodo updateSettings() aggiungi
il mapping dei 6 nuovi campi dal DTO.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. AGGIORNA DocumentGenerationService
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In service/DocumentGenerationService.java:

- Inietta TenantSettingsService
- Rimuovi le costanti hardcoded:
    * SOGLIA_BOLLO
    * IMPORTO_BOLLO
    * RITENUTA_21
    * ALIQUOTA_IVA_22 (mantieni come default)

- All'inizio del metodo generate() carica
  i settings del tenant:
  TenantSettingsDTO settings =
  tenantSettingsService.getSettings(tenantId)

- Usa i valori dai settings:
  BigDecimal bolloSoglia = settings.getBolloSoglia()
  BigDecimal bolloImporto = settings.getBolloImporto()
  BigDecimal aliquotaRitenuta =
  settings.getAlliquotaRitenutaAcconto()
  .divide(BigDecimal.valueOf(100))

- Per regime forfettario (RF19):
  if ("RF19".equals(settings.getRegimeFiscalePm())) {
  // fattura PM: niente IVA
  iva = BigDecimal.ZERO
  aliquotaIva = BigDecimal.ZERO
  // ma riaddebita comunque i costi
  }

- Per bollo_addebitato_cliente = false:
  // bollo presente ma non addebitato all'ospite
  // bollo_amount salvato ma non sommato al totale
  if (!settings.getBolloAddebitatoCliente()) {
  importoTotale = canone // senza bollo
  // ma salva bollo_amount comunque per tracciabilità
  }

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. AGGIORNA FRONTEND — settingsApi.ts
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a frontend/src/api/settingsApi.ts:

```ts
// Nuovi campi in TenantSettingsDTO
bolloImporto: number;
bolloSoglia: number;
bolloAddebitatoCliente: boolean;
regimeFiscalePm: string;
naturaIvaEsente: string;
aliquotaRitenutaAcconto: number;
```

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7. AGGIORNA TenantSettings.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In frontend/src/pages/tenant/TenantSettings.tsx
nella tab "Parametri Fiscali" aggiungi i nuovi
campi dopo quelli esistenti:

Sezione "Bollo":
- Importo bollo (€): Input number, default 2.00
- Soglia applicazione bollo (€):
  Input number, default 77.47
- Addebita bollo al cliente: Switch

Sezione "Regime PM":
- Regime fiscale PM: Select
    * RF01 → "Ordinario"
    * RF19 → "Forfettario (art.1, L.190/2014)"

Sezione "IVA e Ritenuta":
- Natura IVA esente: Select
    * N2.1 → "N2.1 - Fuori campo IVA art.4 D.L.50/2017"
    * N1 → "N1 - Escluse art.13"
    * N4 → "N4 - Esenti"
- Aliquota ritenuta acconto (%):
  Input number, default 21.00

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
8. BUILD E TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal clean package
cd frontend && npm run build

Verifica che /settings mostri i nuovi campi
nella tab "Parametri Fiscali".

Riporta output di entrambi.