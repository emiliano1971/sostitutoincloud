package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.OwnerProfileRowMapper;
import it.gavia.sostitutoincloud.model.OwnerProfile;
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

    public OwnerProfile insert(OwnerProfile owner) {
        String sql = "INSERT INTO owner_profile " +
                     "(fk_tenant_id, owner_type, first_name, last_name, legal_name, " +
                     "tax_code, vat_number, fk_regime_fiscale_id, email, phone, iban, attivo) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
            ps.setObject(1, owner.getFkTenantId());
            ps.setObject(2, owner.getOwnerType(), Types.OTHER);
            ps.setObject(3, owner.getFirstName());
            ps.setObject(4, owner.getLastName());
            ps.setObject(5, owner.getLegalName());
            ps.setString(6, owner.getTaxCode());
            ps.setObject(7, owner.getVatNumber());
            ps.setObject(8, owner.getFkRegimeFiscaleId());
            ps.setObject(9, owner.getEmail());
            ps.setObject(10, owner.getPhone());
            ps.setObject(11, owner.getIban());
            ps.setBoolean(12, Boolean.TRUE.equals(owner.getAttivo()));
            return ps;
        }, keyHolder);
        Integer id = keyHolder.getKey().intValue();
        log.info("OwnerProfileDAO.insert() - id={}", id);
        return findById(id).orElseThrow();
    }

    public OwnerProfile updateStatus(Integer id, Boolean attivo) {
        log.info("OwnerProfileDAO.updateStatus() - id={} attivo={}", id, attivo);
        jdbcTemplate.update(
                "UPDATE owner_profile SET attivo = ?, updated_at = NOW() WHERE id = ?",
                attivo, id);
        return findById(id).orElseThrow();
    }

    public OwnerProfile updateFkRegimeFiscale(Integer id, Integer fkRegimeFiscaleId) {
        log.info("OwnerProfileDAO.updateFkRegimeFiscale() - id={} fkRegimeFiscaleId={}", id, fkRegimeFiscaleId);
        jdbcTemplate.update(
                "UPDATE owner_profile SET fk_regime_fiscale_id = ?, updated_at = NOW() WHERE id = ?",
                fkRegimeFiscaleId, id);
        return findById(id).orElseThrow();
    }

    public OwnerProfile updateAnagrafica(OwnerProfile owner) {
        log.info("OwnerProfileDAO.updateAnagrafica() - id={}", owner.getId());
        String sql = "UPDATE owner_profile SET " +
                     "owner_type = ?, first_name = ?, last_name = ?, legal_name = ?, " +
                     "tax_code = ?, vat_number = ?, fk_regime_fiscale_id = ?, " +
                     "email = ?, phone = ?, iban = ?, updated_at = NOW() " +
                     "WHERE id = ?";
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql);
            ps.setObject(1, owner.getOwnerType(), Types.OTHER);
            ps.setObject(2, owner.getFirstName());
            ps.setObject(3, owner.getLastName());
            ps.setObject(4, owner.getLegalName());
            ps.setString(5, owner.getTaxCode());
            ps.setObject(6, owner.getVatNumber());
            ps.setObject(7, owner.getFkRegimeFiscaleId());
            ps.setObject(8, owner.getEmail());
            ps.setObject(9, owner.getPhone());
            ps.setObject(10, owner.getIban());
            ps.setObject(11, owner.getId());
            return ps;
        });
        return findById(owner.getId()).orElseThrow();
    }
}
