Leggi il file CLAUDE.md, docs/db/schema-target.sql e
docs/analisi-frontend.md prima di procedere.

Crea DTO + Service + Controller per AuditLog.
Solo lettura — nessun insert/update.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. DTO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea dto/audit/AuditLogDTO.java:
- @Data @Builder @NoArgsConstructor @AllArgsConstructor
- Campi esatti da schema-target.sql:
    * Integer id
    * Integer fkTenantId       ← nullable
    * Integer fkUtenteId       ← nullable
    * String userEmail
    * String action
    * String entityType
    * Integer entityId         ← nullable
    * String details
    * String ipAddress         ← nullable
    * LocalDateTime createdAt

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. SERVICE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea service/AuditLogService.java:
- @Service @Log4j2
- Costruttore con AuditLogDAO

- Metodi:

  List<AuditLogDTO> findByTenantId(Integer tenantId,
  String q, String action, Integer page, Integer size)
    - carica audit log del tenant ordinati per created_at DESC
      usando findByTenantIdOrderByCreatedAtDesc(tenantId, 1000)
    - applica filtri IN MEMORIA:
        * se q != null: filtra su details CONTAINS q
          OR userEmail CONTAINS q (case-insensitive)
        * se action != null: filtra su action STARTS WITH action
          (es. "booking." trova "booking.import", "booking.cancel")
    - applica paginazione: skip(page*size).limit(size)
    - mappa su AuditLogDTO
    - Log INFO con count

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. CONTROLLER
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea controller/AuditLogController.java:
- @RestController @Log4j2
- @RequestMapping("/api/audit-log")
- Costruttore con AuditLogService
- tenantId hardcoded a 1 // TODO: SecurityContext
- Endpoints:

  GET /api/audit-log
    - query params opzionali: q, action, page, size
    - ResponseEntity<List<AuditLogDTO>>

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Dopo aver creato i file lancia:
mvn -Plocal clean package

Poi riavvia Tomcat e testa:
curl -u admin:admin \
"http://localhost:8081/sostitutoincloud/api/audit-log"
curl -u admin:admin \
"http://localhost:8081/sostitutoincloud/api/audit-log?action=booking."

Riporta l'output del build e dei curl.