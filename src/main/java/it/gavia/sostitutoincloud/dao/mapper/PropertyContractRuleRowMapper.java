package it.gavia.sostitutoincloud.dao.mapper;

import it.gavia.sostitutoincloud.model.PropertyContractRule;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

public class PropertyContractRuleRowMapper implements RowMapper<PropertyContractRule> {

    @Override
    public PropertyContractRule mapRow(ResultSet rs, int rowNum) throws SQLException {
        Integer fkCanaleOtaId = (Integer) rs.getObject("fk_canale_ota_id");
        return PropertyContractRule.builder()
                .id(rs.getInt("id"))
                .fkPropertyId(rs.getInt("fk_property_id"))
                .fkTenantId(rs.getInt("fk_tenant_id"))
                .fkCanaleOtaId(fkCanaleOtaId)
                .tipo(rs.getString("tipo"))
                .calcMode(rs.getString("calc_mode"))
                .valore((BigDecimal) rs.getObject("valore"))
                .isRemainder(rs.getBoolean("is_remainder"))
                .ordine(rs.getInt("ordine"))
                .attivo(rs.getBoolean("attivo"))
                .createdAt(rs.getObject("created_at", LocalDateTime.class))
                .updatedAt(rs.getObject("updated_at", LocalDateTime.class))
                .build();
    }
}
