package it.gavia.sostitutoincloud.dao.mapper;

import it.gavia.sostitutoincloud.model.ImportTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

public class ImportTemplateRowMapper implements RowMapper<ImportTemplate> {

    @Override
    public ImportTemplate mapRow(ResultSet rs, int rowNum) throws SQLException {
        return ImportTemplate.builder()
                .id(rs.getInt("id"))
                .fkTenantId(rs.getInt("fk_tenant_id"))
                .nome(rs.getString("nome"))
                .descrizione(rs.getString("descrizione"))
                .headerRow(rs.getInt("header_row"))
                .bookingMapping(rs.getString("booking_mapping"))
                .guestMapping(rs.getString("guest_mapping"))
                .createdAt(rs.getObject("created_at", LocalDateTime.class))
                .updatedAt(rs.getObject("updated_at", LocalDateTime.class))
                .build();
    }
}
