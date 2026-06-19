Leggi il file CLAUDE.md e docs/db/schema-target.sql
prima di procedere.

Implementa il sistema di audit log centralizzato.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. ANALISI PRELIMINARE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Prima leggi:
- src/main/java/it/gavia/sostitutoincloud/model/AuditLog.java
- src/main/java/it/gavia/sostitutoincloud/dao/AuditLogDAO.java

Verifica la struttura esatta della tabella
audit_log e i campi disponibili.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. AGGIORNA AuditLogDAO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a dao/AuditLogDAO.java:

AuditLog insert(AuditLog log)
- INSERT INTO audit_log con tutti i campi
- Usa KeyHolder per id generato
- Dopo insert: rileggi con findById()
  oppure restituisci l'oggetto con id settato
- Log DEBUG: "AuditLogDAO.insert() -
  azione={} tenantId={}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. CREA AuditService
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea service/AuditService.java:
- @Service @Log4j2
- Costruttore con AuditLogDAO

  void log(String azione, String entita,
  Integer entitaId, String descrizione,
  Integer tenantId, Integer utenteId,
  String ipAddress)
    - Costruisce AuditLog e chiama
      auditLogDAO.insert()
    - Non lancia eccezioni — se insert fallisce
      logga WARN e continua (audit non deve
      bloccare il flusso principale)
    - Log DEBUG

  void log(String azione, String entita,
  Integer entitaId, String descrizione)
    - Overload senza ip/tenant/utente
    - Ricava tenantId e utenteId dal
      SecurityUtils.getCurrentTenantId()
      SecurityUtils.getCurrentUtenteId()
    - ipAddress = null
    - Chiama il metodo principale

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. INIETTA AuditService NEI SERVICE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi AuditService come dipendenza e
chiama audit.log() dopo ogni operazione
riuscita nei seguenti Service:

TenantService:
- create() → "tenant.create", "Tenant", id,
  "Creato tenant {displayName}"
- update() → "tenant.update", "Tenant", id,
  "Aggiornato tenant {displayName}"
- updateStatus() stato="active" →
  "tenant.activate", "Tenant", id,
  "Tenant {displayName} riattivato"
- updateStatus() stato="suspended" →
  "tenant.suspend", "Tenant", id,
  "Tenant {displayName} sospeso"

UserManagementService:
- create() → "user.create", "Utente", id,
  "Creato utente {email} ruolo {ruolo}"
- delete() → "user.delete", "Utente", id,
  "Eliminato utente id={id}"
- updateStatus() attivo=true →
  "user.activate", "Utente", id,
  "Utente {email} riattivato"
- updateStatus() attivo=false →
  "user.suspend", "Utente", id,
  "Utente {email} disattivato"

OwnerService:
- create() → "owner.create", "OwnerProfile", id,
  "Creato proprietario {firstName} {lastName}"
- update() → "owner.update", "OwnerProfile", id,
  "Aggiornato proprietario {firstName} {lastName}"
- updateStatus() attivo=true →
  "owner.activate", "OwnerProfile", id,
  "Proprietario {firstName} {lastName} riattivato"
- updateStatus() attivo=false →
  "owner.suspend", "OwnerProfile", id,
  "Proprietario {firstName} {lastName} disattivato"

PropertyService:
- create() → "property.create", "Property", id,
  "Creato immobile {displayName} ({internalCode})"
- updateStatus() attivo=true →
  "property.activate", "Property", id,
  "Immobile {displayName} riattivato"
- updateStatus() attivo=false →
  "property.suspend", "Property", id,
  "Immobile {displayName} disattivato"
- updateOwner() → "property.assign_owner",
  "Property", id,
  "Immobile {displayName} assegnato a owner id={fkOwnerId}"

BookingImportService:
- confirm() → "booking.import", "Booking", null,
  "Importate {imported} prenotazioni da CSV"

DocumentGenerationService:
- generate() → "document.issue",
  "FiscalDocument", documentId,
  "Emesso documento {documentNumber}
  per prenotazione {externalBookingId}"

TouristTaxService:
- create() → "tourist_tax.create",
  "RegolaTassaSoggiorno", id,
  "Creata regola tassa soggiorno per {comune}"
- update() → "tourist_tax.update",
  "RegolaTassaSoggiorno", id,
  "Aggiornata regola tassa soggiorno per {comune}"

CanaleOtaService:
- create() → "ota.create", "CanaleOta", id,
  "Creato canale OTA {nome}"
- update() → "ota.update", "CanaleOta", id,
  "Aggiornato canale OTA {nome}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. BUILD E TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal clean package

Dopo il build riavvia Tomcat ed esegui
una operazione qualsiasi (es. modifica
un proprietario) e verifica sul DB:

psql -U sostitutoincloud -d sostitutoincloud \
-h localhost \
-c "SELECT azione, entita, descrizione,
created_at FROM audit_log
ORDER BY created_at DESC LIMIT 5;"

Riporta output del build e della query.