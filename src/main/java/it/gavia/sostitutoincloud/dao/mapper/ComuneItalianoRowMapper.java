package it.gavia.sostitutoincloud.dao.mapper;

import it.gavia.sostitutoincloud.model.ComuneItaliano;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ComuneItalianoRowMapper implements RowMapper<ComuneItaliano> {

    @Override
    public ComuneItaliano mapRow(ResultSet rs, int rowNum) throws SQLException {
        return ComuneItaliano.builder()
                .id(rs.getInt("id"))
                .nome(rs.getString("nome"))
                .siglaProvincia(rs.getString("sigla_provincia"))
                .regione(rs.getString("regione"))
                .codiceBelfiore(rs.getString("codice_belfiore"))
                .build();
    }
}
