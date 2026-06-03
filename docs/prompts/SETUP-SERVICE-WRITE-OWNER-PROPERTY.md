Leggi il file CLAUDE.md e docs/analisi-frontend.md
prima di procedere.

Implementa i metodi di scrittura nei Service e Controller
per owner e property. I DAO di scrittura sono già pronti.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. DTO NUOVI
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea dto/owner/OwnerCreateDTO.java:
- @Data @NoArgsConstructor @AllArgsConstructor
- Campi obbligatori:
    * String ownerType  ← "persona_fisica"|"piva"|"societa"
    * String firstName
    * String lastName
    * String taxCode
    * String email
- Campi opzionali:
    * String legalName
    * String vatNumber
    * Integer fkRegimeFiscaleId
    * String phone
    * String iban

Crea dto/property/PropertyUpdateOwnerDTO.java:
- @Data @NoArgsConstructor @AllArgsConstructor
- Integer fkOwnerId

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. AGGIORNA OwnerService
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a service/OwnerService.java:

OwnerDetailDTO create(Integer tenantId,
OwnerCreateDTO dto)
- Valida che taxCode non esista già per il tenant
  (ownerProfileDAO.findByTaxCode() — se presente
  lancia IllegalArgumentException
  "Proprietario con questo CF già esistente")
- Costruisce OwnerProfile da DTO:
    * fkTenantId = tenantId
    * ownerType = dto.ownerType
    * firstName, lastName, taxCode, email, ecc.
    * fkRegimeFiscaleId = dto.fkRegimeFiscaleId
      oppure default id di "cedolare_secca"
      (carica da regimeFiscaleDAO.findByCodice
      ("cedolare_secca"))
    * attivo = true
- Chiama ownerProfileDAO.insert(owner)
- Ricarica con findById e mappa su OwnerDetailDTO
- Log INFO: "OwnerService.create() - tenantId={}
  taxCode={}"

OwnerDetailDTO updateStatus(Integer tenantId,
Integer ownerId, Boolean attivo)
- Sostituisce lo stub esistente con implementazione reale
- Verifica esistenza e appartenenza al tenant
- Chiama ownerProfileDAO.updateStatus(ownerId, attivo)
- Ricarica e mappa su OwnerDetailDTO
- Log INFO

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. AGGIORNA OwnerController
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a controller/OwnerController.java:

POST /api/owners
- @RequestBody OwnerCreateDTO
- tenantId = SecurityUtils.getCurrentTenantId()
- chiama ownerService.create(tenantId, dto)
- ResponseEntity.status(201).body(result)
- gestisce IllegalArgumentException → 400

PATCH /api/owners/{id}/status — sostituisce lo stub
- @RequestBody OwnerStatusUpdateDTO
- chiama ownerService.updateStatus(tenantId, id,
  dto.getAttivo())
- ResponseEntity.ok(result)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. AGGIORNA PropertyService
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a service/PropertyService.java:

PropertyDetailDTO create(Integer tenantId,
PropertyCreateDTO dto)
- Sostituisce lo stub esistente
- Valida che internalCode non esista già per il tenant
  (propertyDAO.findByTenantId() — se presente con
  stesso internalCode lancia IllegalArgumentException
  "Codice immobile già esistente")
- Ricava fkTipoImmobileId da TipoImmobileDAO
  .findByCodice(dto.getPropertyType())
  oppure usa il primo disponibile
- Costruisce Property da DTO:
    * fkTenantId = tenantId
    * fkPmUserId = SecurityUtils.getCurrentUtenteId()
    * attivo = true
    * tutti i campi del DTO
- Chiama propertyDAO.insert(property)
- Se dto.getOtaCodes() non è vuota:
  per ogni OtaCode nel DTO:
    * risolve fkCanaleOtaId da canaleOtaDAO
      .findByCodice(otaCode.canaleCodiceName)
    * inserisce con propertyOtaCodeDAO.insert()
- Ricarica e mappa su PropertyDetailDTO
- Log INFO

PropertyDetailDTO updateStatus(Integer tenantId,
Integer propertyId, Boolean attivo)
- Sostituisce lo stub esistente
- Verifica esistenza e appartenenza al tenant
- Chiama propertyDAO.updateStatus(propertyId, attivo)
- Ricarica e mappa su PropertyDetailDTO
- Log INFO

PropertyDetailDTO updateOwner(Integer tenantId,
Integer propertyId, Integer fkOwnerId)
- Verifica esistenza property e appartenenza al tenant
- Verifica che owner esista e appartenga al tenant
- Chiama propertyDAO.updateOwner(propertyId, fkOwnerId)
- Ricarica e mappa su PropertyDetailDTO
- Log INFO

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. AGGIORNA PropertyController
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a controller/PropertyController.java:

POST /api/properties — sostituisce lo stub
- @RequestBody PropertyCreateDTO
- chiama propertyService.create(tenantId, dto)
- ResponseEntity.status(201).body(result)
- gestisce IllegalArgumentException → 400

PATCH /api/properties/{id}/status — sostituisce stub
- @RequestBody PropertyStatusUpdateDTO
- chiama propertyService.updateStatus(tenantId, id,
  dto.getAttivo())
- ResponseEntity.ok(result)

PUT /api/properties/{id}/owner
- @RequestBody PropertyUpdateOwnerDTO
- chiama propertyService.updateOwner(tenantId, id,
  dto.getFkOwnerId())
- ResponseEntity.ok(result)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. AGGIORNA TenantSettingsService
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In service/TenantSettingsService.java
sostituisci lo stub updateSettings():
- Implementa UPDATE dati aziendali tenant
  usando tenantDAO.update(tenant)
- Salva settings con tenantSettingsDAO.save()
- Restituisce TenantSettingsDTO aggiornato
- Rimuovi UnsupportedOperationException

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7. BUILD E TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Dopo aver implementato lancia:
mvn -Plocal clean package

Poi riavvia Tomcat e testa:

# Crea nuovo proprietario
curl -s -X POST \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{
"ownerType": "persona_fisica",
"firstName": "Mario",
"lastName": "Rossi",
"taxCode": "RSSMRA80A01H501Z",
"email": "mario.rossi@email.it",
"phone": "+39 333 9999999",
"iban": "IT60X0542811101000000999999"
}' \
http://localhost:8081/sostitutoincloud/api/owners \
| python3 -m json.tool

# Toggle stato owner
curl -s -X PATCH \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{"attivo": false}' \
http://localhost:8081/sostitutoincloud/api/owners/1/status \
| python3 -m json.tool

# Crea nuovo immobile
curl -s -X POST \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{
"displayName": "Appartamento Test",
"internalCode": "ROM-003",
"propertyType": "LT",
"city": "Roma",
"region": "Lazio",
"fkOwnerId": 1
}' \
http://localhost:8081/sostitutoincloud/api/properties \
| python3 -m json.tool

Riporta l'output del build e dei curl.