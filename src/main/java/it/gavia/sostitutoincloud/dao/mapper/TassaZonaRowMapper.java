package it.gavia.sostitutoincloud.dao.mapper;

import it.gavia.sostitutoincloud.model.TassaZona;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

public class TassaZonaRowMapper implements RowMapper<TassaZona> {

    @Override
    public TassaZona mapRow(ResultSet rs, int rowNum) throws SQLException {
        return TassaZona.builder()
                .id(rs.getInt("id"))
                .fkRegolaId(rs.getInt("fk_regola_id"))
                .label(rs.getString("label"))
                .reductionPct((Integer) rs.getObject("reduction_pct"))
                .createdAt(rs.getObject("created_at", LocalDateTime.class))
                .build();
    }
}
