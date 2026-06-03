Leggi il file CLAUDE.md e docs/db/schema-target.sql
prima di procedere.

Aggiungi metodi di scrittura (INSERT/UPDATE) ai DAO
per owner_profile e property.
Segui le convenzioni già esistenti nel progetto.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. OwnerProfileDAO — aggiungi metodi
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a dao/OwnerProfileDAO.java:

OwnerProfile insert(OwnerProfile owner)
- INSERT INTO owner_profile con tutti i campi
  (esclusi id, created_at, updated_at — generati dal DB)
- Usa KeyHolder per recuperare l'id generato
- Dopo insert: rileggi il record con findById()
  e restituisci l'oggetto completo
- Log INFO: "OwnerProfileDAO.insert() - id={}"

OwnerProfile updateStatus(Integer id, Boolean attivo)
- UPDATE owner_profile SET attivo=?, updated_at=NOW()
  WHERE id=?
- Dopo update: rileggi con findById() e restituisci
- Log INFO: "OwnerProfileDAO.updateStatus() - id={} attivo={}"

OwnerProfile updateFkRegimeFiscale(Integer id,
Integer fkRegimeFiscaleId)
- UPDATE owner_profile SET fk_regime_fiscale_id=?,
  updated_at=NOW() WHERE id=?
- Dopo update: rileggi con findById() e restituisci
- Log INFO

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. PropertyDAO — aggiungi metodi
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a dao/PropertyDAO.java:

Property insert(Property property)
- INSERT INTO property con tutti i campi
  (esclusi id, created_at, updated_at)
- Usa KeyHolder per recuperare l'id generato
- Dopo insert: rileggi con findById()
- Log INFO: "PropertyDAO.insert() - id={}"

Property updateStatus(Integer id, Boolean attivo)
- UPDATE property SET attivo=?, updated_at=NOW()
  WHERE id=?
- Dopo update: rileggi con findById() e restituisci
- Log INFO

Property updateOwner(Integer id, Integer fkOwnerId)
- UPDATE property SET fk_owner_id=?, updated_at=NOW()
  WHERE id=?
- Dopo update: rileggi con findById() e restituisci
- Log INFO

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. PropertyOtaCodeDAO — aggiungi metodi
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a dao/PropertyOtaCodeDAO.java:

PropertyOtaCode insert(PropertyOtaCode otaCode)
- INSERT INTO property_ota_code
- Usa KeyHolder per id generato
- Log INFO

void deleteByPropertyId(Integer propertyId)
- DELETE FROM property_ota_code
  WHERE fk_property_id=?
- Log INFO: "PropertyOtaCodeDAO.deleteByPropertyId()
    - propertyId={}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. TenantDAO — aggiungi metodo update
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a dao/TenantDAO.java:

Tenant update(Tenant tenant)
- UPDATE tenant SET legal_name=?, display_name=?,
  tax_code=?, vat_number=?, administrative_email=?,
  pec=?, phone=?, legal_address=?,
  updated_at=NOW()
  WHERE id=?
- Dopo update: rileggi con findById() e restituisci
- Log INFO: "TenantDAO.update() - id={}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. BUILD
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Dopo aver aggiunto i metodi lancia:
mvn -Plocal clean package

Riporta l'output del build.