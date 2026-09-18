package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.AuditLogRowMapper;
import it.gavia.sostitutoincloud.model.AuditLog;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.Collections;
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

    /**
     * Vista globale su tutti i tenant — solo per il super_admin, che non ha un proprio
     * tenant. Senza questa query un tenantId null cadrebbe su "fk_tenant_id = NULL",
     * che in SQL non è mai vero e restituirebbe zero righe.
     */
    public List<AuditLog> findAllOrderByCreatedAtDesc(Integer limit) {
        log.debug("AuditLogDAO.findAllOrderByCreatedAtDesc() - limit={}", limit);
        String sql = SELECT_ALL + " ORDER BY created_at DESC LIMIT ?";
        return jdbcTemplate.query(sql, auditLogRowMapper, limit);
    }

    public AuditLog insert(AuditLog entry) {
        String sql = "INSERT INTO audit_log " +
                     "(fk_tenant_id, fk_utente_id, user_email, action, entity_type, entity_id, details, ip_address) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
            ps.setObject(1, entry.getFkTenantId());
            ps.setObject(2, entry.getFkUtenteId());
            ps.setString(3, entry.getUserEmail());
            ps.setString(4, entry.getAction());
            ps.setString(5, entry.getEntityType());
            ps.setObject(6, entry.getEntityId());
            ps.setString(7, entry.getDetails());
            ps.setString(8, entry.getIpAddress());
            return ps;
        }, keyHolder);
        Integer id = keyHolder.getKey().intValue();
        entry.setId(id);
        log.debug("AuditLogDAO.insert() - azione={} tenantId={}", entry.getAction(), entry.getFkTenantId());
        return entry;
    }

    /**
     * Cancella le tracce di audit di un tenant: sia quelle scritte dagli utenti del
     * tenant (fk_tenant_id) sia quelle che hanno il tenant come entità (entity_type
     * 'Tenant'), scritte dal super_admin e quindi senza fk_tenant_id.
     * La FK è ON DELETE SET NULL: senza questa pulizia resterebbero righe orfane.
     */
    /**
     * Cancella le tracce di audit relative a un insieme di entità (es. entity_type
     * 'Booking'). entity_id non è una FK: senza questa pulizia le righe resterebbero
     * a puntare a record inesistenti.
     */
    public int deleteByEntity(String entityType, List<Integer> entityIds) {
        if (entityIds == null || entityIds.isEmpty()) {
            return 0;
        }
        String placeholders = String.join(",", Collections.nCopies(entityIds.size(), "?"));
        String sql = "DELETE FROM audit_log WHERE entity_type = ? AND entity_id IN (" + placeholders + ")";
        Object[] args = new Object[entityIds.size() + 1];
        args[0] = entityType;
        for (int i = 0; i < entityIds.size(); i++) {
            args[i + 1] = entityIds.get(i);
        }
        int righe = jdbcTemplate.update(sql, args);
        log.info("AuditLogDAO.deleteByEntity() - entityType={} entità={} eliminati={}",
                entityType, entityIds.size(), righe);
        return righe;
    }

    public int deleteByTenant(Integer tenantId) {
        String sql = "DELETE FROM audit_log WHERE fk_tenant_id = ? " +
                     "OR (entity_type = 'Tenant' AND entity_id = ?)";
        int righe = jdbcTemplate.update(sql, tenantId, tenantId);
        log.info("AuditLogDAO.deleteByTenant() - tenantId={} eliminati={}", tenantId, righe);
        return righe;
    }
}
