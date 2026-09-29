Leggi il CLAUDE.md prima di procedere.
Leggi i file esistenti:
- service/OwnerProfileService.java
- service/PropertyService.java
- dao/OwnerProfileDAO.java
- dao/PropertyDAO.java
- dao/PropertyContractRuleDAO.java
- controller/OwnerProfileController.java
- docs/db/schema-target.sql
  (tabelle owner_profile, property,
  property_contract_rule, tenant_settings)
- /mnt/project/template_importazione_proprietari.xlsx
  ← leggi le colonne del template
  prima di procedere.

Implementa l'import massivo di
proprietari e immobili da file Excel.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
LOGICA DI BUSINESS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Ogni riga del file = un immobile
con i dati del proprietario.

Deduplicazione:
- Proprietario: chiave = codice_fiscale
  SE esiste → associa l'immobile
  al proprietario esistente
  SENZA sovrascrivere i dati
  SE non esiste → crea nuovo proprietario

- Immobile: chiave = nome_immobile
    + fk_owner_id
      SE esiste → salta la riga
      SE non esiste → crea nuovo immobile

Errori:
- Riga con errore → salta e continua
- Proprietario senza CF → errore
- Nome immobile vuoto → errore
- Città vuota → errore

Regole contratto:
- Crea solo se la colonna è valorizzata
- commissione_ota_pct → regola tipo
  'commissione_ota' con calc_mode
  'percentuale_lordo' sul canale OTA
  default del tenant
  (fk_canale_ota_default_id da
  tenant_settings — se NULL salta
  la regola OTA)
- pulizie_importo → regola tipo
  'pulizie' con calc_mode 'fisso'
- cambio_biancheria_euro_persona →
  regola tipo 'cambio_biancheria'
  con calc_mode 'fisso_per_persona'
- commissione_pm_pct + tipo →
  regola tipo 'commissione_pm'
  con calc_mode 'percentuale_lordo'
  o 'percentuale_netto'

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. MIGRATION DB
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Verifica l'ultimo numero migration
e crea il file successivo:
023_tenant_canale_ota_default.sql

ALTER TABLE tenant_settings
ADD COLUMN IF NOT EXISTS
fk_canale_ota_default_id INTEGER
REFERENCES canale_ota(id)
ON DELETE SET NULL;

COMMENT ON COLUMN
tenant_settings.fk_canale_ota_default_id
IS 'Canale OTA di default usato per
le regole commissione_ota
nell import massivo proprietari.
NULL = nessun canale default
configurato.';

Aggiorna schema-target.sql.
Esegui sul DB locale.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. BACKEND — TenantSettings
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a model/TenantSettings.java:
Integer fkCanaleOtaDefaultId

Aggiorna mapper e DAO per leggere
e scrivere fk_canale_ota_default_id.

Aggiorna TenantSettingsService e
il DTO di configurazione tenant per
esporre e aggiornare il campo.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. BACKEND — OwnerBulkImportService
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea service/OwnerBulkImportService.java:
- @Service @Log4j2
- Costruttore con:
  OwnerProfileDAO, PropertyDAO,
  PropertyContractRuleDAO,
  TenantSettingsService,
  CanaleOtaDAO

Metodo principale:

OwnerBulkImportResult importa(
Integer tenantId,
Integer utenteId,
MultipartFile file)

Classe risultato
OwnerBulkImportResult:
int righeProcessate
int proprietariCreati
int proprietariEsistenti
int immobiliCreati
int immobiliSaltati
int righeInErrore
List<OwnerBulkImportErrore> errori

Classe errore:
int numeroRiga
String descrizioneRiga
← "Rossi Mario - Appartamento Centro"
String messaggio

Logica importa():

1. Leggi il file Excel con
   WorkbookFactory.create(file.getInputStream())

2. Prendi il primo foglio
   (foglio "Importazione")

3. Trova la riga di intestazione:
   cerca la riga che contiene
   "Cognome Proprietario" o "Cognome"
   nella prima colonna (può essere
   riga 3 o diversa)

