package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.F24RecordRowMapper;
import it.gavia.sostitutoincloud.model.F24Record;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Log4j2
@Repository
public class F24RecordDAO {

    private static final String SELECT_ALL =
            "SELECT id, fk_tenant_id, fk_codice_tributo_id, period, total_amount, withholdings_count, " +
            "stato, deadline_date, payment_date, created_at, updated_at FROM f24_record";

    private final JdbcTemplate jdbcTemplate;
    private final F24RecordRowMapper f24RecordRowMapper = new F24RecordRowMapper();

    public F24RecordDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<F24Record> findAll() {
        List<F24Record> result = jdbcTemplate.query(SELECT_ALL + " ORDER BY id", f24RecordRowMapper);
        log.debug("F24RecordDAO.findAll() - trovati {} record", result.size());
        return result;
    }

    public Optional<F24Record> findById(Integer id) {
        log.debug("F24RecordDAO.findById() - id={}", id);
        List<F24Record> result = jdbcTemplate.query(SELECT_ALL + " WHERE id = ?", f24RecordRowMapper, id);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public List<F24Record> findByTenantId(Integer tenantId) {
        log.debug("F24RecordDAO.findByTenantId() - tenantId={}", tenantId);
        return jdbcTemplate.query(SELECT_ALL + " WHERE fk_tenant_id = ? ORDER BY period DESC", f24RecordRowMapper, tenantId);
    }

    public List<F24Record> findByTenantIdAndPeriod(Integer tenantId, String period) {
        log.debug("F24RecordDAO.findByTenantIdAndPeriod() - tenantId={}, period={}", tenantId, period);
        String sql = SELECT_ALL + " WHERE fk_tenant_id = ? AND period = ? ORDER BY id";
        return jdbcTemplate.query(sql, f24RecordRowMapper, tenantId, period);
    }

    public List<F24Record> findByTenantIdAndStatus(Integer tenantId, String status) {
        log.debug("F24RecordDAO.findByTenantIdAndStatus() - tenantId={}, status={}", tenantId, status);
        String sql = SELECT_ALL + " WHERE fk_tenant_id = ? AND stato = ? ORDER BY period DESC";
        return jdbcTemplate.query(sql, f24RecordRowMapper, tenantId, status);
    }
}
