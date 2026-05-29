package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.CuRecordRowMapper;
import it.gavia.sostitutoincloud.model.CuRecord;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Log4j2
@Repository
public class CuRecordDAO {

    private static final String SELECT_ALL =
            "SELECT id, fk_tenant_id, fk_owner_id, tax_year, total_compensi, total_ritenute, " +
            "stato, generated_at, created_at, updated_at FROM cu_record";

    private final JdbcTemplate jdbcTemplate;
    private final CuRecordRowMapper cuRecordRowMapper = new CuRecordRowMapper();

    public CuRecordDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CuRecord> findAll() {
        List<CuRecord> result = jdbcTemplate.query(SELECT_ALL + " ORDER BY id", cuRecordRowMapper);
        log.debug("CuRecordDAO.findAll() - trovati {} record", result.size());
        return result;
    }

    public Optional<CuRecord> findById(Integer id) {
        log.debug("CuRecordDAO.findById() - id={}", id);
        List<CuRecord> result = jdbcTemplate.query(SELECT_ALL + " WHERE id = ?", cuRecordRowMapper, id);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public List<CuRecord> findByTenantId(Integer tenantId) {
        log.debug("CuRecordDAO.findByTenantId() - tenantId={}", tenantId);
        return jdbcTemplate.query(SELECT_ALL + " WHERE fk_tenant_id = ? ORDER BY tax_year DESC", cuRecordRowMapper, tenantId);
    }

    public List<CuRecord> findByOwnerId(Integer ownerId) {
        log.debug("CuRecordDAO.findByOwnerId() - ownerId={}", ownerId);
        return jdbcTemplate.query(SELECT_ALL + " WHERE fk_owner_id = ? ORDER BY tax_year DESC", cuRecordRowMapper, ownerId);
    }

    public List<CuRecord> findByTenantIdAndOwnerId(Integer tenantId, Integer ownerId) {
        log.debug("CuRecordDAO.findByTenantIdAndOwnerId() - tenantId={}, ownerId={}", tenantId, ownerId);
        String sql = SELECT_ALL + " WHERE fk_tenant_id = ? AND fk_owner_id = ? ORDER BY tax_year DESC";
        return jdbcTemplate.query(sql, cuRecordRowMapper, tenantId, ownerId);
    }

    public List<CuRecord> findByTenantIdAndTaxYear(Integer tenantId, Integer taxYear) {
        log.debug("CuRecordDAO.findByTenantIdAndTaxYear() - tenantId={}, taxYear={}", tenantId, taxYear);
        String sql = SELECT_ALL + " WHERE fk_tenant_id = ? AND tax_year = ? ORDER BY id";
        return jdbcTemplate.query(sql, cuRecordRowMapper, tenantId, taxYear);
    }

    public List<CuRecord> findByTenantIdAndStatus(Integer tenantId, String status) {
        log.debug("CuRecordDAO.findByTenantIdAndStatus() - tenantId={}, status={}", tenantId, status);
        String sql = SELECT_ALL + " WHERE fk_tenant_id = ? AND stato = ? ORDER BY tax_year DESC";
        return jdbcTemplate.query(sql, cuRecordRowMapper, tenantId, status);
    }
}