4. Per ogni riga dati dopo
   l'intestazione:

   a) Leggi i valori con getCellValue()
   già presente in BookingImportService
   ← copia o rendi condivisibile
   il metodo

   b) Salta se riga completamente vuota

   c) Valida campi obbligatori:
    - cognome non blank
    - nome non blank
    - codice_fiscale non blank
      (normalizza: toUpperCase().trim())
    - nome_immobile non blank
    - citta non blank
      SE errore → aggiungi a errori
      e continua con riga successiva

   d) Leggi campi facoltativi:
    - iban (trim)
    - regime_fiscale (default:
      'cedolare_secca' se vuoto)
    - email
    - telefono
    - indirizzo
    - primo_immobile: 'Si'/'si'/'SI'
      → true, altrimenti false
      (default true se vuoto)

   e) Leggi regole contratto:
    - commissione_ota_pct (BigDecimal)
    - pulizie_importo (BigDecimal)
    - cambio_biancheria_euro_persona
      (BigDecimal)
    - commissione_pm_pct (BigDecimal)
    - commissione_pm_tipo
      ('lordo'→'percentuale_lordo',
      'netto'→'percentuale_netto',
      default: 'percentuale_lordo')

   f) Cerca proprietario per CF:
   OwnerProfile owner =
   ownerProfileDAO
   .findByCodFiscAndTenant(
   cf, tenantId)
   .orElse(null)

   SE null → crea proprietario:
   Cerca fkRegimeFiscaleId dalla
   lookup regime_fiscale
   per codice = regime_fiscale
   (default cedolare_secca)

        ownerProfileDAO.insert(
          new OwnerProfile con i dati)
        proprietariCreati++
   ALTRIMENTI:
   proprietariEsistenti++
   usa owner esistente

   g) Cerca immobile per nome + owner:
   Property prop =
   propertyDAO
   .findByNameAndOwner(
   nome_immobile,
   owner.getId(),
   tenantId)
   .orElse(null)

   SE prop != null:
   immobiliSaltati++
   continua con prossima riga

   SE null → crea immobile:
   propertyDAO.insert(
   new Property con i dati)

        Crea regole contratto
        se valorizzate:
        
        SE commissione_ota_pct > 0:
          Carica canaleOtaDefaultId
          da tenant_settings
          SE canaleOtaDefaultId != null:
            crea regola commissione_ota
          ALTRIMENTI:
            log WARN "Canale OTA default
              non configurato per
              tenant {}"
        
        SE pulizie_importo > 0:
          crea regola pulizie fisso
        
        SE cambio_biancheria > 0:
          crea regola cambio_biancheria
          fisso_per_persona
        
        SE commissione_pm_pct > 0:
          crea regola commissione_pm
          con calc_mode determinato
          da commissione_pm_tipo
        
        immobiliCreati++

   h) righeProcessate++

5. Log INFO "OwnerBulkImportService
    - tenant={} processate={} owners={}
      immobili={} errori={}"

6. Return OwnerBulkImportResult

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. METODI DAO AGGIUNTIVI
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi se non esistono:

OwnerProfileDAO:
Optional<OwnerProfile>
findByCodFiscAndTenant(
String codFisc,
Integer tenantId)

PropertyDAO:
Optional<Property>
findByNameAndOwner(
String name,
Integer ownerId,
Integer tenantId)

PropertyContractRuleDAO:
PropertyContractRule insert(
PropertyContractRule rule)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. BACKEND — Controller
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a controller/
OwnerProfileController.java
(o crea OwnerBulkImportController.java):

POST /api/owners/import-bulk
- @RequestParam MultipartFile file
- tenantId da SecurityUtils
- utenteId da SecurityUtils
- chiama ownerBulkImportService.importa()
- 200 OwnerBulkImportResult
- 400 se file non valido o vuoto
- 500 se errore lettura file
- Log INFO

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. FRONTEND — Impostazioni Tenant
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Nella pagina impostazioni tenant
(TenantSettings.tsx o equivalente)
aggiungi il campo canale OTA default:

<div>
  <Label>Canale OTA default per import</Label>
  <p className="text-xs
    text-muted-foreground mb-2">
    Usato per la regola commissione OTA
    nell'importazione massiva proprietari.
  </p>
  <Select
    value={settings.fkCanaleOtaDefaultId
      ?.toString() ?? ''}
    onValueChange={v =>
      setSettings({...settings,
        fkCanaleOtaDefaultId:
          v ? parseInt(v) : null})}
  >
    <SelectTrigger>
      <SelectValue
        placeholder="Nessun canale
          default" />
    </SelectTrigger>
    <SelectContent>
      <SelectItem value="">
        Nessun canale default
      </SelectItem>
      {canali.map(c => (
        <SelectItem
          key={c.id}
          value={c.id.toString()}>
          {c.nome}
        </SelectItem>
      ))}
    </SelectContent>
  </Select>
