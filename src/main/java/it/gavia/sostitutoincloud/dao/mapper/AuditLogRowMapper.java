package it.gavia.sostitutoincloud.dao.mapper;

import it.gavia.sostitutoincloud.model.AuditLog;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

@Log4j2
public class AuditLogRowMapper implements RowMapper<AuditLog> {

    @Override
    public AuditLog mapRow(ResultSet rs, int rowNum) throws SQLException {
        return AuditLog.builder()
                .id(rs.getInt("id"))
                .fkTenantId(rs.getObject("fk_tenant_id", Integer.class))
                .fkUtenteId(rs.getObject("fk_utente_id", Integer.class))
                .userEmail(rs.getString("user_email"))
                .action(rs.getString("action"))
                .entityType(rs.getString("entity_type"))
                .entityId(rs.getObject("entity_id", Integer.class))
                .details(rs.getString("details"))
                .ipAddress(rs.getString("ip_address"))
                .createdAt(rs.getObject("created_at", LocalDateTime.class))
                .build();
    }
}
