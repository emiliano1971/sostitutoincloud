package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.BookingRowMapper;
import it.gavia.sostitutoincloud.dto.settlement.BookingDaLiquidareDTO;
import it.gavia.sostitutoincloud.model.Booking;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Log4j2
@Repository
public class BookingDAO {

    // ID stato_prenotazione (evita magic numbers nel codice del workflow booking)
    public static final int STATO_IMPORTED   = 1;
    public static final int STATO_ENRICHED   = 2;
    public static final int STATO_READY      = 3;
    public static final int STATO_DOC_ISSUED = 4;
    public static final int STATO_SETTLED    = 5;
    public static final int STATO_CANCELLED  = 6;

    private static final String SELECT_ALL =
            "SELECT b.id, b.fk_tenant_id, b.fk_property_id, b.fk_owner_id, b.fk_canale_ota_id, b.fk_regime_fiscale_id, " +
            "b.external_booking_id, b.guest_name, b.guest_tax_code, " +
            "b.guest_birth_date, b.guest_sesso, b.guest_birth_place, b.guest_birth_belfiore, " +
            "b.guest_doc_type, b.guest_doc_number, b.guest_country, b.guest_address, b.guest_phone, " +
            "b.checkin_date, b.checkout_date, " +
            "b.nights, b.guests, b.gross_amount, b.ota_commission_amount, b.cleaning_amount, " +
            "b.pm_fee_amount, b.owner_net_amount, b.withholding_amount, b.aliquota_ritenuta, b.tourist_tax_amount, " +
            "b.tourist_tax_included_in_gross, b.tourist_tax_collection, b.fk_stato_prenotazione_id, " +
            "b.payment_status, b.settlement_status, b.created_at, b.updated_at " +
            "FROM booking b";

    private final JdbcTemplate jdbcTemplate;
    private final BookingRowMapper bookingRowMapper = new BookingRowMapper();

    public BookingDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Booking> findAll() {
        List<Booking> result = jdbcTemplate.query(SELECT_ALL + " ORDER BY b.id", bookingRowMapper);
        log.debug("BookingDAO.findAll() - trovati {} record", result.size());
        return result;
    }

