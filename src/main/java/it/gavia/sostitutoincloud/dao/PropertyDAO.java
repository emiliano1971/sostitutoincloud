package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.PropertyRowMapper;
import it.gavia.sostitutoincloud.model.Property;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;

@Log4j2
@Repository
public class PropertyDAO {

    private static final String SELECT_ALL =
            "SELECT id, fk_tenant_id, fk_owner_id, fk_pm_user_id, fk_tipo_immobile_id, " +
            "internal_code, display_name, address, city, region, cin_code, attivo, " +
            "primo_immobile, created_at, updated_at FROM property";

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

    public int countActiveByOwner(Integer ownerId, Integer tenantId) {
        log.debug("PropertyDAO.countActiveByOwner() - ownerId={} tenantId={}", ownerId, tenantId);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM property WHERE fk_owner_id = ? AND fk_tenant_id = ? AND attivo = TRUE",
                Integer.class, ownerId, tenantId);
        return count != null ? count : 0;
    }

    public Property insert(Property property) {
        String sql = "INSERT INTO property " +
                     "(fk_tenant_id, fk_owner_id, fk_pm_user_id, fk_tipo_immobile_id, " +
                     "internal_code, display_name, address, city, region, cin_code, attivo, primo_immobile) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
            ps.setObject(1, property.getFkTenantId());
            ps.setObject(2, property.getFkOwnerId());
            ps.setObject(3, property.getFkPmUserId());
            ps.setObject(4, property.getFkTipoImmobileId());
            ps.setString(5, property.getInternalCode());
            ps.setString(6, property.getDisplayName());
            ps.setObject(7, property.getAddress());
            ps.setString(8, property.getCity());
            ps.setObject(9, property.getRegion());
            ps.setObject(10, property.getCinCode());
            ps.setBoolean(11, Boolean.TRUE.equals(property.getAttivo()));
            ps.setBoolean(12, Boolean.TRUE.equals(property.getPrimoImmobile()));
            return ps;
        }, keyHolder);
        Integer id = keyHolder.getKey().intValue();
        log.info("PropertyDAO.insert() - id={}", id);
        return findById(id).orElseThrow();
    }

    public Property updateStatus(Integer id, Boolean attivo) {
        log.info("PropertyDAO.updateStatus() - id={} attivo={}", id, attivo);
        jdbcTemplate.update(
                "UPDATE property SET attivo = ?, updated_at = NOW() WHERE id = ?",
                attivo, id);
        return findById(id).orElseThrow();
    }

    public Property updatePrimoImmobile(Integer id, Boolean primoImmobile) {
        log.info("PropertyDAO.updatePrimoImmobile() - id={} primoImmobile={}", id, primoImmobile);
        jdbcTemplate.update(
                "UPDATE property SET primo_immobile = ?, updated_at = NOW() WHERE id = ?",
                Boolean.TRUE.equals(primoImmobile), id);
        return findById(id).orElseThrow();
    }

    public Property updateOwner(Integer id, Integer fkOwnerId) {
        log.info("PropertyDAO.updateOwner() - id={} fkOwnerId={}", id, fkOwnerId);
        jdbcTemplate.update(
                "UPDATE property SET fk_owner_id = ?, updated_at = NOW() WHERE id = ?",
                fkOwnerId, id);
        return findById(id).orElseThrow();
    }

    public Property update(Property property) {
        log.info("PropertyDAO.update() - id={}", property.getId());
        jdbcTemplate.update(
                "UPDATE property SET display_name = ?, internal_code = ?, address = ?, city = ?, " +
                "region = ?, cin_code = ?, fk_tipo_immobile_id = ?, updated_at = NOW() " +
                "WHERE id = ? AND fk_tenant_id = ?",
                property.getDisplayName(),
                property.getInternalCode(),
                property.getAddress() != null ? property.getAddress() : "",
                property.getCity(),
                property.getRegion() != null ? property.getRegion() : "",
                property.getCinCode(),
                property.getFkTipoImmobileId(),
                property.getId(),
                property.getFkTenantId());
        return findById(property.getId()).orElseThrow();
    }
}