</div>

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7. FRONTEND — Wizard Import Massivo
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea frontend/src/pages/admin/
OwnerBulkImport.tsx (o aggiungi
alla pagina owners esistente):

Step 1 — Upload file:
- Drop zone per file .xlsx/.xls
- Link download template
- Pulsante "Importa"

Step 2 — Risultati:
Mostra OwnerBulkImportResult:

  <div className="space-y-4">
    <div className="grid grid-cols-2
      gap-4">
      <Card>
        <CardContent className="pt-4">
          <p className="text-2xl
            font-bold text-green-600">
            {result.immobiliCreati}
          </p>
          <p className="text-sm
            text-muted-foreground">
            Immobili creati
          </p>
        </CardContent>
      </Card>
      <Card>
        <CardContent className="pt-4">
          <p className="text-2xl
            font-bold">
            {result.proprietariCreati}
          </p>
          <p className="text-sm
            text-muted-foreground">
            Proprietari creati
          </p>
        </CardContent>
      </Card>
    </div>

    SE result.righeInErrore > 0:
    <Card className="border-destructive">
      <CardHeader>
        <CardTitle className="text-sm
          text-destructive">
          {result.righeInErrore} righe
          con errori (saltate)
        </CardTitle>
      </CardHeader>
      <CardContent>
        <table className="text-xs w-full">
          <thead>
            <tr>
              <th>Riga</th>
              <th>Dati</th>
              <th>Errore</th>
            </tr>
          </thead>
          <tbody>
            {result.errori.map(e => (
              <tr key={e.numeroRiga}>
                <td>{e.numeroRiga}</td>
                <td>{e.descrizioneRiga}</td>
                <td className=
                  "text-destructive">
                  {e.messaggio}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </CardContent>
    </Card>
  </div>

Aggiungi la route in App.tsx o
nel router esistente.

Aggiungi link nella sidebar o
nella pagina Owners.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
8. BUILD E TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal -DskipTests clean package
cd frontend && npm run build
npm run typecheck

TOKEN=$(curl -s -X POST \
http://localhost:8081/sostitutoincloud/\
api/public/login \
-H "Content-Type: application/json" \
-d '{"email":"admin@casavacanze.it",
"password":"atena2026"}' \
| python3 -c "import sys,json; \
print(json.load(sys.stdin)['token'])")

# Configura canale OTA default
# (Booking.com = id 2)
curl -s -X PATCH \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{"fkCanaleOtaDefaultId": 2}' \
"http://localhost:8081/sostitutoincloud/\
api/tenant/settings" \
| python3 -m json.tool \
| grep "canaleOtaDefault"

# Importa il template di esempio
curl -s -X POST \
-H "Authorization: Bearer $TOKEN" \
-F "file=@/mnt/project/\
template_importazione_proprietari.xlsx" \
"http://localhost:8081/sostitutoincloud/\
api/owners/import-bulk" \
| python3 -m json.tool

# Verifica che Mario Rossi sia stato
# creato (o saltato se esiste già)
curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/owners" \
| python3 -c "
import sys,json
owners = json.load(sys.stdin)
rossi = [o for o in owners
if 'Rossi' in str(o.get('cognome',''))
or 'RSSMRA' in str(
o.get('codFisc',''))]
for o in rossi:
print(o)
"

# Verifica regole contratto create
# per l'immobile di Mario Rossi
# (cerca property con nome
#  'Appartamento Centro')

Verifica che:
- Il template viene letto
  correttamente (riga esempio)
- Proprietario creato con CF
  RSSMRA80A01H501Z
- Immobile creato con regole
  OTA 15%, pulizie 60€,
  cambio 20€/persona, PM 10% netto
- Seconda importazione dello stesso
  file → tutto saltato (duplicati)
- Riga con CF mancante → errore
  nella lista errori

Riporta output build e curl.