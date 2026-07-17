package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.PropertyContractRuleRowMapper;
import it.gavia.sostitutoincloud.model.PropertyContractRule;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;

@Log4j2
@Repository
public class PropertyContractRuleDAO {

    private static final String SELECT_ALL =
            "SELECT id, fk_property_id, fk_tenant_id, fk_canale_ota_id, tipo, calc_mode, " +
            "valore, is_remainder, ordine, attivo, created_at, updated_at " +
            "FROM property_contract_rule";

    private final JdbcTemplate jdbcTemplate;
    private final PropertyContractRuleRowMapper rowMapper = new PropertyContractRuleRowMapper();

    public PropertyContractRuleDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<PropertyContractRule> findByPropertyId(Integer propertyId) {
        log.debug("PropertyContractRuleDAO.findByPropertyId() - propertyId={}", propertyId);
        List<PropertyContractRule> result = jdbcTemplate.query(
                SELECT_ALL + " WHERE fk_property_id = ? AND attivo = TRUE ORDER BY ordine ASC, id ASC",
                rowMapper, propertyId);
        log.debug("PropertyContractRuleDAO.findByPropertyId() - trovate {} regole", result.size());
        return result;
    }

    public PropertyContractRule findById(Integer id) {
        log.debug("PropertyContractRuleDAO.findById() - id={}", id);
        List<PropertyContractRule> result = jdbcTemplate.query(
                SELECT_ALL + " WHERE id = ?", rowMapper, id);
        if (result.isEmpty()) {
            throw new RuntimeException("Regola contratto non trovata: id=" + id);
        }
        return result.get(0);
    }

    public PropertyContractRule insert(PropertyContractRule rule) {
        String sql = "INSERT INTO property_contract_rule " +
                     "(fk_property_id, fk_tenant_id, fk_canale_ota_id, tipo, calc_mode, " +
                     "valore, is_remainder, ordine, attivo) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
            ps.setObject(1, rule.getFkPropertyId());
            ps.setObject(2, rule.getFkTenantId());
            ps.setObject(3, rule.getFkCanaleOtaId());
            ps.setString(4, rule.getTipo());
            ps.setString(5, rule.getCalcMode());
            ps.setObject(6, rule.getValore());
            ps.setBoolean(7, Boolean.TRUE.equals(rule.getIsRemainder()));
            ps.setObject(8, rule.getOrdine() != null ? rule.getOrdine() : 0);
            ps.setBoolean(9, rule.getAttivo() == null || Boolean.TRUE.equals(rule.getAttivo()));
            return ps;
        }, keyHolder);
        Integer id = keyHolder.getKey().intValue();
        log.info("PropertyContractRuleDAO.insert() - id={} propertyId={} tipo={}",
                id, rule.getFkPropertyId(), rule.getTipo());
        return findById(id);
    }

    public PropertyContractRule update(PropertyContractRule rule) {
        log.info("PropertyContractRuleDAO.update() - id={}", rule.getId());
        jdbcTemplate.update(
                "UPDATE property_contract_rule SET tipo = ?, calc_mode = ?, valore = ?, " +
                "is_remainder = ?, fk_canale_ota_id = ?, ordine = ?, updated_at = NOW() WHERE id = ?",
                rule.getTipo(),
                rule.getCalcMode(),
                rule.getValore(),
                Boolean.TRUE.equals(rule.getIsRemainder()),
                rule.getFkCanaleOtaId(),
                rule.getOrdine() != null ? rule.getOrdine() : 0,
                rule.getId());
        return findById(rule.getId());
    }

    public void delete(Integer id) {
        log.info("PropertyContractRuleDAO.delete() - id={}", id);
        jdbcTemplate.update("DELETE FROM property_contract_rule WHERE id = ?", id);
    }
}
