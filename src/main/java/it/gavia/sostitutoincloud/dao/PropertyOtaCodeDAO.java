package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.PropertyOtaCodeRowMapper;
import it.gavia.sostitutoincloud.model.PropertyOtaCode;
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

    public PropertyOtaCode insert(PropertyOtaCode otaCode) {
        String sql = "INSERT INTO property_ota_code (fk_property_id, fk_canale_ota_id, external_id) " +
                     "VALUES (?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
            ps.setObject(1, otaCode.getFkPropertyId());
            ps.setObject(2, otaCode.getFkCanaleOtaId());
            ps.setString(3, otaCode.getExternalId());
            return ps;
        }, keyHolder);
        Integer id = keyHolder.getKey().intValue();
        log.info("PropertyOtaCodeDAO.insert() - id={}", id);
        otaCode.setId(id);
        return otaCode;
    }

    public void deleteByPropertyId(Integer propertyId) {
        log.info("PropertyOtaCodeDAO.deleteByPropertyId() - propertyId={}", propertyId);
        jdbcTemplate.update("DELETE FROM property_ota_code WHERE fk_property_id = ?", propertyId);
    }

    /**
     * Risolve l'immobile a partire dal nome canale OTA (ORIGINE) e dal codice esterno
     * struttura (STRUTTURA), filtrando per tenant. Match case-insensitive (ILIKE).
     */
    public Optional<PropertyOtaMatch> matchByCanaleNomeAndExternalId(String canaleNome, String externalId, Integer tenantId) {
        log.debug("PropertyOtaCodeDAO.matchByCanaleNomeAndExternalId() - canale={}, externalId={}, tenant={}",
                canaleNome, externalId, tenantId);
        String sql = "SELECT p.id AS property_id, p.display_name AS property_name, " +
                "c.id AS canale_id, c.nome AS canale_nome " +
                "FROM property p " +
                "JOIN property_ota_code poc ON poc.fk_property_id = p.id " +
                "JOIN canale_ota c ON c.id = poc.fk_canale_ota_id " +
                "WHERE c.nome ILIKE ? AND poc.external_id ILIKE ? AND p.fk_tenant_id = ?";
        List<PropertyOtaMatch> result = jdbcTemplate.query(sql, (rs, rowNum) -> new PropertyOtaMatch(
                rs.getInt("property_id"),
                rs.getString("property_name"),
                rs.getInt("canale_id"),
                rs.getString("canale_nome")
        ), canaleNome, externalId, tenantId);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    /** Proiezione del match immobile/canale per l'import V2. */
    public record PropertyOtaMatch(Integer propertyId, String propertyName, Integer canaleId, String canaleNome) {}
}