    public Optional<Booking> findById(Integer id) {
        log.debug("BookingDAO.findById() - id={}", id);
        List<Booking> result = jdbcTemplate.query(SELECT_ALL + " WHERE b.id = ?", bookingRowMapper, id);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public List<Booking> findByTenantId(Integer tenantId) {
        log.debug("BookingDAO.findByTenantId() - tenantId={}", tenantId);
        return jdbcTemplate.query(SELECT_ALL + " WHERE b.fk_tenant_id = ? ORDER BY b.id", bookingRowMapper, tenantId);
    }

    public List<Booking> findByPropertyId(Integer propertyId) {
        log.debug("BookingDAO.findByPropertyId() - propertyId={}", propertyId);
        return jdbcTemplate.query(SELECT_ALL + " WHERE b.fk_property_id = ? ORDER BY b.id", bookingRowMapper, propertyId);
    }

    public List<Booking> findByOwnerId(Integer ownerId) {
        log.debug("BookingDAO.findByOwnerId() - ownerId={}", ownerId);
        String sql = SELECT_ALL + " JOIN property p ON b.fk_property_id = p.id WHERE p.fk_owner_id = ? ORDER BY b.id";
        return jdbcTemplate.query(sql, bookingRowMapper, ownerId);
    }

    public List<Booking> findByOwnerAndTenant(Integer tenantId, Integer ownerId) {
        log.debug("BookingDAO.findByOwnerAndTenant() - tenantId={}, ownerId={}", tenantId, ownerId);
        String sql = SELECT_ALL + " WHERE b.fk_tenant_id = ? AND b.fk_owner_id = ? ORDER BY b.checkout_date DESC";
        return jdbcTemplate.query(sql, bookingRowMapper, tenantId, ownerId);
    }

    public List<Booking> findByStatoPrenotazioneId(Integer statoId) {
        log.debug("BookingDAO.findByStatoPrenotazioneId() - statoId={}", statoId);
        return jdbcTemplate.query(SELECT_ALL + " WHERE b.fk_stato_prenotazione_id = ? ORDER BY b.id", bookingRowMapper, statoId);
    }

    public List<Booking> findByTenantIdAndStatoPrenotazioneId(Integer tenantId, Integer statoId) {
        log.debug("BookingDAO.findByTenantIdAndStatoPrenotazioneId() - tenantId={}, statoId={}", tenantId, statoId);
        String sql = SELECT_ALL + " WHERE b.fk_tenant_id = ? AND b.fk_stato_prenotazione_id = ? ORDER BY b.id";
        return jdbcTemplate.query(sql, bookingRowMapper, tenantId, statoId);
    }

    public List<Booking> findByGuestTaxCode(String taxCode) {
        log.debug("BookingDAO.findByGuestTaxCode() - taxCode={}", taxCode);
        return jdbcTemplate.query(SELECT_ALL + " WHERE b.guest_tax_code = ? ORDER BY b.id", bookingRowMapper, taxCode);
    }

    /**
     * L'id prenotazione esterno è univoco solo dentro la terna del vincolo
     * uq_external_booking (tenant, canale, id esterno): tutti e tre fanno quindi
     * parte della chiave di ricerca.
     *
     * <p>Sul canale il confronto è {@code IS NOT DISTINCT FROM} e non {@code =}:
     * {@code canaleOtaId} può essere null (import V1, dove il lookup del canale non è
     * bloccante) e con l'uguaglianza semplice un parametro null non matcherebbe mai,
     * spegnendo in silenzio il rilevamento dei duplicati. Il CAST serve a dare a
     * PostgreSQL il tipo del parametro quando è null.
     */
    public Optional<Booking> findByExternalBookingId(String externalId, Integer tenantId, Integer canaleOtaId) {
        log.debug("BookingDAO.findByExternalBookingId() - externalId={} tenantId={} canaleOtaId={}",
                externalId, tenantId, canaleOtaId);
        String sql = SELECT_ALL + " WHERE b.external_booking_id = ? AND b.fk_tenant_id = ? " +
                "AND b.fk_canale_ota_id IS NOT DISTINCT FROM CAST(? AS INTEGER)";
        List<Booking> result = jdbcTemplate.query(sql, bookingRowMapper, externalId, tenantId, canaleOtaId);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public List<Booking> findByCheckinDateBetween(LocalDate from, LocalDate to) {
        log.debug("BookingDAO.findByCheckinDateBetween() - from={}, to={}", from, to);
        String sql = SELECT_ALL + " WHERE b.checkin_date BETWEEN ? AND ? ORDER BY b.checkin_date";
        return jdbcTemplate.query(sql, bookingRowMapper, from, to);
    }

    public List<Booking> findByTenantIdAndCheckinDateBetween(Integer tenantId, LocalDate from, LocalDate to) {
        log.debug("BookingDAO.findByTenantIdAndCheckinDateBetween() - tenantId={}, from={}, to={}", tenantId, from, to);
        String sql = SELECT_ALL + " WHERE b.fk_tenant_id = ? AND b.checkin_date BETWEEN ? AND ? ORDER BY b.checkin_date";
        return jdbcTemplate.query(sql, bookingRowMapper, tenantId, from, to);
    }

    public Booking insert(Booking booking) {
        String sql = "INSERT INTO booking (" +
                "fk_tenant_id, fk_property_id, fk_owner_id, fk_canale_ota_id, fk_regime_fiscale_id, " +
                "external_booking_id, guest_name, guest_tax_code, " +
                // Anagrafica ospite: va scritta già in fase di import, non solo con updateGuestData().
                "guest_birth_date, guest_sesso, guest_birth_place, guest_birth_belfiore, " +
                "guest_doc_type, guest_doc_number, guest_country, guest_address, guest_phone, " +
                "checkin_date, checkout_date, nights, guests, " +
                "gross_amount, ota_commission_amount, cleaning_amount, pm_fee_amount, " +
                "owner_net_amount, withholding_amount, aliquota_ritenuta, tourist_tax_amount, " +
                "tourist_tax_included_in_gross, tourist_tax_collection, " +
                "fk_stato_prenotazione_id, payment_status, settlement_status" +
                ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
            ps.setObject(1, booking.getFkTenantId());
            ps.setObject(2, booking.getFkPropertyId());
            ps.setObject(3, booking.getFkOwnerId());
            ps.setObject(4, booking.getFkCanaleOtaId());
            ps.setObject(5, booking.getFkRegimeFiscaleId());
            ps.setObject(6, booking.getExternalBookingId());
            ps.setString(7, booking.getGuestName());
            ps.setObject(8, booking.getGuestTaxCode());
            ps.setObject(9, booking.getGuestBirthDate());
            ps.setObject(10, booking.getGuestSesso());
            ps.setObject(11, booking.getGuestBirthPlace());
            ps.setObject(12, booking.getGuestBirthBelfiore());
            ps.setObject(13, booking.getGuestDocType());
            ps.setObject(14, booking.getGuestDocNumber());
            ps.setObject(15, booking.getGuestCountry());
            ps.setObject(16, booking.getGuestAddress());
            ps.setObject(17, booking.getGuestPhone());
            ps.setObject(18, booking.getCheckinDate());
            ps.setObject(19, booking.getCheckoutDate());
            ps.setObject(20, booking.getNights());
            ps.setObject(21, booking.getGuests());
            ps.setObject(22, booking.getGrossAmount());
            ps.setObject(23, booking.getOtaCommissionAmount());
            ps.setObject(24, booking.getCleaningAmount());
            ps.setObject(25, booking.getPmFeeAmount());
            ps.setObject(26, booking.getOwnerNetAmount());
            ps.setObject(27, booking.getWithholdingAmount());
            ps.setObject(28, booking.getAliquotaRitenuta());
            ps.setObject(29, booking.getTouristTaxAmount());
            ps.setBoolean(30, Boolean.TRUE.equals(booking.getTouristTaxIncludedInGross()));
            ps.setObject(31, booking.getTouristTaxCollection(), Types.OTHER);
            ps.setObject(32, booking.getFkStatoPrenotazioneId());
            ps.setObject(33, booking.getPaymentStatus(), Types.OTHER);
            ps.setObject(34, booking.getSettlementStatus(), Types.OTHER);
            return ps;
        }, keyHolder);
        Integer id = keyHolder.getKey().intValue();
        log.info("BookingDAO.insert() - externalId={} tenantId={}", booking.getExternalBookingId(), booking.getFkTenantId());
        return findById(id).orElseThrow();
    }

