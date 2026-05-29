package it.gavia.sostitutoincloud.dao.mapper;

import it.gavia.sostitutoincloud.model.TipoDocumento;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

@Log4j2
public class TipoDocumentoRowMapper implements RowMapper<TipoDocumento> {

    @Override
    public TipoDocumento mapRow(ResultSet rs, int rowNum) throws SQLException {
        return TipoDocumento.builder()
                .id(rs.getInt("id"))
                .codice(rs.getString("codice"))
                .descrizione(rs.getString("descrizione"))
                .richiedeIva(rs.getBoolean("richiede_iva"))
                .trasmessoSdi(rs.getBoolean("trasmesso_sdi"))
                .attivo(rs.getBoolean("attivo"))
                .createdAt(rs.getObject("created_at", LocalDateTime.class))
                .updatedAt(rs.getObject("updated_at", LocalDateTime.class))
                .build();
    }
}
