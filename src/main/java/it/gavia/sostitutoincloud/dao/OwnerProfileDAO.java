package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.OwnerProfileRowMapper;
import it.gavia.sostitutoincloud.model.OwnerProfile;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Log4j2
@Repository
public class OwnerProfileDAO {

    private static final String SELECT_COLS =
            "SELECT id, fk_tenant_id, owner_type, first_name, last_name, legal_name, " +
            "tax_code, vat_number, fk_regime_fiscale_id, email, phone, iban, " +
            "attivo, created_at, updated_at FROM owner_profile";

    private final JdbcTemplate jdbcTemplate;
    private final OwnerProfileRowMapper ownerProfileRowMapper = new OwnerProfileRowMapper();

    public OwnerProfileDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<OwnerProfile> findAll() {
        log.debug("OwnerProfileDAO.findAll()");
        List<OwnerProfile> result = jdbcTemplate.query(SELECT_COLS + " ORDER BY id", ownerProfileRowMapper);
        log.debug("OwnerProfileDAO.findAll() - trovati {} record", result.size());
        return result;
    }

    public Optional<OwnerProfile> findById(Integer id) {
        log.debug("OwnerProfileDAO.findById() - id={}", id);
        List<OwnerProfile> result = jdbcTemplate.query(SELECT_COLS + " WHERE id = ?", ownerProfileRowMapper, id);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public List<OwnerProfile> findByTenantId(Integer tenantId) {
        log.debug("OwnerProfileDAO.findByTenantId() - tenantId={}", tenantId);
        List<OwnerProfile> result = jdbcTemplate.query(
                SELECT_COLS + " WHERE fk_tenant_id = ? ORDER BY id", ownerProfileRowMapper, tenantId);
        log.debug("OwnerProfileDAO.findByTenantId() - trovati {} record", result.size());
        return result;
    }

    public Optional<OwnerProfile> findByTaxCode(String taxCode) {
        log.debug("OwnerProfileDAO.findByTaxCode() - taxCode={}", taxCode);
        List<OwnerProfile> result = jdbcTemplate.query(
                SELECT_COLS + " WHERE tax_code = ?", ownerProfileRowMapper, taxCode);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public List<OwnerProfile> findByAttivo(Boolean attivo) {
        log.debug("OwnerProfileDAO.findByAttivo() - attivo={}", attivo);
        List<OwnerProfile> result = jdbcTemplate.query(
                SELECT_COLS + " WHERE attivo = ? ORDER BY id", ownerProfileRowMapper, attivo);
        log.debug("OwnerProfileDAO.findByAttivo() - trovati {} record", result.size());
        return result;
    }

    public List<OwnerProfile> findByTenantIdAndAttivo(Integer tenantId, Boolean attivo) {
        log.debug("OwnerProfileDAO.findByTenantIdAndAttivo() - tenantId={}, attivo={}", tenantId, attivo);
        List<OwnerProfile> result = jdbcTemplate.query(
                SELECT_COLS + " WHERE fk_tenant_id = ? AND attivo = ? ORDER BY id",
                ownerProfileRowMapper, tenantId, attivo);
        log.debug("OwnerProfileDAO.findByTenantIdAndAttivo() - trovati {} record", result.size());
        return result;
    }
}