    public void deleteById(Integer id) {
        jdbcTemplate.update("DELETE FROM booking WHERE id = ?", id);
        log.info("BookingDAO.deleteById() - id={}", id);
    }

    public void updateTouristTax(Integer id, java.math.BigDecimal touristTaxAmount) {
        jdbcTemplate.update("UPDATE booking SET tourist_tax_amount = ?, updated_at = NOW() WHERE id = ?",
                touristTaxAmount, id);
        log.info("BookingDAO.updateTouristTax() - id={} amount={}", id, touristTaxAmount);
    }

    /**
     * Riscrive in blocco le voci dello split economico dopo un ricalcolo.
     * Il filtro sul tenant fa parte della protezione: id di un altro tenant → 0 righe.
     */
    public int updateSplit(Integer id,
                           Integer tenantId,
                           Boolean touristTaxIncludedInGross,
                           BigDecimal touristTaxAmount,
                           BigDecimal otaCommission,
                           BigDecimal cleaning,
                           BigDecimal pmFee,
                           BigDecimal ownerNet,
                           BigDecimal withholding) {
        String sql = "UPDATE booking SET " +
                "tourist_tax_included_in_gross = ?, " +
                "tourist_tax_amount = ?, " +
                "ota_commission_amount = ?, " +
                "cleaning_amount = ?, " +
                "pm_fee_amount = ?, " +
                "owner_net_amount = ?, " +
                "withholding_amount = ?, " +
                "updated_at = NOW() " +
                "WHERE id = ? AND fk_tenant_id = ?";
        int updated = jdbcTemplate.update(sql,
                Boolean.TRUE.equals(touristTaxIncludedInGross),
                touristTaxAmount, otaCommission, cleaning, pmFee, ownerNet, withholding,
                id, tenantId);
        log.info("BookingDAO.updateSplit() - id={} tenantId={}", id, tenantId);
        return updated;
    }

    public void updateStato(Integer bookingId, Integer fkStatoPrenotazioneId) {
        String sql = "UPDATE booking SET fk_stato_prenotazione_id = ?, updated_at = NOW() WHERE id = ?";
        jdbcTemplate.update(sql, fkStatoPrenotazioneId, bookingId);
        log.info("BookingDAO.updateStato() - bookingId={} stato={}", bookingId, fkStatoPrenotazioneId);
    }

