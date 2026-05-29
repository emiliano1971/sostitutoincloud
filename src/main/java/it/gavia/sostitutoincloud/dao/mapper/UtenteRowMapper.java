package it.gavia.sostitutoincloud.dao.mapper;

import it.gavia.sostitutoincloud.model.Utente;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

@Log4j2
public class UtenteRowMapper implements RowMapper<Utente> {

    @Override
    public Utente mapRow(ResultSet rs, int rowNum) throws SQLException {
        return Utente.builder()
                .id(rs.getInt("id"))
                .fkTenantId(rs.getObject("fk_tenant_id", Integer.class))
                .email(rs.getString("email"))
                .firstName(rs.getString("first_name"))
                .lastName(rs.getString("last_name"))
                .ruolo(rs.getString("ruolo"))
                .attivo(rs.getBoolean("attivo"))
                .lastLogin(rs.getObject("last_login", LocalDateTime.class))
                .createdAt(rs.getObject("created_at", LocalDateTime.class))
                .updatedAt(rs.getObject("updated_at", LocalDateTime.class))
                .fkOwnerId(rs.getObject("fk_owner_id", Integer.class))
                .build();
    }
}
