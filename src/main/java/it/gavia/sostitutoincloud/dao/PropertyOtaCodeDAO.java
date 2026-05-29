package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.PropertyOtaCodeRowMapper;
import it.gavia.sostitutoincloud.model.PropertyOtaCode;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Log4j2
@Repository
public class PropertyOtaCodeDAO {

    private static final String SELECT_ALL =
            "SELECT id, fk_property_id, fk_canale_ota_id, external_id, created_at, updated_at " +
            "FROM property_ota_code";

    private final JdbcTemplate jdbcTemplate;
    private final PropertyOtaCodeRowMapper rowMapper = new PropertyOtaCodeRowMapper();

    public PropertyOtaCodeDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<PropertyOtaCode> findByPropertyId(Integer propertyId) {
        log.debug("PropertyOtaCodeDAO.findByPropertyId() - propertyId={}", propertyId);
        return jdbcTemplate.query(SELECT_ALL + " WHERE fk_property_id = ? ORDER BY id", rowMapper, propertyId);
    }

    public List<PropertyOtaCode> findByCanaleOtaId(Integer canaleOtaId) {
        log.debug("PropertyOtaCodeDAO.findByCanaleOtaId() - canaleOtaId={}", canaleOtaId);
        return jdbcTemplate.query(SELECT_ALL + " WHERE fk_canale_ota_id = ? ORDER BY id", rowMapper, canaleOtaId);
    }

    public Optional<PropertyOtaCode> findByPropertyIdAndCanaleOtaId(Integer propertyId, Integer canaleOtaId) {
        log.debug("PropertyOtaCodeDAO.findByPropertyIdAndCanaleOtaId() - propertyId={}, canaleOtaId={}", propertyId, canaleOtaId);
        List<PropertyOtaCode> result = jdbcTemplate.query(
                SELECT_ALL + " WHERE fk_property_id = ? AND fk_canale_ota_id = ?",
                rowMapper, propertyId, canaleOtaId);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }
}
