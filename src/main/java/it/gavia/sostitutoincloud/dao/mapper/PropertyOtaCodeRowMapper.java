package it.gavia.sostitutoincloud.dao.mapper;

import it.gavia.sostitutoincloud.model.PropertyOtaCode;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

@Log4j2
public class PropertyOtaCodeRowMapper implements RowMapper<PropertyOtaCode> {

    @Override
    public PropertyOtaCode mapRow(ResultSet rs, int rowNum) throws SQLException {
        return PropertyOtaCode.builder()
                .id(rs.getInt("id"))
                .fkPropertyId(rs.getInt("fk_property_id"))
                .fkCanaleOtaId(rs.getInt("fk_canale_ota_id"))
                .externalId(rs.getString("external_id"))
                .createdAt(rs.getObject("created_at", LocalDateTime.class))
                .updatedAt(rs.getObject("updated_at", LocalDateTime.class))
                .build();
    }
}
