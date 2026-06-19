package it.gavia.sostitutoincloud.dao.mapper;

import it.gavia.sostitutoincloud.model.TassaStagione;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

public class TassaStagioneRowMapper implements RowMapper<TassaStagione> {

    @Override
    public TassaStagione mapRow(ResultSet rs, int rowNum) throws SQLException {
        return TassaStagione.builder()
                .id(rs.getInt("id"))
                .fkRegolaId(rs.getInt("fk_regola_id"))
                .label(rs.getString("label"))
                .startMonth((Integer) rs.getObject("start_month"))
                .startDay((Integer) rs.getObject("start_day"))
                .endMonth((Integer) rs.getObject("end_month"))
                .endDay((Integer) rs.getObject("end_day"))
                .reductionPct((Integer) rs.getObject("reduction_pct"))
                .createdAt(rs.getObject("created_at", LocalDateTime.class))
                .build();
    }
}
