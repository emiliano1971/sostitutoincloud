package it.gavia.sostitutoincloud.dao.mapper;

import it.gavia.sostitutoincloud.model.Property;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

@Log4j2
public class PropertyRowMapper implements RowMapper<Property> {

    @Override
    public Property mapRow(ResultSet rs, int rowNum) throws SQLException {
        return Property.builder()
                .id(rs.getInt("id"))
                .fkTenantId(rs.getInt("fk_tenant_id"))
                .fkOwnerId(rs.getInt("fk_owner_id"))
                .fkPmUserId((Integer) rs.getObject("fk_pm_user_id"))
                .fkTipoImmobileId((Integer) rs.getObject("fk_tipo_immobile_id"))
                .internalCode(rs.getString("internal_code"))
                .displayName(rs.getString("display_name"))
                .address(rs.getString("address"))
                .city(rs.getString("city"))
                .region(rs.getString("region"))
                .cinCode(rs.getString("cin_code"))
                .attivo(rs.getBoolean("attivo"))
                .primoImmobile(rs.getBoolean("primo_immobile"))
                .createdAt(rs.getObject("created_at", LocalDateTime.class))
                .updatedAt(rs.getObject("updated_at", LocalDateTime.class))
                .build();
    }
}
