package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.SettlementBookingRowMapper;
import it.gavia.sostitutoincloud.model.SettlementBooking;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Log4j2
@Repository
public class SettlementBookingDAO {

    private static final String SELECT_ALL =
            "SELECT id, fk_settlement_id, fk_booking_id, created_at FROM settlement_booking";

    private final JdbcTemplate jdbcTemplate;
    private final SettlementBookingRowMapper rowMapper = new SettlementBookingRowMapper();

    public SettlementBookingDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SettlementBooking> findBySettlementId(Integer settlementId) {
        log.debug("SettlementBookingDAO.findBySettlementId() - settlementId={}", settlementId);
        return jdbcTemplate.query(SELECT_ALL + " WHERE fk_settlement_id = ? ORDER BY id", rowMapper, settlementId);
    }

    public List<SettlementBooking> findByBookingId(Integer bookingId) {
        log.debug("SettlementBookingDAO.findByBookingId() - bookingId={}", bookingId);
        return jdbcTemplate.query(SELECT_ALL + " WHERE fk_booking_id = ? ORDER BY id", rowMapper, bookingId);
    }

    public void insert(Integer settlementId, Integer bookingId) {
        log.debug("SettlementBookingDAO.insert() - settlementId={}, bookingId={}", settlementId, bookingId);
        jdbcTemplate.update("INSERT INTO settlement_booking (fk_settlement_id, fk_booking_id) VALUES (?, ?)",
                settlementId, bookingId);
    }

    public void deleteBySettlementId(Integer settlementId) {
        log.debug("SettlementBookingDAO.deleteBySettlementId() - settlementId={}", settlementId);
        jdbcTemplate.update("DELETE FROM settlement_booking WHERE fk_settlement_id = ?", settlementId);
    }

    public void deleteByBookingId(Integer bookingId) {
        log.debug("SettlementBookingDAO.deleteByBookingId() - bookingId={}", bookingId);
        jdbcTemplate.update("DELETE FROM settlement_booking WHERE fk_booking_id = ?", bookingId);
    }

    public boolean existsBySettlementIdAndBookingId(Integer settlementId, Integer bookingId) {
        log.debug("SettlementBookingDAO.existsBySettlementIdAndBookingId() - settlementId={}, bookingId={}", settlementId, bookingId);
        String sql = "SELECT COUNT(*) FROM settlement_booking WHERE fk_settlement_id = ? AND fk_booking_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, settlementId, bookingId);
        return count != null && count > 0;
    }
}
