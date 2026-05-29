package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.AuditLogRowMapper;
import it.gavia.sostitutoincloud.model.AuditLog;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Log4j2
@Repository
public class AuditLogDAO {

    private static final String SELECT_ALL =
            "SELECT id, fk_tenant_id, fk_utente_id, user_email, action, entity_type, " +
            "entity_id, details, ip_address, created_at FROM audit_log";

    private final JdbcTemplate jdbcTemplate;
    private final AuditLogRowMapper auditLogRowMapper = new AuditLogRowMapper();

    public AuditLogDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AuditLog> findByTenantId(Integer tenantId) {
        log.debug("AuditLogDAO.findByTenantId() - tenantId={}", tenantId);
        return jdbcTemplate.query(SELECT_ALL + " WHERE fk_tenant_id = ? ORDER BY created_at DESC", auditLogRowMapper, tenantId);
    }

    public List<AuditLog> findByUserId(Integer userId) {
        log.debug("AuditLogDAO.findByUserId() - userId={}", userId);
        return jdbcTemplate.query(SELECT_ALL + " WHERE fk_utente_id = ? ORDER BY created_at DESC", auditLogRowMapper, userId);
    }

    public List<AuditLog> findByTenantIdAndEntityName(Integer tenantId, String entityName) {
        log.debug("AuditLogDAO.findByTenantIdAndEntityName() - tenantId={}, entityName={}", tenantId, entityName);
        String sql = SELECT_ALL + " WHERE fk_tenant_id = ? AND entity_type = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, auditLogRowMapper, tenantId, entityName);
    }

    public List<AuditLog> findByTenantIdAndEntityId(Integer tenantId, Integer entityId) {
        log.debug("AuditLogDAO.findByTenantIdAndEntityId() - tenantId={}, entityId={}", tenantId, entityId);
        String sql = SELECT_ALL + " WHERE fk_tenant_id = ? AND entity_id = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, auditLogRowMapper, tenantId, entityId);
    }

    public List<AuditLog> findByTenantIdOrderByCreatedAtDesc(Integer tenantId, Integer limit) {
        log.debug("AuditLogDAO.findByTenantIdOrderByCreatedAtDesc() - tenantId={}, limit={}", tenantId, limit);
        String sql = SELECT_ALL + " WHERE fk_tenant_id = ? ORDER BY created_at DESC LIMIT ?";
        return jdbcTemplate.query(sql, auditLogRowMapper, tenantId, limit);
    }
}
