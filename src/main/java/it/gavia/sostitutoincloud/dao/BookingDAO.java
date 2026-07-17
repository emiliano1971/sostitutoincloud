package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.BookingRowMapper;
import it.gavia.sostitutoincloud.model.Booking;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Log4j2
@Repository
public class BookingDAO {

    private static final String SELECT_ALL =
            "SELECT b.id, b.fk_tenant_id, b.fk_property_id, b.fk_owner_id, b.fk_canale_ota_id, b.fk_scenario_fiscale_id, " +
            "b.external_booking_id, b.guest_name, b.guest_tax_code, b.checkin_date, b.checkout_date, " +
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

    public Optional<Booking> findByExternalBookingId(String externalId) {
        log.debug("BookingDAO.findByExternalBookingId() - externalId={}", externalId);
        List<Booking> result = jdbcTemplate.query(SELECT_ALL + " WHERE b.external_booking_id = ?", bookingRowMapper, externalId);
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
                "fk_tenant_id, fk_property_id, fk_owner_id, fk_canale_ota_id, fk_scenario_fiscale_id, " +
                "external_booking_id, guest_name, guest_tax_code, " +
                "checkin_date, checkout_date, nights, guests, " +
                "gross_amount, ota_commission_amount, cleaning_amount, pm_fee_amount, " +
                "owner_net_amount, withholding_amount, aliquota_ritenuta, tourist_tax_amount, " +
                "tourist_tax_included_in_gross, tourist_tax_collection, " +
                "fk_stato_prenotazione_id, payment_status, settlement_status" +
                ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
            ps.setObject(1, booking.getFkTenantId());
            ps.setObject(2, booking.getFkPropertyId());
            ps.setObject(3, booking.getFkOwnerId());
            ps.setObject(4, booking.getFkCanaleOtaId());
            ps.setObject(5, booking.getFkScenarioFiscaleId());
            ps.setObject(6, booking.getExternalBookingId());
            ps.setString(7, booking.getGuestName());
            ps.setObject(8, booking.getGuestTaxCode());
            ps.setObject(9, booking.getCheckinDate());
            ps.setObject(10, booking.getCheckoutDate());
            ps.setObject(11, booking.getNights());
            ps.setObject(12, booking.getGuests());
            ps.setObject(13, booking.getGrossAmount());
            ps.setObject(14, booking.getOtaCommissionAmount());
            ps.setObject(15, booking.getCleaningAmount());
            ps.setObject(16, booking.getPmFeeAmount());
            ps.setObject(17, booking.getOwnerNetAmount());
            ps.setObject(18, booking.getWithholdingAmount());
            ps.setObject(19, booking.getAliquotaRitenuta());
            ps.setObject(20, booking.getTouristTaxAmount());
            ps.setBoolean(21, Boolean.TRUE.equals(booking.getTouristTaxIncludedInGross()));
            ps.setObject(22, booking.getTouristTaxCollection(), Types.OTHER);
            ps.setObject(23, booking.getFkStatoPrenotazioneId());
            ps.setObject(24, booking.getPaymentStatus(), Types.OTHER);
            ps.setObject(25, booking.getSettlementStatus(), Types.OTHER);
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

    public void updateStato(Integer bookingId, Integer fkStatoPrenotazioneId) {
        String sql = "UPDATE booking SET fk_stato_prenotazione_id = ?, updated_at = NOW() WHERE id = ?";
        jdbcTemplate.update(sql, fkStatoPrenotazioneId, bookingId);
        log.info("BookingDAO.updateStato() - bookingId={} stato={}", bookingId, fkStatoPrenotazioneId);
    }

    public Integer countByTenantIdAndStatoPrenotazioneId(Integer tenantId, Integer statoId) {
        log.debug("BookingDAO.countByTenantIdAndStatoPrenotazioneId() - tenantId={}, statoId={}", tenantId, statoId);
        String sql = "SELECT COUNT(*) FROM booking WHERE fk_tenant_id = ? AND fk_stato_prenotazione_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, tenantId, statoId);
        return count != null ? count : 0;
    }
}
