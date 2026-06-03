package it.gavia.sostitutoincloud.dao.mapper;

import it.gavia.sostitutoincloud.model.CanaleOta;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

@Log4j2
public class CanaleOtaRowMapper implements RowMapper<CanaleOta> {

    @Override
    public CanaleOta mapRow(ResultSet rs, int rowNum) throws SQLException {
        return CanaleOta.builder()
                .id(rs.getInt("id"))
                .codice(rs.getString("codice"))
                .nome(rs.getString("nome"))
                .commissioneDefaultPct((java.math.BigDecimal) rs.getObject("commissione_default_pct"))
                .tassaSoggiornoInclusa(rs.getBoolean("tassa_soggiorno_inclusa"))
                .touristTaxCollection(rs.getString("tourist_tax_collection"))
                .attivo(rs.getBoolean("attivo"))
                .createdAt(rs.getObject("created_at", LocalDateTime.class))
                .updatedAt(rs.getObject("updated_at", LocalDateTime.class))
                .build();
    }
}
