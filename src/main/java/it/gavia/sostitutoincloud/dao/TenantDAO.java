package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.TenantRowMapper;
import it.gavia.sostitutoincloud.model.Tenant;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Log4j2
@Repository
public class TenantDAO {

    private final JdbcTemplate jdbcTemplate;
    private final TenantRowMapper tenantRowMapper = new TenantRowMapper();

    public TenantDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Tenant> findAll() {
        String sql = "SELECT id, legal_name, display_name, tax_code, vat_number, stato, " +
                     "administrative_email, pec, phone, legal_address, " +
                     "activated_at, created_at, updated_at " +
                     "FROM tenant ORDER BY id";
        List<Tenant> result = jdbcTemplate.query(sql, tenantRowMapper);
        log.debug("TenantDAO.findAll() - trovati {} record", result.size());
        return result;
    }

    public Optional<Tenant> findById(Integer id) {
        log.debug("TenantDAO.findById() - id={}", id);
        String sql = "SELECT id, legal_name, display_name, tax_code, vat_number, stato, " +
                     "administrative_email, pec, phone, legal_address, " +
                     "activated_at, created_at, updated_at " +
                     "FROM tenant WHERE id = ?";
        List<Tenant> result = jdbcTemplate.query(sql, tenantRowMapper, id);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public Optional<Tenant> findByEmail(String email) {
        log.debug("TenantDAO.findByEmail() - email={}", email);
        String sql = "SELECT id, legal_name, display_name, tax_code, vat_number, stato, " +
                     "administrative_email, pec, phone, legal_address, " +
                     "activated_at, created_at, updated_at " +
                     "FROM tenant WHERE administrative_email = ?";
        List<Tenant> result = jdbcTemplate.query(sql, tenantRowMapper, email);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public boolean existsById(Integer id) {
        log.debug("TenantDAO.existsById() - id={}", id);
        String sql = "SELECT COUNT(*) FROM tenant WHERE id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, id);
        return count != null && count > 0;
    }
}