    /** Aggiorna i dati anagrafici dell'ospite. Restituisce il numero di righe modificate (0 se tenant non combacia). */
    public int updateGuestAnagrafica(Integer bookingId, Integer tenantId, Booking g) {
        String sql = "UPDATE booking SET guest_name = ?, guest_tax_code = ?, guest_birth_date = ?, " +
                "guest_sesso = ?, guest_birth_place = ?, guest_birth_belfiore = ?, " +
                "guest_doc_type = ?, guest_doc_number = ?, guest_country = ?, " +
                "guest_address = ?, guest_phone = ?, updated_at = NOW() " +
                "WHERE id = ? AND fk_tenant_id = ?";
        int updated = jdbcTemplate.update(sql,
                g.getGuestName(), g.getGuestTaxCode(), g.getGuestBirthDate(),
                g.getGuestSesso(), g.getGuestBirthPlace(), g.getGuestBirthBelfiore(),
                g.getGuestDocType(), g.getGuestDocNumber(), g.getGuestCountry(),
                g.getGuestAddress(), g.getGuestPhone(),
                bookingId, tenantId);
        log.info("BookingDAO.updateGuestAnagrafica() - bookingId={} tenantId={} updated={}", bookingId, tenantId, updated);
        return updated;
    }

    /** Conta i booking del tenant con CF estero (guest_tax_code LIKE 'EST%') creati nell'anno indicato. */
    public Integer countCfEsteroByTenantAndAnno(Integer tenantId, Integer anno) {
        log.debug("BookingDAO.countCfEsteroByTenantAndAnno() - tenantId={} anno={}", tenantId, anno);
        String sql = "SELECT COUNT(*) FROM booking WHERE fk_tenant_id = ? " +
                "AND EXTRACT(YEAR FROM created_at) = ? AND guest_tax_code LIKE 'EST%'";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, tenantId, anno);
        return count != null ? count : 0;
    }

    public Integer countByTenantIdAndStatoPrenotazioneId(Integer tenantId, Integer statoId) {
        log.debug("BookingDAO.countByTenantIdAndStatoPrenotazioneId() - tenantId={}, statoId={}", tenantId, statoId);
        String sql = "SELECT COUNT(*) FROM booking WHERE fk_tenant_id = ? AND fk_stato_prenotazione_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, tenantId, statoId);
        return count != null ? count : 0;
    }

    /**
     * Prenotazioni con documenti già emessi ('doc_issued') non ancora incluse in alcuna
     * liquidazione: sono quelle che il prossimo calcolo dovrebbe raccogliere.
     * Query esternalizzata in sql/booking/da_liquidare.sql.
     */
    public List<BookingDaLiquidareDTO> findDaLiquidare(Integer tenantId) {
        String sql = loadSql("sql/booking/da_liquidare.sql");
        List<BookingDaLiquidareDTO> result = jdbcTemplate.query(sql, (rs, rowNum) -> {
            BigDecimal canone = rs.getBigDecimal("canone_locazione");
            BigDecimal ritenuta = rs.getBigDecimal("ritenuta_amount");
            if (canone == null) canone = BigDecimal.ZERO;
            if (ritenuta == null) ritenuta = BigDecimal.ZERO;
            return BookingDaLiquidareDTO.builder()
                    .bookingId(rs.getInt("booking_id"))
                    .externalBookingId(rs.getString("external_booking_id"))
                    .ownerName(rs.getString("owner_name"))
                    .propertyName(rs.getString("property_name"))
                    .checkinDate(rs.getObject("checkin_date", LocalDate.class))
                    .checkoutDate(rs.getObject("checkout_date", LocalDate.class))
                    .canoneLocazione(canone)
                    .ritenutaAmount(ritenuta)
                    .nettoProprietario(canone.subtract(ritenuta))
                    .periodoLedger(String.format("%02d/%d",
                            rs.getInt("periodo_mese"), rs.getInt("periodo_anno")))
                    .build();
        }, tenantId);
        log.debug("BookingDAO.findDaLiquidare() - tenantId={} count={}", tenantId, result.size());
        return result;
    }

    /**
     * Id dei booking di un tenant il cui external_booking_id combacia col pattern LIKE.
     * Usata dal cleanup dei test E2E: il filtro sul tenant è parte della protezione.
     */
    public List<Integer> findIdsByExternalIdPattern(Integer tenantId, String pattern) {
        String sql = "SELECT id FROM booking WHERE fk_tenant_id = ? " +
                "AND external_booking_id LIKE ? ORDER BY id";
        List<Integer> ids = jdbcTemplate.queryForList(sql, Integer.class, tenantId, pattern);
        log.debug("BookingDAO.findIdsByExternalIdPattern() - tenantId={} pattern={} trovati={}",
                tenantId, pattern, ids.size());
        return ids;
    }

    private String loadSql(String classpath) {
        try (InputStream is = new ClassPathResource(classpath).getInputStream()) {
            return StreamUtils.copyToString(is, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile caricare SQL: " + classpath, e);
        }
    }
}
