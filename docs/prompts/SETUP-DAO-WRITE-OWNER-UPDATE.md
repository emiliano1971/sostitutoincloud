Leggi il file CLAUDE.md prima di procedere.

Aggiungi metodo updateAnagrafica a OwnerProfileDAO
e il relativo Service + Controller.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. OwnerProfileDAO — aggiungi metodo
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a dao/OwnerProfileDAO.java:

OwnerProfile updateAnagrafica(OwnerProfile owner)
- UPDATE owner_profile SET
  owner_type=?, first_name=?, last_name=?,
  legal_name=?, tax_code=?, vat_number=?,
  fk_regime_fiscale_id=?, email=?, phone=?,
  iban=?, updated_at=NOW()
  WHERE id=?
- Usa Types.OTHER per owner_type (enum PostgreSQL)
- Dopo update: rileggi con findById() e restituisci
- Log INFO: "OwnerProfileDAO.updateAnagrafica() - id={}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. DTO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea dto/owner/OwnerUpdateDTO.java:
- @Data @NoArgsConstructor @AllArgsConstructor
- Stessi campi di OwnerCreateDTO ma tutti opzionali:
    * String ownerType
    * String firstName
    * String lastName
    * String legalName
    * String taxCode
    * String vatNumber
    * Integer fkRegimeFiscaleId
    * String email
    * String phone
    * String iban

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. OwnerService — aggiungi metodo
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a service/OwnerService.java:

OwnerDetailDTO update(Integer tenantId,
Integer ownerId, OwnerUpdateDTO dto)
- Verifica esistenza e appartenenza al tenant
- Se dto.taxCode è cambiato: verifica che non esista
  già per un altro owner del tenant
- Aggiorna i campi non null del DTO sull'oggetto
  OwnerProfile esistente (partial update)
- Chiama ownerProfileDAO.updateAnagrafica(owner)
- Ricarica e mappa su OwnerDetailDTO
- Log INFO: "OwnerService.update() - id={}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. OwnerController — aggiungi endpoint
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a controller/OwnerController.java:

PUT /api/owners/{id}
- @RequestBody OwnerUpdateDTO
- tenantId = SecurityUtils.getCurrentTenantId()
- chiama ownerService.update(tenantId, id, dto)
- ResponseEntity.ok(result)
- gestisce IllegalArgumentException → 400

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. BUILD E TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal clean package

Verifica:
curl -s -X PUT \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{"phone": "+39 333 0000000", "iban": "IT60X0542811101000000000001"}' \
http://localhost:8081/sostitutoincloud/api/owners/1 \
| python3 -m json.tool

Riporta output del build e del curl.