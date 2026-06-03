package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.TenantRowMapper;
import it.gavia.sostitutoincloud.model.Tenant;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Types;
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

    public Optional<Tenant> findByTaxCode(String taxCode) {
        log.debug("TenantDAO.findByTaxCode() - taxCode={}", taxCode);
        String sql = "SELECT id, legal_name, display_name, tax_code, vat_number, stato, " +
                     "administrative_email, pec, phone, legal_address, " +
                     "activated_at, created_at, updated_at " +
                     "FROM tenant WHERE tax_code = ?";
        List<Tenant> result = jdbcTemplate.query(sql, tenantRowMapper, taxCode);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public Tenant insert(Tenant tenant) {
        String sql = "INSERT INTO tenant " +
                     "(legal_name, display_name, tax_code, vat_number, stato, " +
                     "administrative_email, pec, phone, legal_address) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
            ps.setString(1, tenant.getLegalName());
            ps.setString(2, tenant.getDisplayName());
            ps.setString(3, tenant.getTaxCode());
            ps.setObject(4, tenant.getVatNumber());
            ps.setObject(5, tenant.getStato(), Types.OTHER);
            ps.setString(6, tenant.getAdministrativeEmail());
            ps.setObject(7, tenant.getPec());
            ps.setObject(8, tenant.getPhone());
            ps.setString(9, tenant.getLegalAddress());
            return ps;
        }, keyHolder);
        Integer id = keyHolder.getKey().intValue();
        log.info("TenantDAO.insert() - id={}", id);
        return findById(id).orElseThrow();
    }

    public Tenant updateStatus(Integer id, String nuovoStato) {
        log.info("TenantDAO.updateStatus() - id={} stato={}", id, nuovoStato);
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "UPDATE tenant SET stato = ?, updated_at = NOW() WHERE id = ?");
            ps.setObject(1, nuovoStato, Types.OTHER);
            ps.setObject(2, id);
            return ps;
        });
        return findById(id).orElseThrow();
    }

    public Tenant update(Tenant tenant) {
        log.info("TenantDAO.update() - id={}", tenant.getId());
        String sql = "UPDATE tenant SET legal_name = ?, display_name = ?, tax_code = ?, " +
                     "vat_number = ?, administrative_email = ?, pec = ?, phone = ?, " +
                     "legal_address = ?, updated_at = NOW() WHERE id = ?";
        jdbcTemplate.update(sql,
                tenant.getLegalName(),
                tenant.getDisplayName(),
                tenant.getTaxCode(),
                tenant.getVatNumber(),
                tenant.getAdministrativeEmail(),
                tenant.getPec(),
                tenant.getPhone(),
                tenant.getLegalAddress(),
                tenant.getId());
        return findById(tenant.getId()).orElseThrow();
    }
}
