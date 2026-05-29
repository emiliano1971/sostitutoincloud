package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.PropertyRowMapper;
import it.gavia.sostitutoincloud.model.Property;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Log4j2
@Repository
public class PropertyDAO {

    private static final String SELECT_ALL =
            "SELECT id, fk_tenant_id, fk_owner_id, fk_pm_user_id, fk_tipo_immobile_id, " +
            "internal_code, display_name, address, city, region, cin_code, attivo, " +
            "created_at, updated_at FROM property";

    private final JdbcTemplate jdbcTemplate;
    private final PropertyRowMapper propertyRowMapper = new PropertyRowMapper();

    public PropertyDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Property> findAll() {
        List<Property> result = jdbcTemplate.query(SELECT_ALL + " ORDER BY id", propertyRowMapper);
        log.debug("PropertyDAO.findAll() - trovati {} record", result.size());
        return result;
    }

    public Optional<Property> findById(Integer id) {
        log.debug("PropertyDAO.findById() - id={}", id);
        List<Property> result = jdbcTemplate.query(SELECT_ALL + " WHERE id = ?", propertyRowMapper, id);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public List<Property> findByTenantId(Integer tenantId) {
        log.debug("PropertyDAO.findByTenantId() - tenantId={}", tenantId);
        return jdbcTemplate.query(SELECT_ALL + " WHERE fk_tenant_id = ? ORDER BY id", propertyRowMapper, tenantId);
    }

    public List<Property> findByOwnerId(Integer ownerId) {
        log.debug("PropertyDAO.findByOwnerId() - ownerId={}", ownerId);
        return jdbcTemplate.query(SELECT_ALL + " WHERE fk_owner_id = ? ORDER BY id", propertyRowMapper, ownerId);
    }

    public List<Property> findByPmUserId(Integer pmUserId) {
        log.debug("PropertyDAO.findByPmUserId() - pmUserId={}", pmUserId);
        return jdbcTemplate.query(SELECT_ALL + " WHERE fk_pm_user_id = ? ORDER BY id", propertyRowMapper, pmUserId);
    }

    public List<Property> findByAttivo(Boolean attivo) {
        log.debug("PropertyDAO.findByAttivo() - attivo={}", attivo);
        return jdbcTemplate.query(SELECT_ALL + " WHERE attivo = ? ORDER BY id", propertyRowMapper, attivo);
    }

    public List<Property> findByTenantIdAndAttivo(Integer tenantId, Boolean attivo) {
        log.debug("PropertyDAO.findByTenantIdAndAttivo() - tenantId={}, attivo={}", tenantId, attivo);
        return jdbcTemplate.query(SELECT_ALL + " WHERE fk_tenant_id = ? AND attivo = ? ORDER BY id",
                propertyRowMapper, tenantId, attivo);
    }

    public Optional<Property> findByCinCode(String cinCode) {
        log.debug("PropertyDAO.findByCinCode() - cinCode={}", cinCode);
        List<Property> result = jdbcTemplate.query(SELECT_ALL + " WHERE cin_code = ?", propertyRowMapper, cinCode);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }
}
