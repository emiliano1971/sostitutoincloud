Leggi il CLAUDE.md prima di procedere.
Leggi docs/db/schema-target.sql
per la struttura di booking_split_economico
prima di procedere.

Crea il layer di accesso dati per
booking_split_economico seguendo
i pattern esistenti del progetto
(es. model/Booking.java,
dao/BookingDAO.java,
dao/mapper/BookingRowMapper.java).

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. MODEL
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea model/BookingSplitEconomico.java:
- @Data @Builder @NoArgsConstructor
  @AllArgsConstructor @Log4j2
- Campi:
  Integer id
  Integer fkBookingId
  Integer fkTenantId
  Integer fkPropertyContractRuleId
  String tipoVoce
  String descrizione
  BigDecimal importo
  BigDecimal aliquotaIva
  Boolean includeInFatturaPm
  Integer ordinamento
  String source
  LocalDateTime deletedAt
  LocalDateTime createdAt
  LocalDateTime updatedAt
  Integer createdBy
  Integer updatedBy

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. ROW MAPPER
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea dao/mapper/
BookingSplitEconomicoRowMapper.java:
- Implementa RowMapper<BookingSplitEconomico>
- Mappa tutti i campi della tabella
- Usa rs.getObject() per i campi
  nullable (fkPropertyContractRuleId,
  deletedAt, createdBy, updatedBy)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. DAO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea dao/BookingSplitEconomicoDAO.java:
- @Repository @Log4j2
- Costruttore con JdbcTemplate
- Costante SELECT_ALL con tutti i campi

Metodi:

List<BookingSplitEconomico>
findByBookingId(Integer bookingId)
- SELECT * FROM booking_split_economico
  WHERE fk_booking_id = ?
  AND deleted_at IS NULL
  ORDER BY ordinamento, id
- Log DEBUG

Optional<BookingSplitEconomico>
findById(Integer id)
- SELECT * FROM booking_split_economico
  WHERE id = ?
  AND deleted_at IS NULL
- Log DEBUG

BookingSplitEconomico insert(
BookingSplitEconomico riga)
- INSERT INTO booking_split_economico
  (fk_booking_id, fk_tenant_id,
  fk_property_contract_rule_id,
  tipo_voce, descrizione, importo,
  aliquota_iva, include_in_fattura_pm,
  ordinamento, source,
  created_by, updated_by)
  VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
- Usa KeyHolder per id generato
- Rileggi con findById()
- Log INFO "BookingSplitEconomicoDAO
  .insert() - bookingId={} tipoVoce={}"

BookingSplitEconomico update(
BookingSplitEconomico riga)
- UPDATE booking_split_economico SET
  descrizione = ?,
  importo = ?,
  aliquota_iva = ?,
  include_in_fattura_pm = ?,
  ordinamento = ?,
  updated_at = NOW(),
  updated_by = ?
  WHERE id = ?
  AND fk_tenant_id = ?
  AND deleted_at IS NULL
- Rileggi con findById()
- Log INFO "BookingSplitEconomicoDAO
  .update() - id={}"

void softDelete(
Integer id, Integer tenantId,
Integer updatedBy)
- UPDATE booking_split_economico SET
  deleted_at = NOW(),
  updated_at = NOW(),
  updated_by = ?
  WHERE id = ?
  AND fk_tenant_id = ?
  AND deleted_at IS NULL
- Log INFO "BookingSplitEconomicoDAO
  .softDelete() - id={}"

void deleteByBookingId(
Integer bookingId)
- DELETE FROM booking_split_economico
  WHERE fk_booking_id = ?
- (hard delete — usato solo dal
  cleanup test e dal deleteBooking)
- Log INFO "BookingSplitEconomicoDAO
  .deleteByBookingId() - bookingId={}"

BigDecimal sumImportoByBookingId(
Integer bookingId)
- SELECT COALESCE(SUM(importo), 0)
  FROM booking_split_economico
  WHERE fk_booking_id = ?
  AND include_in_fattura_pm = true
  AND deleted_at IS NULL
- Restituisce BigDecimal
- Log DEBUG

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. DTO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea dto/booking/
BookingSplitEconomicoDTO.java:
- @Data @Builder @NoArgsConstructor
  @AllArgsConstructor
- Integer id
- Integer fkBookingId
- Integer fkPropertyContractRuleId
  ← nullable
- String tipoVoce
- String descrizione
- BigDecimal importo
- BigDecimal aliquotaIva
- Boolean includeInFatturaPm
- Integer ordinamento
- String source
- LocalDateTime createdAt
- LocalDateTime updatedAt

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. BUILD
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal -DskipTests clean package
Riporta output build.