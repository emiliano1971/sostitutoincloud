Leggi il file CLAUDE.md, docs/db/schema-target.sql e
docs/analisi-frontend.md prima di procedere.

Crea DTO + Service + Controller per la gestione Tenant.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. DTO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea src/main/java/it/gavia/sostitutoincloud/dto/tenant/TenantListDTO.java:
- @Data @Builder @NoArgsConstructor @AllArgsConstructor
- Campi:
    * Integer id
    * String legalName
    * String displayName
    * String taxCode
    * String vatNumber
    * String stato
    * String administrativeEmail
    * String phone
    * String legalAddress
    * LocalDate activatedAt
    * LocalDateTime createdAt
    * Integer propertiesCount   ← conteggio immobili
    * Integer ownersCount       ← conteggio proprietari
    * Integer bookingsCount     ← conteggio prenotazioni attive

Crea src/main/java/it/gavia/sostitutoincloud/dto/tenant/TenantDetailDTO.java:
- Stessi campi di TenantListDTO più:
    * String pec
    * String legalAddress
    * LocalDateTime updatedAt

Crea src/main/java/it/gavia/sostitutoincloud/dto/tenant/TenantStatusUpdateDTO.java:
- @Data @NoArgsConstructor @AllArgsConstructor
- String stato   ← "active" | "suspended"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. SERVICE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea src/main/java/it/gavia/sostitutoincloud/service/TenantService.java:
- @Service @Log4j2
- Costruttore con TenantDAO, PropertyDAO, OwnerProfileDAO, BookingDAO
- Metodi:

  List<TenantListDTO> findAll()
    - chiama tenantDAO.findAll()
    - per ogni tenant conta:
        * propertiesCount = propertyDAO.findByTenantId(id).size()
        * ownersCount = ownerProfileDAO.findByTenantId(id).size()
        * bookingsCount = bookingDAO.findByTenantId(id).size()
    - mappa su TenantListDTO
    - Log INFO: "TenantService.findAll() - {} tenant trovati"

  Optional<TenantDetailDTO> findById(Integer id)
    - chiama tenantDAO.findById(id)
    - mappa su TenantDetailDTO con conteggi
    - Log INFO: "TenantService.findById() - id={}"

  TenantDetailDTO updateStatus(Integer id, String nuovoStato)
    - verifica che id esista — lancia RuntimeException se non trovato
    - verifica che nuovoStato sia "active" o "suspended"
      lancia IllegalArgumentException se non valido
    - per ora logga l'operazione e lancia
      UnsupportedOperationException("Update stato non ancora implementato -
      richiede insert/update nel DAO")
    - Log WARN: "TenantService.updateStatus() - id={} stato={}"

  NOTA: updateStatus è uno stub — verrà implementato quando
  aggiungeremo insert/update nei DAO

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. CONTROLLER
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea src/main/java/it/gavia/sostitutoincloud/controller/TenantController.java:
- @RestController @Log4j2
- @RequestMapping("/api/admin/tenants")
- Costruttore con TenantService
- Endpoints:

  GET /api/admin/tenants
    - chiama tenantService.findAll()
    - restituisce ResponseEntity<List<TenantListDTO>>
    - 200 OK con lista

  GET /api/admin/tenants/{id}
    - chiama tenantService.findById(id)
    - 200 OK se trovato, 404 NOT FOUND se assente
    - restituisce ResponseEntity<TenantDetailDTO>

  PATCH /api/admin/tenants/{id}/status
    - @RequestBody TenantStatusUpdateDTO
    - chiama tenantService.updateStatus(id, dto.getStato())
    - 200 OK se aggiornato
    - 404 se non trovato
    - 400 se stato non valido
    - gestisce UnsupportedOperationException → 501 NOT IMPLEMENTED
      con messaggio esplicativo

  NOTA: questi endpoint sono /api/admin/** quindi richiedono
  ruolo super_admin — da configurare in SecurityConfig
  quando implementeremo i ruoli reali. Per ora sono
  accessibili con autenticazione base.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. GESTIONE ECCEZIONI GLOBALE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea src/main/java/it/gavia/sostitutoincloud/config/GlobalExceptionHandler.java:
- @RestControllerAdvice @Log4j2
- Gestisce:
    * RuntimeException → 404 NOT FOUND con messaggio
    * IllegalArgumentException → 400 BAD REQUEST con messaggio
    * UnsupportedOperationException → 501 NOT IMPLEMENTED con messaggio
    * Exception generica → 500 INTERNAL SERVER ERROR
      (logga lo stack trace, non lo espone al client)
- Risposta JSON standard:
  {
  "status": 404,
  "error": "Not Found",
  "message": "Tenant non trovato: id=99",
  "timestamp": "2026-05-28T10:00:00"
  }

━━━━━━━━━━━━━