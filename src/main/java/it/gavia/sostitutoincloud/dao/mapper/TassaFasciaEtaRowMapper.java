package it.gavia.sostitutoincloud.dao.mapper;

import it.gavia.sostitutoincloud.model.TassaFasciaEta;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

public class TassaFasciaEtaRowMapper implements RowMapper<TassaFasciaEta> {

    @Override
    public TassaFasciaEta mapRow(ResultSet rs, int rowNum) throws SQLException {
        return TassaFasciaEta.builder()
                .id(rs.getInt("id"))
                .fkRegolaId(rs.getInt("fk_regola_id"))
                .label(rs.getString("label"))
                .minAge((Integer) rs.getObject("min_age"))
                .maxAge((Integer) rs.getObject("max_age"))
                .reductionPct((Integer) rs.getObject("reduction_pct"))
                .createdAt(rs.getObject("created_at", LocalDateTime.class))
                .build();
    }
}
