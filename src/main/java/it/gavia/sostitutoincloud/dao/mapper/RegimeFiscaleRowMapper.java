package it.gavia.sostitutoincloud.dao.mapper;

import it.gavia.sostitutoincloud.model.RegimeFiscale;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

@Log4j2
public class RegimeFiscaleRowMapper implements RowMapper<RegimeFiscale> {

    @Override
    public RegimeFiscale mapRow(ResultSet rs, int rowNum) throws SQLException {
        return RegimeFiscale.builder()
                .id(rs.getInt("id"))
                .codice(rs.getString("codice"))
                .descrizione(rs.getString("descrizione"))
                .attivo(rs.getBoolean("attivo"))
                .metadata(rs.getString("metadata"))
                .createdAt(rs.getObject("created_at", LocalDateTime.class))
                .updatedAt(rs.getObject("updated_at", LocalDateTime.class))
                .build();
    }
}
