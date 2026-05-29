package it.gavia.sostitutoincloud.dao.mapper;

import it.gavia.sostitutoincloud.model.StatoPrenotazione;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

@Log4j2
public class StatoPrenotazioneRowMapper implements RowMapper<StatoPrenotazione> {

    @Override
    public StatoPrenotazione mapRow(ResultSet rs, int rowNum) throws SQLException {
        return StatoPrenotazione.builder()
                .id(rs.getInt("id"))
                .codice(rs.getString("codice"))
                .descrizione(rs.getString("descrizione"))
                .finale(rs.getBoolean("finale"))
                .attivo(rs.getBoolean("attivo"))
                .createdAt(rs.getObject("created_at", LocalDateTime.class))
                .updatedAt(rs.getObject("updated_at", LocalDateTime.class))
                .build();
    }
}
