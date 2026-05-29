package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.SettlementRowMapper;
import it.gavia.sostitutoincloud.model.Settlement;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Log4j2
@Repository
public class SettlementDAO {

    private static final String SELECT_ALL =
            "SELECT id, fk_tenant_id, fk_owner_id, period, total_amount, withholding_amount, " +
            "net_amount, stato, payment_date, created_at, updated_at FROM settlement";

    private static final DateTimeFormatter PERIOD_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final JdbcTemplate jdbcTemplate;
    private final SettlementRowMapper settlementRowMapper = new SettlementRowMapper();

    public SettlementDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Settlement> findAll() {
        List<Settlement> result = jdbcTemplate.query(SELECT_ALL + " ORDER BY id", settlementRowMapper);
        log.debug("SettlementDAO.findAll() - trovati {} record", result.size());
        return result;
    }

    public Optional<Settlement> findById(Integer id) {
        log.debug("SettlementDAO.findById() - id={}", id);
        List<Settlement> result = jdbcTemplate.query(SELECT_ALL + " WHERE id = ?", settlementRowMapper, id);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public List<Settlement> findByTenantId(Integer tenantId) {
        log.debug("SettlementDAO.findByTenantId() - tenantId={}", tenantId);
        return jdbcTemplate.query(SELECT_ALL + " WHERE fk_tenant_id = ? ORDER BY period DESC", settlementRowMapper, tenantId);
    }

    public List<Settlement> findByOwnerId(Integer ownerId) {
        log.debug("SettlementDAO.findByOwnerId() - ownerId={}", ownerId);
        return jdbcTemplate.query(SELECT_ALL + " WHERE fk_owner_id = ? ORDER BY period DESC", settlementRowMapper, ownerId);
    }

    public List<Settlement> findByTenantIdAndOwnerId(Integer tenantId, Integer ownerId) {
        log.debug("SettlementDAO.findByTenantIdAndOwnerId() - tenantId={}, ownerId={}", tenantId, ownerId);
        String sql = SELECT_ALL + " WHERE fk_tenant_id = ? AND fk_owner_id = ? ORDER BY period DESC";
        return jdbcTemplate.query(sql, settlementRowMapper, tenantId, ownerId);
    }

    public List<Settlement> findByTenantIdAndStatus(Integer tenantId, String status) {
        log.debug("SettlementDAO.findByTenantIdAndStatus() - tenantId={}, status={}", tenantId, status);
        String sql = SELECT_ALL + " WHERE fk_tenant_id = ? AND stato = ? ORDER BY period DESC";
        return jdbcTemplate.query(sql, settlementRowMapper, tenantId, status);
    }

    /** Trova liquidazioni il cui periodo cade nell'intervallo [from, to] (formato YYYY-MM). */
    public List<Settlement> findByPeriodOverlap(Integer tenantId, Integer ownerId, LocalDate from, LocalDate to) {
        log.debug("SettlementDAO.findByPeriodOverlap() - tenantId={}, ownerId={}, from={}, to={}", tenantId, ownerId, from, to);
        String fromPeriod = from.format(PERIOD_FMT);
        String toPeriod = to.format(PERIOD_FMT);
        String sql = SELECT_ALL + " WHERE fk_tenant_id = ? AND fk_owner_id = ? AND period >= ? AND period <= ? ORDER BY period";
        return jdbcTemplate.query(sql, settlementRowMapper, tenantId, ownerId, fromPeriod, toPeriod);
    }
}
