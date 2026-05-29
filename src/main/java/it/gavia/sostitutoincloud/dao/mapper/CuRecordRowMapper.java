package it.gavia.sostitutoincloud.dao.mapper;

import it.gavia.sostitutoincloud.model.CuRecord;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

@Log4j2
public class CuRecordRowMapper implements RowMapper<CuRecord> {

    @Override
    public CuRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return CuRecord.builder()
                .id(rs.getInt("id"))
                .fkTenantId(rs.getInt("fk_tenant_id"))
                .fkOwnerId(rs.getInt("fk_owner_id"))
                .taxYear(rs.getInt("tax_year"))
                .totalCompensi(rs.getBigDecimal("total_compensi"))
                .totalRitenute(rs.getBigDecimal("total_ritenute"))
                .stato(rs.getString("stato"))
                .generatedAt(rs.getObject("generated_at", LocalDateTime.class))
                .createdAt(rs.getObject("created_at", LocalDateTime.class))
                .updatedAt(rs.getObject("updated_at", LocalDateTime.class))
                .build();
    }
}
